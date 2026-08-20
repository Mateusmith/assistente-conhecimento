param(
    [string]$BaseUrl = "http://localhost:8083",
    [string]$KeycloakUrl = "http://localhost:18084",
    [string]$PrometheusUrl = "http://localhost:19093"
)

$ErrorActionPreference = "Stop"
$arquivo = Join-Path $PSScriptRoot "..\postman\politica-reembolso.md"
$arquivoOcr = Join-Path ([System.IO.Path]::GetTempPath()) "assistente-conhecimento-ocr-$([guid]::NewGuid()).pdf"
$arquivoAntivirus = Join-Path ([System.IO.Path]::GetTempPath()) "assistente-conhecimento-antivirus-$([guid]::NewGuid()).txt"
. "$PSScriptRoot\New-OcrFixture.ps1"

function Obter-Token([string]$usuario) {
    $resposta = Invoke-RestMethod -Method Post `
        -Uri "$KeycloakUrl/realms/contextpilot/protocol/openid-connect/token" `
        -ContentType "application/x-www-form-urlencoded" `
        -Body @{ grant_type = "password"; client_id = "contextpilot-postman"; username = $usuario; password = "context123" }
    return $resposta.access_token
}

function Cabecalho([string]$token) {
    return @{ Authorization = "Bearer $token" }
}

function Obter-PayloadJwt([string]$token) {
    $segmento = $token.Split('.')[1].Replace('-', '+').Replace('_', '/')
    while ($segmento.Length % 4 -ne 0) { $segmento += '=' }
    return [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($segmento)) | ConvertFrom-Json
}

function Esperar-Metrica([string]$consulta, [string]$descricao) {
    $consultaCodificada = [uri]::EscapeDataString($consulta)
    for ($tentativa = 0; $tentativa -lt 30; $tentativa++) {
        $resposta = Invoke-RestMethod "$PrometheusUrl/api/v1/query?query=$consultaCodificada"
        if ($resposta.status -eq "success" -and $resposta.data.result.Count -gt 0 `
                -and [double]$resposta.data.result[0].value[1] -gt 0) {
            return
        }
        Start-Sleep -Milliseconds 500
    }
    throw "Metrica agregada ausente no Prometheus: $descricao."
}

Write-Host "[1/23] Validando saude, autenticacao e audiencia JWT"
$saude = Invoke-RestMethod "$BaseUrl/actuator/health"
if ($saude.status -ne "UP") { throw "Aplicacao indisponivel." }
$tokenAna = Obter-Token "ana"
$tokenCarla = Obter-Token "carla"
$audiencias = @((Obter-PayloadJwt $tokenAna).aud)
if ("contextpilot-api" -notin $audiencias) { throw "Token nao contem a audiencia da API." }

Write-Host "[2/23] Criando espaco e membro"
$espaco = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/espacos" -Headers (Cabecalho $tokenAna) `
    -ContentType "application/json" -Body (@{ nome = "Operacoes Financeiras $(Get-Date -Format HHmmss)"; descricao = "Carga automatizada" } | ConvertTo-Json)
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/membros" -Headers (Cabecalho $tokenAna) `
    -ContentType "application/json" -Body '{"usuarioId":"carla","papel":"LEITOR"}' | Out-Null

Write-Host "[3/23] Enviando documento, ClamAV, MinIO e metadados"
$documento = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/documentos" `
    -Headers (Cabecalho $tokenAna) -Form @{
        titulo = "Politica de reembolso"
        visibilidade = "RESTRITO"
        metadados = '{"departamento":"financeiro","tags":["reembolso","politica"]}'
        arquivo = Get-Item $arquivo
    }
if ($documento.armazenamento -ne "S3" -or $documento.resultadoAntivirus -ne "LIMPO") {
    throw "Upload nao comprovou armazenamento S3 e verificacao limpa do ClamAV."
}
for ($tentativa = 0; $tentativa -lt 60; $tentativa++) {
    Start-Sleep -Milliseconds 500
    $estado = Invoke-RestMethod "$BaseUrl/api/v1/espacos/$($espaco.id)/documentos/$($documento.id)" -Headers (Cabecalho $tokenAna)
    if ($estado.estado -eq "PRONTO") { break }
    if ($estado.estado -eq "FALHOU") { throw "Ingestao falhou: $($estado.erroProcessamento)" }
}
if ($estado.estado -ne "PRONTO") { throw "Tempo limite da ingestao excedido." }
if ($estado.origemTexto -ne "NATIVO" -or $estado.paginasOcr -ne 0) {
    throw "Documento textual nao registrou a origem nativa esperada."
}
$conteudoBaixado = Invoke-WebRequest "$BaseUrl/api/v1/espacos/$($espaco.id)/documentos/$($documento.id)/conteudo" `
    -Headers (Cabecalho $tokenAna)
if ($conteudoBaixado.Content -notmatch "30 dias") { throw "Documento nao foi recuperado corretamente do MinIO." }

Write-Host "[4/23] Provando que a ACL bloqueia o documento"
try {
    Invoke-RestMethod "$BaseUrl/api/v1/espacos/$($espaco.id)/documentos/$($documento.id)" -Headers (Cabecalho $tokenCarla) | Out-Null
    throw "Documento restrito ficou visivel sem permissao."
} catch {
    if ($_.Exception.Response.StatusCode.value__ -ne 404) { throw }
}
$recusa = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/consultas" -Headers (Cabecalho $tokenCarla) `
    -ContentType "application/json" -Body '{"pergunta":"Qual e o prazo para solicitar reembolso?"}'
if (!$recusa.recusada -or $recusa.fontes.Count -ne 0) { throw "A consulta sem permissao vazou contexto." }

Write-Host "[5/23] Concedendo acesso e repetindo a consulta"
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/documentos/$($documento.id)/permissoes" `
    -Headers (Cabecalho $tokenAna) -ContentType "application/json" -Body '{"usuarioId":"carla","nivel":"LEITURA"}' | Out-Null
$resposta = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/consultas" -Headers (Cabecalho $tokenCarla) `
    -ContentType "application/json" -Body '{"pergunta":"Qual e o prazo para solicitar reembolso?"}'
if ($resposta.recusada -or $resposta.resposta -notmatch '\[F1\]' -or $resposta.fontes[0].documentoId -ne $documento.id) {
    throw "Resposta RAG nao possui a citacao esperada."
}

Write-Host "[6/23] Registrando feedback"
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/consultas/$($resposta.consultaId)/feedback" `
    -Headers (Cabecalho $tokenCarla) -ContentType "application/json" -Body '{"util":true,"comentario":"Validado pelo smoke test"}' | Out-Null

Write-Host "[7/23] Executando avaliacao quantitativa e comparando baseline"
$conjunto = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/avaliacoes" -Headers (Cabecalho $tokenAna) `
    -ContentType "application/json" -Body '{"nome":"Regressao financeira","descricao":"Carga automatizada"}'
$caso = @{
    pergunta = "Qual e o prazo para solicitar reembolso?"
    termosEsperados = @("30 dias")
    documentosEsperados = @($documento.id)
    deveRecusar = $false
    latenciaMaximaMs = 10000
    custoMaximoUsd = 1
}
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/avaliacoes/$($conjunto.id)/casos" `
    -Headers (Cabecalho $tokenAna) -ContentType "application/json" -Body ($caso | ConvertTo-Json) | Out-Null
$execucaoBase = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/avaliacoes/$($conjunto.id)/execucoes" -Headers (Cabecalho $tokenAna)
$execucao = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/avaliacoes/$($conjunto.id)/execucoes" -Headers (Cabecalho $tokenAna)
$comparacaoAvaliacao = Invoke-RestMethod `
    "$BaseUrl/api/v1/espacos/$($espaco.id)/avaliacoes/$($conjunto.id)/execucoes/$($execucao.id)/comparacoes/$($execucaoBase.id)" `
    -Headers (Cabecalho $tokenAna)
if ($execucao.taxaAcerto -ne 1 -or $execucao.recallMedio -ne 1 -or $execucao.precisaoMedia -ne 1 `
        -or $execucao.mrrMedio -ne 1 -or $comparacaoAvaliacao.regressao) {
    throw "A avaliacao quantitativa ou a comparacao com baseline falhou."
}

Write-Host "[8/23] Validando conversa, memoria e idempotencia"
$conversa = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/conversas" `
    -Headers (Cabecalho $tokenCarla) -ContentType "application/json" -Body '{"titulo":"Reembolso"}'
$cabecalhoIdempotente = Cabecalho $tokenCarla
$cabecalhoIdempotente["Idempotency-Key"] = "smoke-conversa-001"
$interacao = Invoke-RestMethod -Method Post `
    -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/conversas/$($conversa.id)/mensagens" `
    -Headers $cabecalhoIdempotente -ContentType "application/json" `
    -Body '{"pergunta":"Qual e o prazo para solicitar reembolso?"}'
$interacaoRepetida = Invoke-RestMethod -Method Post `
    -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/conversas/$($conversa.id)/mensagens" `
    -Headers $cabecalhoIdempotente -ContentType "application/json" `
    -Body '{"pergunta":"Qual e o prazo para solicitar reembolso?"}'
$segundaInteracao = Invoke-RestMethod -Method Post `
    -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/conversas/$($conversa.id)/mensagens" `
    -Headers (Cabecalho $tokenCarla) -ContentType "application/json" `
    -Body '{"pergunta":"E quais dados a solicitacao deve conter?"}'
$detalheConversa = Invoke-RestMethod `
    "$BaseUrl/api/v1/espacos/$($espaco.id)/conversas/$($conversa.id)" -Headers (Cabecalho $tokenCarla)
if ($interacao.resposta.consultaId -ne $interacaoRepetida.resposta.consultaId `
        -or $segundaInteracao.resposta.recusada -or @($detalheConversa.mensagens).Count -ne 4) {
    throw "Memoria ou idempotencia da conversa nao foi comprovada."
}

Write-Host "[9/23] Validando streaming somente depois das fontes"
$cabecalhoStreaming = Cabecalho $tokenCarla
$cabecalhoStreaming["Accept"] = "text/event-stream"
$cabecalhoStreaming["Idempotency-Key"] = "smoke-stream-001"
$stream = Invoke-WebRequest -Method Post `
    -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/conversas/$($conversa.id)/mensagens/stream" `
    -Headers $cabecalhoStreaming -ContentType "application/json" -TimeoutSec 120 `
    -Body '{"pergunta":"Pode resumir o prazo novamente?"}'
if ($stream.Content -notmatch 'event:fontes' -or $stream.Content -notmatch 'event:resposta' `
        -or $stream.Content -notmatch 'event:concluido') {
    throw "O fluxo SSE nao publicou fontes, resposta e conclusao."
}

Write-Host "[10/23] Validando auditoria"
$eventos = Invoke-RestMethod "$BaseUrl/api/v1/espacos/$($espaco.id)/auditoria" -Headers (Cabecalho $tokenAna)
if ($eventos.Count -lt 6) { throw "Trilha de auditoria incompleta." }

Write-Host "[11/23] Validando metricas protegidas"
$semCredencial = Invoke-WebRequest "$BaseUrl/actuator/prometheus" -SkipHttpErrorCheck
if ($semCredencial.StatusCode -ne 401) { throw "Metricas deveriam exigir autenticacao HTTP Basic." }
$credencial = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("prometheus:contextpilot_metrics_local"))
$metricas = Invoke-WebRequest "$BaseUrl/actuator/prometheus" -Headers @{ Authorization = "Basic $credencial" }
if ($metricas.StatusCode -ne 200) { throw "Endpoint protegido de metricas indisponivel." }
Esperar-Metrica 'sum(contextpilot_rag_consultas_total)' "consultas RAG"
Esperar-Metrica 'sum(contextpilot_antivirus_verificacoes_total{resultado="limpo"})' "antivirus"
Esperar-Metrica 'sum(contextpilot_armazenamento_operacoes_total{operacao="gravar",resultado="sucesso"})' "armazenamento"

Write-Host "[12/23] Validando servidor MCP"
$cabecalhosMcp = @{
    Authorization = "Bearer $tokenAna"
    Accept = "application/json, text/event-stream"
}
$inicializacaoMcp = Invoke-RestMethod -Method Post -Uri "$BaseUrl/mcp" -Headers $cabecalhosMcp `
    -ContentType "application/json" `
    -Body '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"smoke-test","version":"1.0.0"}}}'
if ($inicializacaoMcp.result.serverInfo.name -ne "assistente-conhecimento-mcp") {
    throw "Servidor MCP nao respondeu com a identidade esperada."
}
$ferramentasMcp = Invoke-RestMethod -Method Post -Uri "$BaseUrl/mcp" -Headers $cabecalhosMcp `
    -ContentType "application/json" -Body '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'
$nomesFerramentas = @($ferramentasMcp.result.tools | ForEach-Object { $_.name })
$ferramentasEsperadas = @("listarDocumentos", "buscarConhecimento", "consultarComFontes")
foreach ($ferramenta in $ferramentasEsperadas) {
    if ($ferramenta -notin $nomesFerramentas) { throw "Ferramenta MCP ausente: $ferramenta" }
}

Write-Host "[13/23] Preparando assinatura inofensiva de teste do antivirus"
$assinaturaTeste = 'X5O!P%@AP[4\PZX54(P^)7CC)7}' + '$EICAR-STANDARD-' + 'ANTIVIRUS-TEST-FILE!$H+H*'
[System.IO.File]::WriteAllText($arquivoAntivirus, $assinaturaTeste, [System.Text.Encoding]::ASCII)

Write-Host "[14/23] Provando bloqueio de malware antes da persistencia"
$ameacaRejeitada = $false
try {
    Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/documentos" `
        -Headers (Cabecalho $tokenAna) -Form @{ titulo = "Arquivo de teste antivirus"; visibilidade = "ESPACO"; arquivo = Get-Item $arquivoAntivirus } | Out-Null
} catch {
    if ([int]$_.Exception.Response.StatusCode -eq 422) { $ameacaRejeitada = $true } else { throw }
} finally {
    Remove-Item -LiteralPath $arquivoAntivirus -Force -ErrorAction SilentlyContinue
}
if (!$ameacaRejeitada) { throw "O ClamAV nao rejeitou a assinatura de teste esperada." }
$documentosAposAmeaca = @(Invoke-RestMethod "$BaseUrl/api/v1/espacos/$($espaco.id)/documentos" -Headers (Cabecalho $tokenAna))
if ($documentosAposAmeaca.Count -ne 1) { throw "O arquivo rejeitado deixou um registro persistido." }

Write-Host "[15/23] Gerando PDF composto somente por imagem"
New-OcrFixture $arquivoOcr

Write-Host "[16/23] Validando OCR real com Tesseract"
$documentoOcr = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/documentos" `
    -Headers (Cabecalho $tokenAna) -Form @{ titulo = "Politica digitalizada"; visibilidade = "ESPACO"; arquivo = Get-Item $arquivoOcr }
for ($tentativa = 0; $tentativa -lt 60; $tentativa++) {
    Start-Sleep -Milliseconds 500
    $estadoOcr = Invoke-RestMethod "$BaseUrl/api/v1/espacos/$($espaco.id)/documentos/$($documentoOcr.id)" -Headers (Cabecalho $tokenAna)
    if ($estadoOcr.estado -eq "PRONTO") { break }
    if ($estadoOcr.estado -eq "FALHOU") { throw "OCR falhou: $($estadoOcr.erroProcessamento)" }
}
Remove-Item -LiteralPath $arquivoOcr -Force -ErrorAction SilentlyContinue
if ($estadoOcr.estado -ne "PRONTO" -or $estadoOcr.origemTexto -ne "OCR" -or $estadoOcr.paginasOcr -ne 1) {
    throw "O PDF digitalizado nao comprovou o fluxo OCR esperado."
}

Write-Host "[17/23] Comparando busca hibrida, semantica e textual"
$comparacao = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/buscas/comparacoes" `
    -Headers (Cabecalho $tokenCarla) -ContentType "application/json" `
    -Body '{"pergunta":"Qual e o prazo para solicitar reembolso?","filtros":{"metadados":{"departamento":"financeiro"},"tags":["reembolso"]}}'
if ($comparacao.resultados.Count -ne 3) { throw "A comparacao nao executou as tres estrategias." }

Write-Host "[18/23] Consultando governanca, quotas e consumo"
$uso = Invoke-RestMethod "$BaseUrl/api/v1/espacos/$($espaco.id)/governanca/uso" -Headers (Cabecalho $tokenAna)
if ($uso.armazenamentoUsadoBytes -le 0 -or $uso.consultasHoje -lt 3) {
    throw "O uso do espaco nao contabilizou armazenamento e consultas."
}

Write-Host "[19/23] Iniciando reindexacao blue-green"
$indicesAntes = Invoke-RestMethod "$BaseUrl/api/v1/espacos/$($espaco.id)/indices-embedding" -Headers (Cabecalho $tokenAna)
$indiceAnterior = $indicesAntes | Where-Object { $_.estado -eq "ATIVO" } | Select-Object -First 1
$novoIndice = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/indices-embedding" `
    -Headers (Cabecalho $tokenAna) -ContentType "application/json" -Body '{"modelo":"local-hashing-v2"}'
if ($novoIndice.estado -ne "CONSTRUINDO") { throw "A reindexacao nao iniciou em modo blue-green." }

Write-Host "[20/23] Aguardando troca atomica do indice"
for ($tentativa = 0; $tentativa -lt 60; $tentativa++) {
    Start-Sleep -Milliseconds 500
    $indicesDepois = Invoke-RestMethod "$BaseUrl/api/v1/espacos/$($espaco.id)/indices-embedding" -Headers (Cabecalho $tokenAna)
    $indiceV2 = $indicesDepois | Where-Object { $_.id -eq $novoIndice.id } | Select-Object -First 1
    if ($indiceV2.estado -eq "ATIVO") { break }
    if ($indiceV2.estado -eq "FALHOU") { throw "Reindexacao falhou: $($indiceV2.erro)" }
}
if ([string]$indiceV2.estado -ne "ATIVO" -or [int]$indiceV2.progressoPercentual -ne 100) {
    throw "O indice novo nao foi ativado integralmente: estado=$($indiceV2.estado), progresso=$($indiceV2.progressoPercentual)."
}
$respostaV2 = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/consultas" `
    -Headers (Cabecalho $tokenCarla) -ContentType "application/json" `
    -Body '{"pergunta":"Qual e o prazo para solicitar reembolso?"}'
if ($respostaV2.modeloEmbedding -ne "local-hashing-v2" -or $respostaV2.recusada) {
    throw "A consulta nao usou o novo indice ativo."
}

Write-Host "[21/23] Validando rollback do indice"
$rollback = Invoke-RestMethod -Method Post `
    -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/indices-embedding/$($indiceAnterior.id)/ativacao" `
    -Headers (Cabecalho $tokenAna)
if ($rollback.estado -ne "ATIVO" -or $rollback.modelo -ne "local-hashing-v1") {
    throw "O rollback nao restaurou o indice anterior."
}

Write-Host "[22/23] Exportando dados pessoais de Carla"
$exportacao = Invoke-RestMethod "$BaseUrl/api/v1/privacidade/exportacao" -Headers (Cabecalho $tokenCarla)
if ($exportacao.usuarioId -ne "carla" -or $exportacao.espacos.Count -lt 1 `
        -or $exportacao.conversas.Count -lt 1) {
    throw "A exportacao LGPD nao retornou os dados esperados."
}

Write-Host "[23/23] Fluxo completo aprovado" -ForegroundColor Green
[pscustomobject]@{
    espacoId = $espaco.id
    documentoId = $documento.id
    consultaId = $resposta.consultaId
    taxaAvaliacao = $execucao.taxaAcerto
    recallAvaliacao = $execucao.recallMedio
    conversaId = $conversa.id
    mensagensConversa = $detalheConversa.mensagens.Count
    streamingValidado = $true
    eventosAuditoria = $eventos.Count
    ferramentasMcp = $nomesFerramentas.Count
    ameacaRejeitada = $ameacaRejeitada
    documentoOcrId = $documentoOcr.id
    paginasOcr = $estadoOcr.paginasOcr
    estrategiasComparadas = $comparacao.resultados.Count
    indiceAtivado = $indiceV2.modelo
    rollbackValidado = $rollback.modelo
    consultasHoje = $uso.consultasHoje
} | Format-List

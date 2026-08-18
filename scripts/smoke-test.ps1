param(
    [string]$BaseUrl = "http://localhost:8083",
    [string]$KeycloakUrl = "http://localhost:18084"
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

Write-Host "[1/15] Validando saude e autenticacao"
$saude = Invoke-RestMethod "$BaseUrl/actuator/health"
if ($saude.status -ne "UP") { throw "Aplicacao indisponivel." }
$tokenAna = Obter-Token "ana"
$tokenCarla = Obter-Token "carla"

Write-Host "[2/15] Criando espaco e membro"
$espaco = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/espacos" -Headers (Cabecalho $tokenAna) `
    -ContentType "application/json" -Body (@{ nome = "Operacoes Financeiras $(Get-Date -Format HHmmss)"; descricao = "Carga automatizada" } | ConvertTo-Json)
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/membros" -Headers (Cabecalho $tokenAna) `
    -ContentType "application/json" -Body '{"usuarioId":"carla","papel":"LEITOR"}' | Out-Null

Write-Host "[3/15] Enviando documento, ClamAV e MinIO"
$documento = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/documentos" `
    -Headers (Cabecalho $tokenAna) -Form @{ titulo = "Politica de reembolso"; visibilidade = "RESTRITO"; arquivo = Get-Item $arquivo }
if ($documento.armazenamento -ne "S3" -or $documento.resultadoAntivirus -ne "LIMPO") {
    throw "Upload nao comprovou armazenamento S3 e verificacao limpa do ClamAV."
}
for ($tentativa = 0; $tentativa -lt 30; $tentativa++) {
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

Write-Host "[4/15] Provando que a ACL bloqueia o documento"
try {
    Invoke-RestMethod "$BaseUrl/api/v1/espacos/$($espaco.id)/documentos/$($documento.id)" -Headers (Cabecalho $tokenCarla) | Out-Null
    throw "Documento restrito ficou visivel sem permissao."
} catch {
    if ($_.Exception.Response.StatusCode.value__ -ne 404) { throw }
}
$recusa = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/consultas" -Headers (Cabecalho $tokenCarla) `
    -ContentType "application/json" -Body '{"pergunta":"Qual e o prazo para solicitar reembolso?"}'
if (!$recusa.recusada -or $recusa.fontes.Count -ne 0) { throw "A consulta sem permissao vazou contexto." }

Write-Host "[5/15] Concedendo acesso e repetindo a consulta"
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/documentos/$($documento.id)/permissoes" `
    -Headers (Cabecalho $tokenAna) -ContentType "application/json" -Body '{"usuarioId":"carla","nivel":"LEITURA"}' | Out-Null
$resposta = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/consultas" -Headers (Cabecalho $tokenCarla) `
    -ContentType "application/json" -Body '{"pergunta":"Qual e o prazo para solicitar reembolso?"}'
if ($resposta.recusada -or $resposta.resposta -notmatch '\[F1\]' -or $resposta.fontes[0].documentoId -ne $documento.id) {
    throw "Resposta RAG nao possui a citacao esperada."
}

Write-Host "[6/15] Registrando feedback"
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/consultas/$($resposta.consultaId)/feedback" `
    -Headers (Cabecalho $tokenCarla) -ContentType "application/json" -Body '{"util":true,"comentario":"Validado pelo smoke test"}' | Out-Null

Write-Host "[7/15] Criando e executando avaliacao"
$conjunto = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/avaliacoes" -Headers (Cabecalho $tokenAna) `
    -ContentType "application/json" -Body '{"nome":"Regressao financeira","descricao":"Carga automatizada"}'
$caso = @{ pergunta = "Qual e o prazo para solicitar reembolso?"; termosEsperados = @("30 dias"); documentosEsperados = @($documento.id); deveRecusar = $false }
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/avaliacoes/$($conjunto.id)/casos" `
    -Headers (Cabecalho $tokenAna) -ContentType "application/json" -Body ($caso | ConvertTo-Json) | Out-Null
$execucao = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/avaliacoes/$($conjunto.id)/execucoes" -Headers (Cabecalho $tokenAna)
if ($execucao.taxaAcerto -ne 1) { throw "A avaliacao nao atingiu 100%." }

Write-Host "[8/15] Validando auditoria"
$eventos = Invoke-RestMethod "$BaseUrl/api/v1/espacos/$($espaco.id)/auditoria" -Headers (Cabecalho $tokenAna)
if ($eventos.Count -lt 6) { throw "Trilha de auditoria incompleta." }

Write-Host "[9/15] Validando metricas protegidas"
$credencial = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("prometheus:contextpilot_metrics_local"))
$metricas = Invoke-WebRequest "$BaseUrl/actuator/prometheus" -Headers @{ Authorization = "Basic $credencial" }
if ($metricas.Content -notmatch "contextpilot_rag_consultas_total") { throw "Metricas de RAG ausentes." }
if ($metricas.Content -notmatch 'contextpilot_antivirus_verificacoes_total\{[^}]*resultado="limpo"') {
    throw "Metrica de verificacao limpa do antivirus ausente."
}
if ($metricas.Content -notmatch 'contextpilot_armazenamento_operacoes_total\{[^}]*operacao="gravar"[^}]*resultado="sucesso"') {
    throw "Metrica de gravacao no armazenamento de objetos ausente."
}

Write-Host "[10/15] Validando servidor MCP"
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

Write-Host "[11/15] Preparando assinatura inofensiva de teste do antivirus"
$assinaturaTeste = 'X5O!P%@AP[4\PZX54(P^)7CC)7}' + '$EICAR-STANDARD-' + 'ANTIVIRUS-TEST-FILE!$H+H*'
[System.IO.File]::WriteAllText($arquivoAntivirus, $assinaturaTeste, [System.Text.Encoding]::ASCII)

Write-Host "[12/15] Provando bloqueio de malware antes da persistencia"
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

Write-Host "[13/15] Gerando PDF composto somente por imagem"
New-OcrFixture $arquivoOcr

Write-Host "[14/15] Validando OCR real com Tesseract"
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

Write-Host "[15/15] Fluxo completo aprovado" -ForegroundColor Green
[pscustomobject]@{
    espacoId = $espaco.id
    documentoId = $documento.id
    consultaId = $resposta.consultaId
    taxaAvaliacao = $execucao.taxaAcerto
    eventosAuditoria = $eventos.Count
    ferramentasMcp = $nomesFerramentas.Count
    ameacaRejeitada = $ameacaRejeitada
    documentoOcrId = $documentoOcr.id
    paginasOcr = $estadoOcr.paginasOcr
} | Format-List

param(
    [string]$BaseUrl = "http://localhost:8083",
    [string]$KeycloakUrl = "http://localhost:18084"
)

$ErrorActionPreference = "Stop"
$arquivo = Join-Path $PSScriptRoot "..\postman\politica-reembolso.md"

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

Write-Host "[1/11] Validando saude e autenticacao"
$saude = Invoke-RestMethod "$BaseUrl/actuator/health"
if ($saude.status -ne "UP") { throw "Aplicacao indisponivel." }
$tokenAna = Obter-Token "ana"
$tokenCarla = Obter-Token "carla"

Write-Host "[2/11] Criando espaco e membro"
$espaco = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/espacos" -Headers (Cabecalho $tokenAna) `
    -ContentType "application/json" -Body (@{ nome = "Operacoes Financeiras $(Get-Date -Format HHmmss)"; descricao = "Carga automatizada" } | ConvertTo-Json)
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/membros" -Headers (Cabecalho $tokenAna) `
    -ContentType "application/json" -Body '{"usuarioId":"carla","papel":"LEITOR"}' | Out-Null

Write-Host "[3/11] Enviando e indexando documento restrito"
$documento = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/documentos" `
    -Headers (Cabecalho $tokenAna) -Form @{ titulo = "Politica de reembolso"; visibilidade = "RESTRITO"; arquivo = Get-Item $arquivo }
for ($tentativa = 0; $tentativa -lt 30; $tentativa++) {
    Start-Sleep -Milliseconds 500
    $estado = Invoke-RestMethod "$BaseUrl/api/v1/espacos/$($espaco.id)/documentos/$($documento.id)" -Headers (Cabecalho $tokenAna)
    if ($estado.estado -eq "PRONTO") { break }
    if ($estado.estado -eq "FALHOU") { throw "Ingestao falhou: $($estado.erroProcessamento)" }
}
if ($estado.estado -ne "PRONTO") { throw "Tempo limite da ingestao excedido." }

Write-Host "[4/11] Provando que a ACL bloqueia o documento"
try {
    Invoke-RestMethod "$BaseUrl/api/v1/espacos/$($espaco.id)/documentos/$($documento.id)" -Headers (Cabecalho $tokenCarla) | Out-Null
    throw "Documento restrito ficou visivel sem permissao."
} catch {
    if ($_.Exception.Response.StatusCode.value__ -ne 404) { throw }
}
$recusa = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/consultas" -Headers (Cabecalho $tokenCarla) `
    -ContentType "application/json" -Body '{"pergunta":"Qual e o prazo para solicitar reembolso?"}'
if (!$recusa.recusada -or $recusa.fontes.Count -ne 0) { throw "A consulta sem permissao vazou contexto." }

Write-Host "[5/11] Concedendo acesso e repetindo a consulta"
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/documentos/$($documento.id)/permissoes" `
    -Headers (Cabecalho $tokenAna) -ContentType "application/json" -Body '{"usuarioId":"carla","nivel":"LEITURA"}' | Out-Null
$resposta = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/consultas" -Headers (Cabecalho $tokenCarla) `
    -ContentType "application/json" -Body '{"pergunta":"Qual e o prazo para solicitar reembolso?"}'
if ($resposta.recusada -or $resposta.resposta -notmatch '\[F1\]' -or $resposta.fontes[0].documentoId -ne $documento.id) {
    throw "Resposta RAG nao possui a citacao esperada."
}

Write-Host "[6/11] Registrando feedback"
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/consultas/$($resposta.consultaId)/feedback" `
    -Headers (Cabecalho $tokenCarla) -ContentType "application/json" -Body '{"util":true,"comentario":"Validado pelo smoke test"}' | Out-Null

Write-Host "[7/11] Criando e executando avaliacao"
$conjunto = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/avaliacoes" -Headers (Cabecalho $tokenAna) `
    -ContentType "application/json" -Body '{"nome":"Regressao financeira","descricao":"Carga automatizada"}'
$caso = @{ pergunta = "Qual e o prazo para solicitar reembolso?"; termosEsperados = @("30 dias"); documentosEsperados = @($documento.id); deveRecusar = $false }
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/avaliacoes/$($conjunto.id)/casos" `
    -Headers (Cabecalho $tokenAna) -ContentType "application/json" -Body ($caso | ConvertTo-Json) | Out-Null
$execucao = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/avaliacoes/$($conjunto.id)/execucoes" -Headers (Cabecalho $tokenAna)
if ($execucao.taxaAcerto -ne 1) { throw "A avaliacao nao atingiu 100%." }

Write-Host "[8/11] Validando auditoria"
$eventos = Invoke-RestMethod "$BaseUrl/api/v1/espacos/$($espaco.id)/auditoria" -Headers (Cabecalho $tokenAna)
if ($eventos.Count -lt 6) { throw "Trilha de auditoria incompleta." }

Write-Host "[9/11] Validando metricas protegidas"
$credencial = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("prometheus:contextpilot_metrics_local"))
$metricas = Invoke-WebRequest "$BaseUrl/actuator/prometheus" -Headers @{ Authorization = "Basic $credencial" }
if ($metricas.Content -notmatch "contextpilot_rag_consultas_total") { throw "Metricas de RAG ausentes." }

Write-Host "[10/11] Validando servidor MCP"
$cabecalhosMcp = @{
    Authorization = "Bearer $tokenAna"
    Accept = "application/json, text/event-stream"
}
$inicializacaoMcp = Invoke-RestMethod -Method Post -Uri "$BaseUrl/mcp" -Headers $cabecalhosMcp `
    -ContentType "application/json" `
    -Body '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"smoke-test","version":"1.0.0"}}}'
if ($inicializacaoMcp.result.serverInfo.name -ne "contextpilot-mcp") {
    throw "Servidor MCP nao respondeu com a identidade esperada."
}
$ferramentasMcp = Invoke-RestMethod -Method Post -Uri "$BaseUrl/mcp" -Headers $cabecalhosMcp `
    -ContentType "application/json" -Body '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'
$nomesFerramentas = @($ferramentasMcp.result.tools | ForEach-Object { $_.name })
$ferramentasEsperadas = @("listarDocumentos", "buscarConhecimento", "consultarComFontes")
foreach ($ferramenta in $ferramentasEsperadas) {
    if ($ferramenta -notin $nomesFerramentas) { throw "Ferramenta MCP ausente: $ferramenta" }
}

Write-Host "[11/11] Fluxo completo aprovado" -ForegroundColor Green
[pscustomobject]@{
    espacoId = $espaco.id
    documentoId = $documento.id
    consultaId = $resposta.consultaId
    taxaAvaliacao = $execucao.taxaAcerto
    eventosAuditoria = $eventos.Count
    ferramentasMcp = $nomesFerramentas.Count
} | Format-List

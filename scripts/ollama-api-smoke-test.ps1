param(
    [string]$BaseUrl = "http://localhost:8083",
    [string]$KeycloakUrl = "http://localhost:18084",
    [int]$TimeoutSegundos = 360
)

$ErrorActionPreference = "Stop"
$arquivoImagem = Join-Path ([System.IO.Path]::GetTempPath()) "assistente-conhecimento-api-ollama-$([guid]::NewGuid()).png"
. "$PSScriptRoot\New-OcrFixture.ps1"

function Cabecalho([string]$token) {
    return @{ Authorization = "Bearer $token" }
}

function Obter-Token() {
    return (Invoke-RestMethod -Method Post `
        -Uri "$KeycloakUrl/realms/contextpilot/protocol/openid-connect/token" `
        -ContentType "application/x-www-form-urlencoded" `
        -Body @{
            grant_type = "password"
            client_id = "contextpilot-postman"
            username = "ana"
            password = "context123"
        }).access_token
}

try {
    Write-Host "[1/5] Validando aplicacao e autenticacao"
    $saude = Invoke-RestMethod "$BaseUrl/actuator/health"
    if ($saude.status -ne "UP") { throw "A aplicacao nao esta saudavel." }
    $token = Obter-Token

    Write-Host "[2/5] Criando espaco com embedding Ollama"
    $espaco = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/espacos" `
        -Headers (Cabecalho $token) -ContentType "application/json" `
        -Body (@{
            nome = "Ollama multimodal $(Get-Date -Format HHmmss)"
            descricao = "Teste integrado da versao 1.5.0"
        } | ConvertTo-Json)

    Write-Host "[3/5] Enviando imagem para ClamAV, S3, OCR, visao e embedding"
    New-ImageFixture $arquivoImagem
    $documento = Invoke-RestMethod -Method Post `
        -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/documentos" `
        -Headers (Cabecalho $token) -Form @{
            titulo = "Comprovante visual do pedido"
            visibilidade = "RESTRITO"
            metadados = '{"tipo":"comprovante","origem":"teste-integrado"}'
            arquivo = Get-Item $arquivoImagem
        }

    Write-Host "[4/5] Aguardando processamento multimodal"
    $estado = $null
    for ($segundo = 1; $segundo -le $TimeoutSegundos; $segundo++) {
        Start-Sleep -Seconds 1
        $estado = Invoke-RestMethod `
            "$BaseUrl/api/v1/espacos/$($espaco.id)/documentos/$($documento.id)" `
            -Headers (Cabecalho $token)
        if ($estado.estado -eq "PRONTO") { break }
        if ($estado.estado -eq "FALHOU") {
            throw "A ingestao falhou: $($estado.erroProcessamento)"
        }
        if ($segundo % 30 -eq 0) {
            Write-Host "Processamento ainda em $($estado.estado) apos ${segundo}s"
        }
    }
    if ($estado.estado -ne "PRONTO" -or -not $estado.visaoAplicada `
            -or $estado.provedorVisao -ne "ollama") {
        throw "O documento nao concluiu o pipeline de visao com Ollama."
    }

    Write-Host "[5/5] Consultando RAG com geracao Ollama e citacao"
    $resposta = Invoke-RestMethod -Method Post `
        -Uri "$BaseUrl/api/v1/espacos/$($espaco.id)/consultas" `
        -Headers (Cabecalho $token) -ContentType "application/json" `
        -Body '{"pergunta":"Qual e o numero e o estado do pedido no comprovante visual?"}' `
        -TimeoutSec $TimeoutSegundos
    if ($resposta.provedorIa -notlike "ollama:*" -or $resposta.fontes.Count -lt 1 `
            -or $resposta.resposta -notmatch "8742") {
        throw "A resposta nao comprovou geracao Ollama, citacao e reconhecimento do pedido 8742."
    }

    [pscustomobject]@{
        espacoId = $espaco.id
        documentoId = $documento.id
        origemTexto = $estado.origemTexto
        paginasOcr = $estado.paginasOcr
        visaoAplicada = $estado.visaoAplicada
        provedorVisao = $estado.provedorVisao
        modeloVisao = $estado.modeloVisao
        provedorResposta = $resposta.provedorIa
        resposta = $resposta.resposta
        fontes = $resposta.fontes.Count
        pedidoReconhecido = $true
    } | Format-List
} finally {
    Remove-Item -LiteralPath $arquivoImagem -Force -ErrorAction SilentlyContinue
}

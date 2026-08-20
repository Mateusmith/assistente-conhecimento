# ADR 011: ingestao multimodal segura

## Status

Aceita.

## Decisao

PNG e JPEG entram no mesmo pipeline de documentos. A API valida extensao, assinatura,
formato decodificavel, dimensoes e pixels antes de ClamAV e S3. Tesseract extrai texto
visivel; quando habilitada, a visao OpenAI ou Ollama gera uma descricao factual. OCR e
descricao formam trechos normais, sujeitos a prompt injection, tenant, ACL e citacoes.
OpenAI exige `VISION_ALLOW_EXTERNAL_PROVIDER=true`; Ollama permanece local.

## Consequencias

A descricao visual melhora busca de diagramas e telas, mas pode conter erro do modelo.
Ela nunca e evidencia independente: a resposta deve citar o documento e respeitar sua
permissao. Tokens, modelo, provedor e custo ficam registrados para governanca.

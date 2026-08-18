# ADR 004: ingestao segura e armazenamento de objetos

- Estado: aceito
- Data: 2026-08-17

## Contexto

A primeira versao guardava o arquivo original em `BYTEA`. Essa escolha simplificava a demonstracao, mas fazia o banco transacional crescer com conteudo binario, nao verificava malware e rejeitava PDFs digitalizados por falta de texto nativo.

## Decisao

Novos documentos seguem este fluxo:

1. validacao de tamanho, tipo e assinatura;
2. verificacao fail-closed pelo ClamAV usando `INSTREAM`;
3. deduplicacao por SHA-256 e verificacao do mesmo hash em toda leitura futura;
4. gravacao em bucket privado por API S3;
5. criacao transacional do documento e da tarefa de ingestao;
6. extracao nativa com PDFBox ou OCR com Tesseract;
7. persistencia da origem do texto e da quantidade de paginas processadas.

Se a transacao falhar depois da gravacao, uma sincronizacao transacional remove o objeto. A migracao marca registros anteriores como `BANCO`, preserva seus bytes e permite leitura pelos dois modos. Nao ha migracao destrutiva nem necessidade de reenviar arquivos.

## Consequencias

- PostgreSQL guarda metadados, ACLs, texto e vetores; MinIO/S3 guarda binarios.
- Uma indisponibilidade do antivirus bloqueia uploads em vez de aceitar conteudo nao verificado.
- O ambiente precisa operar MinIO/S3, ClamAV e os dados de idioma do Tesseract.
- PDFs digitalizados passam a ser pesquisaveis, ao custo de CPU e tempo controlados por limites.
- Uma futura migracao dos bytes legados pode ocorrer em segundo plano sem mudar o contrato da API.

# Modelo de ameacas

## Ativos

- texto e arquivos originais dos documentos;
- embeddings, perguntas, respostas e citacoes;
- papeis, ACLs e trilha de auditoria;
- credenciais OAuth2 e chave opcional da OpenAI.

## Fronteiras de confianca

1. Cliente para API/MCP.
2. API para Keycloak.
3. Aplicacao para PostgreSQL.
4. Aplicacao para MinIO/S3 e ClamAV.
5. Aplicacao para o processo local do Tesseract.
6. Aplicacao para OpenAI quando habilitada.
7. Prometheus para o endpoint tecnico de metricas.

## Ameacas e controles

| Ameaca | Controle implementado |
|---|---|
| Recuperacao de documento proibido | ACL aplicada dentro do SQL antes de ordenar/limitar |
| Enumeracao por identificador | recursos inacessiveis respondem como nao encontrados |
| Prompt injection no documento | prompt separa instrucao de contexto e trata documento como dado nao confiavel |
| Alucinacao sem evidencia | citacao obrigatoria, marcador validado e recusa segura |
| Citacao inventada | somente marcadores de fontes recuperadas sao aceitos |
| Arquivo disfarçado | extensao e assinatura PDF verificadas; formatos permitidos por lista positiva |
| Malware em upload | ClamAV antes da persistencia; indisponibilidade falha fechada com HTTP 503 |
| Zip bomb ou abuso de upload | limite de 10 MB; apenas PDF/texto; PDFBox processa fora da requisicao |
| Nome de arquivo malicioso | chave S3 gerada pelo servidor; nome exibido e normalizado |
| Corrupcao ou substituicao do objeto | SHA-256 recalculado e comparado ao metadado em toda leitura |
| Objeto orfao apos falha no banco | compensacao remove o objeto quando a transacao nao confirma |
| Exaustao por OCR | acionamento apenas sem texto nativo; limite de paginas, DPI e tempo por pagina |
| Tarefa duplicada | hash unico, tarefa unica e reivindicacao com bloqueio |
| Vazamento de segredo | chave somente por variavel de ambiente; corpo/prompt nao e registrado |
| Abuso do MCP | autenticacao obrigatoria e ferramentas somente de consulta |
| Metricas publicas | cadeia Basic Auth separada da API de negocio |

## Riscos residuais

- A qualidade do OCR depende da resolucao, orientacao e idioma do documento digitalizado.
- ClamAV reduz o risco conhecido, mas depende de assinaturas atualizadas e nao substitui CDR/sandbox em cenarios de alta criticidade.
- O modo local e extrativo e nao substitui um modelo generativo para perguntas complexas.
- O ambiente Docker usa credenciais de demonstracao e Keycloak em modo de desenvolvimento.
- Informacoes pessoais em perguntas e respostas exigem politica de retencao alinhada a LGPD.

## Checklist de producao

- TLS em todas as fronteiras e issuer HTTPS.
- Segredos em Vault/KMS/Secrets Manager.
- Keycloak em modo de producao com MFA administrativo.
- Criptografia gerenciada no S3, versionamento, retencao e CDR/sandbox quando o risco exigir.
- Atualizacao e monitoramento das assinaturas do ClamAV.
- Limite de requisicoes, cotas por espaco e protecao contra custo inesperado.
- Retencao, anonimização e exclusao conforme LGPD.
- Alertas para aumento de recusas, falhas de ingestao e latencia.
- Testes adversariais com documentos contendo instrucoes maliciosas.

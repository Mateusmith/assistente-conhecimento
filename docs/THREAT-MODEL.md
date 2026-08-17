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
4. Aplicacao para OpenAI quando habilitada.
5. Prometheus para o endpoint tecnico de metricas.

## Ameacas e controles

| Ameaca | Controle implementado |
|---|---|
| Recuperacao de documento proibido | ACL aplicada dentro do SQL antes de ordenar/limitar |
| Enumeracao por identificador | recursos inacessiveis respondem como nao encontrados |
| Prompt injection no documento | prompt separa instrucao de contexto e trata documento como dado nao confiavel |
| Alucinacao sem evidencia | citacao obrigatoria, marcador validado e recusa segura |
| Citacao inventada | somente marcadores de fontes recuperadas sao aceitos |
| Arquivo disfarçado | extensao e assinatura PDF verificadas; formatos permitidos por lista positiva |
| Zip bomb ou abuso de upload | limite de 10 MB; apenas PDF/texto; PDFBox processa fora da requisicao |
| Tarefa duplicada | hash unico, tarefa unica e reivindicacao com bloqueio |
| Vazamento de segredo | chave somente por variavel de ambiente; corpo/prompt nao e registrado |
| Abuso do MCP | autenticacao obrigatoria e ferramentas somente de consulta |
| Metricas publicas | cadeia Basic Auth separada da API de negocio |

## Riscos residuais

- PDFBox nao executa OCR; PDFs somente com imagem falham por texto insuficiente.
- O modo local e extrativo e nao substitui um modelo generativo para perguntas complexas.
- O ambiente Docker usa credenciais de demonstracao e Keycloak em modo de desenvolvimento.
- A verificacao de malware deve ser adicionada antes de aceitar arquivos de origem externa em producao.
- Informacoes pessoais em perguntas e respostas exigem politica de retencao alinhada a LGPD.

## Checklist de producao

- TLS em todas as fronteiras e issuer HTTPS.
- Segredos em Vault/KMS/Secrets Manager.
- Keycloak em modo de producao com MFA administrativo.
- Antivirus/CDR para uploads e armazenamento S3 criptografado.
- Limite de requisicoes, cotas por espaco e protecao contra custo inesperado.
- Retencao, anonimização e exclusao conforme LGPD.
- Alertas para aumento de recusas, falhas de ingestao e latencia.
- Testes adversariais com documentos contendo instrucoes maliciosas.

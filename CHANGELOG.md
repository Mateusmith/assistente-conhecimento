# Changelog

Todas as mudancas relevantes deste projeto sao registradas neste arquivo.
O formato segue [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/)
e o versionamento segue [Semantic Versioning](https://semver.org/lang/pt-BR/).

## [1.5.0] - 2026-08-20

### Adicionado

- Upload de PNG/JPEG com verificacao de assinatura, limite de pixels, ClamAV, S3 e OCR de imagem.
- Visao multimodal opcional com OpenAI ou Ollama, descricao citavel e telemetria de tokens/custo.
- Provedor Ollama para chat e embeddings, overlay Docker e testes de contrato entre provedores.
- Smoke tests reproduziveis para contratos Ollama e para o pipeline completo da API.
- Importacao de ate 5.000 casos de avaliacao, historico de execucoes e resultados paginados.
- Execucao-base vinculada ao job e metricas/alerta para leases de avaliacao abandonados.

### Alterado

- Avaliacoes grandes agora sao processadas em lotes configuraveis e liberam o worker entre lotes.
- O endpoint resumido limita resultados embutidos; a colecao completa fica no recurso paginado.
- A descricao visual e tratada como dado nao confiavel e passa pelas mesmas regras de ACL, prompt injection e citacao.

### Corrigido

- A coluna de origem do texto agora comporta `OCR_E_VISAO` em bancos novos e existentes.

## [1.4.0] - 2026-08-20

### Adicionado

- Tokenizacao reversivel de e-mail, CPF, CNPJ, telefone, JWT e chave de API antes de chamadas ao provedor externo.
- Recuperacao contextual com MMR, diversidade de documentos e janela de trechos vizinhos com ACL reaplicada.
- Rastro por consulta com indice, versao e SHA-256 do prompt, candidatos, fontes de contexto e dados protegidos.
- Execucoes de avaliacao assincronas, cancelaveis e retomaveis com progresso, lease e `SKIP LOCKED`.
- Revisao tecnica das decisoes de recuperacao, privacidade, proveniencia e operacao de IA.

### Alterado

- O endpoint de execucao de avaliacao agora responde `202 Accepted` e oferece consulta de estado.
- O provedor local passa a identificar a geracao como `local-extrativo-v2`.
- Smoke test e colecao Postman passam a validar explicabilidade e a aguardar jobs de avaliacao.

## [1.3.0] - 2026-08-20

### Adicionado

- Conversas persistentes isoladas por espaco e usuario, com memoria limitada e titulo automatico.
- Lease por conversa e `Idempotency-Key` vinculada a impressao SHA-256 da requisicao.
- Streaming SSE por eventos que publica somente a resposta final com citacoes validadas.
- Avaliacao RAG 2.0 com recall, precisao, MRR, latencia p95, custo e limites por caso.
- Comparacao entre execucao atual e baseline com deteccao objetiva de regressao.
- Validacao obrigatoria da audiencia `contextpilot-api` em tokens JWT.

### Alterado

- Historico conversacional participa da busca, mas nunca e aceito como fonte.
- Exportacao, exclusao e retencao LGPD agora incluem conversas e mensagens.
- Keycloak emite a audiencia da API para Postman, Swagger e MCP.
- Falhas durante avaliacao passam a encerrar a execucao com estado `FALHOU`.

## [1.2.0] - 2026-08-18

### Adicionado

- Indices de embedding blue-green com lotes idempotentes, lease, troca atomica e rollback.
- Modelo local `local-hashing-v2` para demonstrar a reindexacao sem dependencia paga.
- Busca hibrida, semantica e textual com metadados, datas, MIME e reranking.
- Redis com rate limiting e quotas distribuidas de consulta, upload e armazenamento.
- Telemetria diaria de tokens e custo estimado por espaco.
- Exportacao, exclusao, pseudonimizacao e retencao configuravel para LGPD.
- Deteccao de prompt injection, benchmark adversarial e relatorio bloqueante no CI.
- Gateway Nginx e overlay com tres replicas, incluindo retomada de leases expirados.
- SLOs, Alertmanager, novos paineis Grafana e runbooks operacionais.
- Configtree, validacao de segredos, TLS obrigatorio em `prod` e cabecalhos defensivos.

### Alterado

- Consultas registram indice, modelo, estrategia, tokens e custo utilizados.
- Uploads aceitam metadados JSON pesquisaveis.
- Smoke test passa de 15 para 21 verificacoes de ponta a ponta.
- Colecao Postman inclui comparacao de busca, reindexacao, governanca e LGPD.

## [1.1.0] - 2026-08-17

### Adicionado

- Armazenamento privado de documentos em MinIO/S3 com metadado SHA-256 e compensacao em rollback.
- Verificacao de integridade SHA-256 em toda leitura do objeto original.
- Verificacao de malware com ClamAV `INSTREAM` e politica fail-closed.
- OCR em portugues e ingles para PDFs sem camada de texto, com limites de paginas e tempo.
- Metricas e paineis Grafana para antivirus, armazenamento de objetos e origem OCR.
- Smoke test com bloqueio EICAR e PDF digitalizado gerado em tempo de execucao.

### Alterado

- Migracao V2 retrocompativel: novos arquivos deixam de ocupar `BYTEA`, mas os antigos continuam acessiveis.
- Respostas de documento informam armazenamento, verificacao antivirus e origem do texto.
- Colecao Postman valida armazenamento seguro e recuperacao do original.

## [1.0.0] - 2026-08-17

### Adicionado

- Espacos de conhecimento multiusuario com papeis e isolamento por tenant.
- Upload seguro de PDF, Markdown e texto, com deduplicacao SHA-256 e fila transacional.
- Extracao, fragmentacao, embeddings e busca hibrida com PostgreSQL e pgvector.
- Autorizacao aplicada antes do ranking e permissoes especificas por documento.
- Respostas RAG com citacoes verificadas, recusa segura e provedor local deterministico.
- Integracao opcional com OpenAI Responses API e embeddings, sem persistencia remota.
- Feedback, conjuntos de avaliacao, execucoes de regressao e trilha de auditoria.
- Servidor MCP autenticado com ferramentas de documentos, busca e consulta com fontes.
- OAuth2/OIDC com Keycloak, Swagger PKCE e metricas protegidas por credencial separada.
- Prometheus, Grafana, Zipkin, Actuator, logs estruturados e metricas de negocio.
- Testes unitarios, integracao com Testcontainers, smoke test e colecao Postman executavel.
- Docker Compose completo, CI no GitHub Actions e documentacao de arquitetura e ameacas.

[1.0.0]: https://github.com/Mateusmith/assistente-conhecimento/releases/tag/v1.0.0
[1.1.0]: https://github.com/Mateusmith/assistente-conhecimento/releases/tag/v1.1.0
[1.2.0]: https://github.com/Mateusmith/assistente-conhecimento/releases/tag/v1.2.0
[1.3.0]: https://github.com/Mateusmith/assistente-conhecimento/releases/tag/v1.3.0
[1.4.0]: https://github.com/Mateusmith/assistente-conhecimento/releases/tag/v1.4.0
[1.5.0]: https://github.com/Mateusmith/assistente-conhecimento/releases/tag/v1.5.0

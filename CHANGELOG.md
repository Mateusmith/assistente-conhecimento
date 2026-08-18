# Changelog

Todas as mudancas relevantes deste projeto sao registradas neste arquivo.
O formato segue [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/)
e o versionamento segue [Semantic Versioning](https://semver.org/lang/pt-BR/).

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

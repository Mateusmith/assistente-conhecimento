# Arquitetura do ContextPilot

## Contexto

O ContextPilot organiza conhecimento privado em espacos. Membros possuem um papel no espaco, mas documentos restritos exigem permissao propria. Consultas geram respostas somente a partir dos trechos acessiveis ao usuario autenticado.

```mermaid
C4Context
    title Contexto do ContextPilot
    Person(usuario, "Colaborador", "Consulta conhecimento autorizado")
    Person(curador, "Curador", "Publica documentos e avalia respostas")
    System(contextpilot, "ContextPilot", "RAG seguro e verificavel")
    System_Ext(keycloak, "Keycloak", "Identidade OAuth2/OIDC")
    System_Ext(openai, "OpenAI", "Geracao e embeddings opcionais")
    System_Ext(observabilidade, "Stack de observabilidade", "Metricas e traces")
    Rel(usuario, contextpilot, "REST ou MCP")
    Rel(curador, contextpilot, "REST")
    Rel(contextpilot, keycloak, "Valida JWT")
    Rel(contextpilot, openai, "HTTPS, quando habilitado")
    Rel(contextpilot, observabilidade, "Prometheus e OTLP/Zipkin")
```

## Modulos

| Pacote | Responsabilidade |
|---|---|
| `workspace` | espacos, membros, papeis e verificacao de acesso |
| `document` | upload, deduplicacao, ACL, extracao, fila e fragmentacao |
| `retrieval` | embeddings e busca hibrida autorizada |
| `answer` | geracao, validacao de citacoes, historico e feedback |
| `evaluation` | conjuntos, casos, execucoes e pontuacoes |
| `mcp` | ferramentas MCP autenticadas e somente de consulta |
| `audit` | trilha imutavel das operacoes relevantes |
| `configuration` | seguranca, OpenAPI, OpenAI e configuracoes transversais |

## Modelo de dados

```mermaid
erDiagram
    ESPACOS ||--o{ MEMBROS_ESPACO : possui
    ESPACOS ||--o{ DOCUMENTOS : organiza
    DOCUMENTOS ||--o{ PERMISSOES_DOCUMENTO : restringe
    DOCUMENTOS ||--o{ TRECHOS_DOCUMENTO : fragmenta
    DOCUMENTOS ||--|| TAREFAS_INGESTAO : processa
    ESPACOS ||--o{ CONSULTAS_RAG : recebe
    CONSULTAS_RAG ||--o{ CITACOES_RESPOSTA : fundamenta
    CONSULTAS_RAG ||--o{ FEEDBACK_RESPOSTA : avalia
    ESPACOS ||--o{ CONJUNTOS_AVALIACAO : define
    CONJUNTOS_AVALIACAO ||--o{ CASOS_AVALIACAO : contem
    CONJUNTOS_AVALIACAO ||--o{ EXECUCOES_AVALIACAO : executa
    EXECUCOES_AVALIACAO ||--o{ RESULTADOS_AVALIACAO : produz
```

## Busca hibrida

A consulta combina 70% de similaridade cosseno e 30% de relevancia `tsvector` em portugues. O CTE inicial seleciona somente trechos de documentos prontos e autorizados. Assim, um documento proibido nunca disputa o ranking nem influencia a existencia de uma resposta.

## Consistencia

- upload de documento e criacao da tarefa pertencem a uma transacao;
- cada hash SHA-256 aparece uma unica vez por espaco;
- trabalhadores reivindicam tarefas atomicamente com `FOR UPDATE SKIP LOCKED`;
- substituicao de trechos e conclusao da tarefa pertencem a uma transacao;
- respostas e suas citacoes sao gravadas juntas;
- feedback e permissao usam `UPSERT` para repeticao segura.

## Escala

O primeiro limite esperado e a geracao externa, nao o banco. A fila pode ganhar mais replicas da aplicacao sem duplicar uma tarefa. Para volumes muito maiores, os proximos passos naturais sao armazenamento de objetos, particionamento por espaco, reindexacao em segundo plano e limites de uso por locatario.

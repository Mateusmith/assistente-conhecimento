# ADR 007: memoria conversacional isolada e streaming validado

## Status

Aceita.

## Contexto

Perguntas de continuacao precisam do historico, mas uma memoria global pode misturar
usuarios ou tenants. Streaming de tokens tambem pode expor uma resposta antes da
validacao de citacoes e transformar uma recusa posterior em controle apenas aparente.

## Decisao

Cada conversa pertence simultaneamente a um espaco e a um usuario. O repositorio so
consulta essa chave composta, e apenas as ultimas mensagens configuradas entram na
memoria. O historico ajuda a recuperar contexto e interpretar a pergunta atual, mas nao
e fonte: a resposta ainda depende dos trechos autorizados recuperados para a interacao.

Uma lease por conversa impede duas geracoes simultaneas. `Idempotency-Key`, unica por
conversa, e vinculada a uma impressao SHA-256 da requisicao para tornar retries seguros
sem aceitar a mesma chave com outro corpo.

O endpoint SSE publica `etapa`, `fontes`, `resposta` e `concluido`. A resposta so e
emitida depois da validacao de marcadores; tokens crus do modelo nao atravessam a
fronteira HTTP.

## Consequencias

- perguntas de continuacao preservam ACL e rastreabilidade;
- timeout ou retry do cliente nao duplica custo nem historico;
- o primeiro byte da resposta chega depois da geracao completa, em troca de nao vazar
  conteudo ainda nao validado;
- conversas e mensagens entram na exportacao, exclusao e retencao LGPD.

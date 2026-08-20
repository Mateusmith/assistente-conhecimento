# ADR 010: avaliacoes RAG como jobs retomaveis

## Status

Aceita.

## Contexto

Executar um dataset inteiro dentro da requisicao HTTP aumenta timeout, prende conexoes e
perde progresso quando a instancia reinicia. A aplicacao tambem pode operar com varias
replicas.

## Decisao

O endpoint cria uma execucao `PENDENTE` e responde `202 Accepted`. Workers reivindicam
jobs com `FOR UPDATE SKIP LOCKED`, registram identidade e lease, persistem cada caso com
chave unica e renovam a lease durante o processamento. Cada reivindicacao processa um
lote limitado e libera a execucao. Jobs abandonados podem ser
retomados por outra replica. O usuario pode solicitar cancelamento e consultar progresso.

## Consequencias

Clientes precisam consultar o recurso ate um estado terminal e paginar os resultados. Em troca, datasets longos
nao dependem do timeout HTTP e preservam resultados ja processados. Uma falha entre a
consulta RAG e a persistencia do resultado pode gerar uma consulta adicional na retomada,
mas nunca duplica o resultado do caso.

# ADR 005: indices de embedding blue-green

## Status

Aceita.

## Decisao

Cada espaco possui exatamente um indice `ATIVO` e no maximo um `CONSTRUINDO`.
Vetores pertencem a um indice versionado, e modelos diferentes nunca compartilham o
mesmo espaco vetorial. A troca ocorre apenas quando a quantidade de vetores coincide
com a quantidade atual de trechos, dentro de transacao que bloqueia o espaco.

## Consequencias

- consultas continuam no indice anterior durante a construcao;
- lotes sao retomaveis e idempotentes;
- rollback e imediato enquanto o indice arquivado continua completo;
- armazenar temporariamente dois conjuntos de vetores aumenta uso de disco.

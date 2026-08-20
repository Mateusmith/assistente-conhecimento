# ADR 012: avaliacoes grandes em lotes

## Status

Aceita.

## Decisao

Datasets aceitam importacao em massa, mas workers carregam no maximo o lote configurado.
Cada resultado usa `UPSERT`, atualiza progresso e renova lease. Ao terminar o lote, o
worker remove sua posse; qualquer replica pode continuar. Agregados finais sao calculados
no PostgreSQL, o historico nao embute resultados e a consulta detalhada e paginada.

## Consequencias

Memoria cresce com o lote, nao com o dataset. O banco recebe mais ciclos de reivindicacao,
em troca de justica entre jobs, retomada fina e respostas HTTP de tamanho previsivel.

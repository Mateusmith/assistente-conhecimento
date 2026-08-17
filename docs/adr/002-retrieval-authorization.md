# ADR 002: autorizacao dentro da recuperacao

**Status:** aceito

## Contexto

Buscar os melhores trechos e remover os proibidos depois do ranking pode vazar metadados, reduzir a qualidade e devolver menos fontes do que o solicitado.

## Decisao

Incluir associacao ao espaco, visibilidade, propriedade e permissao documental no CTE que antecede o calculo de relevancia.

## Consequencias

- nenhum trecho proibido entra no ranking ou no prompt;
- a consulta e mais complexa, mas testavel isoladamente;
- indices de membros e permissoes tornam o custo previsivel;
- toda futura estrategia de recuperacao deve preservar essa regra.

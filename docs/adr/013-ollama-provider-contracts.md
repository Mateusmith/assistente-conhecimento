# ADR 013: Ollama e contratos de provedores

## Status

Aceita.

## Decisao

Ollama integra geracao por `/api/chat`, embeddings por `/api/embed` e imagens pelo campo
`images`. Modelos e dimensoes sao configuraveis. O overlay Docker prepara os modelos
antes da aplicacao. Testes comuns exigem texto, identificacao, prompt rastreavel, tokens,
custo valido, citacoes e vetores de 384 dimensoes de todos os provedores habilitados.

## Consequencias

Desenvolvimento pode permanecer offline, mas latencia e memoria dependem do hardware.
Trocar embedding continua exigindo reindexacao blue-green; contrato igual nao significa
qualidade igual, que permanece responsabilidade dos datasets de avaliacao.

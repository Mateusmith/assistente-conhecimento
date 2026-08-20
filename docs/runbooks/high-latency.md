# Latencia alta

1. Compare p95 HTTP, RAG, ingestao e reindexacao.
2. Verifique pool JDBC, latencia do provedor de IA e fila de ingestao.
3. Pause uma reindexacao pesada arquivando a implantacao responsavel, sem trocar o indice ativo.
4. Escale trabalhadores somente depois de confirmar capacidade no banco e no provedor.

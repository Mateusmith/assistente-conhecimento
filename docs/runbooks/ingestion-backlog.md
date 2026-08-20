# Fila de ingestao atrasada

1. Consulte tarefas `PENDENTE` e leases `PROCESSANDO` expirados.
2. Confirme saude de MinIO, ClamAV, OCR e provedor de embeddings.
3. Trabalhadores retomam automaticamente leases vencidos; nao altere linhas manualmente.
4. Escale `aplicacao` se a dependencia lenta estiver saudavel e houver capacidade no PostgreSQL.

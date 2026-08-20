# Segredos por arquivo

O Spring Boot carrega automaticamente arquivos de `/run/secrets` por `configtree`.
Em producao, monte arquivos com estes nomes e permissoes somente de leitura:

- `database_password`
- `object_storage_secret_key`
- `observability_password`
- `privacy_pseudonym_salt`
- `openai_api_key` quando o provedor OpenAI estiver habilitado

O valor do arquivo prevalece sobre a credencial local correspondente. Nao versione os
arquivos reais. Docker Secrets, Kubernetes Secrets com CSI, Vault Agent, AWS Secrets
Manager ou outro gerenciador podem materializa-los nesse diretorio.

O perfil `prod` interrompe a inicializacao quando encontra segredo curto/de demonstracao,
issuer sem HTTPS ou TLS sem terminacao confiavel. O gateway de referencia espera
`tls.crt` e `tls.key` no diretorio indicado por `TLS_DIRECTORY`.

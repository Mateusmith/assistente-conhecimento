# API indisponivel

1. Confirme `docker compose ps` e o endpoint `/actuator/health/readiness`.
2. Verifique os logs de `aplicacao`, PostgreSQL, Redis, MinIO e Keycloak sem copiar tokens.
3. Valide conexoes, espaco em disco e a ultima migracao Flyway.
4. Reverta a ultima implantacao se a readiness nao recuperar dentro de dez minutos.

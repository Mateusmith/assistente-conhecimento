# ADR 006: governanca distribuida com Redis e PostgreSQL

## Status

Aceita.

## Decisao

Rate limits e reservas de quota usam contador atomico Redis com script Lua. O valor
inicial vem do PostgreSQL, que permanece como registro duravel de consultas, uploads,
armazenamento e consumo de IA. O perfil de teste usa implementacao local explicita.

## Consequencias

- varias replicas compartilham os mesmos limites;
- reiniciar Redis nao zera o consumo ja persistido;
- indisponibilidade do Redis afeta novas requisicoes no ambiente distribuido;
- nenhum identificador de tenant e publicado como label Prometheus.

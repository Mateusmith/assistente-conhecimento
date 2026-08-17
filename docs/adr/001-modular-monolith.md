# ADR 001: monolito modular

**Status:** aceito

## Contexto

Ingestao, autorizacao, recuperacao e avaliacao compartilham invariantes transacionais. Separar esses fluxos em microsservicos no primeiro ciclo criaria consistencia eventual e operacao distribuida sem necessidade comprovada.

## Decisao

Usar uma aplicacao Spring Boot organizada por capacidades, com um PostgreSQL e contratos claros entre pacotes.

## Consequencias

- implantacao e testes ponta a ponta simples;
- transacoes locais preservam os invariantes centrais;
- modulos podem ser extraidos quando escala ou equipes independentes justificarem;
- disciplina de dependencia entre pacotes continua necessaria.

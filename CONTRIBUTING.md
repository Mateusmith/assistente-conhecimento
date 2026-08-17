# Contribuindo

## Fluxo

1. Abra uma issue descrevendo problema, regra e criterio de aceite.
2. Crie uma branch a partir de `main`.
3. Mantenha codigo e dominio em portugues; nomes de arquivos, pastas, pacotes e classes permanecem em ingles.
4. Inclua testes proporcionais ao risco da mudanca.
5. Execute `./mvnw verify` e `docker compose config`.
6. Abra um pull request pequeno, com motivacao, testes e impacto operacional.

## Convencoes

- Nunca aplique ACL somente depois da recuperacao vetorial.
- Nao registre tokens, chaves, documentos completos ou prompts com dados privados.
- Migracoes Flyway publicadas sao imutaveis; crie uma nova versao.
- Mensageria, cache e novos servicos exigem problema mensuravel e ADR.
- Dependencias precisam de versao suportada e justificativa.

## Commits

Use mensagens objetivas no formato Conventional Commits, por exemplo:

```text
feat(retrieval): adiciona filtro por classificacao documental
fix(security): impede leitura indireta por citacao
test(evaluation): cobre caso de recusa esperada
```

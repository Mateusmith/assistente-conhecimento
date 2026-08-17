# ADR 003: provedores de IA intercambiaveis

**Status:** aceito

## Contexto

Uma demonstracao de portfolio nao deve exigir gasto ou chave externa. Ao mesmo tempo, o projeto precisa provar integracao profissional com um provedor generativo.

## Decisao

Definir portas para embeddings e geracao. O modo `local` usa hashing + extracao; o modo `openai` usa Embeddings API + Responses API com `store=false`.

## Consequencias

- CI e avaliacao funcional sao deterministicas;
- OpenAI pode ser habilitada apenas por configuracao;
- vetores de provedores diferentes nao podem coexistir no mesmo indice;
- trocar de provedor exige reindexacao completa.

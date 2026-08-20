# Decisoes de engenharia de IA

## Objetivo

Este documento resume os criterios tecnicos adotados na evolucao do Assistente de
Conhecimento e as decisoes relevantes para a arquitetura do produto.

## Decisoes adotadas

| Tema | Decisao |
|---|---|
| Recuperacao | combinar busca hibrida, reranking, MMR e trechos vizinhos |
| Autorizacao | reaplicar tenant, membro, ACL, estado e risco de prompt antes do contexto |
| Privacidade externa | tokenizar identificadores e segredos reconheciveis antes da chamada de IA |
| Proveniencia | registrar indice, modelo, estrategia, versao e SHA-256 do template de prompt |
| Explicabilidade | apresentar fontes e metadados verificaveis, nunca cadeia de pensamento |
| Avaliacao | executar datasets como jobs retomaveis, cancelaveis e observaveis |
| Concorrencia | usar lotes, lease, `FOR UPDATE SKIP LOCKED` e resultado idempotente por caso |
| Visao | converter imagem em descricao citavel sem conceder autoridade ao conteudo visual |
| IA local | oferecer Ollama por configuracao, sem alterar o contrato externo da API |
| Ferramentas com efeito | exigir aprovacao persistida, autorizacao, idempotencia e outbox antes de adotar |
| Agentes autonomos | nao adotar sem um fluxo de negocio que justifique custo e superficie de risco |
| Fine-tuning | priorizar RAG para conhecimento privado que muda com frequencia |
| Busca aberta | manter fora do fluxo corporativo enquanto as fontes autorizadas forem o contrato |

## Capacidades entregues

1. **RAG contextual:** reduz repeticao, preserva contexto proximo e mantem a ACL dentro
   da recuperacao.
2. **Protecao antes da IA externa:** e-mail, CPF, CNPJ, telefone, JWT e chaves de API
   viram marcadores temporarios e sao restaurados apenas localmente.
3. **Rastreabilidade operacional:** cada consulta registra configuracao de recuperacao,
   template de prompt, fontes, custo, latencia e quantidade de valores protegidos.
4. **Avaliacao assincrona:** execucoes respondem `202 Accepted`, informam progresso,
   aceitam cancelamento e podem ser retomadas por outra replica em lotes limitados.
5. **Multimodal seguro:** PNG/JPEG combina OCR e descricao visual sob ACL e validacao de citacoes.
6. **Ollama:** chat, embeddings e visao locais cumprem testes de contrato equivalentes.

## Limites declarados

- a tokenizacao baseada em padroes nao substitui um DLP/NER corporativo;
- deteccao de prompt injection e defesa em profundidade;
- descricao visual pode errar e por isso nunca substitui a fonte nem a citacao;
- o modo local e deterministico e nao se apresenta como um LLM;
- ferramentas que alteram estado permanecem fora do MCP atual, que e somente leitura.

## Evolucoes coerentes

- DLP/NER corporativo para dados sem formato deterministico;
- benchmark multimodal de dominio com ground truth versionado;
- MCP cliente apenas para uma fonte corporativa autenticada e util.

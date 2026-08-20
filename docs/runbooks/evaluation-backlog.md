# Avaliacao RAG sem progresso

## Sinais

- `contextpilot_avaliacao_leases_expirados` maior que zero;
- execucao permanece `EXECUTANDO` sem alterar `casosProcessados`;
- `ultimoLoteEm` deixou de avancar.

## Diagnostico

1. Confirme a saude da aplicacao, PostgreSQL e do provedor de IA.
2. Consulte `execucoes_avaliacao` e compare `trabalhador_id`, `bloqueado_ate` e `ultimo_lote_em`.
3. Procure a execucao pelo identificador nos logs e traces, sem registrar perguntas ou respostas.
4. Verifique indisponibilidade, limite de contexto ou modelo ausente no provedor configurado.

## Recuperacao

Um worker saudavel reivindica automaticamente a execucao depois do vencimento do lease.
Corrija o provedor ou aumente o lease apenas quando uma unica consulta puder exceder o
tempo atual. Se o dataset estiver incorreto, solicite cancelamento pela API e crie uma
nova execucao; resultados ja gravados permanecem consultaveis na execucao cancelada.

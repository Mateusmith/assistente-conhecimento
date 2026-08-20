# Matriz de capacidades de IA

## Escopo da revisao

A revisao considerou praticas de Java, Spring, arquitetura, seguranca, concorrencia,
dados, operacao e sistemas de IA. O repositorio publico registra somente capacidades,
riscos e decisoes do produto. Consulte
[Decisoes de engenharia de IA](AI-ENGINEERING-DECISIONS.md).

## Cobertura

| Capacidade estudada | Decisao no Assistente de Conhecimento |
|---|---|
| Chat e memoria persistente | Implementado na 1.3 com isolamento por espaco e usuario, janela limitada e LGPD |
| RAG e vector store | Implementado com PostgreSQL/pgvector, ACL antes do ranking e citacoes validadas |
| Filtros de metadados | Implementado antes da recuperacao, incluindo datas, MIME, tags e documentos |
| Streaming | Implementado como SSE por etapas; tokens crus nao saem antes da validacao |
| Avaliacao de relevancia | Evoluido para termos, recall, precisao, MRR, p95, custo e baseline |
| Avaliacao em escala | Jobs assincronos com progresso, cancelamento, lease, retomada e `SKIP LOCKED` |
| Saida estruturada | A API ja retorna records tipados para resposta, fontes, avaliacao e erros |
| Ingestao/ETL | Implementado com fila, leases, ClamAV, S3, OCR, fragmentacao e embeddings |
| Observabilidade | Implementado com metricas de negocio, traces, SLOs, alertas, dashboard e runbooks |
| MCP servidor | Implementado com JWT, ferramentas somente de leitura e reaplicacao de ACL |
| Troca de modelo | Implementado com provedores intercambiaveis e indices blue-green com rollback |
| Privacidade no provedor | Tokenizacao reversivel de identificadores e segredos antes da chamada externa |
| Proveniencia | Indice, estrategia, versao e hash do prompt, candidatos e fontes por consulta |
| Recuperacao contextual | MMR, diversidade e vizinhos com tenant, ACL e risco reaplicados no SQL |
| Multimodal visual | OCR cobre PDFs digitalizados; imagem nativa e modelo de visao permanecem no roadmap |
| MCP cliente | Adiado ate existir uma fonte remota com contrato, autenticacao e utilidade claras |
| Tool calling com efeitos | Nao adotado sem aprovacao humana, idempotencia e autorizacao por acao |
| Multiagentes | Nao adotado: aumentaria custo e complexidade sem um fluxo de negocio que os justifique |

## Melhorias sobre os exemplos

- memoria nao usa usuario fixo nem identificador global;
- ferramentas nunca listam dados fora do tenant e nao confiam apenas no prompt;
- streaming nao revela uma resposta que ainda pode ser recusada pelo validador;
- MCP e autenticado e read-only;
- avaliacao separa qualidade de recuperacao, qualidade textual, desempenho e custo;
- concorrencia usa lease retomavel e retries idempotentes;
- dados reconhecidos por padrao nao saem em claro para a IA externa;
- explicabilidade registra proveniencia operacional, nao cadeia de pensamento;
- toda persistencia pessoal participa de exportacao, exclusao e retencao.

## Proximas evolucoes coerentes

1. Upload PNG/JPEG, OCR de imagem nativa e um provedor de visao opcional.
2. Perfil local com Ollama e teste de contrato entre provedores.
3. DLP/NER corporativo opcional para dados que nao possuem formato deterministico.
4. Conector MCP cliente somente quando houver uma fonte corporativa real para integrar.

Esses itens nao bloqueiam a versao 1.4. Eles formam um roadmap tecnico sem transformar
o projeto em uma demonstracao de recursos desconectados do problema principal.

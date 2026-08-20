# Modelo de ameacas

## Ativos e fronteiras

Os ativos incluem arquivos, texto, embeddings, perguntas, respostas, citacoes,
identidades, ACLs, trilha de auditoria e credenciais. As fronteiras sao cliente/gateway,
API/Keycloak, API/PostgreSQL, API/Redis, API/S3, API/ClamAV/Tesseract e API/provedor de IA.

## Ameacas e controles

| Ameaca | Controle implementado |
|---|---|
| Acesso cross-tenant | tenant e ACL no SQL antes do ranking; teste adversarial bloqueante |
| Enumeracao de UUID | recurso inacessivel responde como nao encontrado |
| Prompt injection em documento | detector marca o trecho; recuperacao o exclui; prompt trata contexto como dado |
| Citacao ou resposta inventada | marcador obrigatorio validado contra fontes recuperadas; recusa segura |
| Troca de modelo inconsistente | indices separados; blue-green; contagem final; rollback |
| Arquivo disfarçado ou malware | lista positiva, assinatura PDF e ClamAV fail-closed |
| Corrupcao no S3 | SHA-256 no banco, no metadado e recalculado na leitura |
| Exaustao por upload/OCR | limite de tamanho, quota, paginas, DPI e timeout |
| Abuso ou custo de API | limites por usuario, quota por tenant, tokens e custo agregados |
| Tarefa abandonada | lease com expiracao e retomada por outra replica |
| Vazamento em logs/metricas | sem pergunta, resposta, token ou tenant como label de alta cardinalidade |
| Segredo no repositorio | `configtree`, arquivos ignorados e validacao obrigatoria no perfil `prod` |
| Transporte sem protecao | perfil `prod` exige TLS e HSTS e ativado em conexao segura |
| Retencao excessiva de PII | politica por espaco, expurgo agendado, exportacao e exclusao LGPD |
| Exclusao que deixa tenant orfao | unico proprietario deve transferir/excluir o espaco antes |
| Abuso MCP | JWT obrigatorio e ferramentas somente de leitura que reaplicam ACL |
| Metricas expostas | Basic Auth independente do Bearer Token da API |

## Benchmark adversarial

`src/test/resources/adversarial/benchmark.json` cobre instrucoes maliciosas em portugues
e ingles, citacao inexistente e resposta sem fonte. A integracao ainda tenta filtrar um
documento de outro espaco. O CI publica `target/adversarial-report.json` e falha quando
qualquer caso regressa.

## Riscos residuais

- deteccao de prompt injection e defesa em profundidade, nao prova matematica;
- ClamAV depende de assinaturas e nao substitui CDR/sandbox em alta criticidade;
- OCR depende da qualidade e orientacao da imagem;
- custo e estimativa configuravel, nao fatura do provedor;
- o modo local e deterministico e adequado a testes, nao a raciocinio generativo complexo;
- credenciais de demonstracao e Keycloak `start-dev` sao exclusivos do ambiente local.

## Checklist de producao

1. Use issuer HTTPS, Keycloak em modo de producao e MFA administrativo.
2. Materialize segredos em `/run/secrets` por Vault/KMS/Secrets Manager/CSI.
3. Ative TLS, backup, PITR, criptografia S3 e rotacao de chaves.
4. Configure precos do contrato de IA e alertas de orcamento.
5. Encaminhe Alertmanager para o canal de plantao e exercite os runbooks.
6. Expanda o benchmark com dados sinteticos do dominio, nunca documentos reais.

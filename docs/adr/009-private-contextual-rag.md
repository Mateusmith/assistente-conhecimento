# ADR 009: RAG contextual com protecao de dados externos

## Status

Aceita.

## Contexto

Busca vetorial pura pode devolver trechos repetidos e perder a explicacao imediatamente
anterior ou posterior ao melhor trecho. Ao mesmo tempo, enviar documentos autorizados
a um provedor externo nao significa que dados pessoais e segredos devam sair do ambiente.

## Decisao

- recuperar ate quatro vezes o limite final antes do reranking;
- combinar score original, cobertura da pergunta e titulo;
- selecionar ancoras com MMR lexical para reduzir redundancia;
- consultar a janela vizinha reaplicando tenant, ACL, estado, indice e risco de prompt;
- tokenizar dados sensiveis antes da chamada externa e restaura-los somente localmente;
- persistir versao e SHA-256 do prompt, candidatos e fontes usadas.

Os marcadores sao deterministas dentro de uma chamada, mas nao sao persistidos como um
mapa separado. Metricas registram apenas tipo e quantidade, nunca o valor protegido.

## Consequencias

Respostas recebem contexto mais coerente e auditavel. O custo e de poucas consultas SQL
adicionais por pergunta, limitado pelo numero de ancoras. A protecao baseada em padroes
nao substitui um DLP especializado nem reconhece todos os nomes de pessoas; por isso o
controle e descrito com seu limite real.

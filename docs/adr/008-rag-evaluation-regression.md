# ADR 008: avaliacao RAG quantitativa e comparacao de regressao

## Status

Aceita.

## Contexto

Uma taxa binaria nao mostra se a recuperacao encontrou todos os documentos esperados,
trouxe muito ruido ou posicionou a fonte correta tarde demais. Tambem pode aprovar uma
mudanca que melhora o texto, mas viola o limite de latencia ou custo.

## Decisao

Cada caso pode definir termos, documentos relevantes, recusa esperada, latencia maxima
e custo maximo. Cada resultado registra:

- cobertura de termos;
- recall dos documentos esperados;
- precisao dos documentos recuperados;
- MRR da primeira fonte relevante;
- recusa correta, latencia, custo e respeito ao orcamento.

A execucao agrega taxa de acerto, recall, precisao, MRR, latencia p95, custo, modelo de
embedding e provedor. Uma API compara uma execucao atual com uma base e marca regressao
quando uma metrica de qualidade cai mais de cinco pontos percentuais ou quando latencia
ou custo crescem mais de 20% acima dos limites de ruido definidos.

## Consequencias

- troca de modelo ou indice passa a ter evidencia comparavel;
- qualidade, desempenho e custo deixam de ser confundidos em uma unica nota;
- a avaliacao continua deterministica no provedor local e pode rodar no CI;
- conjuntos grandes ainda sao sincronos e devem migrar para uma fila em uma versao futura.

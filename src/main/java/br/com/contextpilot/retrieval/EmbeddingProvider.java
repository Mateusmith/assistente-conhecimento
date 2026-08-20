package br.com.contextpilot.retrieval;

import java.math.BigDecimal;

public interface EmbeddingProvider {

    float[] gerar(String texto);

    default ResultadoEmbedding gerarComUso(String texto) {
        return new ResultadoEmbedding(gerar(texto), 0, BigDecimal.ZERO);
    }

    String nome();

    String provedor();

    int dimensoes();

    record ResultadoEmbedding(float[] vetor, int tokensEntrada, BigDecimal custoEstimadoUsd) {
    }
}

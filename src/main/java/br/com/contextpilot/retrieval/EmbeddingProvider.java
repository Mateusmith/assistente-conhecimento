package br.com.contextpilot.retrieval;

public interface EmbeddingProvider {

    float[] gerar(String texto);

    String nome();
}

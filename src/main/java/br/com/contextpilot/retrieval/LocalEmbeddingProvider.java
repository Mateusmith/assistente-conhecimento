package br.com.contextpilot.retrieval;

import java.text.Normalizer;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class LocalEmbeddingProvider implements EmbeddingProvider {

    private final int dimensoes;

    LocalEmbeddingProvider(@Value("${contextpilot.ia.dimensoes}") int dimensoes) {
        this.dimensoes = dimensoes;
    }

    @Override
    public float[] gerar(String texto) {
        float[] vetor = new float[dimensoes];
        String[] termos = normalizar(texto).split("\\s+");

        for (int indice = 0; indice < termos.length; indice++) {
            String termo = termos[indice];
            if (termo.length() < 2) {
                continue;
            }
            adicionar(vetor, termo, 1.0f);
            if (indice + 1 < termos.length) {
                adicionar(vetor, termo + "_" + termos[indice + 1], 0.45f);
            }
        }

        normalizar(vetor);
        return vetor;
    }

    @Override
    public String nome() {
        return "local-hashing-v1";
    }

    @Override
    public String provedor() {
        return "local";
    }

    @Override
    public int dimensoes() {
        return dimensoes;
    }

    private void adicionar(float[] vetor, String termo, float peso) {
        int hash = termo.hashCode();
        int posicao = Math.floorMod(hash, vetor.length);
        float sinal = (hash & 1) == 0 ? 1.0f : -1.0f;
        vetor[posicao] += peso * sinal;
    }

    private void normalizar(float[] vetor) {
        double soma = 0;
        for (float valor : vetor) {
            soma += valor * valor;
        }
        double norma = Math.sqrt(soma);
        if (norma == 0) {
            return;
        }
        for (int indice = 0; indice < vetor.length; indice++) {
            vetor[indice] /= (float) norma;
        }
    }

    private String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }
}

package br.com.contextpilot.retrieval;

import java.text.Normalizer;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class LocalEmbeddingV2Provider implements EmbeddingProvider {

    private final int dimensoes;

    LocalEmbeddingV2Provider(@Value("${contextpilot.ia.dimensoes}") int dimensoes) {
        this.dimensoes = dimensoes;
    }

    @Override
    public float[] gerar(String texto) {
        float[] vetor = new float[dimensoes];
        String normalizado = normalizarTexto(texto);
        String[] termos = normalizado.split("\\s+");

        for (int indice = 0; indice < termos.length; indice++) {
            String termo = termos[indice];
            if (termo.length() < 2) {
                continue;
            }
            adicionar(vetor, "t:" + termo, 1.0f);
            adicionarCaracteres(vetor, termo);
            if (indice + 1 < termos.length) {
                adicionar(vetor, "b:" + termo + "_" + termos[indice + 1], 0.65f);
            }
        }

        normalizarVetor(vetor);
        return vetor;
    }

    @Override
    public String nome() {
        return "local-hashing-v2";
    }

    @Override
    public String provedor() {
        return "local";
    }

    @Override
    public int dimensoes() {
        return dimensoes;
    }

    private void adicionarCaracteres(float[] vetor, String termo) {
        String delimitado = "^" + termo + "$";
        for (int indice = 0; indice + 3 <= delimitado.length(); indice++) {
            adicionar(vetor, "c:" + delimitado.substring(indice, indice + 3), 0.20f);
        }
    }

    private void adicionar(float[] vetor, String termo, float peso) {
        int hash = termo.hashCode();
        int posicao = Math.floorMod(hash, vetor.length);
        vetor[posicao] += ((hash & 1) == 0 ? peso : -peso);
    }

    private void normalizarVetor(float[] vetor) {
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

    private String normalizarTexto(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }
}

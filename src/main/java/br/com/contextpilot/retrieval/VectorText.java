package br.com.contextpilot.retrieval;

import java.util.Locale;

public final class VectorText {

    private VectorText() {
    }

    public static String serializar(float[] vetor) {
        StringBuilder texto = new StringBuilder(vetor.length * 10).append('[');
        for (int indice = 0; indice < vetor.length; indice++) {
            if (indice > 0) {
                texto.append(',');
            }
            texto.append(String.format(Locale.ROOT, "%.8f", vetor[indice]));
        }
        return texto.append(']').toString();
    }
}

package br.com.contextpilot.document;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TextChunker {

    private final int tamanhoMaximo;
    private final int sobreposicao;

    public TextChunker(
            @Value("${contextpilot.documentos.tamanho-trecho}") int tamanhoMaximo,
            @Value("${contextpilot.documentos.sobreposicao}") int sobreposicao) {
        if (tamanhoMaximo < 200 || sobreposicao < 0 || sobreposicao >= tamanhoMaximo) {
            throw new IllegalArgumentException("Configuracao de fragmentacao invalida.");
        }
        this.tamanhoMaximo = tamanhoMaximo;
        this.sobreposicao = sobreposicao;
    }

    public List<String> dividir(String texto) {
        List<String> trechos = new ArrayList<>();
        int inicio = 0;

        while (inicio < texto.length()) {
            int fimDesejado = Math.min(inicio + tamanhoMaximo, texto.length());
            int fim = encontrarFim(texto, inicio, fimDesejado);
            String trecho = texto.substring(inicio, fim).trim();
            if (!trecho.isBlank()) {
                trechos.add(trecho);
            }
            if (fim >= texto.length()) {
                break;
            }
            inicio = encontrarInicio(texto, Math.max(inicio + 1, fim - sobreposicao), fim);
        }
        return List.copyOf(trechos);
    }

    private int encontrarFim(String texto, int inicio, int fimDesejado) {
        if (fimDesejado == texto.length()) {
            return fimDesejado;
        }
        int limiteInferior = inicio + (int) (tamanhoMaximo * 0.65);
        for (int indice = fimDesejado; indice > limiteInferior; indice--) {
            char caractere = texto.charAt(indice - 1);
            if (caractere == '\n' || caractere == '.' || caractere == '!' || caractere == '?') {
                return indice;
            }
        }
        return fimDesejado;
    }

    private int encontrarInicio(String texto, int candidato, int fimAnterior) {
        for (int indice = candidato; indice < fimAnterior; indice++) {
            if (Character.isWhitespace(texto.charAt(indice))) {
                return indice + 1;
            }
        }
        return candidato;
    }
}

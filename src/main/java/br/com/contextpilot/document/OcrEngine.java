package br.com.contextpilot.document;

import org.apache.pdfbox.pdmodel.PDDocument;

interface OcrEngine {

    ResultadoOcr extrair(PDDocument documento);

    record ResultadoOcr(String texto, int paginasProcessadas) {
    }
}

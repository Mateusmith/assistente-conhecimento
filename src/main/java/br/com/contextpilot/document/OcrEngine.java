package br.com.contextpilot.document;

import java.awt.image.BufferedImage;

import br.com.contextpilot.shared.domain.BusinessRuleException;
import org.apache.pdfbox.pdmodel.PDDocument;

interface OcrEngine {

    ResultadoOcr extrair(PDDocument documento);

    default ResultadoOcr extrair(BufferedImage imagem) {
        throw new BusinessRuleException("O motor de OCR nao aceita imagens avulsas.");
    }

    default boolean ativo() {
        return true;
    }

    record ResultadoOcr(String texto, int paginasProcessadas) {
    }
}

package br.com.contextpilot.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;

import br.com.contextpilot.document.DocumentModels.OrigemTexto;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class TextExtractorTest {

    @Test
    void devePreferirCamadaDeTextoNativaDoPdf() throws Exception {
        OcrEngine ocrNaoEsperado = documento -> {
            throw new AssertionError("OCR nao deveria ser chamado.");
        };
        var resultado = new TextExtractor(ocrNaoEsperado)
                .extrair("application/pdf", pdfComTexto("Politica corporativa com conteudo nativo suficiente."));

        assertThat(resultado.origem()).isEqualTo(OrigemTexto.NATIVO);
        assertThat(resultado.paginasOcr()).isZero();
        assertThat(resultado.texto()).contains("Politica corporativa");
    }

    @Test
    void deveUsarOcrQuandoPdfNaoPossuiCamadaDeTexto() throws Exception {
        OcrEngine ocr = documento -> new OcrEngine.ResultadoOcr(
                "Texto reconhecido por OCR com informacao suficiente para indexacao.", 1);
        var resultado = new TextExtractor(ocr).extrair("application/pdf", pdfSemTexto());

        assertThat(resultado.origem()).isEqualTo(OrigemTexto.OCR);
        assertThat(resultado.paginasOcr()).isOne();
        assertThat(resultado.texto()).contains("reconhecido por OCR");
    }

    @Test
    void deveExtrairTextoDeImagemComOcr() throws Exception {
        OcrEngine ocr = new OcrEngine() {
            @Override
            public ResultadoOcr extrair(PDDocument documento) {
                throw new AssertionError("PDF nao deveria ser processado.");
            }

            @Override
            public ResultadoOcr extrair(BufferedImage imagem) {
                return new ResultadoOcr("Numero do pedido 8742 e situacao aprovada.", 1);
            }
        };

        var resultado = new TextExtractor(ocr).extrair("image/png", pngValido());

        assertThat(resultado.origem()).isEqualTo(OrigemTexto.OCR);
        assertThat(resultado.paginasOcr()).isOne();
        assertThat(resultado.texto()).contains("pedido 8742");
    }

    private byte[] pdfComTexto(String texto) throws Exception {
        try (var documento = new PDDocument(); var saida = new ByteArrayOutputStream()) {
            var pagina = new PDPage();
            documento.addPage(pagina);
            try (var conteudo = new PDPageContentStream(documento, pagina)) {
                conteudo.beginText();
                conteudo.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                conteudo.newLineAtOffset(50, 700);
                conteudo.showText(texto);
                conteudo.endText();
            }
            documento.save(saida);
            return saida.toByteArray();
        }
    }

    private byte[] pdfSemTexto() throws Exception {
        try (var documento = new PDDocument(); var saida = new ByteArrayOutputStream()) {
            documento.addPage(new PDPage());
            documento.save(saida);
            return saida.toByteArray();
        }
    }

    private byte[] pngValido() throws Exception {
        var imagem = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        try (var saida = new ByteArrayOutputStream()) {
            javax.imageio.ImageIO.write(imagem, "png", saida);
            return saida.toByteArray();
        }
    }
}

package br.com.contextpilot.document;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import br.com.contextpilot.shared.domain.BusinessRuleException;
import br.com.contextpilot.document.DocumentModels.OrigemTexto;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
class TextExtractor {

    private static final int TAMANHO_MINIMO = 20;

    private final OcrEngine ocr;

    TextExtractor(OcrEngine ocr) {
        this.ocr = ocr;
    }

    TextoExtraido extrair(String tipoMime, byte[] conteudo) {
        return switch (tipoMime) {
            case "application/pdf" -> extrairPdf(conteudo);
            case "text/plain", "text/markdown" ->
                    new TextoExtraido(exigirTextoSuficiente(extrairUtf8(conteudo)), OrigemTexto.NATIVO, 0);
            default -> throw new BusinessRuleException("Tipo de documento nao suportado para extracao.");
        };
    }

    private TextoExtraido extrairPdf(byte[] conteudo) {
        try (var documento = Loader.loadPDF(conteudo)) {
            if (documento.isEncrypted()) {
                throw new BusinessRuleException("PDFs protegidos por senha nao sao aceitos.");
            }
            String textoNativo = normalizar(new PDFTextStripper().getText(documento));
            if (textoNativo.length() >= TAMANHO_MINIMO) {
                return new TextoExtraido(textoNativo, OrigemTexto.NATIVO, 0);
            }
            var resultado = ocr.extrair(documento);
            return new TextoExtraido(exigirTextoSuficiente(resultado.texto()), OrigemTexto.OCR,
                    resultado.paginasProcessadas());
        } catch (IOException excecao) {
            throw new BusinessRuleException("O PDF esta corrompido ou nao pode ser lido.");
        }
    }

    private String extrairUtf8(byte[] conteudo) {
        try {
            var decodificador = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            return normalizar(decodificador.decode(ByteBuffer.wrap(conteudo)).toString());
        } catch (CharacterCodingException excecao) {
            throw new BusinessRuleException("O arquivo de texto deve usar codificacao UTF-8.");
        }
    }

    private String exigirTextoSuficiente(String texto) {
        String limpo = normalizar(texto);
        if (limpo.length() < TAMANHO_MINIMO) {
            throw new BusinessRuleException("O documento nao possui texto suficiente para indexacao.");
        }
        return limpo;
    }

    private String normalizar(String texto) {
        return texto
                .replace("\u0000", "")
                .replaceAll("[\\t\\x0B\\f\\r ]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    record TextoExtraido(String texto, OrigemTexto origem, int paginasOcr) {
    }
}

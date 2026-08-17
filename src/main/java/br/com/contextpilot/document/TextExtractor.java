package br.com.contextpilot.document;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import br.com.contextpilot.shared.domain.BusinessRuleException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
class TextExtractor {

    String extrair(String tipoMime, byte[] conteudo) {
        return switch (tipoMime) {
            case "application/pdf" -> extrairPdf(conteudo);
            case "text/plain", "text/markdown" -> extrairUtf8(conteudo);
            default -> throw new BusinessRuleException("Tipo de documento nao suportado para extracao.");
        };
    }

    private String extrairPdf(byte[] conteudo) {
        try (var documento = Loader.loadPDF(conteudo)) {
            if (documento.isEncrypted()) {
                throw new BusinessRuleException("PDFs protegidos por senha nao sao aceitos.");
            }
            return limpar(new PDFTextStripper().getText(documento));
        } catch (IOException excecao) {
            throw new BusinessRuleException("O PDF esta corrompido ou nao pode ser lido.");
        }
    }

    private String extrairUtf8(byte[] conteudo) {
        try {
            var decodificador = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            return limpar(decodificador.decode(ByteBuffer.wrap(conteudo)).toString());
        } catch (CharacterCodingException excecao) {
            throw new BusinessRuleException("O arquivo de texto deve usar codificacao UTF-8.");
        }
    }

    private String limpar(String texto) {
        String limpo = texto
                .replace("\u0000", "")
                .replaceAll("[\\t\\x0B\\f\\r ]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        if (limpo.length() < 20) {
            throw new BusinessRuleException("O documento nao possui texto suficiente para indexacao.");
        }
        return limpo;
    }
}

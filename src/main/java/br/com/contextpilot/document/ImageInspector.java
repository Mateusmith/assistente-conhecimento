package br.com.contextpilot.document;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;

import br.com.contextpilot.configuration.VisionProperties;
import br.com.contextpilot.shared.domain.BusinessRuleException;
import org.springframework.stereotype.Component;

@Component
class ImageInspector {

    private final VisionProperties propriedades;

    ImageInspector(VisionProperties propriedades) {
        this.propriedades = propriedades;
    }

    InformacaoImagem validar(byte[] conteudo, String tipoMime) {
        try (var entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(conteudo))) {
            if (entrada == null) {
                throw invalida();
            }
            var leitores = ImageIO.getImageReaders(entrada);
            if (!leitores.hasNext()) {
                throw invalida();
            }
            ImageReader leitor = leitores.next();
            try {
                leitor.setInput(entrada, true, true);
                int largura = leitor.getWidth(0);
                int altura = leitor.getHeight(0);
                String formato = leitor.getFormatName().toLowerCase(java.util.Locale.ROOT);
                validarFormato(tipoMime, formato);
                validarDimensoes(largura, altura);
                return new InformacaoImagem(largura, altura);
            } finally {
                leitor.dispose();
            }
        } catch (IOException excecao) {
            throw invalida();
        }
    }

    private void validarFormato(String tipoMime, String formato) {
        boolean png = "image/png".equals(tipoMime) && "png".equals(formato);
        boolean jpeg = "image/jpeg".equals(tipoMime) && ("jpeg".equals(formato) || "jpg".equals(formato));
        if (!png && !jpeg) {
            throw invalida();
        }
    }

    private void validarDimensoes(int largura, int altura) {
        long pixels = (long) largura * altura;
        if (largura < 1 || altura < 1
                || largura > propriedades.maximoLargura()
                || altura > propriedades.maximoAltura()
                || pixels > propriedades.maximoPixels()) {
            throw new BusinessRuleException("A imagem excede os limites seguros de dimensao ou quantidade de pixels.");
        }
    }

    private BusinessRuleException invalida() {
        return new BusinessRuleException("A imagem esta corrompida ou o formato real nao corresponde ao arquivo.");
    }

    record InformacaoImagem(int largura, int altura) {
    }
}

package br.com.contextpilot.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import br.com.contextpilot.configuration.VisionProperties;
import br.com.contextpilot.shared.domain.BusinessRuleException;
import org.junit.jupiter.api.Test;

class ImageInspectorTest {

    @Test
    void deveLerDimensoesSemDecodificarUmaImagemGigante() throws Exception {
        var inspetor = new ImageInspector(propriedades(100));

        var informacao = inspetor.validar(imagem("png", 10, 8), "image/png");

        assertThat(informacao.largura()).isEqualTo(10);
        assertThat(informacao.altura()).isEqualTo(8);
    }

    @Test
    void deveBloquearImagemQueExcedeQuantidadeSeguraDePixels() throws Exception {
        var inspetor = new ImageInspector(propriedades(50));

        assertThatThrownBy(() -> inspetor.validar(imagem("png", 10, 8), "image/png"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("limites seguros");
    }

    @Test
    void deveBloquearFormatoRealDiferenteDoMimeDeclarado() throws Exception {
        var inspetor = new ImageInspector(propriedades(100));

        assertThatThrownBy(() -> inspetor.validar(imagem("png", 5, 5), "image/jpeg"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("formato real");
    }

    private VisionProperties propriedades(long maximoPixels) {
        return new VisionProperties(false, "modelo", "low", 1000, 100, 100, maximoPixels, false);
    }

    private byte[] imagem(String formato, int largura, int altura) throws Exception {
        var imagem = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        try (var saida = new ByteArrayOutputStream()) {
            javax.imageio.ImageIO.write(imagem, formato, saida);
            return saida.toByteArray();
        }
    }
}

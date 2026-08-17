package br.com.contextpilot.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TextChunkerTest {

    @Test
    void deveDividirSemPerderASequenciaDoConteudo() {
        String texto = "Primeiro paragrafo com uma regra importante. ".repeat(8)
                + "\n\nSegundo paragrafo com outra regra relevante. ".repeat(8);
        var fragmentador = new TextChunker(240, 40);

        var trechos = fragmentador.dividir(texto);

        assertThat(trechos).hasSizeGreaterThan(2);
        assertThat(trechos).allMatch(trecho -> trecho.length() <= 240);
        assertThat(String.join(" ", trechos)).contains("Primeiro paragrafo", "Segundo paragrafo");
    }

    @Test
    void deveRejeitarSobreposicaoMaiorQueOTrecho() {
        assertThatThrownBy(() -> new TextChunker(200, 200))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fragmentacao");
    }
}

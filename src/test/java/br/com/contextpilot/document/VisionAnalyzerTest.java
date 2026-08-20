package br.com.contextpilot.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;

import br.com.contextpilot.configuration.AiProperties;
import br.com.contextpilot.configuration.OllamaGateway;
import br.com.contextpilot.configuration.OpenAiGateway;
import br.com.contextpilot.configuration.VisionProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.ObjectMapper;

class VisionAnalyzerTest {

    @Test
    void deveEnviarImagemComoDataUrlSomenteQuandoPoliticaExternaPermitir() throws Exception {
        OpenAiGateway gateway = mock(OpenAiGateway.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<OpenAiGateway> openAi = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<OllamaGateway> ollama = mock(ObjectProvider.class);
        when(openAi.getIfAvailable()).thenReturn(gateway);
        when(gateway.enviar(eq("/v1/responses"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ObjectMapper().readTree("""
                        {"output":[{"content":[{"type":"output_text","text":"Fluxograma com tres etapas."}]}],
                         "usage":{"input_tokens":30,"output_tokens":8}}
                        """));
        var analisador = new VisionAnalyzer(
                new VisionProperties(true, "gpt-visao", "low", 1000, 100, 100, 10000, true),
                propriedadesIa("openai"), openAi, ollama);

        var resultado = analisador.analisar("image/png", new byte[]{1, 2, 3});

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Object> corpo = ArgumentCaptor.forClass(Object.class);
        verify(gateway).enviar(eq("/v1/responses"), corpo.capture());
        assertThat(corpo.getValue().toString()).contains("data:image/png;base64,AQID");
        assertThat(resultado.aplicada()).isTrue();
        assertThat(resultado.descricao()).contains("tres etapas");
        assertThat(resultado.tokensEntrada()).isEqualTo(30);
    }

    private AiProperties propriedadesIa(String provedor) {
        return new AiProperties(provedor, "https://api.openai.com", "chave", "chat", "embedding",
                384, 500, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}

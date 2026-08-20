package br.com.contextpilot.answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.contextpilot.answer.AnswerModels.FonteContexto;
import br.com.contextpilot.configuration.AiProperties;
import br.com.contextpilot.configuration.OllamaGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class OllamaAnswerGeneratorTest {

    @Test
    void deveUsarApiLocalSemCustoExternoEPreservarCitacoes() throws Exception {
        OllamaGateway gateway = mock(OllamaGateway.class);
        when(gateway.enviar(eq("/api/chat"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ObjectMapper().readTree("""
                        {"message":{"role":"assistant","content":"O prazo e de sete dias. [F1]"},
                         "prompt_eval_count":42,"eval_count":11,"done":true}
                        """));
        var gerador = new OllamaAnswerGenerator(gateway, propriedades());
        var fonte = new FonteContexto("F1", UUID.randomUUID(), UUID.randomUUID(),
                "Politica", 0, "O prazo de reembolso e de sete dias corridos.", 0.95);

        var resultado = gerador.gerar("Qual e o prazo?", List.of(fonte));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Object> corpo = ArgumentCaptor.forClass(Object.class);
        verify(gateway).enviar(eq("/api/chat"), corpo.capture());
        assertThat(corpo.getValue().toString()).contains("O prazo de reembolso").contains("stream=false");
        assertThat(resultado.texto()).contains("[F1]");
        assertThat(resultado.provedor()).isEqualTo("ollama:qwen3:0.6b");
        assertThat(resultado.tokensEntrada()).isEqualTo(42);
        assertThat(resultado.custoEstimadoUsd()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private AiProperties propriedades() {
        return new AiProperties("ollama", "", "", "qwen3:0.6b", "all-minilm",
                384, 500, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}

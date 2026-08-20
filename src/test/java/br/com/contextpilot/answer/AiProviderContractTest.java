package br.com.contextpilot.answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import br.com.contextpilot.answer.AnswerModels.FonteContexto;
import br.com.contextpilot.configuration.AiPrivacyProperties;
import br.com.contextpilot.configuration.AiProperties;
import br.com.contextpilot.configuration.OllamaGateway;
import br.com.contextpilot.configuration.OpenAiGateway;
import br.com.contextpilot.privacy.SensitiveDataProtector;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.ObjectMapper;

class AiProviderContractTest {

    @TestFactory
    List<DynamicTest> todosOsProvedoresDevemCumprirContratoDeGeracao() throws Exception {
        FonteContexto fonte = new FonteContexto("F1", UUID.randomUUID(), UUID.randomUUID(),
                "Politica", 0, "O reembolso corporativo deve ser solicitado em sete dias corridos.", 0.95);
        return List.of(
                cenario("local", LocalAnswerGenerator::new),
                cenario("openai", this::openAi),
                cenario("ollama", this::ollama))
                .stream()
                .map(cenario -> DynamicTest.dynamicTest(cenario.nome(), () -> {
                    var resultado = cenario.gerador().get().gerar("Qual e o prazo de reembolso?", List.of(fonte));
                    assertThat(resultado.texto()).isNotBlank().contains("[F1]");
                    assertThat(resultado.provedor()).isNotBlank();
                    assertThat(resultado.versaoPrompt()).isNotBlank();
                    assertThat(resultado.impressaoPrompt()).hasSize(64);
                    assertThat(resultado.tokensEntrada()).isGreaterThanOrEqualTo(0);
                    assertThat(resultado.tokensSaida()).isGreaterThanOrEqualTo(0);
                    assertThat(resultado.custoEstimadoUsd()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
                }))
                .toList();
    }

    private Cenario cenario(String nome, Supplier<AnswerGenerator> gerador) {
        return new Cenario(nome, gerador);
    }

    private AnswerGenerator openAi() {
        try {
            OpenAiGateway gateway = mock(OpenAiGateway.class);
            when(gateway.enviar(eq("/v1/responses"), org.mockito.ArgumentMatchers.any()))
                    .thenReturn(new ObjectMapper().readTree("""
                            {"output":[{"content":[{"type":"output_text",
                              "text":"O prazo e de sete dias corridos. [F1]"}]}],
                             "usage":{"input_tokens":20,"output_tokens":8}}
                            """));
            return new OpenAiAnswerGenerator(gateway, propriedades("openai"), new AiPrivacyProperties(true),
                    new SensitiveDataProtector(new SimpleMeterRegistry()));
        } catch (Exception excecao) {
            throw new IllegalStateException(excecao);
        }
    }

    private AnswerGenerator ollama() {
        try {
            OllamaGateway gateway = mock(OllamaGateway.class);
            when(gateway.enviar(eq("/api/chat"), org.mockito.ArgumentMatchers.any()))
                    .thenReturn(new ObjectMapper().readTree("""
                            {"message":{"content":"O prazo e de sete dias corridos. [F1]"},
                             "prompt_eval_count":20,"eval_count":8}
                            """));
            return new OllamaAnswerGenerator(gateway, propriedades("ollama"));
        } catch (Exception excecao) {
            throw new IllegalStateException(excecao);
        }
    }

    private AiProperties propriedades(String provedor) {
        return new AiProperties(provedor, "https://api.openai.com", "segredo", "modelo-chat", "embedding",
                384, 500, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private record Cenario(String nome, Supplier<AnswerGenerator> gerador) {
    }
}

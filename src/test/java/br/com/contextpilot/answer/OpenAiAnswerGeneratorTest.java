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
import br.com.contextpilot.configuration.AiPrivacyProperties;
import br.com.contextpilot.configuration.AiProperties;
import br.com.contextpilot.configuration.OpenAiGateway;
import br.com.contextpilot.privacy.SensitiveDataProtector;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class OpenAiAnswerGeneratorTest {

    @Test
    void deveEnviarSomenteMarcadoresAoProvedorERestaurarValoresNaResposta() throws Exception {
        OpenAiGateway gateway = mock(OpenAiGateway.class);
        var propriedades = new AiProperties(
                "openai", "https://api.openai.com", "segredo", "gpt-teste", "embedding-teste",
                384, 500, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        var protetor = new SensitiveDataProtector(new SimpleMeterRegistry());
        var gerador = new OpenAiAnswerGenerator(
                gateway, propriedades, new AiPrivacyProperties(true), protetor);
        var json = new ObjectMapper();
        when(gateway.enviar(eq("/v1/responses"), org.mockito.ArgumentMatchers.any())).thenReturn(json.readTree("""
                {"output":[{"content":[{"type":"output_text",
                  "text":"O contato e [[DADO_EMAIL_1]]. [F1]"}]}],
                 "usage":{"input_tokens":20,"output_tokens":8}}
                """));
        var fonte = new FonteContexto(
                "F1", UUID.randomUUID(), UUID.randomUUID(), "Cadastro", 0,
                "O contato responsavel e ana@empresa.com.br.", 0.9);

        var resultado = gerador.gerar("Qual e o contato?", List.of(fonte));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Object> corpo = ArgumentCaptor.forClass(Object.class);
        verify(gateway).enviar(eq("/v1/responses"), corpo.capture());
        Map<String, Object> enviado = (Map<String, Object>) corpo.getValue();
        assertThat(enviado.get("input").toString())
                .contains("[[DADO_EMAIL_1]]")
                .doesNotContain("ana@empresa.com.br");
        assertThat(resultado.texto()).isEqualTo("O contato e ana@empresa.com.br. [F1]");
        assertThat(resultado.dadosSensiveisProtegidos()).isEqualTo(1);
        assertThat(resultado.versaoPrompt()).isEqualTo("rag-seguro-v2");
        assertThat(resultado.impressaoPrompt()).hasSize(64);
    }
}

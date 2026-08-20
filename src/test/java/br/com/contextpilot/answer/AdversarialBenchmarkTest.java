package br.com.contextpilot.answer;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.contextpilot.answer.AnswerModels.FonteContexto;
import br.com.contextpilot.document.PromptInjectionDetector;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AdversarialBenchmarkTest {

    @Test
    void deveBloquearRegressoesDoBenchmarkAdversarial() throws Exception {
        ObjectMapper json = new ObjectMapper();
        JsonNode conjunto;
        try (var arquivo = getClass().getResourceAsStream("/adversarial/benchmark.json")) {
            conjunto = json.readTree(arquivo);
        }
        var detector = new PromptInjectionDetector();
        var validador = new AnswerIntegrityValidator(new SimpleMeterRegistry());
        var fonte = new FonteContexto("F1", UUID.randomUUID(), UUID.randomUUID(),
                "Politica", 0, "O prazo e de 30 dias.", 0.9);
        List<Map<String, Object>> resultados = new ArrayList<>();

        for (JsonNode caso : conjunto.path("casosPromptInjection")) {
            boolean obtido = detector.suspeito(caso.path("texto").asText());
            boolean esperado = caso.path("suspeito").asBoolean();
            resultados.add(Map.of("id", caso.path("id").asText(), "aprovado", obtido == esperado));
            assertThat(obtido).as(caso.path("id").asText()).isEqualTo(esperado);
        }
        for (JsonNode caso : conjunto.path("casosCitacao")) {
            boolean obtido = validador.validar(caso.path("resposta").asText(), List.of(fonte)).recusa();
            boolean esperado = caso.path("deveRecusar").asBoolean();
            resultados.add(Map.of("id", caso.path("id").asText(), "aprovado", obtido == esperado));
            assertThat(obtido).as(caso.path("id").asText()).isEqualTo(esperado);
        }

        Path relatorio = Path.of("target", "adversarial-report.json");
        Files.createDirectories(relatorio.getParent());
        Files.writeString(relatorio, json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                "versao", conjunto.path("versao").asText(),
                "total", resultados.size(),
                "aprovados", resultados.stream().filter(item -> Boolean.TRUE.equals(item.get("aprovado"))).count(),
                "resultados", resultados)));
    }
}

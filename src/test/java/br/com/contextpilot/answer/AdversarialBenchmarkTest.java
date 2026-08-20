package br.com.contextpilot.answer;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
        JsonNode configuracao = conjunto.path("configuracao");
        assertThat(configuracao.path("versaoPrompt").asText()).isNotBlank();
        assertThat(configuracao.path("indice").asText()).isNotBlank();

        var detector = new PromptInjectionDetector();
        var validador = new AnswerIntegrityValidator(new SimpleMeterRegistry());
        var fonte = new FonteContexto("F1", UUID.randomUUID(), UUID.randomUUID(),
                "Politica", 0, "O prazo e de 30 dias.", 0.9);
        List<Map<String, Object>> resultados = new ArrayList<>();

        for (JsonNode caso : conjunto.path("casosPromptInjection")) {
            boolean obtido = detector.suspeito(caso.path("texto").asText());
            boolean esperado = caso.path("suspeito").asBoolean();
            resultados.add(Map.of(
                    "categoria", "prompt_injection",
                    "id", caso.path("id").asText(),
                    "aprovado", obtido == esperado));
            assertThat(obtido).as(caso.path("id").asText()).isEqualTo(esperado);
        }

        for (JsonNode caso : conjunto.path("casosRespostaPorProvedor")) {
            for (JsonNode resposta : caso.path("respostas")) {
                boolean obtido = validador.validar(resposta.path("texto").asText(), List.of(fonte)).recusa();
                boolean esperado = resposta.path("deveRecusar").asBoolean();
                resultados.add(Map.of(
                        "categoria", caso.path("categoria").asText(),
                        "id", caso.path("id").asText(),
                        "provedor", resposta.path("provedor").asText(),
                        "modelo", resposta.path("modelo").asText(),
                        "aprovado", obtido == esperado));
                assertThat(obtido)
                        .as("%s/%s".formatted(caso.path("id").asText(), resposta.path("provedor").asText()))
                        .isEqualTo(esperado);
            }
        }

        Set<String> provedores = resultados.stream()
                .filter(item -> item.containsKey("provedor"))
                .map(item -> item.get("provedor").toString())
                .collect(Collectors.toSet());
        assertThat(provedores).as("comparacao entre provedores").hasSizeGreaterThanOrEqualTo(2);

        Map<String, Long> aprovadosPorProvedor = resultados.stream()
                .filter(item -> item.containsKey("provedor"))
                .filter(item -> Boolean.TRUE.equals(item.get("aprovado")))
                .collect(Collectors.groupingBy(
                        item -> item.get("provedor").toString(),
                        LinkedHashMap::new,
                        Collectors.counting()));

        Path relatorio = Path.of("target", "adversarial-report.json");
        Files.createDirectories(relatorio.getParent());
        Map<String, Object> relatorioPublicavel = new LinkedHashMap<>();
        relatorioPublicavel.put("versaoDataset", conjunto.path("versao").asText());
        relatorioPublicavel.put("versaoPrompt", configuracao.path("versaoPrompt").asText());
        relatorioPublicavel.put("indice", configuracao.path("indice").asText());
        relatorioPublicavel.put("modoExecucao", configuracao.path("modoExecucao").asText());
        relatorioPublicavel.put("provedoresComparados", provedores.stream().sorted().toList());
        relatorioPublicavel.put("aprovadosPorProvedor", aprovadosPorProvedor);
        relatorioPublicavel.put("total", resultados.size());
        relatorioPublicavel.put("aprovados",
                resultados.stream().filter(item -> Boolean.TRUE.equals(item.get("aprovado"))).count());
        relatorioPublicavel.put("resultados", resultados);
        Files.writeString(relatorio, json.writerWithDefaultPrettyPrinter().writeValueAsString(relatorioPublicavel));
    }
}

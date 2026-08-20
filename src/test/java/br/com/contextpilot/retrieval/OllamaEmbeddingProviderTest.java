package br.com.contextpilot.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import br.com.contextpilot.configuration.AiProperties;
import br.com.contextpilot.configuration.OllamaGateway;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class OllamaEmbeddingProviderTest {

    @Test
    void deveValidarDimensaoEContabilizarTokensDoEmbeddingLocal() throws Exception {
        OllamaGateway gateway = mock(OllamaGateway.class);
        String valores = IntStream.range(0, 384).mapToObj(indice -> "0.01")
                .collect(Collectors.joining(","));
        when(gateway.enviar(eq("/api/embed"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ObjectMapper().readTree(
                        "{\"embeddings\":[[" + valores + "]],\"prompt_eval_count\":9}"));
        var provedor = new OllamaEmbeddingProvider(gateway, propriedades());

        var resultado = provedor.gerarComUso("politica de reembolso");

        assertThat(resultado.vetor()).hasSize(384);
        assertThat(resultado.tokensEntrada()).isEqualTo(9);
        assertThat(resultado.custoEstimadoUsd()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(provedor.nome()).isEqualTo("ollama:all-minilm");
    }

    private AiProperties propriedades() {
        return new AiProperties("ollama", "", "", "qwen3:0.6b", "all-minilm",
                384, 500, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}

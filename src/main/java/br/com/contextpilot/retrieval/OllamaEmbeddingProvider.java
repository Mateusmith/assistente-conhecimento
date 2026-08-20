package br.com.contextpilot.retrieval;

import java.math.BigDecimal;
import java.util.Map;

import br.com.contextpilot.configuration.AiProperties;
import br.com.contextpilot.configuration.OllamaGateway;
import tools.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "contextpilot.ia.provedor", havingValue = "ollama")
class OllamaEmbeddingProvider implements EmbeddingProvider {

    private final OllamaGateway ollama;
    private final AiProperties propriedades;

    OllamaEmbeddingProvider(OllamaGateway ollama, AiProperties propriedades) {
        this.ollama = ollama;
        this.propriedades = propriedades;
    }

    @Override
    public float[] gerar(String texto) {
        return gerarComUso(texto).vetor();
    }

    @Override
    public ResultadoEmbedding gerarComUso(String texto) {
        JsonNode resposta = ollama.enviar("/api/embed", Map.of(
                "model", propriedades.modeloEmbedding(),
                "input", texto,
                "dimensions", propriedades.dimensoes(),
                "truncate", true));
        JsonNode valores = resposta.path("embeddings").path(0);
        if (!valores.isArray() || valores.size() != propriedades.dimensoes()) {
            throw new IllegalStateException("O Ollama retornou um embedding com dimensao inesperada.");
        }
        float[] vetor = new float[valores.size()];
        for (int indice = 0; indice < valores.size(); indice++) {
            vetor[indice] = valores.get(indice).floatValue();
        }
        int tokens = resposta.path("prompt_eval_count").asInt(0);
        return new ResultadoEmbedding(vetor, tokens, BigDecimal.ZERO);
    }

    @Override
    public String nome() {
        return "ollama:" + propriedades.modeloEmbedding();
    }

    @Override
    public String provedor() {
        return "ollama";
    }

    @Override
    public int dimensoes() {
        return propriedades.dimensoes();
    }
}

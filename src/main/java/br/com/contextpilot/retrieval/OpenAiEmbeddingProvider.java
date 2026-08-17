package br.com.contextpilot.retrieval;

import java.util.Map;

import br.com.contextpilot.configuration.AiProperties;
import br.com.contextpilot.configuration.OpenAiGateway;
import tools.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "contextpilot.ia.provedor", havingValue = "openai")
class OpenAiEmbeddingProvider implements EmbeddingProvider {

    private final OpenAiGateway openAi;
    private final AiProperties propriedades;

    OpenAiEmbeddingProvider(OpenAiGateway openAi, AiProperties propriedades) {
        this.openAi = openAi;
        this.propriedades = propriedades;
    }

    @Override
    public float[] gerar(String texto) {
        JsonNode resposta = openAi.enviar("/v1/embeddings", Map.of(
                "model", propriedades.modeloEmbedding(),
                "input", texto,
                "dimensions", propriedades.dimensoes(),
                "encoding_format", "float"));
        JsonNode valores = resposta.path("data").path(0).path("embedding");
        if (!valores.isArray() || valores.size() != propriedades.dimensoes()) {
            throw new IllegalStateException("A OpenAI retornou um embedding com dimensao inesperada.");
        }
        float[] vetor = new float[valores.size()];
        for (int indice = 0; indice < valores.size(); indice++) {
            vetor[indice] = valores.get(indice).floatValue();
        }
        return vetor;
    }

    @Override
    public String nome() {
        return "openai:" + propriedades.modeloEmbedding();
    }
}

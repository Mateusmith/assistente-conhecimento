package br.com.contextpilot.answer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import br.com.contextpilot.answer.AnswerModels.FonteContexto;
import br.com.contextpilot.answer.AnswerModels.MensagemMemoria;
import br.com.contextpilot.answer.AnswerModels.ResultadoGeracao;
import br.com.contextpilot.configuration.AiProperties;
import br.com.contextpilot.configuration.OllamaGateway;
import tools.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "contextpilot.ia.provedor", havingValue = "ollama")
class OllamaAnswerGenerator implements AnswerGenerator {

    private final OllamaGateway ollama;
    private final AiProperties propriedades;

    OllamaAnswerGenerator(OllamaGateway ollama, AiProperties propriedades) {
        this.ollama = ollama;
        this.propriedades = propriedades;
    }

    @Override
    public ResultadoGeracao gerar(String pergunta, List<FonteContexto> fontes) {
        return gerar(pergunta, fontes, List.of());
    }

    @Override
    public ResultadoGeracao gerar(
            String pergunta,
            List<FonteContexto> fontes,
            List<MensagemMemoria> memoria) {
        String entrada = RagPrompt.montarEntrada(pergunta, fontes, memoria);
        JsonNode resposta = ollama.enviar("/api/chat", Map.of(
                "model", propriedades.modeloChat(),
                "messages", List.of(
                        Map.of("role", "system", "content", RagPrompt.INSTRUCOES),
                        Map.of("role", "user", "content", entrada)),
                "stream", false,
                "options", Map.of("num_predict", propriedades.limiteTokensResposta(), "temperature", 0)));

        String texto = resposta.path("message").path("content").asText("").trim();
        if (texto.isBlank()) {
            throw new IllegalStateException("O Ollama nao retornou texto na resposta.");
        }
        int tokensEntrada = resposta.path("prompt_eval_count").asInt(0);
        int tokensSaida = resposta.path("eval_count").asInt(0);
        return new ResultadoGeracao(
                texto, "ollama:" + propriedades.modeloChat(), RagPrompt.VERSAO,
                PromptTrace.impressao(RagPrompt.INSTRUCOES), 0,
                tokensEntrada, tokensSaida, BigDecimal.ZERO);
    }
}

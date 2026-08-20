package br.com.contextpilot.answer;

import java.util.List;
import java.util.Map;

import br.com.contextpilot.answer.AnswerModels.FonteContexto;
import br.com.contextpilot.answer.AnswerModels.MensagemMemoria;
import br.com.contextpilot.answer.AnswerModels.ResultadoGeracao;
import br.com.contextpilot.configuration.AiProperties;
import br.com.contextpilot.configuration.AiPrivacyProperties;
import br.com.contextpilot.configuration.OpenAiGateway;
import br.com.contextpilot.privacy.SensitiveDataProtector;
import tools.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "contextpilot.ia.provedor", havingValue = "openai")
class OpenAiAnswerGenerator implements AnswerGenerator {

    private final OpenAiGateway openAi;
    private final AiProperties propriedades;
    private final AiPrivacyProperties privacidade;
    private final SensitiveDataProtector protetor;

    OpenAiAnswerGenerator(
            OpenAiGateway openAi,
            AiProperties propriedades,
            AiPrivacyProperties privacidade,
            SensitiveDataProtector protetor) {
        this.openAi = openAi;
        this.propriedades = propriedades;
        this.privacidade = privacidade;
        this.protetor = protetor;
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

        var entradaProtegida = privacidade.protegerDadosSensiveisProvedorExterno()
                ? protetor.proteger(entrada)
                : new SensitiveDataProtector.TextoProtegido(entrada, Map.of(), Map.of());

        JsonNode resposta = openAi.enviar("/v1/responses", Map.of(
                "model", propriedades.modeloChat(),
                "instructions", RagPrompt.INSTRUCOES,
                "input", entradaProtegida.texto(),
                "max_output_tokens", propriedades.limiteTokensResposta(),
                "store", false));

        String texto = entradaProtegida.restaurar(extrairTexto(resposta));
        if (texto.isBlank()) {
            throw new IllegalStateException("A OpenAI nao retornou texto na resposta.");
        }
        int tokensEntrada = resposta.path("usage").path("input_tokens").asInt(0);
        int tokensSaida = resposta.path("usage").path("output_tokens").asInt(0);
        java.math.BigDecimal custo = propriedades.calcularCustoChat(tokensEntrada, tokensSaida);
        return new ResultadoGeracao(
                texto.trim(), "openai:" + propriedades.modeloChat(), RagPrompt.VERSAO,
                PromptTrace.impressao(RagPrompt.INSTRUCOES), entradaProtegida.totalProtegido(),
                tokensEntrada, tokensSaida, custo);
    }

    private String extrairTexto(JsonNode resposta) {
        StringBuilder texto = new StringBuilder();
        for (JsonNode item : resposta.path("output")) {
            for (JsonNode conteudo : item.path("content")) {
                if ("output_text".equals(conteudo.path("type").asText())) {
                    if (!texto.isEmpty()) {
                        texto.append('\n');
                    }
                    texto.append(conteudo.path("text").asText());
                }
            }
        }
        return texto.toString();
    }
}

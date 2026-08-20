package br.com.contextpilot.configuration;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("contextpilot.ia")
public record AiProperties(
        String provedor,
        String urlBase,
        String chave,
        String modeloChat,
        String modeloEmbedding,
        int dimensoes,
        int limiteTokensResposta,
        BigDecimal custoChatEntradaMilhao,
        BigDecimal custoChatSaidaMilhao,
        BigDecimal custoEmbeddingMilhao) {

    public BigDecimal calcularCustoChat(int tokensEntrada, int tokensSaida) {
        BigDecimal entrada = custoChatEntradaMilhao == null ? BigDecimal.ZERO : custoChatEntradaMilhao;
        BigDecimal saida = custoChatSaidaMilhao == null ? BigDecimal.ZERO : custoChatSaidaMilhao;
        return entrada.multiply(BigDecimal.valueOf(tokensEntrada))
                .add(saida.multiply(BigDecimal.valueOf(tokensSaida)))
                .divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.HALF_UP);
    }

    public BigDecimal calcularCustoEmbedding(int tokens) {
        BigDecimal custo = custoEmbeddingMilhao == null ? BigDecimal.ZERO : custoEmbeddingMilhao;
        return custo.multiply(BigDecimal.valueOf(tokens))
                .divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.HALF_UP);
    }
}

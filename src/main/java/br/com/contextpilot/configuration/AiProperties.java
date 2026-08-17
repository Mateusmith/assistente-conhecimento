package br.com.contextpilot.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("contextpilot.ia")
public record AiProperties(
        String provedor,
        String urlBase,
        String chave,
        String modeloChat,
        String modeloEmbedding,
        int dimensoes,
        int limiteTokensResposta) {
}

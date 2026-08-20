package br.com.contextpilot.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("contextpilot.governanca")
public record GovernanceProperties(
        boolean redisAtivo,
        int requisicoesPorMinuto,
        int consultasPorMinuto,
        Duration janelaRateLimit,
        String salPrivacidade) {
}

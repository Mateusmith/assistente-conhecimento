package br.com.contextpilot.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("contextpilot.ollama")
public record OllamaProperties(
        String urlBase,
        Duration timeout,
        int maximoTentativas) {
}

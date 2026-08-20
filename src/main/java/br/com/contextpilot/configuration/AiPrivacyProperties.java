package br.com.contextpilot.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("contextpilot.privacidade-ia")
public record AiPrivacyProperties(
        boolean protegerDadosSensiveisProvedorExterno) {
}

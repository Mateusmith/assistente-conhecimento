package br.com.contextpilot.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("contextpilot.visao")
public record VisionProperties(
        boolean ativo,
        String modelo,
        String detalhe,
        int limiteCaracteresDescricao,
        int maximoLargura,
        int maximoAltura,
        long maximoPixels,
        boolean permitirProvedorExterno) {
}

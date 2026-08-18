package br.com.contextpilot.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("contextpilot.ocr")
public record OcrProperties(
        boolean ativo,
        String executavel,
        String idiomas,
        int dpi,
        int maximoPaginas,
        Duration timeoutPorPagina) {
}

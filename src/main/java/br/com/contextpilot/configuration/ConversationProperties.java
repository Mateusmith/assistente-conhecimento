package br.com.contextpilot.configuration;

import java.time.Duration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("contextpilot.conversas")
public record ConversationProperties(
        @NotNull Duration tempoLease,
        @NotNull Duration timeoutStreaming,
        @Min(2) @Max(40) int limiteMensagensMemoria) {
}

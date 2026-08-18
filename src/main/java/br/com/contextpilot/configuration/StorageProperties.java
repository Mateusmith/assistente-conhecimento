package br.com.contextpilot.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("contextpilot.armazenamento")
public record StorageProperties(
        String endpoint,
        String regiao,
        String chaveAcesso,
        String chaveSecreta,
        String bucket,
        boolean acessoPorCaminho,
        boolean criarBucket,
        Duration timeout) {
}

package br.com.contextpilot.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("contextpilot.antivirus")
public record AntivirusProperties(
        boolean ativo,
        String host,
        int porta,
        int tamanhoBloco,
        Duration timeoutConexao,
        Duration timeoutLeitura) {
}

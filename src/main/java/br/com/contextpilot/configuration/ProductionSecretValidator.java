package br.com.contextpilot.configuration;

import java.net.URI;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
class ProductionSecretValidator implements ApplicationRunner {

    private final Environment ambiente;

    ProductionSecretValidator(Environment ambiente) {
        this.ambiente = ambiente;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        if (!ambiente.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        List<String> fracos = List.of(
                "contextpilot.governanca.sal-privacidade",
                "contextpilot.observabilidade.senha",
                "contextpilot.armazenamento.chave-secreta",
                "spring.datasource.password").stream()
                .filter(this::fraco)
                .toList();
        if (!fracos.isEmpty()) {
            throw new IllegalStateException(
                    "Segredos ausentes ou inseguros no perfil prod: " + String.join(", ", fracos));
        }
        boolean tlsNaAplicacao = ambiente.getProperty("server.ssl.enabled", Boolean.class, false);
        boolean tlsNoProxy = ambiente.getProperty(
                "contextpilot.seguranca.tls-terminado-no-proxy", Boolean.class, false);
        if (!tlsNaAplicacao && !tlsNoProxy) {
            throw new IllegalStateException(
                    "Ative TLS na aplicacao ou declare sua terminacao no proxy no perfil prod.");
        }
        if (tlsNoProxy && !"framework".equalsIgnoreCase(
                ambiente.getProperty("server.forward-headers-strategy", ""))) {
            throw new IllegalStateException(
                    "SERVER_FORWARD_HEADERS_STRATEGY deve ser framework quando TLS termina no proxy.");
        }
        exigirHttps("spring.security.oauth2.resourceserver.jwt.issuer-uri");
        exigirHttps("spring.security.oauth2.resourceserver.jwt.jwk-set-uri");
    }

    private boolean fraco(String propriedade) {
        String valor = ambiente.getProperty(propriedade, "");
        String normalizado = valor.toLowerCase(java.util.Locale.ROOT);
        return valor.length() < 16 || normalizado.contains("local") || normalizado.contains("change-me");
    }

    private void exigirHttps(String propriedade) {
        String valor = ambiente.getRequiredProperty(propriedade);
        URI endereco;
        try {
            endereco = URI.create(valor);
        } catch (IllegalArgumentException excecao) {
            throw new IllegalStateException("Endereco invalido em " + propriedade + ".", excecao);
        }
        if (!"https".equalsIgnoreCase(endereco.getScheme()) || endereco.getHost() == null) {
            throw new IllegalStateException(propriedade + " deve usar uma URL HTTPS valida no perfil prod.");
        }
    }
}

package br.com.contextpilot.configuration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

class ProductionSecretValidatorTest {

    @Test
    void deveAceitarSegredosFortesTlsEIdentidadeHttps() {
        var validador = new ProductionSecretValidator(ambienteSeguro());

        assertThatCode(() -> validador.run(argumentos())).doesNotThrowAnyException();
    }

    @Test
    void deveAceitarTlsTerminadoNoProxyComCabecalhosConfiaveis() {
        MockEnvironment ambiente = ambienteSeguro()
                .withProperty("server.ssl.enabled", "false")
                .withProperty("contextpilot.seguranca.tls-terminado-no-proxy", "true")
                .withProperty("server.forward-headers-strategy", "framework");
        var validador = new ProductionSecretValidator(ambiente);

        assertThatCode(() -> validador.run(argumentos())).doesNotThrowAnyException();
    }

    @Test
    void deveRecusarSegredoFracoEmProducao() {
        MockEnvironment ambiente = ambienteSeguro()
                .withProperty("spring.datasource.password", "senha-local");
        var validador = new ProductionSecretValidator(ambiente);

        assertThatThrownBy(() -> validador.run(argumentos()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.password");
    }

    @Test
    void deveRecusarProvedorDeIdentidadeSemHttps() {
        MockEnvironment ambiente = ambienteSeguro()
                .withProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                        "http://identidade.exemplo.com/realms/contextpilot");
        var validador = new ProductionSecretValidator(ambiente);

        assertThatThrownBy(() -> validador.run(argumentos()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("deve usar uma URL HTTPS valida");
    }

    private MockEnvironment ambienteSeguro() {
        MockEnvironment ambiente = new MockEnvironment()
                .withProperty("contextpilot.governanca.sal-privacidade", "sal-privacidade-seguro-2026")
                .withProperty("contextpilot.observabilidade.senha", "senha-observabilidade-segura")
                .withProperty("contextpilot.armazenamento.chave-secreta", "chave-armazenamento-segura")
                .withProperty("spring.datasource.password", "senha-banco-segura-2026")
                .withProperty("server.ssl.enabled", "true")
                .withProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                        "https://identidade.exemplo.com/realms/contextpilot")
                .withProperty("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                        "https://identidade.exemplo.com/realms/contextpilot/certs");
        ambiente.setActiveProfiles("prod");
        return ambiente;
    }

    private DefaultApplicationArguments argumentos() {
        return new DefaultApplicationArguments(new String[0]);
    }
}

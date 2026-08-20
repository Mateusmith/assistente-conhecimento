package br.com.contextpilot.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtAudienceValidatorTest {

    private final JwtAudienceValidator validador = new JwtAudienceValidator("contextpilot-api");

    @Test
    void deveAceitarTokenDestinadoAApi() {
        assertThat(validador.validate(token(List.of("account", "contextpilot-api"))).hasErrors()).isFalse();
    }

    @Test
    void deveRecusarTokenEmitidoParaOutroPublico() {
        assertThat(validador.validate(token(List.of("outro-servico"))).hasErrors()).isTrue();
    }

    private Jwt token(List<String> audiencias) {
        Instant agora = Instant.now();
        return Jwt.withTokenValue("token-de-teste")
                .header("alg", "none")
                .subject("usuario")
                .audience(audiencias)
                .issuedAt(agora)
                .expiresAt(agora.plusSeconds(60))
                .build();
    }
}

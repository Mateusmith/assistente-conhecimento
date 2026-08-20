package br.com.contextpilot.configuration;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public class JwtAudienceValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error ERRO = new OAuth2Error(
            "invalid_token", "O token nao foi emitido para esta API.", null);

    private final String audienciaEsperada;

    public JwtAudienceValidator(String audienciaEsperada) {
        if (audienciaEsperada == null || audienciaEsperada.isBlank()) {
            throw new IllegalArgumentException("A audiencia JWT esperada deve ser informada.");
        }
        this.audienciaEsperada = audienciaEsperada.trim();
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        return token.getAudience().contains(audienciaEsperada)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(ERRO);
    }
}

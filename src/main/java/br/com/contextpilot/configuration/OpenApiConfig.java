package br.com.contextpilot.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI assistenteConhecimentoOpenApi(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String emissor) {
        var fluxo = new OAuthFlow()
                .authorizationUrl(emissor + "/protocol/openid-connect/auth")
                .tokenUrl(emissor + "/protocol/openid-connect/token");

        var esquema = new SecurityScheme()
                .type(SecurityScheme.Type.OAUTH2)
                .flows(new OAuthFlows().authorizationCode(fluxo));

        return new OpenAPI()
                .info(new Info()
                        .title("Assistente de Conhecimento API")
                        .version("1.0.0")
                        .description("Conhecimento corporativo com RAG seguro, fontes verificaveis e MCP."))
                .components(new Components().addSecuritySchemes("oauth2", esquema))
                .addSecurityItem(new SecurityRequirement().addList("oauth2"));
    }
}

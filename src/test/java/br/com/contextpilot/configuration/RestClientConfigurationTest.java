package br.com.contextpilot.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.client.RestClient;

class RestClientConfigurationTest {

    @Test
    void deveFornecerUmConstrutorIsoladoParaCadaGateway() {
        try (var contexto = new AnnotationConfigApplicationContext(RestClientConfiguration.class)) {
            RestClient.Builder primeiro = contexto.getBean(RestClient.Builder.class);
            RestClient.Builder segundo = contexto.getBean(RestClient.Builder.class);

            assertThat(primeiro).isNotSameAs(segundo);
        }
    }
}

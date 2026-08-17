package br.com.contextpilot.configuration;

import java.time.Duration;

import tools.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(name = "contextpilot.ia.provedor", havingValue = "openai")
public class OpenAiGateway {

    private final RestClient cliente;

    public OpenAiGateway(RestClient.Builder construtor, AiProperties propriedades) {
        if (propriedades.chave() == null || propriedades.chave().isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY e obrigatoria quando o provedor de IA e openai.");
        }
        var fabrica = new JdkClientHttpRequestFactory();
        fabrica.setReadTimeout(Duration.ofSeconds(60));
        this.cliente = construtor
                .baseUrl(propriedades.urlBase())
                .requestFactory(fabrica)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + propriedades.chave())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public JsonNode enviar(String caminho, Object corpo) {
        RuntimeException ultimaFalha = null;
        for (int tentativa = 1; tentativa <= 3; tentativa++) {
            try {
                JsonNode resposta = cliente.post().uri(caminho).body(corpo).retrieve().body(JsonNode.class);
                if (resposta == null) {
                    throw new IllegalStateException("A OpenAI retornou uma resposta vazia.");
                }
                return resposta;
            } catch (RestClientResponseException excecao) {
                ultimaFalha = new IllegalStateException(
                        "A OpenAI recusou a requisicao com status " + excecao.getStatusCode().value() + ".", excecao);
                if (!(excecao.getStatusCode().value() == 429 || excecao.getStatusCode().is5xxServerError())) {
                    throw ultimaFalha;
                }
            }
            aguardar(tentativa * 300L);
        }
        throw ultimaFalha == null ? new IllegalStateException("Falha ao consultar a OpenAI.") : ultimaFalha;
    }

    private void aguardar(long milissegundos) {
        try {
            Thread.sleep(milissegundos);
        } catch (InterruptedException excecao) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("A consulta a OpenAI foi interrompida.", excecao);
        }
    }
}

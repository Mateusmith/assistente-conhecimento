package br.com.contextpilot.configuration;

import java.time.Duration;

import tools.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(name = "contextpilot.ia.provedor", havingValue = "ollama")
public class OllamaGateway {

    private final RestClient cliente;
    private final int maximoTentativas;

    public OllamaGateway(RestClient.Builder construtor, OllamaProperties propriedades) {
        validar(propriedades);
        var fabrica = new JdkClientHttpRequestFactory();
        fabrica.setReadTimeout(propriedades.timeout());
        this.cliente = construtor
                .baseUrl(propriedades.urlBase())
                .requestFactory(fabrica)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.maximoTentativas = propriedades.maximoTentativas();
    }

    public JsonNode enviar(String caminho, Object corpo) {
        RuntimeException ultimaFalha = null;
        for (int tentativa = 1; tentativa <= maximoTentativas; tentativa++) {
            try {
                JsonNode resposta = cliente.post().uri(caminho).body(corpo).retrieve().body(JsonNode.class);
                if (resposta == null) {
                    throw new IllegalStateException("O Ollama retornou uma resposta vazia.");
                }
                return resposta;
            } catch (RestClientResponseException excecao) {
                ultimaFalha = new IllegalStateException(
                        "O Ollama recusou a requisicao com status " + excecao.getStatusCode().value() + ".", excecao);
                if (!(excecao.getStatusCode().value() == 429 || excecao.getStatusCode().is5xxServerError())) {
                    throw ultimaFalha;
                }
            } catch (org.springframework.web.client.ResourceAccessException excecao) {
                ultimaFalha = new IllegalStateException(
                        "O Ollama local nao esta acessivel em " + caminho + ".", excecao);
            }
            aguardar(tentativa * 300L);
        }
        throw ultimaFalha == null ? new IllegalStateException("Falha ao consultar o Ollama.") : ultimaFalha;
    }

    private void validar(OllamaProperties propriedades) {
        Duration timeout = propriedades.timeout();
        if (propriedades.urlBase() == null || propriedades.urlBase().isBlank()
                || timeout == null || timeout.isZero() || timeout.isNegative()
                || propriedades.maximoTentativas() < 1 || propriedades.maximoTentativas() > 5) {
            throw new IllegalStateException("A configuracao do Ollama e invalida.");
        }
    }

    private void aguardar(long milissegundos) {
        try {
            Thread.sleep(milissegundos);
        } catch (InterruptedException excecao) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("A consulta ao Ollama foi interrompida.", excecao);
        }
    }
}

package br.com.contextpilot.retrieval;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.com.contextpilot.configuration.AiProperties;
import br.com.contextpilot.shared.domain.BusinessRuleException;
import br.com.contextpilot.shared.domain.ServiceUnavailableException;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingProviderRegistry {

    private final Map<String, EmbeddingProvider> provedores;
    private final AiProperties propriedades;

    public EmbeddingProviderRegistry(List<EmbeddingProvider> provedores, AiProperties propriedades) {
        this.provedores = new LinkedHashMap<>();
        for (EmbeddingProvider provedor : provedores) {
            if (this.provedores.put(provedor.nome(), provedor) != null) {
                throw new IllegalStateException("Existe mais de um provedor para o modelo " + provedor.nome() + ".");
            }
        }
        this.propriedades = propriedades;
    }

    public EmbeddingProvider obter(String modelo) {
        EmbeddingProvider provedor = provedores.get(modelo);
        if (provedor == null) {
            throw new ServiceUnavailableException(
                    "O modelo de embedding '%s' nao esta habilitado nesta instancia.".formatted(modelo));
        }
        return provedor;
    }

    public EmbeddingProvider padrao() {
        String modelo = "openai".equalsIgnoreCase(propriedades.provedor())
                ? "openai:" + propriedades.modeloEmbedding()
                : "local-hashing-v1";
        return obter(modelo);
    }

    public List<ModeloDisponivel> listar() {
        return provedores.values().stream()
                .map(provedor -> new ModeloDisponivel(provedor.nome(), provedor.provedor(), provedor.dimensoes()))
                .toList();
    }

    public void validarModelo(String modelo) {
        if (!provedores.containsKey(modelo)) {
            throw new BusinessRuleException("Modelo indisponivel. Consulte os modelos habilitados na API.");
        }
    }

    public record ModeloDisponivel(String modelo, String provedor, int dimensoes) {
    }
}

package br.com.contextpilot.retrieval;

import java.util.List;
import java.util.UUID;

import br.com.contextpilot.retrieval.RetrievalModels.FonteRecuperada;
import br.com.contextpilot.workspace.WorkspaceAccessService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class HybridSearchService {

    private final HybridSearchRepository repositorio;
    private final EmbeddingProvider embeddings;
    private final WorkspaceAccessService acessoEspaco;
    private final int limiteFontes;
    private final double pontuacaoMinima;

    public HybridSearchService(
            HybridSearchRepository repositorio,
            EmbeddingProvider embeddings,
            WorkspaceAccessService acessoEspaco,
            @Value("${contextpilot.busca.limite-fontes}") int limiteFontes,
            @Value("${contextpilot.busca.pontuacao-minima}") double pontuacaoMinima) {
        this.repositorio = repositorio;
        this.embeddings = embeddings;
        this.acessoEspaco = acessoEspaco;
        this.limiteFontes = limiteFontes;
        this.pontuacaoMinima = pontuacaoMinima;
    }

    public List<FonteRecuperada> buscar(UUID espacoId, String pergunta, String usuarioId) {
        acessoEspaco.exigirMembro(espacoId, usuarioId);
        if (pergunta == null || pergunta.isBlank()) {
            return List.of();
        }
        String vetor = VectorText.serializar(embeddings.gerar(pergunta));
        return repositorio.buscar(espacoId, usuarioId, pergunta.trim(), vetor, limiteFontes).stream()
                .filter(fonte -> fonte.pontuacao() >= pontuacaoMinima)
                .toList();
    }
}

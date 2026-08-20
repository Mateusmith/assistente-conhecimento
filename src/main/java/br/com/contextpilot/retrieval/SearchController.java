package br.com.contextpilot.retrieval;

import java.util.UUID;

import br.com.contextpilot.retrieval.RetrievalModels.ComparacaoBuscaResponse;
import br.com.contextpilot.retrieval.RetrievalModels.CompararBuscaRequest;
import br.com.contextpilot.shared.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/espacos/{espacoId}/buscas")
public class SearchController {

    private final HybridSearchService servico;
    private final CurrentUser usuarioAtual;

    public SearchController(HybridSearchService servico, CurrentUser usuarioAtual) {
        this.servico = servico;
        this.usuarioAtual = usuarioAtual;
    }

    @PostMapping("/comparacoes")
    ComparacaoBuscaResponse comparar(
            @PathVariable UUID espacoId,
            @Valid @RequestBody CompararBuscaRequest requisicao) {
        return servico.comparar(espacoId, requisicao.pergunta(), requisicao.filtros(), usuarioAtual.obterId());
    }
}

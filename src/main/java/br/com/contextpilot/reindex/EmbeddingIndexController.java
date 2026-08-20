package br.com.contextpilot.reindex;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import br.com.contextpilot.reindex.EmbeddingIndexModels.CriarIndiceRequest;
import br.com.contextpilot.reindex.EmbeddingIndexModels.IndiceEmbeddingResponse;
import br.com.contextpilot.retrieval.EmbeddingProviderRegistry.ModeloDisponivel;
import br.com.contextpilot.shared.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/espacos/{espacoId}/indices-embedding")
public class EmbeddingIndexController {

    private final EmbeddingIndexService servico;
    private final CurrentUser usuarioAtual;

    public EmbeddingIndexController(EmbeddingIndexService servico, CurrentUser usuarioAtual) {
        this.servico = servico;
        this.usuarioAtual = usuarioAtual;
    }

    @GetMapping
    List<IndiceEmbeddingResponse> listar(@PathVariable UUID espacoId) {
        return servico.listar(espacoId, usuarioAtual.obterId());
    }

    @GetMapping("/modelos")
    List<ModeloDisponivel> listarModelos(@PathVariable UUID espacoId) {
        servico.listar(espacoId, usuarioAtual.obterId());
        return servico.listarModelos();
    }

    @PostMapping
    ResponseEntity<IndiceEmbeddingResponse> iniciar(
            @PathVariable UUID espacoId,
            @Valid @RequestBody CriarIndiceRequest requisicao) {
        IndiceEmbeddingResponse criado = servico.iniciar(espacoId, requisicao, usuarioAtual.obterId());
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/espacos/%s/indices-embedding/%s".formatted(espacoId, criado.id())))
                .body(criado);
    }

    @PostMapping("/{indiceId}/ativacao")
    IndiceEmbeddingResponse ativar(@PathVariable UUID espacoId, @PathVariable UUID indiceId) {
        return servico.ativar(espacoId, indiceId, usuarioAtual.obterId());
    }
}

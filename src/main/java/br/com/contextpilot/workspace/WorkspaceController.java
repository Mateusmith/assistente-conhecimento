package br.com.contextpilot.workspace;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import br.com.contextpilot.shared.security.CurrentUser;
import br.com.contextpilot.workspace.WorkspaceModels.AdicionarMembroRequest;
import br.com.contextpilot.workspace.WorkspaceModels.CriarEspacoRequest;
import br.com.contextpilot.workspace.WorkspaceModels.EspacoResponse;
import br.com.contextpilot.workspace.WorkspaceModels.MembroResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/espacos")
public class WorkspaceController {

    private final WorkspaceService servico;
    private final CurrentUser usuarioAtual;

    public WorkspaceController(WorkspaceService servico, CurrentUser usuarioAtual) {
        this.servico = servico;
        this.usuarioAtual = usuarioAtual;
    }

    @PostMapping
    ResponseEntity<EspacoResponse> criar(@Valid @RequestBody CriarEspacoRequest requisicao) {
        EspacoResponse criado = servico.criar(requisicao, usuarioAtual.obterId());
        return ResponseEntity.created(URI.create("/api/v1/espacos/" + criado.id())).body(criado);
    }

    @GetMapping
    List<EspacoResponse> listar() {
        return servico.listar(usuarioAtual.obterId());
    }

    @GetMapping("/{espacoId}")
    EspacoResponse buscar(@PathVariable UUID espacoId) {
        return servico.buscar(espacoId, usuarioAtual.obterId());
    }

    @PostMapping("/{espacoId}/membros")
    ResponseEntity<MembroResponse> adicionarMembro(
            @PathVariable UUID espacoId,
            @Valid @RequestBody AdicionarMembroRequest requisicao) {
        return ResponseEntity.status(201).body(servico.adicionarMembro(espacoId, requisicao, usuarioAtual.obterId()));
    }

    @GetMapping("/{espacoId}/membros")
    List<MembroResponse> listarMembros(@PathVariable UUID espacoId) {
        return servico.listarMembros(espacoId, usuarioAtual.obterId());
    }
}

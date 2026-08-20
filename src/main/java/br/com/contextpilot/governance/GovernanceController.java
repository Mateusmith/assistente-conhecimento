package br.com.contextpilot.governance;

import java.util.UUID;

import br.com.contextpilot.governance.GovernanceModels.AtualizarGovernancaRequest;
import br.com.contextpilot.governance.GovernanceModels.GovernancaResponse;
import br.com.contextpilot.governance.GovernanceModels.UsoEspacoResponse;
import br.com.contextpilot.shared.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/espacos/{espacoId}/governanca")
public class GovernanceController {

    private final GovernanceService servico;
    private final CurrentUser usuarioAtual;

    public GovernanceController(GovernanceService servico, CurrentUser usuarioAtual) {
        this.servico = servico;
        this.usuarioAtual = usuarioAtual;
    }

    @GetMapping
    GovernancaResponse buscar(@PathVariable UUID espacoId) {
        return servico.buscar(espacoId, usuarioAtual.obterId());
    }

    @PutMapping
    GovernancaResponse atualizar(
            @PathVariable UUID espacoId,
            @Valid @RequestBody AtualizarGovernancaRequest requisicao) {
        return servico.atualizar(espacoId, requisicao, usuarioAtual.obterId());
    }

    @GetMapping("/uso")
    UsoEspacoResponse uso(@PathVariable UUID espacoId) {
        return servico.consultarUso(espacoId, usuarioAtual.obterId());
    }
}

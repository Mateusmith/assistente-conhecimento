package br.com.contextpilot.audit;

import java.util.List;
import java.util.UUID;

import br.com.contextpilot.audit.AuditService.EventoAuditoria;
import br.com.contextpilot.shared.security.CurrentUser;
import br.com.contextpilot.workspace.WorkspaceAccessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/espacos/{espacoId}/auditoria")
public class AuditController {

    private final AuditService auditoria;
    private final WorkspaceAccessService acessoEspaco;
    private final CurrentUser usuarioAtual;

    public AuditController(AuditService auditoria, WorkspaceAccessService acessoEspaco, CurrentUser usuarioAtual) {
        this.auditoria = auditoria;
        this.acessoEspaco = acessoEspaco;
        this.usuarioAtual = usuarioAtual;
    }

    @GetMapping
    List<EventoAuditoria> listar(@PathVariable UUID espacoId, @RequestParam(defaultValue = "50") int limite) {
        String usuarioId = usuarioAtual.obterId();
        acessoEspaco.exigirProprietario(espacoId, usuarioId);
        return auditoria.listar(espacoId, usuarioId, limite);
    }
}

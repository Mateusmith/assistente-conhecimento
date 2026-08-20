package br.com.contextpilot.privacy;

import br.com.contextpilot.privacy.PrivacyModels.ExclusaoPrivacidadeResponse;
import br.com.contextpilot.privacy.PrivacyModels.ExportacaoPrivacidade;
import br.com.contextpilot.shared.security.CurrentUser;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/privacidade")
public class PrivacyController {

    private final PrivacyService servico;
    private final CurrentUser usuarioAtual;

    public PrivacyController(PrivacyService servico, CurrentUser usuarioAtual) {
        this.servico = servico;
        this.usuarioAtual = usuarioAtual;
    }

    @GetMapping("/exportacao")
    ResponseEntity<ExportacaoPrivacidade> exportar() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("meus-dados-contextpilot.json").build().toString())
                .body(servico.exportar(usuarioAtual.obterId()));
    }

    @DeleteMapping("/meus-dados")
    ExclusaoPrivacidadeResponse excluir(@RequestParam boolean confirmar) {
        if (!confirmar) {
            throw new br.com.contextpilot.shared.domain.BusinessRuleException(
                    "Confirme explicitamente a exclusao com confirmar=true.");
        }
        return servico.excluir(usuarioAtual.obterId());
    }
}

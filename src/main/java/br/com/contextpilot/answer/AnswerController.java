package br.com.contextpilot.answer;

import java.util.List;
import java.util.UUID;

import br.com.contextpilot.answer.AnswerModels.PerguntarRequest;
import br.com.contextpilot.answer.AnswerModels.RegistrarFeedbackRequest;
import br.com.contextpilot.answer.AnswerModels.RespostaRag;
import br.com.contextpilot.shared.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/espacos/{espacoId}/consultas")
public class AnswerController {

    private final AnswerService servico;
    private final CurrentUser usuarioAtual;

    public AnswerController(AnswerService servico, CurrentUser usuarioAtual) {
        this.servico = servico;
        this.usuarioAtual = usuarioAtual;
    }

    @PostMapping
    RespostaRag perguntar(@PathVariable UUID espacoId, @Valid @RequestBody PerguntarRequest requisicao) {
        return servico.perguntar(espacoId, requisicao, usuarioAtual.obterId());
    }

    @GetMapping
    List<RespostaRag> listar(@PathVariable UUID espacoId, @RequestParam(defaultValue = "20") int limite) {
        return servico.listar(espacoId, usuarioAtual.obterId(), limite);
    }

    @GetMapping("/{consultaId}")
    RespostaRag buscar(@PathVariable UUID espacoId, @PathVariable UUID consultaId) {
        return servico.buscar(espacoId, consultaId, usuarioAtual.obterId());
    }

    @PostMapping("/{consultaId}/feedback")
    ResponseEntity<Void> feedback(
            @PathVariable UUID espacoId,
            @PathVariable UUID consultaId,
            @Valid @RequestBody RegistrarFeedbackRequest requisicao) {
        servico.registrarFeedback(espacoId, consultaId, requisicao, usuarioAtual.obterId());
        return ResponseEntity.noContent().build();
    }
}

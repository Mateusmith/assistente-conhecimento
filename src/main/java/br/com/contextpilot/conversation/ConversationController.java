package br.com.contextpilot.conversation;

import java.util.List;
import java.util.UUID;

import br.com.contextpilot.answer.AnswerModels.PerguntarRequest;
import br.com.contextpilot.conversation.ConversationModels.AtualizarConversaRequest;
import br.com.contextpilot.conversation.ConversationModels.ConversaDetalhe;
import br.com.contextpilot.conversation.ConversationModels.ConversaResumo;
import br.com.contextpilot.conversation.ConversationModels.CriarConversaRequest;
import br.com.contextpilot.conversation.ConversationModels.InteracaoConversa;
import br.com.contextpilot.shared.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/espacos/{espacoId}/conversas")
public class ConversationController {

    private final ConversationService servico;
    private final ConversationStreamingService streaming;
    private final CurrentUser usuarioAtual;

    public ConversationController(
            ConversationService servico,
            ConversationStreamingService streaming,
            CurrentUser usuarioAtual) {
        this.servico = servico;
        this.streaming = streaming;
        this.usuarioAtual = usuarioAtual;
    }

    @PostMapping
    ResponseEntity<ConversaResumo> criar(
            @PathVariable UUID espacoId,
            @Valid @RequestBody CriarConversaRequest requisicao) {
        return ResponseEntity.status(201).body(servico.criar(espacoId, requisicao, usuarioAtual.obterId()));
    }

    @GetMapping
    List<ConversaResumo> listar(
            @PathVariable UUID espacoId,
            @RequestParam(defaultValue = "20") int limite) {
        return servico.listar(espacoId, usuarioAtual.obterId(), limite);
    }

    @GetMapping("/{conversaId}")
    ConversaDetalhe buscar(@PathVariable UUID espacoId, @PathVariable UUID conversaId) {
        return servico.buscar(espacoId, conversaId, usuarioAtual.obterId());
    }

    @PutMapping("/{conversaId}")
    ConversaResumo atualizar(
            @PathVariable UUID espacoId,
            @PathVariable UUID conversaId,
            @Valid @RequestBody AtualizarConversaRequest requisicao) {
        return servico.atualizar(espacoId, conversaId, requisicao, usuarioAtual.obterId());
    }

    @PostMapping("/{conversaId}/mensagens")
    InteracaoConversa perguntar(
            @PathVariable UUID espacoId,
            @PathVariable UUID conversaId,
            @RequestHeader(value = "Idempotency-Key", required = false) String chaveIdempotencia,
            @Valid @RequestBody PerguntarRequest requisicao) {
        return servico.perguntar(
                espacoId, conversaId, requisicao, usuarioAtual.obterId(), chaveIdempotencia);
    }

    @PostMapping(value = "/{conversaId}/mensagens/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter perguntarComStreaming(
            @PathVariable UUID espacoId,
            @PathVariable UUID conversaId,
            @RequestHeader(value = "Idempotency-Key", required = false) String chaveIdempotencia,
            @Valid @RequestBody PerguntarRequest requisicao) {
        return streaming.iniciar(
                espacoId, conversaId, requisicao, usuarioAtual.obterId(), chaveIdempotencia);
    }

    @DeleteMapping("/{conversaId}")
    ResponseEntity<Void> excluir(@PathVariable UUID espacoId, @PathVariable UUID conversaId) {
        servico.excluir(espacoId, conversaId, usuarioAtual.obterId());
        return ResponseEntity.noContent().build();
    }
}

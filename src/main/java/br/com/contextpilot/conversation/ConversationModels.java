package br.com.contextpilot.conversation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import br.com.contextpilot.answer.AnswerModels.RespostaRag;
import jakarta.validation.constraints.Size;

public final class ConversationModels {

    private ConversationModels() {
    }

    public enum EstadoConversa {
        ATIVA,
        ARQUIVADA
    }

    public enum PapelMensagem {
        USUARIO,
        ASSISTENTE
    }

    public record CriarConversaRequest(@Size(max = 160) String titulo) {
    }

    public record AtualizarConversaRequest(
            @Size(max = 160) String titulo,
            EstadoConversa estado) {
    }

    public record ConversaResumo(
            UUID id,
            UUID espacoId,
            String titulo,
            EstadoConversa estado,
            long versao,
            int quantidadeMensagens,
            Instant criadaEm,
            Instant atualizadaEm) {
    }

    public record MensagemConversa(
            UUID id,
            UUID conversaId,
            UUID consultaId,
            int sequencia,
            PapelMensagem papel,
            String conteudo,
            Instant criadaEm) {
    }

    public record ConversaDetalhe(
            ConversaResumo conversa,
            List<MensagemConversa> mensagens) {
    }

    public record InteracaoConversa(
            UUID conversaId,
            long versao,
            MensagemConversa mensagemUsuario,
            MensagemConversa mensagemAssistente,
            RespostaRag resposta) {
    }
}

package br.com.contextpilot.answer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AnswerModels {

    public static final String RESPOSTA_SEM_CONTEXTO = "Nao encontrei informacao suficiente nos documentos que voce pode acessar.";

    private AnswerModels() {
    }

    public record PerguntarRequest(@NotBlank @Size(max = 2000) String pergunta) {
    }

    public record RegistrarFeedbackRequest(
            @NotNull Boolean util,
            @Size(max = 1000) String comentario) {
    }

    public record FonteResposta(
            String marcador,
            UUID documentoId,
            String tituloDocumento,
            int ordemTrecho,
            String excerto,
            double pontuacao) {
    }

    public record RespostaRag(
            UUID consultaId,
            String pergunta,
            String resposta,
            boolean recusada,
            String provedorIa,
            long latenciaMs,
            Instant criadaEm,
            List<FonteResposta> fontes) {
    }

    record FonteContexto(
            String marcador,
            UUID trechoId,
            UUID documentoId,
            String tituloDocumento,
            int ordemTrecho,
            String conteudo,
            double pontuacao) {
    }

    public record ResultadoGeracao(String texto, String provedor) {
    }
}

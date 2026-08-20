package br.com.contextpilot.answer;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import br.com.contextpilot.retrieval.RetrievalModels.EstrategiaBusca;
import br.com.contextpilot.retrieval.RetrievalModels.FiltrosBusca;

public final class AnswerModels {

    public static final String RESPOSTA_SEM_CONTEXTO = "Nao encontrei informacao suficiente nos documentos que voce pode acessar.";

    private AnswerModels() {
    }

    public record PerguntarRequest(
            @NotBlank @Size(max = 2000) String pergunta,
            EstrategiaBusca estrategia,
            @Valid FiltrosBusca filtros) {

        public PerguntarRequest(String pergunta) {
            this(pergunta, EstrategiaBusca.HIBRIDA, FiltrosBusca.vazios());
        }
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

    public enum PapelMemoria {
        USUARIO,
        ASSISTENTE
    }

    public record MensagemMemoria(PapelMemoria papel, String conteudo) {
    }

    public record RespostaRag(
            UUID consultaId,
            String pergunta,
            String resposta,
            boolean recusada,
            String provedorIa,
            UUID indiceEmbeddingId,
            String modeloEmbedding,
            EstrategiaBusca estrategiaBusca,
            String versaoPrompt,
            String impressaoPrompt,
            int candidatosRecuperados,
            int fontesContexto,
            int dadosSensiveisProtegidos,
            int tokensEntrada,
            int tokensSaida,
            BigDecimal custoEstimadoUsd,
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

    public record ResultadoGeracao(
            String texto,
            String provedor,
            String versaoPrompt,
            String impressaoPrompt,
            int dadosSensiveisProtegidos,
            int tokensEntrada,
            int tokensSaida,
            BigDecimal custoEstimadoUsd) {

        public ResultadoGeracao(String texto, String provedor) {
            this(texto, provedor, "nao-aplicavel", PromptTrace.impressao(provedor),
                    0, 0, 0, BigDecimal.ZERO);
        }
    }
}

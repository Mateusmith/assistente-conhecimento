package br.com.contextpilot.retrieval;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class RetrievalModels {

    private RetrievalModels() {
    }

    public enum EstrategiaBusca {
        HIBRIDA,
        SEMANTICA,
        TEXTUAL
    }

    public record FiltrosBusca(
            @Size(max = 50) List<@NotNull UUID> documentos,
            @Size(max = 20) Map<@NotBlank @Size(max = 60) String,
                    @NotBlank @Size(max = 200) String> metadados,
            @Size(max = 20) List<@NotBlank @Size(max = 60) String> tags,
            @Size(max = 100) String tipoMime,
            Instant criadoDe,
            Instant criadoAte) {

        public static FiltrosBusca vazios() {
            return new FiltrosBusca(List.of(), Map.of(), List.of(), null, null, null);
        }
    }

    public record CompararBuscaRequest(
            @NotBlank @Size(max = 2000) String pergunta,
            @Valid FiltrosBusca filtros) {
    }

    public record FonteRecuperada(
            UUID trechoId,
            UUID documentoId,
            String tituloDocumento,
            int ordemTrecho,
            String conteudo,
            double pontuacaoSemantica,
            double pontuacaoTextual,
            double pontuacao) {
    }

    public record ResultadoBusca(
            UUID indiceId,
            String modeloEmbedding,
            EstrategiaBusca estrategia,
            List<FonteRecuperada> fontes) {
    }

    public record ResultadoEstrategia(
            EstrategiaBusca estrategia,
            List<FonteRecuperada> fontes) {
    }

    public record ComparacaoBuscaResponse(
            UUID indiceId,
            String modeloEmbedding,
            List<ResultadoEstrategia> resultados,
            double sobreposicaoSemanticaTextual) {
    }
}

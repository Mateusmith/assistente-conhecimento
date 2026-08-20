package br.com.contextpilot.evaluation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class EvaluationModels {

    private EvaluationModels() {
    }

    public record CriarConjuntoRequest(
            @NotBlank @Size(max = 160) String nome,
            @Size(max = 500) String descricao) {
    }

    public record CriarCasoRequest(
            @NotBlank @Size(max = 2000) String pergunta,
            @NotNull List<@NotBlank @Size(max = 120) String> termosEsperados,
            @NotNull List<UUID> documentosEsperados,
            boolean deveRecusar,
            @Positive Long latenciaMaximaMs,
            @DecimalMin("0.0") BigDecimal custoMaximoUsd) {
    }

    public record ConjuntoAvaliacao(
            UUID id,
            UUID espacoId,
            String nome,
            String descricao,
            String criadoPor,
            Instant criadoEm,
            int quantidadeCasos) {
    }

    public record CasoAvaliacao(
            UUID id,
            UUID conjuntoId,
            String pergunta,
            List<String> termosEsperados,
            List<UUID> documentosEsperados,
            boolean deveRecusar,
            Long latenciaMaximaMs,
            BigDecimal custoMaximoUsd) {
    }

    public record ResultadoCaso(
            UUID casoId,
            UUID consultaId,
            boolean aprovado,
            double pontuacaoTermos,
            double pontuacaoFontes,
            double precisaoFontes,
            double mrr,
            boolean recusaCorreta,
            long latenciaMs,
            BigDecimal custoUsd,
            boolean orcamentoRespeitado,
            String detalhes) {
    }

    public record ExecucaoAvaliacao(
            UUID id,
            UUID conjuntoId,
            String estado,
            String erro,
            int totalCasos,
            int casosAprovados,
            double taxaAcerto,
            double recallMedio,
            double precisaoMedia,
            double mrrMedio,
            long latenciaP95Ms,
            BigDecimal custoTotalUsd,
            String modeloEmbedding,
            String provedorIa,
            Instant iniciadaEm,
            Instant finalizadaEm,
            List<ResultadoCaso> resultados) {
    }

    public record ComparacaoExecucoes(
            UUID execucaoAtualId,
            UUID execucaoBaseId,
            double deltaTaxaAcerto,
            double deltaRecall,
            double deltaPrecisao,
            double deltaMrr,
            long deltaLatenciaP95Ms,
            BigDecimal deltaCustoUsd,
            boolean regressao,
            List<String> motivos) {
    }
}

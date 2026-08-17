package br.com.contextpilot.evaluation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
            boolean deveRecusar) {
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
            boolean deveRecusar) {
    }

    public record ResultadoCaso(
            UUID casoId,
            UUID consultaId,
            boolean aprovado,
            double pontuacaoTermos,
            double pontuacaoFontes,
            boolean recusaCorreta,
            String detalhes) {
    }

    public record ExecucaoAvaliacao(
            UUID id,
            UUID conjuntoId,
            String estado,
            int totalCasos,
            int casosAprovados,
            double taxaAcerto,
            Instant iniciadaEm,
            Instant finalizadaEm,
            List<ResultadoCaso> resultados) {
    }
}

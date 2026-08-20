package br.com.contextpilot.governance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public final class GovernanceModels {

    private GovernanceModels() {
    }

    public record AtualizarGovernancaRequest(
            @NotNull @Min(10_485_760) @Max(1_099_511_627_776L) Long limiteArmazenamentoBytes,
            @NotNull @Min(1) @Max(1_000_000) Integer limiteConsultasDia,
            @NotNull @Min(1) @Max(100_000) Integer limiteUploadsDia,
            @NotNull @Min(1) @Max(3650) Integer retencaoConsultasDias) {
    }

    public record GovernancaResponse(
            UUID espacoId,
            long limiteArmazenamentoBytes,
            int limiteConsultasDia,
            int limiteUploadsDia,
            int retencaoConsultasDias) {
    }

    public record UsoEspacoResponse(
            UUID espacoId,
            LocalDate data,
            long armazenamentoUsadoBytes,
            long limiteArmazenamentoBytes,
            int consultasHoje,
            int limiteConsultasDia,
            int uploadsHoje,
            int limiteUploadsDia,
            long tokensEntradaUltimos30Dias,
            long tokensSaidaUltimos30Dias,
            BigDecimal custoEstimadoUsdUltimos30Dias,
            List<ConsumoIaResponse> consumoIa) {
    }

    public record ConsumoIaResponse(
            LocalDate data,
            String provedor,
            String modelo,
            String operacao,
            long chamadas,
            long tokensEntrada,
            long tokensSaida,
            BigDecimal custoEstimadoUsd) {
    }
}

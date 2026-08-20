package br.com.contextpilot.reindex;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class EmbeddingIndexModels {

    private EmbeddingIndexModels() {
    }

    public enum EstadoIndice {
        ATIVO,
        CONSTRUINDO,
        ARQUIVADO,
        FALHOU
    }

    public record CriarIndiceRequest(
            @NotBlank @Size(max = 160) String modelo) {
    }

    public record IndiceEmbeddingResponse(
            UUID id,
            UUID espacoId,
            String provedor,
            String modelo,
            int dimensoes,
            EstadoIndice estado,
            int totalTrechos,
            int trechosProcessados,
            int progressoPercentual,
            int tentativas,
            String criadoPor,
            Instant criadoEm,
            Instant iniciadoEm,
            Instant finalizadoEm,
            Instant ativadoEm,
            String erro) {
    }

    record IndiceParaProcessar(UUID id, UUID espacoId, String modelo) {
    }

    record TrechoParaIndexar(UUID id, String conteudo) {
    }
}

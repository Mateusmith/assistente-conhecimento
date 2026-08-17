package br.com.contextpilot.workspace;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class WorkspaceModels {

    private WorkspaceModels() {
    }

    public enum PapelMembro {
        PROPRIETARIO,
        CURADOR,
        LEITOR
    }

    public record CriarEspacoRequest(
            @NotBlank @Size(max = 120) String nome,
            @Size(max = 500) String descricao) {
    }

    public record AdicionarMembroRequest(
            @NotBlank @Size(max = 120) String usuarioId,
            @NotNull PapelMembro papel) {
    }

    public record EspacoResponse(
            UUID id,
            String nome,
            String descricao,
            PapelMembro meuPapel,
            String criadoPor,
            Instant criadoEm) {
    }

    public record MembroResponse(
            String usuarioId,
            PapelMembro papel,
            String adicionadoPor,
            Instant adicionadoEm) {
    }
}

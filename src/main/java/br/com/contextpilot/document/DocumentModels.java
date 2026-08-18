package br.com.contextpilot.document;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class DocumentModels {

    private DocumentModels() {
    }

    public enum VisibilidadeDocumento {
        ESPACO,
        RESTRITO
    }

    public enum EstadoDocumento {
        PENDENTE,
        PROCESSANDO,
        PRONTO,
        FALHOU
    }

    public enum ArmazenamentoDocumento {
        BANCO,
        S3
    }

    public enum ResultadoAntivirus {
        NAO_VERIFICADO,
        LIMPO
    }

    public enum OrigemTexto {
        NATIVO,
        OCR
    }

    public enum NivelPermissaoDocumento {
        LEITURA,
        GESTAO
    }

    public record ConcederPermissaoRequest(
            @NotBlank @Size(max = 120) String usuarioId,
            @NotNull NivelPermissaoDocumento nivel) {
    }

    public record DocumentoResponse(
            UUID id,
            UUID espacoId,
            String titulo,
            String nomeArquivo,
            String tipoMime,
            ArmazenamentoDocumento armazenamento,
            ResultadoAntivirus resultadoAntivirus,
            Instant verificadoAntivirusEm,
            OrigemTexto origemTexto,
            int paginasOcr,
            VisibilidadeDocumento visibilidade,
            EstadoDocumento estado,
            int versao,
            long tamanhoBytes,
            String criadoPor,
            Instant criadoEm,
            Instant processadoEm,
            String erroProcessamento) {
    }

    record DocumentoParaIngestao(
            UUID id,
            UUID espacoId,
            String nomeArquivo,
            String tipoMime,
            ReferenciaConteudo referenciaConteudo) {
    }

    record ReferenciaConteudo(
            ArmazenamentoDocumento armazenamento,
            String chaveArmazenamento,
            byte[] conteudoLegado) {
    }

    record TarefaIngestao(UUID id, UUID documentoId, int tentativa) {
    }
}

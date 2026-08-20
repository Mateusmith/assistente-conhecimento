package br.com.contextpilot.privacy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PrivacyModels {

    private PrivacyModels() {
    }

    public record ExportacaoPrivacidade(
            String usuarioId,
            Instant geradaEm,
            List<EspacoExportado> espacos,
            List<DocumentoExportado> documentos,
            List<ConsultaExportada> consultas,
            List<ConversaExportada> conversas,
            List<FeedbackExportado> feedbacks,
            List<EventoAuditoriaExportado> eventosAuditoria) {
    }

    public record EspacoExportado(UUID id, String nome, String papel, Instant membroDesde) {
    }

    public record DocumentoExportado(UUID id, UUID espacoId, String titulo, String nomeArquivo, Instant criadoEm) {
    }

    public record ConsultaExportada(
            UUID id, UUID espacoId, String pergunta, String resposta, boolean recusada, Instant criadaEm) {
    }

    public record ConversaExportada(
            UUID id,
            UUID espacoId,
            String titulo,
            String estado,
            Instant criadaEm,
            Instant atualizadaEm,
            List<MensagemExportada> mensagens) {
    }

    public record MensagemExportada(
            UUID id,
            UUID consultaId,
            int sequencia,
            String papel,
            String conteudo,
            Instant criadaEm) {
    }

    public record FeedbackExportado(UUID consultaId, boolean util, String comentario, Instant criadoEm) {
    }

    public record EventoAuditoriaExportado(
            UUID id,
            UUID espacoId,
            String acao,
            String recurso,
            String recursoId,
            String detalhes,
            String enderecoIp,
            Instant criadoEm) {
    }

    public record ExclusaoPrivacidadeResponse(
            String estado,
            String identificadorPseudonimo,
            int consultasExcluidas,
            int conversasExcluidas,
            int vinculosExcluidos,
            Instant concluidaEm) {
    }
}

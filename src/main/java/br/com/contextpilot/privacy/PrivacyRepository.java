package br.com.contextpilot.privacy;

import java.util.List;
import java.util.UUID;

import br.com.contextpilot.privacy.PrivacyModels.ConsultaExportada;
import br.com.contextpilot.privacy.PrivacyModels.DocumentoExportado;
import br.com.contextpilot.privacy.PrivacyModels.EspacoExportado;
import br.com.contextpilot.privacy.PrivacyModels.EventoAuditoriaExportado;
import br.com.contextpilot.privacy.PrivacyModels.FeedbackExportado;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class PrivacyRepository {

    private final JdbcClient banco;

    PrivacyRepository(JdbcClient banco) {
        this.banco = banco;
    }

    List<EspacoExportado> listarEspacos(String usuarioId) {
        return banco.sql("""
                        SELECT e.id, e.nome, m.papel, m.adicionado_em
                          FROM membros_espaco m JOIN espacos e ON e.id = m.espaco_id
                         WHERE m.usuario_id = :usuarioId ORDER BY e.nome
                        """)
                .param("usuarioId", usuarioId)
                .query((rs, linha) -> new EspacoExportado(
                        rs.getObject("id", UUID.class), rs.getString("nome"), rs.getString("papel"),
                        rs.getTimestamp("adicionado_em").toInstant()))
                .list();
    }

    List<DocumentoExportado> listarDocumentos(String usuarioId) {
        return banco.sql("""
                        SELECT id, espaco_id, titulo, nome_arquivo, criado_em
                          FROM documentos WHERE criado_por = :usuarioId ORDER BY criado_em
                        """)
                .param("usuarioId", usuarioId)
                .query((rs, linha) -> new DocumentoExportado(
                        rs.getObject("id", UUID.class), rs.getObject("espaco_id", UUID.class),
                        rs.getString("titulo"), rs.getString("nome_arquivo"),
                        rs.getTimestamp("criado_em").toInstant()))
                .list();
    }

    List<ConsultaExportada> listarConsultas(String usuarioId) {
        return banco.sql("""
                        SELECT id, espaco_id, pergunta, resposta, recusada, criada_em
                          FROM consultas_rag WHERE usuario_id = :usuarioId ORDER BY criada_em
                        """)
                .param("usuarioId", usuarioId)
                .query((rs, linha) -> new ConsultaExportada(
                        rs.getObject("id", UUID.class), rs.getObject("espaco_id", UUID.class),
                        rs.getString("pergunta"), rs.getString("resposta"), rs.getBoolean("recusada"),
                        rs.getTimestamp("criada_em").toInstant()))
                .list();
    }

    List<FeedbackExportado> listarFeedbacks(String usuarioId) {
        return banco.sql("""
                        SELECT consulta_id, util, comentario, criado_em
                          FROM feedback_resposta WHERE usuario_id = :usuarioId ORDER BY criado_em
                        """)
                .param("usuarioId", usuarioId)
                .query((rs, linha) -> new FeedbackExportado(
                        rs.getObject("consulta_id", UUID.class), rs.getBoolean("util"),
                        rs.getString("comentario"), rs.getTimestamp("criado_em").toInstant()))
                .list();
    }

    List<EventoAuditoriaExportado> listarEventosAuditoria(String usuarioId) {
        return banco.sql("""
                        SELECT id, espaco_id, acao, recurso, recurso_id, detalhes::text,
                               endereco_ip, criado_em
                          FROM eventos_auditoria
                         WHERE usuario_id = :usuarioId
                         ORDER BY criado_em
                        """)
                .param("usuarioId", usuarioId)
                .query((rs, linha) -> new EventoAuditoriaExportado(
                        rs.getObject("id", UUID.class),
                        rs.getObject("espaco_id", UUID.class),
                        rs.getString("acao"),
                        rs.getString("recurso"),
                        rs.getString("recurso_id"),
                        rs.getString("detalhes"),
                        rs.getString("endereco_ip"),
                        rs.getTimestamp("criado_em").toInstant()))
                .list();
    }

    List<String> listarEspacosSobPropriedade(String usuarioId) {
        return banco.sql("""
                        SELECT e.nome
                          FROM membros_espaco m JOIN espacos e ON e.id = m.espaco_id
                         WHERE m.usuario_id = :usuarioId AND m.papel = 'PROPRIETARIO'
                         ORDER BY e.nome
                        """)
                .param("usuarioId", usuarioId)
                .query(String.class)
                .list();
    }

    int excluirConsultas(String usuarioId) {
        return banco.sql("DELETE FROM consultas_rag WHERE usuario_id = :usuarioId")
                .param("usuarioId", usuarioId).update();
    }

    int excluirVinculosEPseudonimizar(String usuarioId, String pseudonimo) {
        banco.sql("DELETE FROM feedback_resposta WHERE usuario_id = :usuarioId")
                .param("usuarioId", usuarioId).update();
        banco.sql("DELETE FROM permissoes_documento WHERE usuario_id = :usuarioId")
                .param("usuarioId", usuarioId).update();
        int vinculos = banco.sql("DELETE FROM membros_espaco WHERE usuario_id = :usuarioId")
                .param("usuarioId", usuarioId).update();
        atualizar("documentos", "criado_por", usuarioId, pseudonimo);
        atualizar("membros_espaco", "adicionado_por", usuarioId, pseudonimo);
        atualizar("permissoes_documento", "concedido_por", usuarioId, pseudonimo);
        atualizar("conjuntos_avaliacao", "criado_por", usuarioId, pseudonimo);
        atualizar("execucoes_avaliacao", "executada_por", usuarioId, pseudonimo);
        banco.sql("""
                        UPDATE eventos_auditoria
                           SET usuario_id = :pseudonimo, detalhes = '{}'::jsonb, endereco_ip = NULL
                         WHERE usuario_id = :usuarioId
                        """)
                .param("usuarioId", usuarioId).param("pseudonimo", pseudonimo).update();
        return vinculos;
    }

    int expurgarConsultasVencidas() {
        return banco.sql("""
                        DELETE FROM consultas_rag c
                         USING espacos e
                         WHERE c.espaco_id = e.id
                           AND c.criada_em < CURRENT_TIMESTAMP - make_interval(days => e.retencao_consultas_dias)
                        """)
                .update();
    }

    private void atualizar(String tabela, String coluna, String usuarioId, String pseudonimo) {
        String sql = "UPDATE %s SET %s = :pseudonimo WHERE %s = :usuarioId".formatted(tabela, coluna, coluna);
        banco.sql(sql).param("usuarioId", usuarioId).param("pseudonimo", pseudonimo).update();
    }
}

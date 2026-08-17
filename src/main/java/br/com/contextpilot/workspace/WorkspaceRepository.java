package br.com.contextpilot.workspace;

import static br.com.contextpilot.workspace.WorkspaceModels.PapelMembro;
import static br.com.contextpilot.shared.domain.SqlTime.instante;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.contextpilot.workspace.WorkspaceModels.EspacoResponse;
import br.com.contextpilot.workspace.WorkspaceModels.MembroResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class WorkspaceRepository {

    private final JdbcClient banco;

    WorkspaceRepository(JdbcClient banco) {
        this.banco = banco;
    }

    void criar(UUID id, String nome, String descricao, String usuarioId, Instant instante) {
        banco.sql("""
                        INSERT INTO espacos (id, nome, descricao, criado_por, criado_em, atualizado_em)
                        VALUES (:id, :nome, :descricao, :usuarioId, :instante, :instante)
                        """)
                .param("id", id)
                .param("nome", nome)
                .param("descricao", descricao)
                .param("usuarioId", usuarioId)
                .param("instante", instante(instante))
                .update();

        salvarMembro(id, usuarioId, PapelMembro.PROPRIETARIO, usuarioId, instante);
    }

    List<EspacoResponse> listarPorUsuario(String usuarioId) {
        return banco.sql("""
                        SELECT e.id, e.nome, e.descricao, m.papel, e.criado_por, e.criado_em
                          FROM espacos e
                          JOIN membros_espaco m ON m.espaco_id = e.id
                         WHERE m.usuario_id = :usuarioId
                         ORDER BY e.nome
                        """)
                .param("usuarioId", usuarioId)
                .query((rs, linha) -> new EspacoResponse(
                        rs.getObject("id", UUID.class),
                        rs.getString("nome"),
                        rs.getString("descricao"),
                        PapelMembro.valueOf(rs.getString("papel")),
                        rs.getString("criado_por"),
                        rs.getTimestamp("criado_em").toInstant()))
                .list();
    }

    Optional<EspacoResponse> buscar(UUID espacoId, String usuarioId) {
        return banco.sql("""
                        SELECT e.id, e.nome, e.descricao, m.papel, e.criado_por, e.criado_em
                          FROM espacos e
                          JOIN membros_espaco m ON m.espaco_id = e.id
                         WHERE e.id = :espacoId AND m.usuario_id = :usuarioId
                        """)
                .param("espacoId", espacoId)
                .param("usuarioId", usuarioId)
                .query((rs, linha) -> new EspacoResponse(
                        rs.getObject("id", UUID.class),
                        rs.getString("nome"),
                        rs.getString("descricao"),
                        PapelMembro.valueOf(rs.getString("papel")),
                        rs.getString("criado_por"),
                        rs.getTimestamp("criado_em").toInstant()))
                .optional();
    }

    Optional<PapelMembro> buscarPapel(UUID espacoId, String usuarioId) {
        return banco.sql("SELECT papel FROM membros_espaco WHERE espaco_id = :espacoId AND usuario_id = :usuarioId")
                .param("espacoId", espacoId)
                .param("usuarioId", usuarioId)
                .query(String.class)
                .optional()
                .map(PapelMembro::valueOf);
    }

    void salvarMembro(UUID espacoId, String usuarioId, PapelMembro papel, String adicionadoPor, Instant instante) {
        banco.sql("""
                        INSERT INTO membros_espaco (espaco_id, usuario_id, papel, adicionado_por, adicionado_em)
                        VALUES (:espacoId, :usuarioId, :papel, :adicionadoPor, :instante)
                        ON CONFLICT (espaco_id, usuario_id)
                        DO UPDATE SET papel = EXCLUDED.papel, adicionado_por = EXCLUDED.adicionado_por,
                                      adicionado_em = EXCLUDED.adicionado_em
                        """)
                .param("espacoId", espacoId)
                .param("usuarioId", usuarioId)
                .param("papel", papel.name())
                .param("adicionadoPor", adicionadoPor)
                .param("instante", instante(instante))
                .update();
    }

    List<MembroResponse> listarMembros(UUID espacoId) {
        return banco.sql("""
                        SELECT usuario_id, papel, adicionado_por, adicionado_em
                          FROM membros_espaco
                         WHERE espaco_id = :espacoId
                         ORDER BY papel, usuario_id
                        """)
                .param("espacoId", espacoId)
                .query((rs, linha) -> new MembroResponse(
                        rs.getString("usuario_id"),
                        PapelMembro.valueOf(rs.getString("papel")),
                        rs.getString("adicionado_por"),
                        rs.getTimestamp("adicionado_em").toInstant()))
                .list();
    }
}

package br.com.contextpilot.audit;

import static br.com.contextpilot.shared.domain.SqlTime.instante;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.UUID;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final JdbcClient banco;
    private final ObjectMapper json;
    private final Clock relogio;

    public AuditService(JdbcClient banco, ObjectMapper json, Clock relogio) {
        this.banco = banco;
        this.json = json;
        this.relogio = relogio;
    }

    public void registrar(
            UUID espacoId,
            String usuarioId,
            String acao,
            String recurso,
            String recursoId,
            Map<String, ?> detalhes) {
        banco.sql("""
                        INSERT INTO eventos_auditoria
                            (id, espaco_id, usuario_id, acao, recurso, recurso_id, detalhes, criado_em)
                        VALUES (:id, :espacoId, :usuarioId, :acao, :recurso, :recursoId, CAST(:detalhes AS jsonb), :criadoEm)
                        """)
                .param("id", UUID.randomUUID())
                .param("espacoId", espacoId)
                .param("usuarioId", usuarioId)
                .param("acao", acao)
                .param("recurso", recurso)
                .param("recursoId", recursoId)
                .param("detalhes", serializar(detalhes))
                .param("criadoEm", instante(Instant.now(relogio)))
                .update();
    }

    public List<EventoAuditoria> listar(UUID espacoId, String usuarioId, int limite) {
        return banco.sql("""
                        SELECT id, usuario_id, acao, recurso, recurso_id, detalhes::text, criado_em
                          FROM eventos_auditoria
                         WHERE espaco_id = :espacoId
                           AND EXISTS (
                               SELECT 1 FROM membros_espaco m
                                WHERE m.espaco_id = :espacoId AND m.usuario_id = :usuarioId
                                  AND m.papel = 'PROPRIETARIO'
                           )
                         ORDER BY criado_em DESC
                         LIMIT :limite
                        """)
                .param("espacoId", espacoId)
                .param("usuarioId", usuarioId)
                .param("limite", Math.max(1, Math.min(limite, 200)))
                .query((rs, linha) -> new EventoAuditoria(
                        rs.getObject("id", UUID.class),
                        rs.getString("usuario_id"),
                        rs.getString("acao"),
                        rs.getString("recurso"),
                        rs.getString("recurso_id"),
                        rs.getString("detalhes"),
                        rs.getTimestamp("criado_em").toInstant()))
                .list();
    }

    public record EventoAuditoria(
            UUID id,
            String usuarioId,
            String acao,
            String recurso,
            String recursoId,
            String detalhes,
            Instant criadoEm) {
    }

    private String serializar(Map<String, ?> detalhes) {
        try {
            return json.writeValueAsString(detalhes);
        } catch (JacksonException excecao) {
            throw new IllegalStateException("Nao foi possivel serializar os detalhes da auditoria.", excecao);
        }
    }
}

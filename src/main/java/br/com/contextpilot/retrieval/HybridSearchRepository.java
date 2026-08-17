package br.com.contextpilot.retrieval;

import java.util.List;
import java.util.UUID;

import br.com.contextpilot.retrieval.RetrievalModels.FonteRecuperada;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class HybridSearchRepository {

    private final JdbcClient banco;

    HybridSearchRepository(JdbcClient banco) {
        this.banco = banco;
    }

    List<FonteRecuperada> buscar(
            UUID espacoId,
            String usuarioId,
            String pergunta,
            String embedding,
            int limite) {
        return banco.sql("""
                        WITH acessiveis AS (
                            SELECT t.id, t.documento_id, d.titulo, t.ordem, t.conteudo,
                                   GREATEST(0.0, 1.0 - (t.embedding <=> CAST(:embedding AS vector))) AS similaridade,
                                   ts_rank_cd(t.termos, websearch_to_tsquery('portuguese', :pergunta), 32) AS relevancia_textual
                              FROM trechos_documento t
                              JOIN documentos d ON d.id = t.documento_id
                             WHERE t.espaco_id = :espacoId
                               AND d.estado = 'PRONTO'
                               AND EXISTS (
                                   SELECT 1 FROM membros_espaco m
                                    WHERE m.espaco_id = d.espaco_id AND m.usuario_id = :usuarioId
                               )
                               AND (
                                   d.visibilidade = 'ESPACO'
                                   OR d.criado_por = :usuarioId
                                   OR EXISTS (
                                       SELECT 1 FROM membros_espaco m
                                        WHERE m.espaco_id = d.espaco_id AND m.usuario_id = :usuarioId
                                          AND m.papel = 'PROPRIETARIO'
                                   )
                                   OR EXISTS (
                                       SELECT 1 FROM permissoes_documento p
                                        WHERE p.documento_id = d.id AND p.usuario_id = :usuarioId
                                   )
                               )
                        )
                        SELECT id, documento_id, titulo, ordem, conteudo,
                               (0.70 * similaridade + 0.30 * LEAST(1.0, relevancia_textual * 4.0)) AS pontuacao
                          FROM acessiveis
                         WHERE similaridade > 0 OR relevancia_textual > 0
                         ORDER BY pontuacao DESC, documento_id, ordem
                         LIMIT :limite
                        """)
                .param("embedding", embedding)
                .param("pergunta", pergunta)
                .param("espacoId", espacoId)
                .param("usuarioId", usuarioId)
                .param("limite", limite)
                .query((rs, linha) -> new FonteRecuperada(
                        rs.getObject("id", UUID.class),
                        rs.getObject("documento_id", UUID.class),
                        rs.getString("titulo"),
                        rs.getInt("ordem"),
                        rs.getString("conteudo"),
                        rs.getDouble("pontuacao")))
                .list();
    }
}

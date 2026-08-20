package br.com.contextpilot.retrieval;

import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import br.com.contextpilot.retrieval.RetrievalModels.EstrategiaBusca;
import br.com.contextpilot.retrieval.RetrievalModels.FiltrosBusca;
import br.com.contextpilot.retrieval.RetrievalModels.FonteRecuperada;
import br.com.contextpilot.shared.domain.SqlTime;
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
            UUID indiceId,
            String usuarioId,
            String pergunta,
            String embedding,
            EstrategiaBusca estrategia,
            FiltrosBusca filtros,
            String metadadosJson,
            String tagsJson,
            int limite) {
        String documentos = filtros.documentos().stream().map(UUID::toString).collect(Collectors.joining(","));
        return banco.sql("""
                        WITH acessiveis AS (
                            SELECT t.id, t.documento_id, d.titulo, t.ordem, t.conteudo,
                                   GREATEST(0.0, 1.0 - (v.embedding <=> CAST(:embedding AS vector))) AS semantica,
                                   LEAST(1.0, ts_rank_cd(t.termos,
                                       websearch_to_tsquery('portuguese', :pergunta), 32) * 4.0) AS textual
                              FROM trechos_documento t
                              JOIN documentos d ON d.id = t.documento_id
                              JOIN vetores_trecho v ON v.trecho_id = t.id AND v.indice_id = :indiceId
                             WHERE t.espaco_id = :espacoId
                               AND d.estado = 'PRONTO'
                               AND t.risco_prompt = FALSE
                               AND (:semDocumentos OR d.id = ANY(CAST(string_to_array(:documentos, ',') AS uuid[])))
                               AND (:semTipoMime OR d.tipo_mime = :tipoMime)
                               AND (:semDataInicial OR d.criado_em >= :criadoDe)
                               AND (:semDataFinal OR d.criado_em <= :criadoAte)
                               AND (:semMetadados OR d.metadados @> CAST(:metadados AS jsonb))
                               AND (:semTags OR COALESCE(d.metadados -> 'tags', '[]'::jsonb) @> CAST(:tags AS jsonb))
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
                        ), pontuados AS (
                            SELECT *, CASE :estrategia
                                WHEN 'SEMANTICA' THEN semantica
                                WHEN 'TEXTUAL' THEN textual
                                ELSE 0.70 * semantica + 0.30 * textual
                            END AS pontuacao
                              FROM acessiveis
                        )
                        SELECT id, documento_id, titulo, ordem, conteudo, semantica, textual, pontuacao
                          FROM pontuados
                         WHERE pontuacao > 0
                         ORDER BY pontuacao DESC, documento_id, ordem
                         LIMIT :limite
                        """)
                .param("embedding", embedding)
                .param("pergunta", pergunta)
                .param("indiceId", indiceId)
                .param("espacoId", espacoId)
                .param("usuarioId", usuarioId)
                .param("estrategia", estrategia.name())
                .param("documentos", documentos)
                .param("semDocumentos", documentos.isBlank())
                .param("tipoMime", filtros.tipoMime(), Types.VARCHAR)
                .param("semTipoMime", filtros.tipoMime() == null)
                .param("criadoDe", timestamp(filtros.criadoDe()), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("semDataInicial", filtros.criadoDe() == null)
                .param("criadoAte", timestamp(filtros.criadoAte()), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("semDataFinal", filtros.criadoAte() == null)
                .param("metadados", metadadosJson)
                .param("semMetadados", filtros.metadados().isEmpty())
                .param("tags", tagsJson)
                .param("semTags", filtros.tags().isEmpty())
                .param("limite", limite)
                .query((rs, linha) -> new FonteRecuperada(
                        rs.getObject("id", UUID.class),
                        rs.getObject("documento_id", UUID.class),
                        rs.getString("titulo"),
                        rs.getInt("ordem"),
                        rs.getString("conteudo"),
                        rs.getDouble("semantica"),
                        rs.getDouble("textual"),
                        rs.getDouble("pontuacao")))
                .list();
    }

    List<FonteRecuperada> buscarVizinhos(
            UUID espacoId,
            UUID indiceId,
            String usuarioId,
            UUID documentoId,
            int ordemInicial,
            int ordemFinal,
            String pergunta,
            String embedding,
            EstrategiaBusca estrategia) {
        return banco.sql("""
                        WITH acessiveis AS (
                            SELECT t.id, t.documento_id, d.titulo, t.ordem, t.conteudo,
                                   GREATEST(0.0, 1.0 - (v.embedding <=> CAST(:embedding AS vector))) AS semantica,
                                   LEAST(1.0, ts_rank_cd(t.termos,
                                       websearch_to_tsquery('portuguese', :pergunta), 32) * 4.0) AS textual
                              FROM trechos_documento t
                              JOIN documentos d ON d.id = t.documento_id
                              JOIN vetores_trecho v ON v.trecho_id = t.id AND v.indice_id = :indiceId
                             WHERE t.espaco_id = :espacoId
                               AND t.documento_id = :documentoId
                               AND t.ordem BETWEEN :ordemInicial AND :ordemFinal
                               AND d.estado = 'PRONTO'
                               AND t.risco_prompt = FALSE
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
                        SELECT id, documento_id, titulo, ordem, conteudo, semantica, textual,
                               CASE :estrategia
                                   WHEN 'SEMANTICA' THEN semantica
                                   WHEN 'TEXTUAL' THEN textual
                                   ELSE 0.70 * semantica + 0.30 * textual
                               END AS pontuacao
                          FROM acessiveis
                         ORDER BY ordem
                        """)
                .param("embedding", embedding)
                .param("pergunta", pergunta)
                .param("indiceId", indiceId)
                .param("espacoId", espacoId)
                .param("documentoId", documentoId)
                .param("ordemInicial", Math.max(0, ordemInicial))
                .param("ordemFinal", Math.max(0, ordemFinal))
                .param("usuarioId", usuarioId)
                .param("estrategia", estrategia.name())
                .query((rs, linha) -> new FonteRecuperada(
                        rs.getObject("id", UUID.class),
                        rs.getObject("documento_id", UUID.class),
                        rs.getString("titulo"),
                        rs.getInt("ordem"),
                        rs.getString("conteudo"),
                        rs.getDouble("semantica"),
                        rs.getDouble("textual"),
                        rs.getDouble("pontuacao")))
                .list();
    }

    private java.time.OffsetDateTime timestamp(Instant valor) {
        return valor == null ? null : SqlTime.instante(valor);
    }
}

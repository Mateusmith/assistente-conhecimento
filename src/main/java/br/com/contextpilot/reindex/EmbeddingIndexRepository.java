package br.com.contextpilot.reindex;

import static br.com.contextpilot.shared.domain.SqlTime.instante;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.contextpilot.reindex.EmbeddingIndexModels.EstadoIndice;
import br.com.contextpilot.reindex.EmbeddingIndexModels.IndiceEmbeddingResponse;
import br.com.contextpilot.reindex.EmbeddingIndexModels.IndiceParaProcessar;
import br.com.contextpilot.reindex.EmbeddingIndexModels.TrechoParaIndexar;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class EmbeddingIndexRepository {

    private static final String SELECAO = """
            SELECT id, espaco_id, provedor, modelo, dimensoes, estado, total_trechos,
                   trechos_processados, tentativas, criado_por, criado_em, iniciado_em,
                   finalizado_em, ativado_em, erro
              FROM indices_embedding
            """;

    private final JdbcClient banco;

    EmbeddingIndexRepository(JdbcClient banco) {
        this.banco = banco;
    }

    void criar(
            UUID id,
            UUID espacoId,
            String provedor,
            String modelo,
            int dimensoes,
            EstadoIndice estado,
            String usuarioId,
            Instant instante) {
        int total = contarTrechos(espacoId);
        banco.sql("""
                        INSERT INTO indices_embedding
                            (id, espaco_id, provedor, modelo, dimensoes, estado, total_trechos,
                             trechos_processados, criado_por, criado_em, finalizado_em, ativado_em)
                        VALUES
                            (:id, :espacoId, :provedor, :modelo, :dimensoes, :estado, :total,
                             0, :usuarioId, :instante,
                             CASE WHEN :ativo THEN :instante ELSE NULL END,
                             CASE WHEN :ativo THEN :instante ELSE NULL END)
                        """)
                .param("id", id)
                .param("espacoId", espacoId)
                .param("provedor", provedor)
                .param("modelo", modelo)
                .param("dimensoes", dimensoes)
                .param("estado", estado.name())
                .param("total", total)
                .param("usuarioId", usuarioId)
                .param("instante", instante(instante))
                .param("ativo", estado == EstadoIndice.ATIVO)
                .update();
    }

    List<IndiceEmbeddingResponse> listar(UUID espacoId) {
        return banco.sql(SELECAO + " WHERE espaco_id = :espacoId ORDER BY criado_em DESC")
                .param("espacoId", espacoId)
                .query(this::mapear)
                .list();
    }

    Optional<IndiceEmbeddingResponse> buscar(UUID indiceId, UUID espacoId) {
        return banco.sql(SELECAO + " WHERE id = :id AND espaco_id = :espacoId")
                .param("id", indiceId)
                .param("espacoId", espacoId)
                .query(this::mapear)
                .optional();
    }

    Optional<IndiceEmbeddingResponse> buscarAtivo(UUID espacoId) {
        return banco.sql(SELECAO + " WHERE espaco_id = :espacoId AND estado = 'ATIVO'")
                .param("espacoId", espacoId)
                .query(this::mapear)
                .optional();
    }

    Optional<IndiceParaProcessar> reivindicar(Instant agora, Instant bloqueadoAte, String trabalhadorId) {
        return banco.sql("""
                        WITH proximo AS (
                            SELECT id
                              FROM indices_embedding
                             WHERE estado = 'CONSTRUINDO'
                               AND (bloqueado_ate IS NULL OR bloqueado_ate <= :agora)
                             ORDER BY criado_em
                             FOR UPDATE SKIP LOCKED
                             LIMIT 1
                        )
                        UPDATE indices_embedding i
                           SET trabalhador_id = :trabalhadorId,
                               bloqueado_ate = :bloqueadoAte,
                               iniciado_em = COALESCE(iniciado_em, :agora),
                               erro = NULL
                          FROM proximo
                         WHERE i.id = proximo.id
                        RETURNING i.id, i.espaco_id, i.modelo
                        """)
                .param("agora", instante(agora))
                .param("bloqueadoAte", instante(bloqueadoAte))
                .param("trabalhadorId", trabalhadorId)
                .query((rs, linha) -> new IndiceParaProcessar(
                        rs.getObject("id", UUID.class),
                        rs.getObject("espaco_id", UUID.class),
                        rs.getString("modelo")))
                .optional();
    }

    List<TrechoParaIndexar> listarPendentes(UUID indiceId, UUID espacoId, int limite) {
        return banco.sql("""
                        SELECT t.id, t.conteudo
                          FROM trechos_documento t
                         WHERE t.espaco_id = :espacoId
                           AND NOT EXISTS (
                               SELECT 1 FROM vetores_trecho v
                                WHERE v.indice_id = :indiceId AND v.trecho_id = t.id
                           )
                         ORDER BY t.documento_id, t.ordem
                         LIMIT :limite
                        """)
                .param("espacoId", espacoId)
                .param("indiceId", indiceId)
                .param("limite", limite)
                .query((rs, linha) -> new TrechoParaIndexar(
                        rs.getObject("id", UUID.class), rs.getString("conteudo")))
                .list();
    }

    void salvarVetor(UUID indiceId, UUID trechoId, String vetor, Instant instante) {
        banco.sql("""
                        INSERT INTO vetores_trecho (indice_id, trecho_id, embedding, criado_em)
                        VALUES (:indiceId, :trechoId, CAST(:embedding AS vector), :instante)
                        ON CONFLICT (indice_id, trecho_id) DO NOTHING
                        """)
                .param("indiceId", indiceId)
                .param("trechoId", trechoId)
                .param("embedding", vetor)
                .param("instante", instante(instante))
                .update();
    }

    void atualizarProgresso(UUID indiceId, UUID espacoId) {
        banco.sql("""
                        UPDATE indices_embedding
                           SET total_trechos = (SELECT COUNT(*) FROM trechos_documento WHERE espaco_id = :espacoId),
                               trechos_processados = (SELECT COUNT(*) FROM vetores_trecho WHERE indice_id = :indiceId),
                               trabalhador_id = NULL,
                               bloqueado_ate = NULL
                         WHERE id = :indiceId AND estado = 'CONSTRUINDO'
                        """)
                .param("indiceId", indiceId)
                .param("espacoId", espacoId)
                .update();
    }

    int contarTrechos(UUID espacoId) {
        return banco.sql("SELECT COUNT(*) FROM trechos_documento WHERE espaco_id = :espacoId")
                .param("espacoId", espacoId)
                .query(Integer.class)
                .single();
    }

    int contarVetores(UUID indiceId) {
        return banco.sql("SELECT COUNT(*) FROM vetores_trecho WHERE indice_id = :indiceId")
                .param("indiceId", indiceId)
                .query(Integer.class)
                .single();
    }

    void bloquearEspaco(UUID espacoId) {
        banco.sql("SELECT id FROM espacos WHERE id = :espacoId FOR UPDATE")
                .param("espacoId", espacoId)
                .query(UUID.class)
                .single();
    }

    void ativar(UUID indiceId, UUID espacoId, Instant instante) {
        banco.sql("""
                        UPDATE indices_embedding
                           SET estado = 'ARQUIVADO', trabalhador_id = NULL, bloqueado_ate = NULL
                         WHERE espaco_id = :espacoId AND estado = 'ATIVO' AND id <> :indiceId
                        """)
                .param("espacoId", espacoId)
                .param("indiceId", indiceId)
                .update();
        banco.sql("""
                        UPDATE indices_embedding
                           SET estado = 'ATIVO', total_trechos = :total, trechos_processados = :total,
                               finalizado_em = COALESCE(finalizado_em, :instante), ativado_em = :instante,
                               erro = NULL, trabalhador_id = NULL, bloqueado_ate = NULL
                         WHERE id = :indiceId AND espaco_id = :espacoId
                        """)
                .param("indiceId", indiceId)
                .param("espacoId", espacoId)
                .param("total", contarTrechos(espacoId))
                .param("instante", instante(instante))
                .update();
    }

    void registrarFalha(UUID indiceId, String erro, boolean definitiva) {
        banco.sql("""
                        UPDATE indices_embedding
                           SET tentativas = tentativas + 1,
                               estado = CASE WHEN :definitiva THEN 'FALHOU' ELSE estado END,
                               erro = :erro,
                               finalizado_em = CASE WHEN :definitiva THEN CURRENT_TIMESTAMP ELSE finalizado_em END,
                               trabalhador_id = NULL,
                               bloqueado_ate = NULL
                         WHERE id = :indiceId
                        """)
                .param("indiceId", indiceId)
                .param("erro", erro)
                .param("definitiva", definitiva)
                .update();
    }

    int obterTentativas(UUID indiceId) {
        return banco.sql("SELECT tentativas FROM indices_embedding WHERE id = :indiceId")
                .param("indiceId", indiceId)
                .query(Integer.class)
                .single();
    }

    private IndiceEmbeddingResponse mapear(java.sql.ResultSet rs, int linha) throws java.sql.SQLException {
        int total = rs.getInt("total_trechos");
        int processados = rs.getInt("trechos_processados");
        return new IndiceEmbeddingResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("espaco_id", UUID.class),
                rs.getString("provedor"),
                rs.getString("modelo"),
                rs.getInt("dimensoes"),
                EstadoIndice.valueOf(rs.getString("estado")),
                total,
                processados,
                total == 0 ? 100 : Math.min(100, (int) Math.round(processados * 100.0 / total)),
                rs.getInt("tentativas"),
                rs.getString("criado_por"),
                rs.getTimestamp("criado_em").toInstant(),
                converter(rs.getTimestamp("iniciado_em")),
                converter(rs.getTimestamp("finalizado_em")),
                converter(rs.getTimestamp("ativado_em")),
                rs.getString("erro"));
    }

    private Instant converter(java.sql.Timestamp valor) {
        return valor == null ? null : valor.toInstant();
    }
}

package br.com.contextpilot.answer;

import static br.com.contextpilot.shared.domain.SqlTime.instante;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.contextpilot.answer.AnswerModels.FonteContexto;
import br.com.contextpilot.answer.AnswerModels.FonteResposta;
import br.com.contextpilot.answer.AnswerModels.RespostaRag;
import br.com.contextpilot.retrieval.RetrievalModels.EstrategiaBusca;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class AnswerRepository {

    private final JdbcClient banco;

    AnswerRepository(JdbcClient banco) {
        this.banco = banco;
    }

    void salvar(
            UUID consultaId,
            UUID espacoId,
            String usuarioId,
            String pergunta,
            String resposta,
            boolean recusada,
            String provedor,
            UUID indiceEmbeddingId,
            String modeloEmbedding,
            EstrategiaBusca estrategia,
            int tokensEntrada,
            int tokensSaida,
            BigDecimal custoEstimadoUsd,
            long latenciaMs,
            Instant criadaEm,
            List<FonteContexto> fontes) {
        banco.sql("""
                        INSERT INTO consultas_rag
                            (id, espaco_id, usuario_id, pergunta, resposta, recusada, provedor_ia,
                             indice_embedding_id, modelo_embedding, estrategia_busca, tokens_entrada,
                             tokens_saida, custo_estimado_usd, latencia_ms, criada_em)
                        VALUES
                            (:id, :espacoId, :usuarioId, :pergunta, :resposta, :recusada, :provedor,
                             :indiceId, :modeloEmbedding, :estrategia, :tokensEntrada,
                             :tokensSaida, :custo, :latencia, :criadaEm)
                        """)
                .param("id", consultaId)
                .param("espacoId", espacoId)
                .param("usuarioId", usuarioId)
                .param("pergunta", pergunta)
                .param("resposta", resposta)
                .param("recusada", recusada)
                .param("provedor", provedor)
                .param("indiceId", indiceEmbeddingId)
                .param("modeloEmbedding", modeloEmbedding)
                .param("estrategia", estrategia.name())
                .param("tokensEntrada", tokensEntrada)
                .param("tokensSaida", tokensSaida)
                .param("custo", custoEstimadoUsd)
                .param("latencia", latenciaMs)
                .param("criadaEm", instante(criadaEm))
                .update();

        for (int ordem = 0; ordem < fontes.size(); ordem++) {
            FonteContexto fonte = fontes.get(ordem);
            banco.sql("""
                            INSERT INTO citacoes_resposta
                                (consulta_id, trecho_id, documento_id, marcador, excerto, pontuacao, ordem)
                            VALUES
                                (:consultaId, :trechoId, :documentoId, :marcador, :excerto, :pontuacao, :ordem)
                            """)
                    .param("consultaId", consultaId)
                    .param("trechoId", fonte.trechoId())
                    .param("documentoId", fonte.documentoId())
                    .param("marcador", fonte.marcador())
                    .param("excerto", excerto(fonte.conteudo()))
                    .param("pontuacao", fonte.pontuacao())
                    .param("ordem", ordem)
                    .update();
        }
    }

    Optional<RespostaRag> buscar(UUID consultaId, UUID espacoId, String usuarioId) {
        Optional<CabecalhoResposta> cabecalho = banco.sql("""
                        SELECT id, pergunta, resposta, recusada, provedor_ia, modelo_embedding,
                               estrategia_busca, tokens_entrada, tokens_saida, custo_estimado_usd,
                               latencia_ms, criada_em
                          FROM consultas_rag
                         WHERE id = :consultaId AND espaco_id = :espacoId AND usuario_id = :usuarioId
                        """)
                .param("consultaId", consultaId)
                .param("espacoId", espacoId)
                .param("usuarioId", usuarioId)
                .query((rs, linha) -> new CabecalhoResposta(
                        rs.getObject("id", UUID.class),
                        rs.getString("pergunta"),
                        rs.getString("resposta"),
                        rs.getBoolean("recusada"),
                        rs.getString("provedor_ia"),
                        rs.getString("modelo_embedding"),
                        EstrategiaBusca.valueOf(rs.getString("estrategia_busca")),
                        rs.getInt("tokens_entrada"),
                        rs.getInt("tokens_saida"),
                        rs.getBigDecimal("custo_estimado_usd"),
                        rs.getLong("latencia_ms"),
                        rs.getTimestamp("criada_em").toInstant()))
                .optional();
        return cabecalho.map(this::montar);
    }

    List<RespostaRag> listar(UUID espacoId, String usuarioId, int limite) {
        List<CabecalhoResposta> cabecalhos = banco.sql("""
                        SELECT id, pergunta, resposta, recusada, provedor_ia, modelo_embedding,
                               estrategia_busca, tokens_entrada, tokens_saida, custo_estimado_usd,
                               latencia_ms, criada_em
                          FROM consultas_rag
                         WHERE espaco_id = :espacoId AND usuario_id = :usuarioId
                         ORDER BY criada_em DESC
                         LIMIT :limite
                        """)
                .param("espacoId", espacoId)
                .param("usuarioId", usuarioId)
                .param("limite", limite)
                .query((rs, linha) -> new CabecalhoResposta(
                        rs.getObject("id", UUID.class),
                        rs.getString("pergunta"),
                        rs.getString("resposta"),
                        rs.getBoolean("recusada"),
                        rs.getString("provedor_ia"),
                        rs.getString("modelo_embedding"),
                        EstrategiaBusca.valueOf(rs.getString("estrategia_busca")),
                        rs.getInt("tokens_entrada"),
                        rs.getInt("tokens_saida"),
                        rs.getBigDecimal("custo_estimado_usd"),
                        rs.getLong("latencia_ms"),
                        rs.getTimestamp("criada_em").toInstant()))
                .list();
        return cabecalhos.stream().map(this::montar).toList();
    }

    void salvarFeedback(UUID consultaId, String usuarioId, boolean util, String comentario, Instant instante) {
        banco.sql("""
                        INSERT INTO feedback_resposta (id, consulta_id, usuario_id, util, comentario, criado_em)
                        VALUES (:id, :consultaId, :usuarioId, :util, :comentario, :instante)
                        ON CONFLICT (consulta_id, usuario_id)
                        DO UPDATE SET util = EXCLUDED.util, comentario = EXCLUDED.comentario, criado_em = EXCLUDED.criado_em
                        """)
                .param("id", UUID.randomUUID())
                .param("consultaId", consultaId)
                .param("usuarioId", usuarioId)
                .param("util", util)
                .param("comentario", comentario, java.sql.Types.VARCHAR)
                .param("instante", instante(instante))
                .update();
    }

    private List<FonteResposta> listarFontes(UUID consultaId) {
        return banco.sql("""
                        SELECT c.marcador, c.documento_id, d.titulo, t.ordem, c.excerto, c.pontuacao
                          FROM citacoes_resposta c
                          JOIN documentos d ON d.id = c.documento_id
                          JOIN trechos_documento t ON t.id = c.trecho_id
                         WHERE c.consulta_id = :consultaId
                         ORDER BY c.ordem
                        """)
                .param("consultaId", consultaId)
                .query((rs, linha) -> new FonteResposta(
                        rs.getString("marcador"),
                        rs.getObject("documento_id", UUID.class),
                        rs.getString("titulo"),
                        rs.getInt("ordem"),
                        rs.getString("excerto"),
                        rs.getDouble("pontuacao")))
                .list();
    }

    private RespostaRag montar(CabecalhoResposta cabecalho) {
        return new RespostaRag(
                cabecalho.id(), cabecalho.pergunta(), cabecalho.resposta(), cabecalho.recusada(),
                cabecalho.provedor(), cabecalho.modeloEmbedding(), cabecalho.estrategia(),
                cabecalho.tokensEntrada(), cabecalho.tokensSaida(), cabecalho.custoEstimadoUsd(),
                cabecalho.latenciaMs(), cabecalho.criadaEm(), listarFontes(cabecalho.id()));
    }

    private String excerto(String conteudo) {
        String limpo = conteudo.replaceAll("\\s+", " ").trim();
        return limpo.substring(0, Math.min(700, limpo.length()));
    }

    private record CabecalhoResposta(
            UUID id,
            String pergunta,
            String resposta,
            boolean recusada,
            String provedor,
            String modeloEmbedding,
            EstrategiaBusca estrategia,
            int tokensEntrada,
            int tokensSaida,
            BigDecimal custoEstimadoUsd,
            long latenciaMs,
            Instant criadaEm) {
    }
}

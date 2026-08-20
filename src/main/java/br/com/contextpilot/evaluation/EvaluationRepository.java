package br.com.contextpilot.evaluation;

import static br.com.contextpilot.shared.domain.SqlTime.instante;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.contextpilot.evaluation.EvaluationModels.CasoAvaliacao;
import br.com.contextpilot.evaluation.EvaluationModels.ConjuntoAvaliacao;
import br.com.contextpilot.evaluation.EvaluationModels.ExecucaoAvaliacao;
import br.com.contextpilot.evaluation.EvaluationModels.ResultadoCaso;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class EvaluationRepository {

    private final JdbcClient banco;
    private final ObjectMapper json;

    EvaluationRepository(JdbcClient banco, ObjectMapper json) {
        this.banco = banco;
        this.json = json;
    }

    void criarConjunto(UUID id, UUID espacoId, String nome, String descricao, String usuarioId, Instant instante) {
        banco.sql("""
                        INSERT INTO conjuntos_avaliacao (id, espaco_id, nome, descricao, criado_por, criado_em)
                        VALUES (:id, :espacoId, :nome, :descricao, :usuarioId, :instante)
                        """)
                .param("id", id)
                .param("espacoId", espacoId)
                .param("nome", nome)
                .param("descricao", descricao, java.sql.Types.VARCHAR)
                .param("usuarioId", usuarioId)
                .param("instante", instante(instante))
                .update();
    }

    List<ConjuntoAvaliacao> listarConjuntos(UUID espacoId) {
        return banco.sql("""
                        SELECT c.id, c.espaco_id, c.nome, c.descricao, c.criado_por, c.criado_em,
                               COUNT(ca.id) AS quantidade_casos
                          FROM conjuntos_avaliacao c
                          LEFT JOIN casos_avaliacao ca ON ca.conjunto_id = c.id
                         WHERE c.espaco_id = :espacoId
                         GROUP BY c.id
                         ORDER BY c.criado_em DESC
                        """)
                .param("espacoId", espacoId)
                .query((rs, linha) -> new ConjuntoAvaliacao(
                        rs.getObject("id", UUID.class),
                        rs.getObject("espaco_id", UUID.class),
                        rs.getString("nome"),
                        rs.getString("descricao"),
                        rs.getString("criado_por"),
                        rs.getTimestamp("criado_em").toInstant(),
                        rs.getInt("quantidade_casos")))
                .list();
    }

    boolean conjuntoPertenceAoEspaco(UUID conjuntoId, UUID espacoId) {
        return banco.sql("SELECT COUNT(*) FROM conjuntos_avaliacao WHERE id = :id AND espaco_id = :espacoId")
                .param("id", conjuntoId)
                .param("espacoId", espacoId)
                .query(Integer.class)
                .single() > 0;
    }

    void criarCaso(
            UUID id,
            UUID conjuntoId,
            String pergunta,
            List<String> termos,
            List<UUID> documentos,
            boolean deveRecusar,
            Long latenciaMaximaMs,
            BigDecimal custoMaximoUsd,
            Instant instante) {
        banco.sql("""
                        INSERT INTO casos_avaliacao
                            (id, conjunto_id, pergunta, termos_esperados, documentos_esperados, deve_recusar,
                             latencia_maxima_ms, custo_maximo_usd, criado_em)
                        VALUES
                            (:id, :conjuntoId, :pergunta, CAST(:termos AS jsonb), CAST(:documentos AS jsonb),
                             :deveRecusar, :latenciaMaximaMs, :custoMaximoUsd, :instante)
                        """)
                .param("id", id)
                .param("conjuntoId", conjuntoId)
                .param("pergunta", pergunta)
                .param("termos", serializar(termos))
                .param("documentos", serializar(documentos))
                .param("deveRecusar", deveRecusar)
                .param("latenciaMaximaMs", latenciaMaximaMs, java.sql.Types.BIGINT)
                .param("custoMaximoUsd", custoMaximoUsd, java.sql.Types.NUMERIC)
                .param("instante", instante(instante))
                .update();
    }

    List<CasoAvaliacao> listarCasos(UUID conjuntoId) {
        return banco.sql("""
                        SELECT id, conjunto_id, pergunta, termos_esperados::text, documentos_esperados::text,
                               deve_recusar, latencia_maxima_ms, custo_maximo_usd
                          FROM casos_avaliacao
                         WHERE conjunto_id = :conjuntoId
                         ORDER BY criado_em, id
                        """)
                .param("conjuntoId", conjuntoId)
                .query((rs, linha) -> new CasoAvaliacao(
                        rs.getObject("id", UUID.class),
                        rs.getObject("conjunto_id", UUID.class),
                        rs.getString("pergunta"),
                        lerStrings(rs.getString("termos_esperados")),
                        lerUuids(rs.getString("documentos_esperados")),
                        rs.getBoolean("deve_recusar"),
                        rs.getObject("latencia_maxima_ms", Long.class),
                        rs.getBigDecimal("custo_maximo_usd")))
                .list();
    }

    void iniciarExecucao(UUID id, UUID conjuntoId, String usuarioId, int total, Instant instante) {
        banco.sql("""
                        INSERT INTO execucoes_avaliacao
                            (id, conjunto_id, executada_por, estado, total_casos, iniciada_em)
                        VALUES (:id, :conjuntoId, :usuarioId, 'EXECUTANDO', :total, :instante)
                        """)
                .param("id", id)
                .param("conjuntoId", conjuntoId)
                .param("usuarioId", usuarioId)
                .param("total", total)
                .param("instante", instante(instante))
                .update();
    }

    void salvarResultado(UUID execucaoId, ResultadoCaso resultado) {
        banco.sql("""
                        INSERT INTO resultados_avaliacao
                            (id, execucao_id, caso_id, consulta_id, aprovado, pontuacao_termos,
                             pontuacao_fontes, precisao_fontes, mrr, recusa_correta,
                             latencia_ms, custo_usd, orcamento_respeitado, detalhes)
                        VALUES
                            (:id, :execucaoId, :casoId, :consultaId, :aprovado, :termos,
                             :fontes, :precisao, :mrr, :recusa, :latencia, :custo,
                             :orcamento, :detalhes)
                        """)
                .param("id", UUID.randomUUID())
                .param("execucaoId", execucaoId)
                .param("casoId", resultado.casoId())
                .param("consultaId", resultado.consultaId())
                .param("aprovado", resultado.aprovado())
                .param("termos", resultado.pontuacaoTermos())
                .param("fontes", resultado.pontuacaoFontes())
                .param("precisao", resultado.precisaoFontes())
                .param("mrr", resultado.mrr())
                .param("recusa", resultado.recusaCorreta())
                .param("latencia", resultado.latenciaMs())
                .param("custo", resultado.custoUsd())
                .param("orcamento", resultado.orcamentoRespeitado())
                .param("detalhes", resultado.detalhes())
                .update();
    }

    void concluirExecucao(
            UUID id,
            int aprovados,
            double taxa,
            double recallMedio,
            double precisaoMedia,
            double mrrMedio,
            long latenciaP95Ms,
            BigDecimal custoTotalUsd,
            String modeloEmbedding,
            String provedorIa,
            Instant instante) {
        banco.sql("""
                        UPDATE execucoes_avaliacao
                           SET estado = 'CONCLUIDA', casos_aprovados = :aprovados,
                               taxa_acerto = :taxa, recall_medio = :recallMedio,
                               precisao_media = :precisaoMedia, mrr_medio = :mrrMedio,
                               latencia_p95_ms = :latenciaP95Ms, custo_total_usd = :custoTotalUsd,
                               modelo_embedding = :modeloEmbedding, provedor_ia = :provedorIa,
                               finalizada_em = :instante
                         WHERE id = :id
                        """)
                .param("id", id)
                .param("aprovados", aprovados)
                .param("taxa", taxa)
                .param("recallMedio", recallMedio)
                .param("precisaoMedia", precisaoMedia)
                .param("mrrMedio", mrrMedio)
                .param("latenciaP95Ms", latenciaP95Ms)
                .param("custoTotalUsd", custoTotalUsd)
                .param("modeloEmbedding", modeloEmbedding, java.sql.Types.VARCHAR)
                .param("provedorIa", provedorIa, java.sql.Types.VARCHAR)
                .param("instante", instante(instante))
                .update();
    }

    void falharExecucao(UUID id, String erro, Instant instante) {
        banco.sql("""
                        UPDATE execucoes_avaliacao
                           SET estado = 'FALHOU', erro = :erro, finalizada_em = :instante
                         WHERE id = :id
                        """)
                .param("id", id)
                .param("erro", erro)
                .param("instante", instante(instante))
                .update();
    }

    Optional<ExecucaoAvaliacao> buscarExecucao(UUID id, UUID conjuntoId) {
        Optional<CabecalhoExecucao> cabecalho = banco.sql("""
                        SELECT id, conjunto_id, estado, erro, total_casos, casos_aprovados,
                               COALESCE(taxa_acerto, 0) AS taxa_acerto,
                               recall_medio, precisao_media, mrr_medio, latencia_p95_ms,
                               custo_total_usd, modelo_embedding, provedor_ia,
                               iniciada_em, finalizada_em
                          FROM execucoes_avaliacao
                         WHERE id = :id AND conjunto_id = :conjuntoId
                        """)
                .param("id", id)
                .param("conjuntoId", conjuntoId)
                .query((rs, linha) -> {
                    var finalizada = rs.getTimestamp("finalizada_em");
                    return new CabecalhoExecucao(
                            rs.getObject("id", UUID.class),
                            rs.getObject("conjunto_id", UUID.class),
                            rs.getString("estado"),
                            rs.getString("erro"),
                            rs.getInt("total_casos"),
                            rs.getInt("casos_aprovados"),
                            rs.getDouble("taxa_acerto"),
                            rs.getDouble("recall_medio"),
                            rs.getDouble("precisao_media"),
                            rs.getDouble("mrr_medio"),
                            rs.getLong("latencia_p95_ms"),
                            rs.getBigDecimal("custo_total_usd"),
                            rs.getString("modelo_embedding"),
                            rs.getString("provedor_ia"),
                            rs.getTimestamp("iniciada_em").toInstant(),
                            finalizada == null ? null : finalizada.toInstant());
                })
                .optional();
        return cabecalho.map(valor -> new ExecucaoAvaliacao(
                valor.id(), valor.conjuntoId(), valor.estado(), valor.erro(),
                valor.totalCasos(), valor.casosAprovados(),
                valor.taxaAcerto(), valor.recallMedio(), valor.precisaoMedia(), valor.mrrMedio(),
                valor.latenciaP95Ms(), valor.custoTotalUsd(), valor.modeloEmbedding(), valor.provedorIa(),
                valor.iniciadaEm(), valor.finalizadaEm(), listarResultados(valor.id())));
    }

    private List<ResultadoCaso> listarResultados(UUID execucaoId) {
        return banco.sql("""
                        SELECT caso_id, consulta_id, aprovado, pontuacao_termos,
                               pontuacao_fontes, precisao_fontes, mrr, recusa_correta,
                               latencia_ms, custo_usd, orcamento_respeitado, detalhes
                          FROM resultados_avaliacao
                         WHERE execucao_id = :execucaoId
                         ORDER BY caso_id
                        """)
                .param("execucaoId", execucaoId)
                .query((rs, linha) -> new ResultadoCaso(
                        rs.getObject("caso_id", UUID.class),
                        rs.getObject("consulta_id", UUID.class),
                        rs.getBoolean("aprovado"),
                        rs.getDouble("pontuacao_termos"),
                        rs.getDouble("pontuacao_fontes"),
                        rs.getDouble("precisao_fontes"),
                        rs.getDouble("mrr"),
                        rs.getBoolean("recusa_correta"),
                        rs.getLong("latencia_ms"),
                        rs.getBigDecimal("custo_usd"),
                        rs.getBoolean("orcamento_respeitado"),
                        rs.getString("detalhes")))
                .list();
    }

    private String serializar(Object valor) {
        try {
            return json.writeValueAsString(valor);
        } catch (JacksonException excecao) {
            throw new IllegalStateException("Nao foi possivel serializar o caso de avaliacao.", excecao);
        }
    }

    private List<String> lerStrings(String valor) {
        try {
            return Arrays.asList(json.readValue(valor, String[].class));
        } catch (JacksonException excecao) {
            throw new IllegalStateException("Caso de avaliacao corrompido.", excecao);
        }
    }

    private List<UUID> lerUuids(String valor) {
        return lerStrings(valor).stream().map(UUID::fromString).toList();
    }

    private record CabecalhoExecucao(
            UUID id,
            UUID conjuntoId,
            String estado,
            String erro,
            int totalCasos,
            int casosAprovados,
            double taxaAcerto,
            double recallMedio,
            double precisaoMedia,
            double mrrMedio,
            long latenciaP95Ms,
            BigDecimal custoTotalUsd,
            String modeloEmbedding,
            String provedorIa,
            Instant iniciadaEm,
            Instant finalizadaEm) {
    }
}

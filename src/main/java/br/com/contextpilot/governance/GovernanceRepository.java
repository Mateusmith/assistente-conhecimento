package br.com.contextpilot.governance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.contextpilot.governance.GovernanceModels.ConsumoIaResponse;
import br.com.contextpilot.governance.GovernanceModels.GovernancaResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class GovernanceRepository {

    private final JdbcClient banco;

    GovernanceRepository(JdbcClient banco) {
        this.banco = banco;
    }

    Optional<GovernancaResponse> buscar(UUID espacoId) {
        return banco.sql("""
                        SELECT id, limite_armazenamento_bytes, limite_consultas_dia,
                               limite_uploads_dia, retencao_consultas_dias
                          FROM espacos
                         WHERE id = :espacoId
                        """)
                .param("espacoId", espacoId)
                .query((rs, linha) -> new GovernancaResponse(
                        rs.getObject("id", UUID.class),
                        rs.getLong("limite_armazenamento_bytes"),
                        rs.getInt("limite_consultas_dia"),
                        rs.getInt("limite_uploads_dia"),
                        rs.getInt("retencao_consultas_dias")))
                .optional();
    }

    void atualizar(GovernancaResponse governanca) {
        banco.sql("""
                        UPDATE espacos
                           SET limite_armazenamento_bytes = :armazenamento,
                               limite_consultas_dia = :consultas,
                               limite_uploads_dia = :uploads,
                               retencao_consultas_dias = :retencao,
                               atualizado_em = CURRENT_TIMESTAMP
                         WHERE id = :espacoId
                        """)
                .param("espacoId", governanca.espacoId())
                .param("armazenamento", governanca.limiteArmazenamentoBytes())
                .param("consultas", governanca.limiteConsultasDia())
                .param("uploads", governanca.limiteUploadsDia())
                .param("retencao", governanca.retencaoConsultasDias())
                .update();
    }

    long armazenamentoUsado(UUID espacoId) {
        return banco.sql("SELECT COALESCE(SUM(tamanho_bytes), 0) FROM documentos WHERE espaco_id = :espacoId")
                .param("espacoId", espacoId).query(Long.class).single();
    }

    void bloquearEspaco(UUID espacoId) {
        banco.sql("SELECT id FROM espacos WHERE id = :espacoId FOR UPDATE")
                .param("espacoId", espacoId)
                .query(UUID.class)
                .single();
    }

    int consultasHoje(UUID espacoId) {
        return banco.sql("""
                        SELECT COUNT(*) FROM consultas_rag
                         WHERE espaco_id = :espacoId AND criada_em >= CURRENT_DATE
                        """)
                .param("espacoId", espacoId).query(Integer.class).single();
    }

    int uploadsHoje(UUID espacoId) {
        return banco.sql("""
                        SELECT COUNT(*) FROM documentos
                         WHERE espaco_id = :espacoId AND criado_em >= CURRENT_DATE
                        """)
                .param("espacoId", espacoId).query(Integer.class).single();
    }

    void registrarConsumo(
            UUID espacoId,
            LocalDate data,
            String provedor,
            String modelo,
            String operacao,
            long chamadas,
            int tokensEntrada,
            int tokensSaida,
            BigDecimal custo) {
        banco.sql("""
                        INSERT INTO consumo_ia_diario
                            (espaco_id, data, provedor, modelo, operacao, chamadas,
                             tokens_entrada, tokens_saida, custo_estimado_usd, atualizado_em)
                        VALUES
                            (:espacoId, :data, :provedor, :modelo, :operacao, :chamadas,
                             :entrada, :saida, :custo, CURRENT_TIMESTAMP)
                        ON CONFLICT (espaco_id, data, provedor, modelo, operacao)
                        DO UPDATE SET chamadas = consumo_ia_diario.chamadas + EXCLUDED.chamadas,
                                      tokens_entrada = consumo_ia_diario.tokens_entrada + EXCLUDED.tokens_entrada,
                                      tokens_saida = consumo_ia_diario.tokens_saida + EXCLUDED.tokens_saida,
                                      custo_estimado_usd = consumo_ia_diario.custo_estimado_usd + EXCLUDED.custo_estimado_usd,
                                      atualizado_em = CURRENT_TIMESTAMP
                        """)
                .param("espacoId", espacoId)
                .param("data", data)
                .param("provedor", provedor)
                .param("modelo", modelo)
                .param("operacao", operacao)
                .param("chamadas", chamadas)
                .param("entrada", tokensEntrada)
                .param("saida", tokensSaida)
                .param("custo", custo)
                .update();
    }

    List<ConsumoIaResponse> listarConsumo(UUID espacoId, LocalDate desde) {
        return banco.sql("""
                        SELECT data, provedor, modelo, operacao, chamadas, tokens_entrada,
                               tokens_saida, custo_estimado_usd
                          FROM consumo_ia_diario
                         WHERE espaco_id = :espacoId AND data >= :desde
                         ORDER BY data DESC, operacao, modelo
                        """)
                .param("espacoId", espacoId)
                .param("desde", desde)
                .query((rs, linha) -> new ConsumoIaResponse(
                        rs.getObject("data", LocalDate.class),
                        rs.getString("provedor"),
                        rs.getString("modelo"),
                        rs.getString("operacao"),
                        rs.getLong("chamadas"),
                        rs.getLong("tokens_entrada"),
                        rs.getLong("tokens_saida"),
                        rs.getBigDecimal("custo_estimado_usd")))
                .list();
    }
}

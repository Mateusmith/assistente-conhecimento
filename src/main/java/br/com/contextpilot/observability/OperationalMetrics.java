package br.com.contextpilot.observability;


import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class OperationalMetrics {

    private final JdbcClient banco;
    private final MeterRegistry metricas;

    OperationalMetrics(JdbcClient banco, MeterRegistry metricas) {
        this.banco = banco;
        this.metricas = metricas;
    }

    @PostConstruct
    void registrar() {
        Gauge.builder("contextpilot.ingestao.fila.pendentes", this, valor -> valor.contarTarefasPendentes())
                .description("Quantidade de tarefas de ingestao aguardando processamento")
                .register(metricas);
        Gauge.builder("contextpilot.ingestao.fila.mais_antiga.segundos", this, valor -> valor.idadeFila())
                .description("Idade em segundos da tarefa de ingestao mais antiga")
                .register(metricas);
        Gauge.builder("contextpilot.ingestao.leases.expirados", this, valor -> valor.contarLeasesExpirados())
                .description("Quantidade de tarefas PROCESSANDO com lease expirado")
                .register(metricas);
        Gauge.builder("contextpilot.reindexacao.em_andamento", this, valor -> valor.contarReindexacoes())
                .description("Quantidade de reindexacoes blue-green em andamento")
                .register(metricas);
        Gauge.builder("contextpilot.reindexacao.progresso.medio", this, valor -> valor.progressoReindexacao())
                .description("Progresso medio das reindexacoes em andamento, de zero a um")
                .register(metricas);
    }

    private double contarTarefasPendentes() {
        return consultarNumero("SELECT COUNT(*) FROM tarefas_ingestao WHERE estado = 'PENDENTE'");
    }

    private double idadeFila() {
        return consultarNumero("""
                SELECT COALESCE(EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - MIN(criada_em))), 0)
                  FROM tarefas_ingestao WHERE estado = 'PENDENTE'
                """);
    }

    private double contarLeasesExpirados() {
        return consultarNumero("""
                SELECT COUNT(*) FROM tarefas_ingestao
                 WHERE estado = 'PROCESSANDO' AND bloqueado_ate < CURRENT_TIMESTAMP
                """);
    }

    private double contarReindexacoes() {
        return consultarNumero("SELECT COUNT(*) FROM indices_embedding WHERE estado = 'CONSTRUINDO'");
    }

    private double progressoReindexacao() {
        return consultarNumero("""
                SELECT COALESCE(AVG(CASE WHEN total_trechos = 0 THEN 1.0
                                         ELSE trechos_processados::numeric / total_trechos END), 1.0)
                  FROM indices_embedding WHERE estado = 'CONSTRUINDO'
                """);
    }

    private double consultarNumero(String sql) {
        try {
            Number valor = banco.sql(sql).query(Number.class).single();
            return valor.doubleValue();
        } catch (RuntimeException excecao) {
            return Double.NaN;
        }
    }
}

package br.com.contextpilot.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class EvaluationMetricsTest {

    @Test
    void deveManterUltimasMetricasDisponiveisParaPrometheus() {
        var registro = new SimpleMeterRegistry();
        var metricas = new EvaluationMetrics(registro);

        metricas.registrar(0.9, 0.8, 0.7, 0.6, 450, new BigDecimal("0.0123"));

        assertThat(registro.get("contextpilot.avaliacao.taxa_acerto").gauge().value()).isEqualTo(0.9);
        assertThat(registro.get("contextpilot.avaliacao.recall").gauge().value()).isEqualTo(0.8);
        assertThat(registro.get("contextpilot.avaliacao.precisao").gauge().value()).isEqualTo(0.7);
        assertThat(registro.get("contextpilot.avaliacao.mrr").gauge().value()).isEqualTo(0.6);
        assertThat(registro.get("contextpilot.avaliacao.latencia_p95_ms").gauge().value()).isEqualTo(450);
        assertThat(registro.get("contextpilot.avaliacao.custo_total_usd").gauge().value()).isEqualTo(0.0123);
    }
}

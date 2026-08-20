package br.com.contextpilot.evaluation;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
class EvaluationMetrics {

    private final AtomicReference<Double> taxaAcerto = new AtomicReference<>(0.0);
    private final AtomicReference<Double> recall = new AtomicReference<>(0.0);
    private final AtomicReference<Double> precisao = new AtomicReference<>(0.0);
    private final AtomicReference<Double> mrr = new AtomicReference<>(0.0);
    private final AtomicLong latenciaP95Ms = new AtomicLong();
    private final AtomicReference<Double> custoTotalUsd = new AtomicReference<>(0.0);

    EvaluationMetrics(MeterRegistry metricas) {
        metricas.gauge("contextpilot.avaliacao.taxa_acerto", taxaAcerto, AtomicReference::get);
        metricas.gauge("contextpilot.avaliacao.recall", recall, AtomicReference::get);
        metricas.gauge("contextpilot.avaliacao.precisao", precisao, AtomicReference::get);
        metricas.gauge("contextpilot.avaliacao.mrr", mrr, AtomicReference::get);
        metricas.gauge("contextpilot.avaliacao.latencia_p95_ms", latenciaP95Ms);
        metricas.gauge("contextpilot.avaliacao.custo_total_usd", custoTotalUsd, AtomicReference::get);
    }

    void registrar(
            double novaTaxaAcerto,
            double novoRecall,
            double novaPrecisao,
            double novoMrr,
            long novaLatenciaP95Ms,
            BigDecimal novoCustoTotalUsd) {
        taxaAcerto.set(novaTaxaAcerto);
        recall.set(novoRecall);
        precisao.set(novaPrecisao);
        mrr.set(novoMrr);
        latenciaP95Ms.set(novaLatenciaP95Ms);
        custoTotalUsd.set(novoCustoTotalUsd.doubleValue());
    }
}

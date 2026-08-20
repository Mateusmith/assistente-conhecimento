package br.com.contextpilot.evaluation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class EvaluationWorker {

    private final EvaluationRepository repositorio;
    private final EvaluationService servico;
    private final Clock relogio;
    private final Duration tempoLease;
    private final String trabalhadorId = "avaliacao-" + UUID.randomUUID();

    EvaluationWorker(
            EvaluationRepository repositorio,
            EvaluationService servico,
            Clock relogio,
            @Value("${contextpilot.avaliacoes.tempo-lease:5m}") Duration tempoLease) {
        this.repositorio = repositorio;
        this.servico = servico;
        this.relogio = relogio;
        this.tempoLease = tempoLease;
    }

    @Scheduled(
            fixedDelayString = "${contextpilot.avaliacoes.intervalo-worker-ms:2000}",
            initialDelayString = "${contextpilot.avaliacoes.atraso-inicial-ms:2000}")
    void processarProxima() {
        Instant agora = Instant.now(relogio);
        repositorio.reivindicarProxima(trabalhadorId, agora, agora.plus(tempoLease))
                .ifPresent(trabalho -> servico.processar(trabalho, trabalhadorId));
    }
}

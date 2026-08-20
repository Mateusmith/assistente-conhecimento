package br.com.contextpilot.governance;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "contextpilot.governanca.redis-ativo", havingValue = "false", matchIfMissing = true)
class InMemoryDistributedCounter implements DistributedCounter {

    private final ConcurrentHashMap<String, Contador> contadores = new ConcurrentHashMap<>();
    private final Clock relogio;

    InMemoryDistributedCounter(Clock relogio) {
        this.relogio = relogio;
    }

    @Override
    public ResultadoContador incrementar(String chave, long valorInicial, Duration validade) {
        Instant agora = Instant.now(relogio);
        Contador contador = contadores.compute(chave, (ignorada, atual) -> {
            if (atual == null || !atual.expiraEm().isAfter(agora)) {
                return new Contador(new java.util.concurrent.atomic.AtomicLong(valorInicial + 1), agora.plus(validade));
            }
            atual.valor().incrementAndGet();
            return atual;
        });
        return new ResultadoContador(contador.valor().get(), Duration.between(agora, contador.expiraEm()));
    }

    private record Contador(java.util.concurrent.atomic.AtomicLong valor, Instant expiraEm) {
    }
}

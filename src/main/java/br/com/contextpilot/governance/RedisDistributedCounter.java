package br.com.contextpilot.governance;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import br.com.contextpilot.shared.domain.ServiceUnavailableException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "contextpilot.governanca.redis-ativo", havingValue = "true")
class RedisDistributedCounter implements DistributedCounter {

    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
              redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2], 'NX')
            end
            local atual = redis.call('INCR', KEYS[1])
            if redis.call('PTTL', KEYS[1]) < 0 then
              redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            return atual
            """, Long.class);

    private final StringRedisTemplate redis;

    RedisDistributedCounter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public ResultadoContador incrementar(String chave, long valorInicial, Duration validade) {
        try {
            long milissegundos = Math.max(1000, validade.toMillis());
            Long valor = redis.execute(
                    SCRIPT, List.of("contextpilot:" + chave), Long.toString(valorInicial), Long.toString(milissegundos));
            Long restante = redis.getExpire("contextpilot:" + chave, TimeUnit.MILLISECONDS);
            return new ResultadoContador(valor == null ? valorInicial + 1 : valor,
                    Duration.ofMillis(restante == null || restante < 0 ? milissegundos : restante));
        } catch (RuntimeException excecao) {
            throw new ServiceUnavailableException(
                    "O controle distribuido de limites esta temporariamente indisponivel.");
        }
    }
}

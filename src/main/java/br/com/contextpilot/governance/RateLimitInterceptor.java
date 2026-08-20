package br.com.contextpilot.governance;

import java.time.Duration;

import br.com.contextpilot.configuration.GovernanceProperties;
import br.com.contextpilot.shared.domain.RateLimitExceededException;
import br.com.contextpilot.shared.security.CurrentUser;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
class RateLimitInterceptor implements HandlerInterceptor {

    private final DistributedCounter contadores;
    private final CurrentUser usuarioAtual;
    private final GovernanceProperties propriedades;
    private final MeterRegistry metricas;

    RateLimitInterceptor(
            DistributedCounter contadores,
            CurrentUser usuarioAtual,
            GovernanceProperties propriedades,
            MeterRegistry metricas) {
        this.contadores = contadores;
        this.usuarioAtual = usuarioAtual;
        this.propriedades = propriedades;
        this.metricas = metricas;
    }

    @Override
    public boolean preHandle(HttpServletRequest requisicao, HttpServletResponse resposta, Object manipulador) {
        String usuarioId = usuarioAtual.obterId();
        aplicar("rate:usuario:" + usuarioId, propriedades.requisicoesPorMinuto(),
                propriedades.janelaRateLimit(), "requisicoes", resposta);
        if ("POST".equals(requisicao.getMethod()) && requisicao.getRequestURI().matches(".*/consultas/?$")) {
            aplicar("rate:consulta:" + usuarioId, propriedades.consultasPorMinuto(),
                    propriedades.janelaRateLimit(), "consultas", resposta);
        }
        return true;
    }

    private void aplicar(String chave, int limite, Duration janela, String tipo, HttpServletResponse resposta) {
        var resultado = contadores.incrementar(chave, 0, janela);
        resposta.setHeader("X-RateLimit-Limit", Integer.toString(limite));
        resposta.setHeader("X-RateLimit-Remaining", Long.toString(Math.max(0, limite - resultado.valor())));
        if (resultado.valor() > limite) {
            metricas.counter("contextpilot.governanca.limites", "tipo", tipo, "resultado", "bloqueado").increment();
            throw new RateLimitExceededException("Limite temporario de %s excedido.".formatted(tipo),
                    resultado.validadeRestante().toSeconds());
        }
        metricas.counter("contextpilot.governanca.limites", "tipo", tipo, "resultado", "permitido").increment();
    }
}

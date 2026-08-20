package br.com.contextpilot.configuration;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest requisicao,
            HttpServletResponse resposta,
            FilterChain cadeia) throws ServletException, IOException {
        resposta.setHeader("Referrer-Policy", "no-referrer");
        resposta.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        resposta.setHeader("Cross-Origin-Resource-Policy", "same-site");
        resposta.setHeader("Content-Security-Policy",
                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                        + "img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'");
        if (requisicao.getRequestURI().startsWith("/api/")) {
            resposta.setHeader("Cache-Control", "no-store");
        }
        if (requisicao.isSecure()) {
            resposta.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }
        cadeia.doFilter(requisicao, resposta);
    }
}

package br.com.contextpilot.shared.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    public String obterId() {
        return obterId(SecurityContextHolder.getContext().getAuthentication());
    }

    public String obterId(Authentication autenticacao) {
        if (autenticacao == null || !autenticacao.isAuthenticated()) {
            throw new IllegalStateException("Nao existe usuario autenticado.");
        }

        if (autenticacao.getPrincipal() instanceof Jwt jwt) {
            String nome = jwt.getClaimAsString("preferred_username");
            return nome == null || nome.isBlank() ? jwt.getSubject() : nome;
        }

        return autenticacao.getName();
    }
}

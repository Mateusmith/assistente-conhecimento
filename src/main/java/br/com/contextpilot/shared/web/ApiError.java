package br.com.contextpilot.shared.web;

import java.time.Instant;
import java.util.List;

public record ApiError(
        Instant instante,
        int status,
        String codigo,
        String mensagem,
        String caminho,
        List<CampoInvalido> campos) {

    public record CampoInvalido(String campo, String mensagem) {
    }
}

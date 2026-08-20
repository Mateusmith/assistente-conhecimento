package br.com.contextpilot.shared.domain;

public class RateLimitExceededException extends RuntimeException {

    private final long tentarNovamenteEmSegundos;

    public RateLimitExceededException(String mensagem, long tentarNovamenteEmSegundos) {
        super(mensagem);
        this.tentarNovamenteEmSegundos = Math.max(1, tentarNovamenteEmSegundos);
    }

    public long tentarNovamenteEmSegundos() {
        return tentarNovamenteEmSegundos;
    }
}

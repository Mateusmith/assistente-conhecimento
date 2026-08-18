package br.com.contextpilot.shared.domain;

public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String mensagem) {
        super(mensagem);
    }

    public ServiceUnavailableException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}

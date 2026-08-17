package br.com.contextpilot.shared.domain;

public class ForbiddenOperationException extends RuntimeException {

    public ForbiddenOperationException(String mensagem) {
        super(mensagem);
    }
}

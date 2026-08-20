package br.com.contextpilot.shared.web;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import br.com.contextpilot.shared.domain.BusinessRuleException;
import br.com.contextpilot.shared.domain.ConflictException;
import br.com.contextpilot.shared.domain.ForbiddenOperationException;
import br.com.contextpilot.shared.domain.ResourceNotFoundException;
import br.com.contextpilot.shared.domain.RateLimitExceededException;
import br.com.contextpilot.shared.domain.ServiceUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private final Clock relogio;

    public ApiExceptionHandler(Clock relogio) {
        this.relogio = relogio;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiError> naoEncontrado(ResourceNotFoundException excecao, HttpServletRequest requisicao) {
        return resposta(HttpStatus.NOT_FOUND, "RECURSO_NAO_ENCONTRADO", excecao.getMessage(), requisicao, List.of());
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    ResponseEntity<ApiError> proibido(ForbiddenOperationException excecao, HttpServletRequest requisicao) {
        return resposta(HttpStatus.FORBIDDEN, "OPERACAO_NAO_PERMITIDA", excecao.getMessage(), requisicao, List.of());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiError> conflito(ConflictException excecao, HttpServletRequest requisicao) {
        return resposta(HttpStatus.CONFLICT, "CONFLITO", excecao.getMessage(), requisicao, List.of());
    }

    @ExceptionHandler(BusinessRuleException.class)
    ResponseEntity<ApiError> regra(BusinessRuleException excecao, HttpServletRequest requisicao) {
        return resposta(HttpStatus.UNPROCESSABLE_ENTITY, "REGRA_DE_NEGOCIO", excecao.getMessage(), requisicao, List.of());
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    ResponseEntity<ApiError> indisponivel(ServiceUnavailableException excecao, HttpServletRequest requisicao) {
        return resposta(HttpStatus.SERVICE_UNAVAILABLE, "SERVICO_INDISPONIVEL", excecao.getMessage(), requisicao, List.of());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ApiError> limite(RateLimitExceededException excecao, HttpServletRequest requisicao) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", Long.toString(excecao.tentarNovamenteEmSegundos()))
                .body(criarErro(HttpStatus.TOO_MANY_REQUESTS, "LIMITE_EXCEDIDO", excecao.getMessage(),
                        requisicao, List.of()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    ResponseEntity<ApiError> validacao(BindException excecao, HttpServletRequest requisicao) {
        var campos = excecao.getBindingResult().getFieldErrors().stream()
                .map(erro -> new ApiError.CampoInvalido(erro.getField(), erro.getDefaultMessage()))
                .toList();
        return resposta(HttpStatus.BAD_REQUEST, "DADOS_INVALIDOS", "Revise os campos informados.", requisicao, campos);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> mensagemInvalida(HttpMessageNotReadableException excecao, HttpServletRequest requisicao) {
        return resposta(HttpStatus.BAD_REQUEST, "CORPO_INVALIDO", "O corpo da requisicao esta ausente ou invalido.", requisicao, List.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> arquivoGrande(MaxUploadSizeExceededException excecao, HttpServletRequest requisicao) {
        return resposta(HttpStatus.PAYLOAD_TOO_LARGE, "ARQUIVO_MUITO_GRANDE", "O arquivo excede o limite de 10 MB.", requisicao, List.of());
    }

    private ResponseEntity<ApiError> resposta(
            HttpStatus status,
            String codigo,
            String mensagem,
            HttpServletRequest requisicao,
            List<ApiError.CampoInvalido> campos) {
        return ResponseEntity.status(status).body(criarErro(status, codigo, mensagem, requisicao, campos));
    }

    private ApiError criarErro(
            HttpStatus status,
            String codigo,
            String mensagem,
            HttpServletRequest requisicao,
            List<ApiError.CampoInvalido> campos) {
        return new ApiError(Instant.now(relogio), status.value(), codigo, mensagem, requisicao.getRequestURI(), campos);
    }
}

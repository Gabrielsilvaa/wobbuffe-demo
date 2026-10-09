package com.wobbuffe.api.web;

import com.wobbuffe.core.CircuitOpenException;
import com.wobbuffe.core.HttpStatusException;
import com.wobbuffe.core.IntegrationTimeoutException;
import com.wobbuffe.core.OperationPublicationException;
import com.wobbuffe.core.OperationRejectedException;
import com.wobbuffe.core.OperationRoutingUnavailableException;
import com.wobbuffe.core.ResilienceExecutionException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

// Camada REST de integração com os erros tipados pelo Wobbuffe Starter (circuit breaker, timeout, retry e publicação).
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(ResourceNotFoundException e) { return problem(HttpStatus.NOT_FOUND, e.getMessage()); }

    @ExceptionHandler({BadRequestException.class, IllegalArgumentException.class})
    ResponseEntity<ProblemDetail> badRequest(RuntimeException e) { return problem(HttpStatus.BAD_REQUEST, e.getMessage()); }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> conflict() { return problem(HttpStatus.CONFLICT, "A Pokémon with this name already exists"); }

    @ExceptionHandler(OperationRejectedException.class)
    ResponseEntity<ProblemDetail> rejected() { return problem(HttpStatus.CONFLICT, "The operation idempotency key is already in use"); }

    // Traduz falhas tipadas pelo Wobbuffe Starter para respostas HTTP da API, sem mascarar falha de publicação como sucesso.
    @ExceptionHandler({CircuitOpenException.class, OperationPublicationException.class, OperationRoutingUnavailableException.class})
    ResponseEntity<ProblemDetail> unavailable() { return problem(HttpStatus.SERVICE_UNAVAILABLE, "The integration is currently unavailable"); }

    // Mantém o timeout emitido pela política de resiliência como 504 para o consumidor REST.
    @ExceptionHandler(IntegrationTimeoutException.class)
    ResponseEntity<ProblemDetail> timeout() { return problem(HttpStatus.GATEWAY_TIMEOUT, "The external integration timed out"); }

    @ExceptionHandler(ResilienceExecutionException.class)
    ResponseEntity<ProblemDetail> upstreamFailure(ResilienceExecutionException e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof HttpStatusException http) {
                if (http.statusCode() == 404) return problem(HttpStatus.NOT_FOUND, "Pokémon was not found by the upstream service");
                if (http.statusCode() == 408) return problem(HttpStatus.GATEWAY_TIMEOUT, "The external integration timed out");
                if (http.statusCode() == 429) return problem(HttpStatus.SERVICE_UNAVAILABLE, "The external service is rate limiting requests");
                return problem(HttpStatus.BAD_GATEWAY, "The external service returned an error");
            }
            if (current instanceof TimeoutException || current instanceof HttpTimeoutException || current instanceof IntegrationTimeoutException) {
                return problem(HttpStatus.GATEWAY_TIMEOUT, "The external integration timed out");
            }
            if (current instanceof IOException) return problem(HttpStatus.GATEWAY_TIMEOUT, "The external integration could not be reached");
            current = current.getCause();
        }
        return problem(HttpStatus.BAD_GATEWAY, "The external integration failed");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage()).findFirst().orElse("Request validation failed");
        return problem(HttpStatus.BAD_REQUEST, detail);
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}

package br.com.itau.limiteg.orquestrador.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handler global de exceções — mapeia exceções para respostas HTTP apropriadas.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Captura RuntimeException com mensagem "indisponível" (quando serviço externo está down).
     * Retorna 503 Service Unavailable.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Erro interno no servidor";

        // Se a mensagem contém indicação de serviço indisponível, retorna 503
        if (message.toLowerCase().contains("indisponível") ||
            message.toLowerCase().contains("unavailable") ||
            message.toLowerCase().contains("circuit breaker") ||
            message.toLowerCase().contains("timeout")) {

            log.warn("Serviço externo indisponível: {}", message);
            return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, message);
        }

        // Demais RuntimeExceptions viram 500
        log.error("Erro não esperado na execução", ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    /**
     * Captura IllegalStateException (erro de lógica/estado).
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalStateException(IllegalStateException ex) {
        log.warn("Erro de estado: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Captura IllegalArgumentException (argumento inválido).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Argumento inválido: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Captura Exception genérica (fallback).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception ex) {
        log.error("Exceção não tratada", ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno no servidor");
    }

    /**
     * Constrói resposta de erro padronizada.
     */
    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", status.value());
        response.put("error", status.getReasonPhrase());
        response.put("message", message);
        return ResponseEntity.status(status).body(response);
    }
}


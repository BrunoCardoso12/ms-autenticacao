package com.componentes.autenticacao.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

/**
 * Captura exceções lançadas em QUALQUER controller e transforma em respostas JSON amigáveis.
 * Assim o controller e o service não precisam de try/catch.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Erros de regra de negócio lançados pelos Services
    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<Map<String, String>> handleRegraNegocio(RegraNegocioException ex) {
        return ResponseEntity.status(ex.getStatus()).body(Map.of("erro", ex.getMessage()));
    }

    // Erros de validação do DTO (@NotBlank, @Email, @Size...) disparados pelo @Valid
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidacao(MethodArgumentNotValidException ex) {
        Map<String, String> erros = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> erros.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(erros);
    }

    // Servidor de email fora do ar: 503 (Service Unavailable) em vez de um 500 genérico
    @ExceptionHandler(MailException.class)
    public ResponseEntity<Map<String, String>> handleEmail(MailException ex) {
        log.error("Falha ao enviar email", ex); // detalhe técnico só no log, nunca para o cliente
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("erro", "Não foi possível enviar o email. Tente novamente em instantes"));
    }
}

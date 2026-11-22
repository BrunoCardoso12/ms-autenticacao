package com.componentes.autenticacao.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * Exceção para quando uma regra de negócio é violada.
 * Carrega o status HTTP que deve ser devolvido ao cliente.
 */
@Getter
public class RegraNegocioException extends RuntimeException {

    private final HttpStatus status;

    public RegraNegocioException(String mensagem, HttpStatus status) {
        super(mensagem);
        this.status = status;
    }
}

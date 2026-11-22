package com.componentes.autenticacao.model;

/**
 * Para que serve um TokenVerificacao.
 * Separar por tipo impede que um token de "confirmar email"
 * seja usado para "redefinir senha" (e vice-versa).
 */
public enum TipoToken {
    CONFIRMACAO_EMAIL,
    RECUPERACAO_SENHA
}

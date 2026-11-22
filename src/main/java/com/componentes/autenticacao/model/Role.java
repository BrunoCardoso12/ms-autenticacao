package com.componentes.autenticacao.model;

/**
 * Perfis de acesso (papéis) do sistema.
 *
 * USER  -> usuário comum (todo mundo que se cadastra)
 * ADMIN -> administrador (pode acessar rotas /admin/**)
 *
 * O perfil vai dentro do token JWT (claim "roles") e o Spring Security
 * usa essa informação para liberar ou bloquear rotas.
 */
public enum Role {
    USER,
    ADMIN
}

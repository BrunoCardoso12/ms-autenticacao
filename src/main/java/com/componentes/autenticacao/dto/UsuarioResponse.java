package com.componentes.autenticacao.dto;

import com.componentes.autenticacao.model.Role;
import com.componentes.autenticacao.model.Usuario;

/**
 * DTO DE SAÍDA: o que a API devolve sobre um usuário.
 *
 * Existe para NUNCA devolver a senha (nem o hash) na resposta.
 * Se devolvêssemos a entidade Usuario direto, o campo password iria junto no JSON.
 */
public record UsuarioResponse(Long id, String name, String email, Role role, boolean enabled) {

    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(), usuario.getName(), usuario.getEmail(), usuario.getRole(), usuario.isEnabled());
    }
}

package com.componentes.autenticacao.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO DE ENTRADA: o JSON que o cliente envia no cadastro.
 *
 * Aqui ficam as validações de FORMATO (campo vazio, email válido, tamanho mínimo).
 * Regras de NEGÓCIO (email já existe? senhas batem?) ficam no Service.
 *
 * "record" = classe imutável com construtor, getters, equals e toString gerados automaticamente.
 */
public record CadastroRequest(

        @NotBlank(message = "Nome é obrigatório")
        String name,

        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email inválido")
        String email,

        @NotBlank(message = "Senha é obrigatória")
        @Size(min = 8, message = "Senha deve ter no mínimo 8 caracteres")
        String password,

        @NotBlank(message = "Confirmação de senha é obrigatória")
        String passwordConfirmation
) {
}

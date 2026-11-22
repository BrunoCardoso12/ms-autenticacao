package com.componentes.autenticacao.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de POST /auth/redefinir-senha (o token vem do link enviado por email). */
public record RedefinirSenhaRequest(

        @NotBlank(message = "Token é obrigatório")
        String token,

        @NotBlank(message = "Senha é obrigatória")
        @Size(min = 8, message = "Senha deve ter no mínimo 8 caracteres")
        String password,

        @NotBlank(message = "Confirmação de senha é obrigatória")
        String passwordConfirmation
) {
}

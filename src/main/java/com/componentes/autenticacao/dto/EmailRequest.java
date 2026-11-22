package com.componentes.autenticacao.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Corpo de POST /auth/esqueci-senha e POST /auth/reenviar-confirmacao. */
public record EmailRequest(

        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email inválido")
        String email
) {
}

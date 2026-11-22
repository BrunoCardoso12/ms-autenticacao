package com.componentes.autenticacao.dto;

import jakarta.validation.constraints.NotBlank;

/** Corpo de POST /auth/refresh e POST /auth/logout. */
public record RefreshTokenRequest(

        @NotBlank(message = "refreshToken é obrigatório")
        String refreshToken
) {
}

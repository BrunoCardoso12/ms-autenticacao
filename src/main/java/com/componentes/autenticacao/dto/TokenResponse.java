package com.componentes.autenticacao.dto;

/**
 * DTO DE SAÍDA do login e do refresh.
 *
 * - accessToken:  o JWT. O cliente manda em toda requisição protegida no header
 *                 Authorization: Bearer <accessToken>     (dura pouco: 15 min)
 * - refreshToken: usado SÓ em POST /auth/refresh para pegar um novo accessToken
 *                 sem digitar a senha de novo                (dura 7 dias)
 * - tokenType:    sempre "Bearer" (padrão OAuth2: "quem portar o token tem acesso")
 * - expiresIn:    quantos segundos até o accessToken expirar
 */
public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {
}

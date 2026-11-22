package com.componentes.autenticacao.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Funções utilitárias para tokens "opacos" (refresh token, confirmação de email, recuperação de senha).
 *
 * "Opaco" = um texto aleatório sem significado (diferente do JWT, que carrega dados dentro).
 *
 * Classe "final" com construtor privado = só tem métodos estáticos, ninguém instancia.
 */
public final class TokenUtils {

    // SecureRandom: gerador aleatório criptograficamente seguro (NÃO use java.util.Random para isso)
    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenUtils() {
    }

    /** Gera um token aleatório de 32 bytes, em Base64 "URL-safe" (pode ir num link sem quebrar). */
    public static String gerar() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Calcula o hash SHA-256 do token. É ISSO que salvamos no banco.
     *
     * Por que SHA-256 aqui e BCrypt na senha?
     * - Senhas são curtas e previsíveis -> precisam de um hash LENTO (BCrypt) para dificultar força bruta.
     * - Estes tokens já são 32 bytes aleatórios (impossível adivinhar) -> um hash rápido basta,
     *   e permite buscar no banco direto pelo hash.
     */
    public static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não disponível", e); // nunca acontece na prática
        }
    }
}

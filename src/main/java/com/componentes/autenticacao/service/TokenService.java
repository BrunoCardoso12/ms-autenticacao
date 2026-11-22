package com.componentes.autenticacao.service;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.componentes.autenticacao.model.Usuario;

/**
 * Responsabilidade ÚNICA: gerar o ACCESS TOKEN (JWT) de um usuário.
 *
 * Um JWT tem 3 partes separadas por ponto:  HEADER.PAYLOAD.ASSINATURA
 * - O payload ("claims") NÃO é criptografado, só codificado em Base64.
 *   Qualquer um consegue ler (cole um token em https://jwt.io para ver).
 *   -> NUNCA coloque senha ou dados sensíveis nas claims.
 * - A assinatura garante que ninguém ALTEROU o token sem ter a chave privada.
 *   Por isso o usuário não consegue trocar "USER" por "ADMIN" dentro do token.
 *
 * A assinatura é RS256: feita com a chave PRIVADA (veja JwtConfig).
 */
@Service
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final long expirationSeconds;
    // "iss" (issuer): quem emitiu o token. Os outros serviços conferem esse valor
    // para só aceitar tokens emitidos por ESTE serviço de autenticação.
    private final String issuer;

    public TokenService(JwtEncoder jwtEncoder,
                        @Value("${jwt.access-token-expiration-seconds}") long expirationSeconds,
                        @Value("${jwt.issuer}") String issuer) {
        this.jwtEncoder = jwtEncoder;
        this.expirationSeconds = expirationSeconds;
        this.issuer = issuer;
    }

    public String gerarAccessToken(Usuario usuario) {
        Instant agora = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)                                  // "iss": quem emitiu
                .subject(String.valueOf(usuario.getId()))        // "sub": de QUEM é o token (id do usuário)
                .issuedAt(agora)                                 // "iat": quando foi emitido
                .expiresAt(agora.plusSeconds(expirationSeconds)) // "exp": quando expira
                .claim("email", usuario.getEmail())
                .claim("name", usuario.getName())
                // "roles": lida pelo SecurityConfig para liberar rotas de ADMIN
                .claim("roles", List.of(usuario.getRole().name()))
                .build();

        // Assina com RS256 usando a chave privada do JwtConfig.
        // O encoder coloca sozinho o "kid" (id da chave) no cabeçalho do token.
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}

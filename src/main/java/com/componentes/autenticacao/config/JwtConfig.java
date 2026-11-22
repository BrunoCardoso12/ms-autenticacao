package com.componentes.autenticacao.config;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

import lombok.extern.slf4j.Slf4j;

/**
 * Configura a assinatura dos tokens com RS256 (RSA + SHA-256).
 *
 * RS256 usa um PAR de chaves:
 *  - CHAVE PRIVADA: fica SÓ neste serviço. Serve para ASSINAR tokens.
 *  - CHAVE PÚBLICA: pode ser distribuída para qualquer um. Serve só para VALIDAR.
 *
 * Assim, outros microsserviços validam nossos tokens com a chave pública
 * (publicada em /.well-known/jwks.json), mas NÃO conseguem criar tokens falsos.
 *
 * (Antes usávamos HS256: uma única chave secreta que assina E valida. Quem valida
 *  também conseguiria forjar tokens, então não serve para vários serviços.)
 */
@Slf4j
@Configuration
public class JwtConfig {

    /**
     * O par de chaves no formato JWK (JSON Web Key), usado pela biblioteca Nimbus.
     *
     * - Com jwt.private-key-location preenchido: lê a chave privada do arquivo (PRODUÇÃO).
     * - Vazio: gera um par TEMPORÁRIO ao subir (dev/testes). Ao reiniciar a aplicação,
     *   gera outro, e os access tokens antigos deixam de valer (basta fazer refresh ou login).
     */
    @Bean
    public RSAKey rsaKey(@Value("${jwt.private-key-location}") String location, ResourceLoader resourceLoader)
            throws IOException, GeneralSecurityException, JOSEException {

        KeyPair keyPair;
        if (location.isBlank()) {
            log.warn("jwt.private-key-location não configurado: usando chave RSA TEMPORÁRIA (só para dev/testes)");
            keyPair = gerarParTemporario();
        } else {
            keyPair = carregarDoArquivo(resourceLoader.getResource(location));
        }

        return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyUse(KeyUse.SIGNATURE)      // "use": "sig" -> chave serve para assinatura
                .algorithm(JWSAlgorithm.RS256) // "alg": "RS256"
                // "kid" (key id): identifica a chave. Vai no cabeçalho de cada token, e quem
                // valida usa para achar a chave certa no JWKS (útil ao trocar de chave).
                // "Thumbprint" = um hash da chave pública, então o kid é sempre o mesmo para a mesma chave.
                .keyIDFromThumbprint()
                .build();
    }

    /** CRIA e ASSINA tokens com a chave PRIVADA (usado pelo TokenService). */
    @Bean
    public JwtEncoder jwtEncoder(RSAKey rsaKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
    }

    /**
     * VALIDA tokens com a chave PÚBLICA (usado pelo Spring Security nas rotas protegidas
     * DESTE serviço, como /auth/me). Confere assinatura, expiração e o emissor ("iss").
     */
    @Bean
    public JwtDecoder jwtDecoder(RSAKey rsaKey, @Value("${jwt.issuer}") String issuer) throws JOSEException {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }

    // ===================== auxiliares =====================

    private KeyPair gerarParTemporario() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048); // 2048 bits: tamanho mínimo recomendado hoje
        return generator.generateKeyPair();
    }

    /**
     * Lê a chave privada de um arquivo PEM (PKCS#8), gerado com:
     *   openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
     *
     * A chave PÚBLICA não precisa de arquivo: ela é calculada a partir da privada
     * (a chave privada RSA guarda o "módulo" e o "expoente público").
     */
    private KeyPair carregarDoArquivo(Resource arquivo) throws IOException, GeneralSecurityException {
        RSAPrivateKey privateKey;
        try (InputStream in = arquivo.getInputStream()) {
            privateKey = RsaKeyConverters.pkcs8().convert(in);
        }

        RSAPrivateCrtKey crt = (RSAPrivateCrtKey) privateKey;
        RSAPublicKeySpec publicSpec = new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent());
        RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(publicSpec);

        log.info("Chave RSA carregada de {}", arquivo.getDescription());
        return new KeyPair(publicKey, privateKey);
    }
}

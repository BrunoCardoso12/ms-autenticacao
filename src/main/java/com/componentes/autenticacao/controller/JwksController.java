package com.componentes.autenticacao.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;

/**
 * Publica a CHAVE PÚBLICA no formato JWKS (JSON Web Key Set).
 *
 * GET /.well-known/jwks.json  (público)
 *
 * É daqui que OUTROS microsserviços baixam a chave para validar nossos tokens.
 * Basta eles configurarem:
 *   spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://<este-servico>/.well-known/jwks.json
 *
 * "/.well-known/" é um caminho PADRÃO da web para esse tipo de informação,
 * por isso ferramentas e bibliotecas já sabem onde procurar.
 *
 * IMPORTANTE: toPublicJWK() remove a parte privada. Só a chave PÚBLICA sai daqui.
 */
@RestController
public class JwksController {

    private final Map<String, Object> jwks;

    public JwksController(RSAKey rsaKey) {
        this.jwks = new JWKSet(rsaKey.toPublicJWK()).toJSONObject();
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return jwks;
    }
}

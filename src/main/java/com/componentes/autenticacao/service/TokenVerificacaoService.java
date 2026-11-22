package com.componentes.autenticacao.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.componentes.autenticacao.exception.RegraNegocioException;
import com.componentes.autenticacao.model.TipoToken;
import com.componentes.autenticacao.model.TokenVerificacao;
import com.componentes.autenticacao.model.Usuario;
import com.componentes.autenticacao.repository.TokenVerificacaoRepository;
import com.componentes.autenticacao.security.TokenUtils;

import lombok.RequiredArgsConstructor;

/**
 * Tokens de USO ÚNICO enviados por email (confirmação de email e recuperação de senha).
 */
@Service
@RequiredArgsConstructor
public class TokenVerificacaoService {

    private final TokenVerificacaoRepository repository;

    /** Cria um token e devolve o valor PURO (que vai no email). No banco fica só o hash. */
    public String criar(Usuario usuario, TipoToken tipo, Duration validade) {
        // Só o link mais recente vale: invalida os tokens anteriores do mesmo tipo
        repository.invalidarAnteriores(usuario, tipo);

        String token = TokenUtils.gerar();
        repository.save(new TokenVerificacao(TokenUtils.hash(token), usuario, tipo, Instant.now().plus(validade)));
        return token;
    }

    /**
     * Valida o token e o marca como USADO (não pode ser usado de novo).
     * @return o usuário dono do token
     */
    public Usuario consumir(String tokenPuro, TipoToken tipo) {
        TokenVerificacao token = repository.findByTokenHashAndTipo(TokenUtils.hash(tokenPuro), tipo)
                .filter(TokenVerificacao::isValido)
                .orElseThrow(() -> new RegraNegocioException("Token inválido ou expirado", HttpStatus.BAD_REQUEST));

        token.setUsado(true);
        return token.getUsuario();
    }
}

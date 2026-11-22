package com.componentes.autenticacao.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.componentes.autenticacao.exception.RegraNegocioException;
import com.componentes.autenticacao.model.RefreshToken;
import com.componentes.autenticacao.model.Usuario;
import com.componentes.autenticacao.repository.RefreshTokenRepository;
import com.componentes.autenticacao.security.TokenUtils;

/**
 * Cuida do ciclo de vida dos REFRESH TOKENS: criar, trocar (rotacionar) e revogar.
 *
 * Os métodos daqui são chamados pelo AuthService, que já abre a transação (@Transactional).
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final Duration validade;

    public RefreshTokenService(RefreshTokenRepository repository,
                               @Value("${jwt.refresh-token-expiration-days}") long dias) {
        this.repository = repository;
        this.validade = Duration.ofDays(dias);
    }

    /** Cria um refresh token para o usuário. Devolve o token PURO (só o hash fica no banco). */
    public String criar(Usuario usuario) {
        String token = TokenUtils.gerar();
        repository.save(new RefreshToken(TokenUtils.hash(token), usuario, Instant.now().plus(validade)));
        return token;
    }

    /**
     * ROTAÇÃO: cada refresh token só pode ser usado UMA vez.
     * Ao usar, ele é revogado e o chamador gera um novo par de tokens.
     *
     * DETECÇÃO DE ROUBO: se alguém usar um token JÁ revogado, é sinal de que ele
     * foi copiado (o usuário legítimo ou o atacante já tinha usado antes).
     * Nesse caso revogamos TODOS os tokens do usuário, forçando novo login em todo lugar.
     *
     * @return o dono do token (para o AuthService gerar os novos tokens)
     */
    public Usuario rotacionar(String tokenPuro) {
        RefreshToken refreshToken = repository.findByTokenHash(TokenUtils.hash(tokenPuro))
                .orElseThrow(RefreshTokenService::tokenInvalido);

        if (refreshToken.isRevogado()) {
            repository.revogarTodosDoUsuario(refreshToken.getUsuario());
            throw tokenInvalido();
        }
        if (refreshToken.isExpirado()) {
            throw tokenInvalido();
        }

        refreshToken.setRevogado(true); // o JPA salva sozinho ao fim da transação ("dirty checking")
        return refreshToken.getUsuario();
    }

    /** Logout de UM dispositivo: revoga só este refresh token. */
    public void revogar(String tokenPuro) {
        // Se não existir, não faz nada: logout deve ser "idempotente" (chamar 2x não dá erro)
        repository.findByTokenHash(TokenUtils.hash(tokenPuro))
                .ifPresent(t -> t.setRevogado(true));
    }

    /** Logout de TODOS os dispositivos (usado ao redefinir a senha). */
    public void revogarTodos(Usuario usuario) {
        repository.revogarTodosDoUsuario(usuario);
    }

    private static RegraNegocioException tokenInvalido() {
        return new RegraNegocioException("Refresh token inválido ou expirado", HttpStatus.UNAUTHORIZED);
    }
}

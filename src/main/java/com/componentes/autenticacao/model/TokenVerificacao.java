package com.componentes.autenticacao.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tabela "tokens_verificacao": tokens de USO ÚNICO enviados por email.
 *
 * Usado em:
 *  - confirmação de email (link enviado no cadastro)
 *  - recuperação de senha (link enviado no "esqueci minha senha")
 *
 * Assim como o refresh token, guardamos só o HASH do token.
 * O token "puro" existe apenas dentro do email enviado ao usuário.
 */
@Entity
@Table(name = "tokens_verificacao")
@Getter
@Setter
@NoArgsConstructor
public class TokenVerificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoToken tipo;

    @Column(nullable = false)
    private Instant expiraEm;

    // Uso único: depois de usado, não vale mais
    @Column(nullable = false)
    private boolean usado = false;

    public TokenVerificacao(String tokenHash, Usuario usuario, TipoToken tipo, Instant expiraEm) {
        this.tokenHash = tokenHash;
        this.usuario = usuario;
        this.tipo = tipo;
        this.expiraEm = expiraEm;
    }

    public boolean isValido() {
        return !usado && Instant.now().isBefore(expiraEm);
    }
}

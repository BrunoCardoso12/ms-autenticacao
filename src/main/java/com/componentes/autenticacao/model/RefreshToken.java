package com.componentes.autenticacao.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Tabela "refresh_tokens".
 *
 * POR QUE EXISTE?
 * O access token (JWT) NÃO fica guardado no servidor, então não dá para "cancelá-lo".
 * Por isso ele dura pouco (15 min). Para o usuário não ter que digitar a senha
 * a cada 15 min, ele recebe também um REFRESH TOKEN (dura 7 dias), que fica salvo
 * AQUI no banco. Como está no banco, conseguimos REVOGAR (= fazer logout).
 *
 * SEGURANÇA: guardamos só o HASH (SHA-256) do token, igual fazemos com a senha.
 * Se alguém roubar o banco, não consegue usar os tokens.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String tokenHash;

    // Muitos refresh tokens para um usuário (ele pode estar logado em vários dispositivos)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(nullable = false)
    private Instant expiraEm;

    @Column(nullable = false)
    private boolean revogado = false;

    public RefreshToken(String tokenHash, Usuario usuario, Instant expiraEm) {
        this.tokenHash = tokenHash;
        this.usuario = usuario;
        this.expiraEm = expiraEm;
    }

    public boolean isExpirado() {
        return Instant.now().isAfter(expiraEm);
    }
}

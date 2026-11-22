package com.componentes.autenticacao.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.componentes.autenticacao.model.RefreshToken;
import com.componentes.autenticacao.model.Usuario;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revoga TODOS os refresh tokens de um usuário (= "sair de todos os dispositivos").
     * Quando o nome do método não basta, escrevemos a consulta em JPQL com @Query.
     * @Modifying avisa o Spring que é um UPDATE/DELETE, não um SELECT.
     */
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revogado = true WHERE r.usuario = :usuario AND r.revogado = false")
    void revogarTodosDoUsuario(Usuario usuario);
}

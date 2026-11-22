package com.componentes.autenticacao.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.componentes.autenticacao.model.Usuario;

/**
 * REPOSITORY: única camada que conversa com o banco.
 *
 * Ao estender JpaRepository<Usuario, Long> você ganha de graça:
 * save, findById, findAll, deleteById, count...
 *
 * Os métodos abaixo o Spring implementa SOZINHO só pelo nome
 * ("existsBy" + "Email" -> SELECT COUNT(*) > 0 FROM usuarios WHERE email = ?).
 */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    boolean existsByEmail(String email);

    Optional<Usuario> findByEmail(String email); // vai ser usado no login
}

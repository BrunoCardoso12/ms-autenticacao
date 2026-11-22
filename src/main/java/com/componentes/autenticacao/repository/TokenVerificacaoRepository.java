package com.componentes.autenticacao.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.componentes.autenticacao.model.TipoToken;
import com.componentes.autenticacao.model.TokenVerificacao;
import com.componentes.autenticacao.model.Usuario;

public interface TokenVerificacaoRepository extends JpaRepository<TokenVerificacao, Long> {

    // Busca pelo hash E pelo tipo: um token de confirmação não serve para redefinir senha
    Optional<TokenVerificacao> findByTokenHashAndTipo(String tokenHash, TipoToken tipo);

    // Ao gerar um token novo, invalida os anteriores do mesmo tipo (só o último link vale)
    @Modifying
    @Query("UPDATE TokenVerificacao t SET t.usado = true WHERE t.usuario = :usuario AND t.tipo = :tipo AND t.usado = false")
    void invalidarAnteriores(Usuario usuario, TipoToken tipo);
}

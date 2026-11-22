package com.componentes.autenticacao.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.componentes.autenticacao.dto.UsuarioResponse;
import com.componentes.autenticacao.exception.RegraNegocioException;
import com.componentes.autenticacao.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * Consultas sobre usuários (o que NÃO é fluxo de autenticação).
 * Os fluxos de cadastro/login/senha ficam no AuthService.
 */
@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public UsuarioResponse buscarPorId(Long id) {
        return usuarioRepository.findById(id)
                .map(UsuarioResponse::from)
                .orElseThrow(() -> new RegraNegocioException("Usuário não encontrado", HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listarTodos() {
        return usuarioRepository.findAll().stream()
                .map(UsuarioResponse::from)
                .toList();
    }
}

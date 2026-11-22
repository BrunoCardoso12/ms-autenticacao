package com.componentes.autenticacao.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.componentes.autenticacao.dto.UsuarioResponse;
import com.componentes.autenticacao.service.UsuarioService;

import lombok.RequiredArgsConstructor;

/**
 * Rotas exclusivas de ADMINISTRADOR.
 *
 * A proteção está em DOIS lugares (defesa em camadas):
 *  1. SecurityConfig: tudo em /admin/** exige o perfil ADMIN
 *  2. @PreAuthorize aqui: mesmo que alguém mude a URL ou a config, o método continua protegido
 *
 * Usuário logado sem perfil ADMIN -> 403 (Forbidden: "sei quem você é, mas não pode").
 * Sem token                       -> 401 (Unauthorized: "não sei quem você é").
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UsuarioService usuarioService;

    @GetMapping("/usuarios")
    public List<UsuarioResponse> listarUsuarios() {
        return usuarioService.listarTodos();
    }
}

package com.componentes.autenticacao.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.componentes.autenticacao.dto.CadastroRequest;
import com.componentes.autenticacao.dto.EmailRequest;
import com.componentes.autenticacao.dto.LoginRequest;
import com.componentes.autenticacao.dto.MensagemResponse;
import com.componentes.autenticacao.dto.RedefinirSenhaRequest;
import com.componentes.autenticacao.dto.RefreshTokenRequest;
import com.componentes.autenticacao.dto.TokenResponse;
import com.componentes.autenticacao.dto.UsuarioResponse;
import com.componentes.autenticacao.service.AuthService;
import com.componentes.autenticacao.service.UsuarioService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * CONTROLLER: porta de entrada HTTP.
 *
 * Responsabilidade: receber a requisição, validar o formato (@Valid),
 * chamar o service e devolver a resposta. NENHUMA regra de negócio aqui.
 *
 * Quais rotas são públicas e quais exigem token está definido no SecurityConfig.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UsuarioService usuarioService;

    // ---------- Cadastro e confirmação de email (públicos) ----------

    @PostMapping("/cadastro")
    @ResponseStatus(HttpStatus.CREATED)
    public MensagemResponse cadastrar(@RequestBody @Valid CadastroRequest request) {
        return authService.cadastrar(request);
    }

    // GET porque é o link que o usuário clica no email (o navegador faz um GET)
    @GetMapping("/confirmar-email")
    public MensagemResponse confirmarEmail(@RequestParam String token) {
        return authService.confirmarEmail(token);
    }

    @PostMapping("/reenviar-confirmacao")
    public MensagemResponse reenviarConfirmacao(@RequestBody @Valid EmailRequest request) {
        return authService.reenviarConfirmacao(request.email());
    }

    // ---------- Login, refresh e logout (públicos: o próprio corpo traz a credencial) ----------

    @PostMapping("/login")
    public TokenResponse login(@RequestBody @Valid LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@RequestBody @Valid RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT) // 204: deu certo, sem corpo na resposta
    public void logout(@RequestBody @Valid RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
    }

    // ---------- Recuperação de senha (públicos) ----------

    @PostMapping("/esqueci-senha")
    public MensagemResponse esqueciSenha(@RequestBody @Valid EmailRequest request) {
        return authService.esqueciSenha(request.email());
    }

    @PostMapping("/redefinir-senha")
    public MensagemResponse redefinirSenha(@RequestBody @Valid RedefinirSenhaRequest request) {
        return authService.redefinirSenha(request);
    }

    // ---------- Usuário logado (PROTEGIDO: exige access token) ----------

    /**
     * Quando a requisição chega aqui, o Spring Security JÁ validou o token
     * (assinatura + expiração). Se fosse inválido, nem entraria no método (401).
     * jwt.getSubject() é o "sub" que o TokenService colocou (o id do usuário).
     */
    @GetMapping("/me")
    public UsuarioResponse me(@AuthenticationPrincipal Jwt jwt) {
        return usuarioService.buscarPorId(Long.valueOf(jwt.getSubject()));
    }
}

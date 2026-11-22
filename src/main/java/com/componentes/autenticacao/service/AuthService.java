package com.componentes.autenticacao.service;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.componentes.autenticacao.dto.CadastroRequest;
import com.componentes.autenticacao.dto.LoginRequest;
import com.componentes.autenticacao.dto.MensagemResponse;
import com.componentes.autenticacao.dto.RedefinirSenhaRequest;
import com.componentes.autenticacao.dto.TokenResponse;
import com.componentes.autenticacao.exception.RegraNegocioException;
import com.componentes.autenticacao.model.TipoToken;
import com.componentes.autenticacao.model.Usuario;
import com.componentes.autenticacao.repository.UsuarioRepository;

/**
 * SERVICE principal da autenticação: ORQUESTRA os fluxos.
 *
 * Ele não faz tudo sozinho; delega para services especializados:
 *  - TokenService             -> gera o access token (JWT)
 *  - RefreshTokenService      -> cria/rotaciona/revoga refresh tokens
 *  - TokenVerificacaoService  -> tokens de uso único enviados por email
 *  - EmailService             -> envia os emails
 *  - LoginAttemptService      -> bloqueio por excesso de tentativas
 *
 * Cada classe com UMA responsabilidade = mais fácil de entender, testar e mudar.
 */
@Service
public class AuthService {

    // Mensagem genérica para fluxos por email: NÃO revela se o email está cadastrado
    private static final String MSG_EMAIL_GENERICA =
            "Se o email estiver cadastrado, você receberá as instruções em instantes";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final TokenVerificacaoService tokenVerificacaoService;
    private final EmailService emailService;
    private final LoginAttemptService loginAttemptService;
    private final Duration validadeConfirmacao;
    private final Duration validadeRecuperacao;

    public AuthService(UsuarioRepository usuarioRepository,
                       PasswordEncoder passwordEncoder,
                       TokenService tokenService,
                       RefreshTokenService refreshTokenService,
                       TokenVerificacaoService tokenVerificacaoService,
                       EmailService emailService,
                       LoginAttemptService loginAttemptService,
                       @Value("${app.token.confirmacao-email-horas}") long horasConfirmacao,
                       @Value("${app.token.recuperacao-senha-minutos}") long minutosRecuperacao) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
        this.tokenVerificacaoService = tokenVerificacaoService;
        this.emailService = emailService;
        this.loginAttemptService = loginAttemptService;
        this.validadeConfirmacao = Duration.ofHours(horasConfirmacao);
        this.validadeRecuperacao = Duration.ofMinutes(minutosRecuperacao);
    }

    // =====================================================================
    // CADASTRO
    // Cria o usuário DESATIVADO e envia o link de confirmação por email.
    // Não devolve token: só depois de confirmar o email ele consegue logar.
    // =====================================================================
    @Transactional
    public MensagemResponse cadastrar(CadastroRequest request) {
        validarSenhasIguais(request.password(), request.passwordConfirmation());

        String email = normalizarEmail(request.email());
        if (usuarioRepository.existsByEmail(email)) {
            throw new RegraNegocioException("Email já cadastrado", HttpStatus.CONFLICT);
        }

        Usuario usuario = new Usuario(request.name().trim(), email, passwordEncoder.encode(request.password()));
        usuarioRepository.save(usuario); // role = USER e enabled = false (valores padrão da entidade)

        enviarEmailConfirmacao(usuario);
        // Se o envio do email falhar, a exceção desfaz a transação (rollback):
        // o usuário NÃO fica salvo e pode tentar se cadastrar de novo.

        return new MensagemResponse("Cadastro realizado! Verifique seu email para ativar a conta");
    }

    // =====================================================================
    // CONFIRMAR EMAIL (link clicado no email)
    // =====================================================================
    @Transactional
    public MensagemResponse confirmarEmail(String token) {
        Usuario usuario = tokenVerificacaoService.consumir(token, TipoToken.CONFIRMACAO_EMAIL);
        usuario.setEnabled(true);
        return new MensagemResponse("Email confirmado! Você já pode fazer login");
    }

    // =====================================================================
    // REENVIAR CONFIRMAÇÃO (o link expirou ou o email se perdeu)
    // =====================================================================
    @Transactional
    public MensagemResponse reenviarConfirmacao(String emailInformado) {
        usuarioRepository.findByEmail(normalizarEmail(emailInformado))
                .filter(u -> !u.isEnabled()) // só faz sentido para quem ainda não confirmou
                .ifPresent(this::enviarEmailConfirmacao);
        return new MensagemResponse(MSG_EMAIL_GENERICA);
    }

    // =====================================================================
    // LOGIN
    // =====================================================================
    @Transactional
    public TokenResponse login(LoginRequest request) {
        String email = normalizarEmail(request.email());

        // 1. Está bloqueado por excesso de tentativas? -> 429
        loginAttemptService.verificarBloqueio(email);

        // 2. Email e senha conferem? Se não existir OU a senha estiver errada -> MESMO erro (401),
        //    para não revelar a um atacante quais emails estão cadastrados.
        Usuario usuario = usuarioRepository.findByEmail(email)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPassword()))
                .orElse(null);

        if (usuario == null) {
            loginAttemptService.registrarFalha(email);
            throw new RegraNegocioException("Email ou senha inválidos", HttpStatus.UNAUTHORIZED);
        }

        // 3. Senha certa, mas o email não foi confirmado -> 403.
        //    (Checado DEPOIS da senha, para não dar essa informação a quem não sabe a senha.)
        if (!usuario.isEnabled()) {
            throw new RegraNegocioException(
                    "Email ainda não confirmado. Verifique sua caixa de entrada", HttpStatus.FORBIDDEN);
        }

        loginAttemptService.registrarSucesso(email);
        return gerarTokens(usuario);
    }

    // =====================================================================
    // REFRESH: troca um refresh token válido por um NOVO par de tokens
    //
    // noRollbackFor: normalmente uma exceção desfaz tudo o que foi feito na transação.
    // Aqui NÃO queremos isso: se for detectado reuso de token (possível roubo), o
    // RefreshTokenService revoga todos os tokens do usuário e DEPOIS lança a exceção.
    // Sem o noRollbackFor, essa revogação seria desfeita.
    // =====================================================================
    @Transactional(noRollbackFor = RegraNegocioException.class)
    public TokenResponse refresh(String refreshToken) {
        Usuario usuario = refreshTokenService.rotacionar(refreshToken);

        // O usuário pode ter sido desativado depois de logar
        if (!usuario.isEnabled()) {
            throw new RegraNegocioException("Conta desativada", HttpStatus.FORBIDDEN);
        }
        return gerarTokens(usuario);
    }

    // =====================================================================
    // LOGOUT
    // Revoga o refresh token: ele não serve mais para gerar novos access tokens.
    // O access token atual continua válido até expirar (no máximo 15 min), porque
    // JWT não fica guardado no servidor. O cliente deve apagá-lo da memória.
    // =====================================================================
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revogar(refreshToken);
    }

    // =====================================================================
    // ESQUECI A SENHA: envia um token de recuperação por email
    // =====================================================================
    @Transactional
    public MensagemResponse esqueciSenha(String emailInformado) {
        usuarioRepository.findByEmail(normalizarEmail(emailInformado)).ifPresent(usuario -> {
            String token = tokenVerificacaoService.criar(usuario, TipoToken.RECUPERACAO_SENHA, validadeRecuperacao);
            emailService.enviarRecuperacaoSenha(usuario.getEmail(), usuario.getName(), token);
        });
        // Sempre a mesma resposta, exista o email ou não
        return new MensagemResponse(MSG_EMAIL_GENERICA);
    }

    // =====================================================================
    // REDEFINIR SENHA (com o token recebido por email)
    // =====================================================================
    @Transactional
    public MensagemResponse redefinirSenha(RedefinirSenhaRequest request) {
        validarSenhasIguais(request.password(), request.passwordConfirmation());

        Usuario usuario = tokenVerificacaoService.consumir(request.token(), TipoToken.RECUPERACAO_SENHA);
        usuario.setPassword(passwordEncoder.encode(request.password()));

        // Quem recebeu o token no email provou que é dono do email -> pode ativar a conta
        usuario.setEnabled(true);

        // Segurança: desloga de TODOS os dispositivos (se alguém tinha a senha antiga, perde o acesso)
        refreshTokenService.revogarTodos(usuario);
        loginAttemptService.registrarSucesso(usuario.getEmail());

        return new MensagemResponse("Senha alterada! Faça login com a nova senha");
    }

    // ===================== métodos auxiliares (privados) =====================

    private TokenResponse gerarTokens(Usuario usuario) {
        String accessToken = tokenService.gerarAccessToken(usuario);
        String refreshToken = refreshTokenService.criar(usuario);
        return new TokenResponse(accessToken, refreshToken, "Bearer", tokenService.getExpirationSeconds());
    }

    private void enviarEmailConfirmacao(Usuario usuario) {
        String token = tokenVerificacaoService.criar(usuario, TipoToken.CONFIRMACAO_EMAIL, validadeConfirmacao);
        emailService.enviarConfirmacaoEmail(usuario.getEmail(), usuario.getName(), token);
    }

    private void validarSenhasIguais(String senha, String confirmacao) {
        if (!senha.equals(confirmacao)) {
            throw new RegraNegocioException("As senhas não coincidem", HttpStatus.BAD_REQUEST);
        }
    }

    // Evita "Bruno@X.com" e "bruno@x.com" serem tratados como contas diferentes
    private String normalizarEmail(String email) {
        return email.trim().toLowerCase();
    }
}

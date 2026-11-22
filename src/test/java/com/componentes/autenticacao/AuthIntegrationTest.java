package com.componentes.autenticacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.componentes.autenticacao.service.EmailService;
import com.jayway.jsonpath.JsonPath;

/**
 * TESTES DE INTEGRAÇÃO: sobem a aplicação inteira (com banco H2 em memória)
 * e fazem requisições HTTP "de mentira" com o MockMvc, sem abrir porta de rede.
 *
 * Testam o fluxo de ponta a ponta: controller -> service -> repository -> banco.
 *
 * Padrão de cada teste ("Given / When / Then" ou "Arrange / Act / Assert"):
 *   1. prepara o cenário   2. executa a ação   3. confere o resultado
 *
 * Cada teste usa um email diferente para não interferir nos outros
 * (o banco é compartilhado entre os testes desta classe).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired
    private MockMvc mvc;

    /**
     * @MockitoBean troca o EmailService verdadeiro por um "dublê" que não envia nada.
     * Depois perguntamos ao mock "você foi chamado? com quais argumentos?" e assim
     * pegamos o token que IRIA no email.
     */
    @MockitoBean
    private EmailService emailService;

    // ============================ CADASTRO ============================

    @Test
    void cadastroValido_retorna201_eEnviaEmailDeConfirmacao() throws Exception {
        cadastrar("Ana", "ana@teste.com", "12345678", "12345678")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensagem").exists());

        verify(emailService).enviarConfirmacaoEmail(eq("ana@teste.com"), eq("Ana"), anyString());
    }

    @Test
    void cadastroComDadosInvalidos_retorna400ComErroPorCampo() throws Exception {
        cadastrar("", "nao-e-email", "123", "123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").exists())
                .andExpect(jsonPath("$.email").exists())
                .andExpect(jsonPath("$.password").exists());
    }

    @Test
    void cadastroComSenhasDiferentes_retorna400() throws Exception {
        cadastrar("Bia", "bia@teste.com", "12345678", "87654321")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("As senhas não coincidem"));
    }

    @Test
    void cadastroComEmailRepetido_retorna409() throws Exception {
        cadastrar("Caio", "caio@teste.com", "12345678", "12345678").andExpect(status().isCreated());

        // Mesmo email com letras maiúsculas também conta como repetido
        cadastrar("Caio 2", "CAIO@teste.com", "12345678", "12345678")
                .andExpect(status().isConflict());
    }

    // ===================== CONFIRMAÇÃO DE EMAIL + LOGIN =====================

    @Test
    void loginAntesDeConfirmarEmail_retorna403() throws Exception {
        cadastrar("Duda", "duda@teste.com", "12345678", "12345678");

        login("duda@teste.com", "12345678").andExpect(status().isForbidden());
    }

    @Test
    void fluxoCompleto_cadastroConfirmacaoLoginEMe() throws Exception {
        criarUsuarioConfirmado("Edu", "edu@teste.com", "12345678");

        String json = login("edu@teste.com", "12345678")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();

        String accessToken = JsonPath.read(json, "$.accessToken");

        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("edu@teste.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist()); // senha NUNCA na resposta
    }

    @Test
    void tokenDeConfirmacaoSoPodeSerUsadoUmaVez() throws Exception {
        cadastrar("Fabi", "fabi@teste.com", "12345678", "12345678");
        String token = capturarTokenDeConfirmacao("fabi@teste.com");

        mvc.perform(get("/auth/confirmar-email").param("token", token)).andExpect(status().isOk());
        mvc.perform(get("/auth/confirmar-email").param("token", token)).andExpect(status().isBadRequest());
    }

    // ============================ LOGIN ============================

    @Test
    void loginComSenhaErradaOuEmailInexistente_retornaMesmoErro401() throws Exception {
        criarUsuarioConfirmado("Gabi", "gabi@teste.com", "12345678");

        login("gabi@teste.com", "senhaErrada").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("Email ou senha inválidos"));
        login("ninguem@teste.com", "12345678").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("Email ou senha inválidos"));
    }

    @Test
    void depoisDe5TentativasErradas_bloqueiaMesmoComSenhaCerta() throws Exception {
        criarUsuarioConfirmado("Hugo", "hugo@teste.com", "12345678");

        for (int i = 0; i < 5; i++) {
            login("hugo@teste.com", "errada" + i).andExpect(status().isUnauthorized());
        }

        login("hugo@teste.com", "12345678").andExpect(status().isTooManyRequests());
    }

    // ===================== ROTAS PROTEGIDAS =====================

    @Test
    void rotaProtegidaSemTokenOuComTokenAdulterado_retorna401() throws Exception {
        mvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/auth/me").header("Authorization", "Bearer token.falso.123"))
                .andExpect(status().isUnauthorized());
    }

    // ===================== REFRESH TOKEN E LOGOUT =====================

    @Test
    void refresh_geraNovosTokens_eTokenAntigoNaoFuncionaMais() throws Exception {
        criarUsuarioConfirmado("Iris", "iris@teste.com", "12345678");
        String refreshAntigo = refreshTokenDoLogin("iris@teste.com", "12345678");

        String json = refresh(refreshAntigo)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String refreshNovo = JsonPath.read(json, "$.refreshToken");
        assertThat(refreshNovo).isNotEqualTo(refreshAntigo);

        // Rotação: o antigo já foi usado e não vale mais
        refresh(refreshAntigo).andExpect(status().isUnauthorized());
    }

    @Test
    void reusoDeRefreshToken_revogaTodosOsTokensDoUsuario() throws Exception {
        criarUsuarioConfirmado("Joao", "joao@teste.com", "12345678");
        String refresh1 = refreshTokenDoLogin("joao@teste.com", "12345678");

        String json = refresh(refresh1).andReturn().getResponse().getContentAsString();
        String refresh2 = JsonPath.read(json, "$.refreshToken");

        // Alguém reusa o refresh1 (possível roubo) -> o sistema revoga tudo
        refresh(refresh1).andExpect(status().isUnauthorized());
        // ... inclusive o refresh2, que era válido
        refresh(refresh2).andExpect(status().isUnauthorized());
    }

    @Test
    void logout_invalidaORefreshToken() throws Exception {
        criarUsuarioConfirmado("Karla", "karla@teste.com", "12345678");
        String refreshToken = refreshTokenDoLogin("karla@teste.com", "12345678");

        mvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isNoContent());

        refresh(refreshToken).andExpect(status().isUnauthorized());
    }

    // ===================== RECUPERAÇÃO DE SENHA =====================

    @Test
    void esqueciSenha_redefinir_loginSoComSenhaNova() throws Exception {
        criarUsuarioConfirmado("Leo", "leo@teste.com", "senhaAntiga1");
        String refreshAntesDaTroca = refreshTokenDoLogin("leo@teste.com", "senhaAntiga1");

        postJson("/auth/esqueci-senha", "{\"email\":\"leo@teste.com\"}").andExpect(status().isOk());

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarRecuperacaoSenha(eq("leo@teste.com"), anyString(), captor.capture());
        String token = captor.getValue();

        postJson("/auth/redefinir-senha", """
                {"token":"%s","password":"senhaNova123","passwordConfirmation":"senhaNova123"}
                """.formatted(token)).andExpect(status().isOk());

        login("leo@teste.com", "senhaAntiga1").andExpect(status().isUnauthorized());
        login("leo@teste.com", "senhaNova123").andExpect(status().isOk());
        // Trocar a senha desloga de todos os dispositivos
        refresh(refreshAntesDaTroca).andExpect(status().isUnauthorized());
    }

    @Test
    void esqueciSenhaComEmailInexistente_respondeIgual_semRevelarNada() throws Exception {
        postJson("/auth/esqueci-senha", "{\"email\":\"fantasma@teste.com\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagem").exists());
    }

    // ===================== PERFIS (ROLES) =====================

    @Test
    void usuarioComum_naoAcessaRotaDeAdmin_403() throws Exception {
        criarUsuarioConfirmado("Mia", "mia@teste.com", "12345678");
        String accessToken = accessTokenDoLogin("mia@teste.com", "12345678");

        mvc.perform(get("/admin/usuarios").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_acessaRotaDeAdmin_200() throws Exception {
        // O admin é criado pelo AdminInicializador com os dados do application-test.properties
        String accessToken = accessTokenDoLogin("admin@teste.com", "admin12345");

        mvc.perform(get("/admin/usuarios").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ===================== JWKS (chave pública para outros serviços) =====================

    @Test
    void jwks_publicaChavePublica_semAPartePrivada() throws Exception {
        mvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                .andExpect(jsonPath("$.keys[0].kid").exists())
                .andExpect(jsonPath("$.keys[0].n").exists())          // parte pública (módulo)
                .andExpect(jsonPath("$.keys[0].d").doesNotExist())    // parte PRIVADA: nunca pode sair
                .andExpect(jsonPath("$.keys[0].p").doesNotExist());
    }

    // ===================== métodos auxiliares =====================

    private ResultActions postJson(String url, String json) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions cadastrar(String nome, String email, String senha, String confirmacao) throws Exception {
        return postJson("/auth/cadastro", """
                {"name":"%s","email":"%s","password":"%s","passwordConfirmation":"%s"}
                """.formatted(nome, email, senha, confirmacao));
    }

    private ResultActions login(String email, String senha) throws Exception {
        return postJson("/auth/login", """
                {"email":"%s","password":"%s"}
                """.formatted(email, senha));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return postJson("/auth/refresh", "{\"refreshToken\":\"" + refreshToken + "\"}");
    }

    /** Pega o token que o AuthService passou para o EmailService (que é um mock). */
    private String capturarTokenDeConfirmacao(String email) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarConfirmacaoEmail(eq(email), anyString(), captor.capture());
        return captor.getValue();
    }

    /** Cadastra e já confirma o email, simulando o clique no link. */
    private void criarUsuarioConfirmado(String nome, String email, String senha) throws Exception {
        cadastrar(nome, email, senha, senha).andExpect(status().isCreated());
        String token = capturarTokenDeConfirmacao(email);
        mvc.perform(get("/auth/confirmar-email").param("token", token)).andExpect(status().isOk());
    }

    private String accessTokenDoLogin(String email, String senha) throws Exception {
        String json = login(email, senha).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.accessToken");
    }

    private String refreshTokenDoLogin(String email, String senha) throws Exception {
        String json = login(email, senha).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.refreshToken");
    }
}

package com.componentes.autenticacao.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.componentes.autenticacao.model.Role;
import com.componentes.autenticacao.model.Usuario;
import com.componentes.autenticacao.repository.UsuarioRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Cria o PRIMEIRO administrador quando a aplicação sobe.
 *
 * Problema que resolve: o cadastro público sempre cria USER. Então como nasce o
 * primeiro ADMIN? Por configuração: se app.admin.email e app.admin.password
 * estiverem preenchidos e esse usuário ainda não existir, ele é criado aqui.
 *
 * ApplicationRunner = código que o Spring executa uma vez, logo depois de subir.
 * @Slf4j (Lombok) cria o campo "log" para escrever no console.
 */
@Slf4j
@Component
public class AdminInicializador implements ApplicationRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String senha;

    public AdminInicializador(UsuarioRepository usuarioRepository,
                              PasswordEncoder passwordEncoder,
                              @Value("${app.admin.email}") String email,
                              @Value("${app.admin.password}") String senha) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email.trim().toLowerCase();
        this.senha = senha;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() || senha.isBlank()) {
            return; // nada configurado: não cria admin
        }
        if (usuarioRepository.existsByEmail(email)) {
            return; // já existe: não faz nada (não sobrescreve a senha)
        }

        Usuario admin = new Usuario("Administrador", email, passwordEncoder.encode(senha));
        admin.setRole(Role.ADMIN);
        admin.setEnabled(true); // admin não precisa confirmar email
        usuarioRepository.save(admin);

        log.info("Usuário ADMIN criado: {}", email);
    }
}

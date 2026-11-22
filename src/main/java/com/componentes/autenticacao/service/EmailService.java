package com.componentes.autenticacao.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Responsabilidade ÚNICA: montar e enviar os emails.
 *
 * O JavaMailSender é criado pelo Spring Boot a partir das propriedades spring.mail.*
 * Em dev, os emails vão para o Mailpit (veja em http://localhost:8025).
 * Nos testes, esta classe inteira é substituída por um mock (nada é enviado).
 */
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String remetente;
    private final String appUrl;

    public EmailService(JavaMailSender mailSender,
                        @Value("${app.email.remetente}") String remetente,
                        @Value("${app.url}") String appUrl) {
        this.mailSender = mailSender;
        this.remetente = remetente;
        this.appUrl = appUrl;
    }

    public void enviarConfirmacaoEmail(String para, String nome, String token) {
        String link = appUrl + "/auth/confirmar-email?token=" + token;
        enviar(para, "Confirme seu email", """
                Olá, %s!

                Para ativar sua conta, acesse o link abaixo:
                %s

                O link expira em 24 horas. Se você não fez este cadastro, ignore este email.
                """.formatted(nome, link));
    }

    public void enviarRecuperacaoSenha(String para, String nome, String token) {
        // Num sistema com frontend, aqui iria um link para a TELA de nova senha
        // (ex.: https://meusite.com/redefinir-senha?token=...), e essa tela chamaria a API.
        // Como ainda não há frontend, enviamos o token para usar direto na API.
        enviar(para, "Recuperação de senha", """
                Olá, %s!

                Recebemos um pedido para redefinir sua senha.
                Use o token abaixo em POST %s/auth/redefinir-senha:

                %s

                O token expira em 30 minutos. Se você não pediu, ignore este email:
                sua senha continua a mesma.
                """.formatted(nome, appUrl, token));
    }

    private void enviar(String para, String assunto, String texto) {
        SimpleMailMessage mensagem = new SimpleMailMessage();
        mensagem.setFrom(remetente);
        mensagem.setTo(para);
        mensagem.setSubject(assunto);
        mensagem.setText(texto);
        mailSender.send(mensagem);
    }
}

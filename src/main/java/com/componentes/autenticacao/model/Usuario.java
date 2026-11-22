package com.componentes.autenticacao.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * MODEL / ENTITY: representa a tabela "usuarios" no banco.
 *
 * Responsabilidade: apenas guardar os dados do usuário.
 * NÃO tem regra de negócio, NÃO tem passwordConfirmation (isso é coisa do formulário/DTO).
 *
 * @Getter/@Setter (Lombok) geram os getters e setters na compilação,
 * equivalentes aos que você escreveria à mão.
 */
@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor // construtor vazio: obrigatório para o JPA/Hibernate
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // o banco gera o id
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true) // não permite dois usuários com o mesmo email
    private String email;

    @Column(nullable = false)
    private String password; // SEMPRE o hash (BCrypt), nunca a senha em texto puro

    // Perfil de acesso. EnumType.STRING salva "USER"/"ADMIN" no banco
    // (o padrão salvaria 0/1, que quebra se você mudar a ordem do enum).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    // false até o usuário clicar no link de confirmação enviado por email.
    // Enquanto for false, o login é recusado.
    @Column(nullable = false)
    private boolean enabled = false;

    // Construtor usado por NÓS no service (sem id, porque quem gera é o banco)
    public Usuario(String name, String email, String passwordHash) {
        this.name = name;
        this.email = email;
        this.password = passwordHash;
    }
}

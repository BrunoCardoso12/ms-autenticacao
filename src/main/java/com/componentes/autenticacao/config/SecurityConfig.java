package com.componentes.autenticacao.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuração do Spring Security.
 *
 * Como o projeto tem o Spring Security, POR PADRÃO TODOS os endpoints exigem autenticação.
 * Aqui dizemos quais são públicos, quais exigem ADMIN, e que o resto exige um JWT válido.
 *
 * @EnableMethodSecurity liga o uso de @PreAuthorize nos controllers/services.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    // Lido do application.properties (app.cors.allowed-origins), separado por vírgula
    @Value("${app.cors.allowed-origins}")
    private String corsAllowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
                // Liga o CORS com as regras do método corsConfigurationSource() (mais abaixo)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // API REST sem sessão/cookies -> CSRF não se aplica
                .csrf(csrf -> csrf.disable())
                // Não guarda sessão no servidor: cada requisição se autentica pelo próprio token
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // A ORDEM IMPORTA: a primeira regra que combinar com a URL é a que vale
                        .requestMatchers(
                                "/auth/cadastro",
                                "/auth/confirmar-email",
                                "/auth/reenviar-confirmacao",
                                "/auth/login",
                                "/auth/refresh",
                                "/auth/logout",
                                "/auth/esqueci-senha",
                                "/auth/redefinir-senha",
                                "/.well-known/jwks.json", // chave PÚBLICA para outros serviços validarem tokens
                                "/actuator/health/**", // health check (usado pelo Docker/infraestrutura)
                                "/error" // página de erro interna do Spring
                        ).permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                // Liga a validação de JWT: lê o header "Authorization: Bearer <token>",
                // valida com o JwtDecoder (JwtConfig) e transforma as claims em permissões.
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    /**
     * Ensina o Spring a ler os perfis de dentro do token.
     *
     * Nosso token tem:  "roles": ["ADMIN"]
     * O Spring espera:  autoridade "ROLE_ADMIN"   (é isso que hasRole("ADMIN") procura)
     *
     * Então: lê a claim "roles" e adiciona o prefixo "ROLE_".
     */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /**
     * CORS (Cross-Origin Resource Sharing).
     *
     * O NAVEGADOR bloqueia, por segurança, que um site (ex.: https://meusite.com) chame
     * uma API em OUTRO domínio (ex.: https://api.meusite.com), a não ser que a API diga
     * "esse site pode". É isso que esta configuração faz.
     *
     * Obs.: CORS só vale para navegadores. Postman, curl e outros backends não são afetados.
     */
    private CorsConfigurationSource corsConfigurationSource() {
        List<String> origens = Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .filter(o -> !o.isEmpty())
                .toList();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origens); // lista vazia = nenhum site liberado
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(3600L); // o navegador guarda essa permissão por 1 hora

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Bean do BCrypt. Por ser um @Bean, o Spring consegue injetá-lo
     * em qualquer classe que peça um PasswordEncoder.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

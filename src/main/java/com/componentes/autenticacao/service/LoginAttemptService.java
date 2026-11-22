package com.componentes.autenticacao.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.componentes.autenticacao.exception.RegraNegocioException;

/**
 * Proteção contra FORÇA BRUTA: depois de N senhas erradas seguidas para o mesmo email,
 * bloqueia novas tentativas por X minutos (valores no application.properties).
 *
 * LIMITAÇÃO (importante saber):
 * Os contadores ficam na MEMÓRIA desta instância. Se a aplicação reiniciar, zeram.
 * Se você rodar 2+ instâncias do microsserviço, cada uma conta separado.
 * Em produção com várias instâncias, o comum é guardar isso num Redis
 * ou fazer o limite no API Gateway.
 */
@Service
public class LoginAttemptService {

    // Estado de cada email: quantas falhas seguidas e até quando está bloqueado
    private record Tentativas(int falhas, Instant bloqueadoAte) {
    }

    // ConcurrentHashMap: seguro para várias requisições ao mesmo tempo (várias threads)
    private final Map<String, Tentativas> porEmail = new ConcurrentHashMap<>();

    private final int maxTentativas;
    private final Duration tempoBloqueio;

    public LoginAttemptService(@Value("${app.login.max-tentativas}") int maxTentativas,
                               @Value("${app.login.bloqueio-minutos}") long bloqueioMinutos) {
        this.maxTentativas = maxTentativas;
        this.tempoBloqueio = Duration.ofMinutes(bloqueioMinutos);
    }

    /** Lança 429 (Too Many Requests) se o email estiver bloqueado. */
    public void verificarBloqueio(String email) {
        Tentativas t = porEmail.get(email);
        if (t != null && t.bloqueadoAte() != null && Instant.now().isBefore(t.bloqueadoAte())) {
            long minutos = Math.max(1, Duration.between(Instant.now(), t.bloqueadoAte()).toMinutes());
            throw new RegraNegocioException(
                    "Muitas tentativas de login. Tente novamente em " + minutos + " minuto(s)",
                    HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    public void registrarFalha(String email) {
        // compute: lê e atualiza a entrada de forma atômica (sem condição de corrida)
        porEmail.compute(email, (chave, atual) -> {
            int falhas = (atual == null ? 0 : atual.falhas()) + 1;
            if (falhas >= maxTentativas) {
                return new Tentativas(0, Instant.now().plus(tempoBloqueio)); // bloqueia e zera o contador
            }
            return new Tentativas(falhas, null);
        });
    }

    public void registrarSucesso(String email) {
        porEmail.remove(email);
    }
}

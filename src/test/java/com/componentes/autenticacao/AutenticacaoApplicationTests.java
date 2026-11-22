package com.componentes.autenticacao;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Teste mais básico: a aplicação consegue SUBIR? (todos os beans são criados sem erro)
 * @ActiveProfiles("test") -> usa o application-test.properties (H2 em memória, sem Docker).
 */
@SpringBootTest
@ActiveProfiles("test")
class AutenticacaoApplicationTests {

	@Test
	void contextLoads() {
	}

}

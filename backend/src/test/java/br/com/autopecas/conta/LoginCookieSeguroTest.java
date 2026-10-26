package br.com.autopecas.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.autopecas.ApoioDeTeste;
import br.com.autopecas.TesteDeIntegracao;
import br.com.autopecas.conta.internal.domain.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Em produção o cookie só trafega por HTTPS (app.seguranca.cookie-seguro=true, o padrão do application.properties). */
@TesteDeIntegracao
@TestPropertySource(properties = "app.seguranca.cookie-seguro=true")
class LoginCookieSeguroTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApoioDeTeste apoio;

    @Test
    void oCookieDeSessaoVemMarcadoComoSecure() throws Exception {
        Usuario usuario = apoio.usuario();

        String cabecalho = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "senha": "%s"}"""
                                .formatted(usuario.getEmail(), ApoioDeTeste.SENHA)))
                .andReturn()
                .getResponse()
                .getHeader("Set-Cookie");

        assertThat(cabecalho).contains("Secure").contains("HttpOnly").contains("SameSite=Lax");
    }
}

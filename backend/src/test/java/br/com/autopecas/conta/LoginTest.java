package br.com.autopecas.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.autopecas.ApoioDeTeste;
import br.com.autopecas.TesteDeIntegracao;
import br.com.autopecas.conta.internal.domain.StatusVendedor;
import br.com.autopecas.conta.internal.domain.Usuario;
import br.com.autopecas.conta.internal.infra.seguranca.CookieDeSessao;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@TesteDeIntegracao
class LoginTest {

    private static final String ROTA = "/api/v1/auth/login";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApoioDeTeste apoio;

    @Autowired
    JwtDecoder decodificador;

    private static String json(String email, String senha) {
        return """
                {"email": "%s", "senha": "%s"}""".formatted(email, senha);
    }

    private MockHttpServletRequestBuilder login(String email, String senha) {
        return post(ROTA).contentType(MediaType.APPLICATION_JSON).content(json(email, senha));
    }

    private static String setCookie(ResultActions resultado) {
        return resultado.andReturn().getResponse().getHeader("Set-Cookie");
    }

    @Test
    void loginCorretoDevolveASessaoComOAuthId() throws Exception {
        Usuario usuario = apoio.usuario();

        mockMvc.perform(login(usuario.getEmail(), ApoioDeTeste.SENHA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(usuario.getId().toString()))
                .andExpect(jsonPath("$.nome").value("Usuário de Teste"))
                .andExpect(jsonPath("$.email").value(usuario.getEmail()))
                .andExpect(jsonPath("$.vendedor").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.authId").value(apoio.authIdDe(usuario)))
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.senhaHash").doesNotExist());
    }

    @Test
    void oCookieDeSessaoEHttpOnlySameSiteLaxEValeUmaHora() throws Exception {
        Usuario usuario = apoio.usuario();

        String cabecalho = setCookie(mockMvc.perform(login(usuario.getEmail(), ApoioDeTeste.SENHA)));

        assertThat(cabecalho)
                .startsWith(CookieDeSessao.NOME + "=")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .contains("Path=/")
                .contains("Max-Age=3600")
                .doesNotContain("Secure");
    }

    @Test
    void oJwtDoCookieIdentificaOUsuario() throws Exception {
        Usuario usuario = apoio.usuario();

        MockHttpServletResponse resposta = mockMvc.perform(login(usuario.getEmail(), ApoioDeTeste.SENHA))
                .andReturn()
                .getResponse();

        Cookie cookie = resposta.getCookie(CookieDeSessao.NOME);
        assertThat(cookie).isNotNull();
        Jwt jwt = decodificador.decode(cookie.getValue());
        assertThat(jwt.getSubject()).isEqualTo(usuario.getId().toString());
        assertThat(jwt.getId()).isNotBlank();
    }

    @Test
    void cadaLoginGeraUmTokenDiferente() throws Exception {
        Usuario usuario = apoio.usuario();

        Cookie primeiro = mockMvc.perform(login(usuario.getEmail(), ApoioDeTeste.SENHA))
                .andReturn()
                .getResponse()
                .getCookie(CookieDeSessao.NOME);
        Cookie segundo = mockMvc.perform(login(usuario.getEmail(), ApoioDeTeste.SENHA))
                .andReturn()
                .getResponse()
                .getCookie(CookieDeSessao.NOME);

        assertThat(primeiro.getValue()).isNotEqualTo(segundo.getValue());
    }

    @Test
    void oLoginDoVendedorTrazOStatusDoPerfil() throws Exception {
        Usuario vendedor = apoio.vendedor(StatusVendedor.APROVADO);

        mockMvc.perform(login(vendedor.getEmail(), ApoioDeTeste.SENHA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendedor.status").value("APROVADO"));
    }

    @Test
    void oEmailNaoDependeDeCaixaNemDeEspacosNasPontas() throws Exception {
        Usuario usuario = apoio.usuario();

        mockMvc.perform(login("  " + usuario.getEmail().toUpperCase() + "  ", ApoioDeTeste.SENHA))
                .andExpect(status().isOk());
    }

    @Test
    void senhaErradaEEmailInexistenteDaoARespostaIdentica() throws Exception {
        Usuario usuario = apoio.usuario();

        String senhaErrada = mockMvc.perform(login(usuario.getEmail(), "senha-errada"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("E-mail ou senha inválidos."))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String emailInexistente = mockMvc.perform(login(apoio.emailUnico(), ApoioDeTeste.SENHA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("E-mail ou senha inválidos."))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(senhaErrada).isEqualTo(emailInexistente);
    }

    @Test
    void senhaComMaisDe72BytesDa401EmVezDeErroInterno() throws Exception {
        Usuario usuario = apoio.usuario();

        mockMvc.perform(login(usuario.getEmail(), "a".repeat(200)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("E-mail ou senha inválidos."));
    }

    @Test
    void camposEmBrancoDao400() throws Exception {
        mockMvc.perform(login("", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Dados inválidos"))
                .andExpect(jsonPath("$.erros.email").isNotEmpty())
                .andExpect(jsonPath("$.erros.senha").isNotEmpty());
    }

    @Test
    void umCookieAntigoOuInvalidoNaoImpedeOLogin() throws Exception {
        Usuario usuario = apoio.usuario();

        mockMvc.perform(login(usuario.getEmail(), ApoioDeTeste.SENHA).cookie(new Cookie(CookieDeSessao.NOME, "lixo")))
                .andExpect(status().isOk());
    }

    @Test
    void umCookieInvalidoTambemNaoImpedeOCadastro() throws Exception {
        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie(CookieDeSessao.NOME, "lixo"))
                        .content("""
                                {"nome": "Ana", "email": "%s", "senha": "senha-valida-1"}"""
                                .formatted(apoio.emailUnico())))
                .andExpect(status().isCreated());
    }
}

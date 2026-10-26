package br.com.autopecas.conta;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.autopecas.ApoioDeTeste;
import br.com.autopecas.TesteDeIntegracao;
import br.com.autopecas.conta.internal.domain.StatusVendedor;
import br.com.autopecas.conta.internal.domain.Usuario;
import br.com.autopecas.conta.internal.infra.persistencia.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@TesteDeIntegracao
class MeTest {

    private static final String ROTA = "/api/v1/me";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApoioDeTeste apoio;

    @Autowired
    UsuarioRepository usuarios;

    @Test
    void oCookieSozinhoBastaEDevolveOAuthIdParaOFrontend() throws Exception {
        Usuario usuario = apoio.usuario();

        mockMvc.perform(get(ROTA).cookie(apoio.cookieDe(usuario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(usuario.getId().toString()))
                .andExpect(jsonPath("$.nome").value(usuario.getNome()))
                .andExpect(jsonPath("$.email").value(usuario.getEmail()))
                .andExpect(jsonPath("$.vendedor").value(nullValue()))
                .andExpect(jsonPath("$.authId").value(apoio.authIdDe(usuario)))
                .andExpect(jsonPath("$.senhaHash").doesNotExist());
    }

    @ParameterizedTest
    @EnumSource(StatusVendedor.class)
    void oPerfilDeVendedorApareceComOStatus(StatusVendedor status) throws Exception {
        Usuario vendedor = apoio.vendedor(status);

        mockMvc.perform(get(ROTA).cookie(apoio.cookieDe(vendedor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendedor.status").value(status.name()));
    }

    @Test
    void semCookieENaoAutenticado() throws Exception {
        mockMvc.perform(get(ROTA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Não autenticado"));
    }

    @Test
    void umUsuarioRemovidoNaoTemMaisSessaoMesmoComTokenValido() throws Exception {
        Usuario usuario = apoio.usuario();
        var cookie = apoio.cookieDe(usuario);
        usuarios.deleteById(usuario.getId());
        usuarios.flush();

        mockMvc.perform(get(ROTA).cookie(cookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Sessão inválida."));
    }

    @Test
    void soOGetDeMeDispensaOAuthId() throws Exception {
        Usuario usuario = apoio.usuario();

        // Outros métodos na mesma rota continuam exigindo o X-Auth-Id (aqui cai em 401 antes do 405).
        mockMvc.perform(post(ROTA).cookie(apoio.cookieDe(usuario))).andExpect(status().isUnauthorized());
    }
}

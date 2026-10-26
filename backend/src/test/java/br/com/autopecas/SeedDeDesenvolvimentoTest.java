package br.com.autopecas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Com o contexto "dev" do Liquibase, o banco vem com os usuários e as notificações que o frontend usa no mock. */
@TesteDeIntegracao
@TestPropertySource(properties = "spring.liquibase.contexts=dev")
class SeedDeDesenvolvimentoTest {

    private static final String SENHA_DE_DESENVOLVIMENTO = "senha-dev-123";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    private Cookie entrar(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "senha": "%s"}"""
                                .formatted(email, SENHA_DE_DESENVOLVIMENTO)))
                .andReturn()
                .getResponse()
                .getCookie("access_token");
    }

    @Test
    void aSeedFoiAplicadaNoContextoDev() {
        List<String> aplicados =
                jdbc.queryForList("select id from databasechangelog order by orderexecuted", String.class);

        assertThat(aplicados).contains("900-seed-dev");
    }

    @Test
    void osTresUsuariosDeDesenvolvimentoConseguemEntrarEVeemOProprioPerfil() throws Exception {
        mockMvc.perform(get("/api/v1/me").cookie(entrar("vynicyus@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Vynicyus Silva"))
                .andExpect(jsonPath("$.vendedor.status").value("APROVADO"));
        mockMvc.perform(get("/api/v1/me").cookie(entrar("ana@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendedor").value(nullValue()));
        mockMvc.perform(get("/api/v1/me").cookie(entrar("pedro@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendedor.status").value("PENDENTE"));
    }

    @Test
    void asNotificacoesDoVendedorBatemComOMockDoFrontend() throws Exception {
        Cookie cookie = entrar("vynicyus@example.com");
        String authId = mockMvc.perform(get("/api/v1/me").cookie(cookie))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"authId\":\"([0-9a-f]+)\".*", "$1");

        mockMvc.perform(get("/api/v1/notificacoes")
                        .param("tipos", "CHAT,PROPOSTA,SISTEMA")
                        .param("limite", "20")
                        .cookie(cookie)
                        .header("X-Auth-Id", authId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(5)))
                .andExpect(jsonPath("$.itens[0].titulo").value("Auto Peças Silva"))
                .andExpect(jsonPath("$.naoLidas.chat").value(2))
                .andExpect(jsonPath("$.naoLidas.outras").value(1))
                .andExpect(jsonPath("$.naoLidas.pecasAProcura").value(2));
    }
}

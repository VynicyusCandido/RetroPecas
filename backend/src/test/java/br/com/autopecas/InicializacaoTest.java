package br.com.autopecas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@TesteDeIntegracao
class InicializacaoTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void aSaudeDaAplicacaoFicaDisponivelSemAutenticacao() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void oLiquibaseAplicouOsChangelogsSemASeedDeDesenvolvimento() {
        List<String> aplicados =
                jdbc.queryForList("select id from databasechangelog order by orderexecuted", String.class);

        assertThat(aplicados)
                .containsExactly(
                        "001-criar-usuario",
                        "002-criar-perfil-vendedor",
                        "003-criar-token-revogado",
                        "004-criar-notificacao");
    }
}

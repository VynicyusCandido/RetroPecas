package br.com.autopecas;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Properties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

/**
 * Os padrões de produção vivem só em application.properties; os testes de integração usam o perfil "test" e nunca
 * os veem. Sem este teste, apagar uma linha (ou trocá-la por um valor de desenvolvimento) passaria despercebido:
 * a produção rodaria a seed com senha pública, emitiria cookie sem Secure ou subiria com segredo padrão.
 */
class ConfiguracaoDeProducaoTest {

    private static Properties producao;

    @BeforeAll
    static void carregarOApplicationPropertiesDeProducao() throws IOException {
        ClassPathResource arquivo = new ClassPathResource("application.properties");
        assertThat(arquivo.getURL().toString())
                .as("tem de ser o arquivo principal, não um de src/test/resources")
                .doesNotContain("test-classes");
        producao = PropertiesLoaderUtils.loadProperties(new EncodedResource(arquivo, UTF_8));
    }

    @Test
    void oLiquibaseSoRodaOsChangesetsDeProducaoPorPadrao() {
        // Sem contexto o Liquibase roda todos os changesets, inclusive a seed de desenvolvimento.
        assertThat(producao.getProperty("spring.liquibase.contexts")).isEqualTo("producao");
    }

    @Test
    void oCookieDeSessaoESecurePorPadrao() {
        assertThat(producao.getProperty("app.seguranca.cookie-seguro")).isEqualTo("true");
    }

    @Test
    void oSegredoDoJwtVemSoDaVariavelDeAmbienteSemValorPadrao() {
        assertThat(producao.getProperty("app.seguranca.jwt-segredo")).isEqualTo("${JWT_SEGREDO}");
    }

    @Test
    void oSegredoDoAuthIdVemSoDaVariavelDeAmbienteSemValorPadrao() {
        assertThat(producao.getProperty("app.seguranca.auth-id-segredo")).isEqualTo("${AUTH_ID_SEGREDO}");
    }
}

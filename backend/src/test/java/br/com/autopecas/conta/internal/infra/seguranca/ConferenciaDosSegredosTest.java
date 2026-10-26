package br.com.autopecas.conta.internal.infra.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * A conferência tem de rodar na subida de verdade: um método que ninguém chama não protege nada. Sobe só as
 * propriedades de segurança e a conferência, num contexto do Spring, sem banco.
 */
class ConferenciaDosSegredosTest {

    private static final String SEGREDO_A = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String SEGREDO_B = Base64.getEncoder().encodeToString(new byte[] {
        1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30,
        31, 32
    });

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SegurancaProperties.class)
    static class Propriedades {}

    private final ApplicationContextRunner contexto =
            new ApplicationContextRunner().withUserConfiguration(Propriedades.class, ConferenciaDosSegredos.class);

    @Test
    void aAplicacaoNaoSobeComOMesmoSegredoNoJwtENoAuthId() {
        contexto.withPropertyValues(
                        "app.seguranca.jwt-segredo=" + SEGREDO_A,
                        "app.seguranca.auth-id-segredo=" + SEGREDO_A,
                        "app.seguranca.token-validade=PT1H")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("diferentes")
                            .hasMessageNotContaining(SEGREDO_A);
                });
    }

    @Test
    void aAplicacaoSobeComSegredosDiferentes() {
        contexto.withPropertyValues(
                        "app.seguranca.jwt-segredo=" + SEGREDO_A,
                        "app.seguranca.auth-id-segredo=" + SEGREDO_B,
                        "app.seguranca.token-validade=PT1H")
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }
}

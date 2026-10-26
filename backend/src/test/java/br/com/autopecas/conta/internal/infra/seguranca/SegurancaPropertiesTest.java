package br.com.autopecas.conta.internal.infra.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class SegurancaPropertiesTest {

    private static final String SEGREDO_DE_32_BYTES = Base64.getEncoder().encodeToString(new byte[32]);

    private static SegurancaProperties com(String jwtSegredo, String authIdSegredo) {
        return new SegurancaProperties(jwtSegredo, authIdSegredo, Duration.ofHours(1), false);
    }

    private static SegurancaProperties ligadaAPartirDe(Map<String, String> extras) {
        Map<String, String> propriedades = new HashMap<>(Map.of(
                "app.seguranca.jwt-segredo", SEGREDO_DE_32_BYTES,
                "app.seguranca.auth-id-segredo", SEGREDO_DE_32_BYTES,
                "app.seguranca.token-validade", "PT1H"));
        propriedades.putAll(extras);
        return new Binder(new MapConfigurationPropertySource(propriedades))
                .bind("app.seguranca", SegurancaProperties.class)
                .get();
    }

    @Test
    void semAPropriedadeOCookieSeguroVemLigado() {
        // O padrão do Java para boolean é false: sem @DefaultValue("true"), esquecer a propriedade
        // emitiria cookies sem Secure.
        assertThat(ligadaAPartirDe(Map.of()).cookieSeguro()).isTrue();
    }

    @Test
    void apenasUmaPropriedadeExplicitaDesligaOCookieSeguro() {
        assertThat(ligadaAPartirDe(Map.of("app.seguranca.cookie-seguro", "false")).cookieSeguro())
                .isFalse();
    }

    @Test
    void aceitaSegredosDe32BytesEmBase64() {
        SegurancaProperties propriedades = com(SEGREDO_DE_32_BYTES, SEGREDO_DE_32_BYTES);

        assertThat(propriedades.jwtChave()).hasSize(32);
        assertThat(propriedades.authIdChave()).hasSize(32);
    }

    @Test
    void recusaOMesmoSegredoNoJwtENoAuthId() {
        assertThatThrownBy(() -> com(SEGREDO_DE_32_BYTES, SEGREDO_DE_32_BYTES).exigirSegredosDistintos())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt-segredo")
                .hasMessageContaining("auth-id-segredo")
                .hasMessageContaining("diferentes")
                .hasMessageNotContaining(SEGREDO_DE_32_BYTES);
    }

    @Test
    void recusaSegredosComOsMesmosBytesMesmoQueOTextoEmBase64SejaDiferente() {
        // O último caractere antes do "=" carrega 2 bits sem uso: os dois textos decodificam para os mesmos 32 bytes.
        String outroTexto = SEGREDO_DE_32_BYTES.substring(0, SEGREDO_DE_32_BYTES.length() - 2) + "B=";
        assertThat(outroTexto).isNotEqualTo(SEGREDO_DE_32_BYTES);
        assertThat(com(outroTexto, outroTexto).jwtChave()).isEqualTo(com(SEGREDO_DE_32_BYTES, outroTexto).jwtChave());

        assertThatThrownBy(() -> com(SEGREDO_DE_32_BYTES, outroTexto).exigirSegredosDistintos())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("diferentes");
    }

    @Test
    void aceitaSegredosDiferentes() {
        String outro = Base64.getEncoder().encodeToString(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
            16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32});

        assertThatCode(() -> com(SEGREDO_DE_32_BYTES, outro).exigirSegredosDistintos())
                .doesNotThrowAnyException();
    }

    @Test
    void recusaOSegredoDoJwtComMenosDe32Bytes() {
        String curto = Base64.getEncoder().encodeToString(new byte[31]);

        assertThatThrownBy(() -> com(curto, SEGREDO_DE_32_BYTES).jwtChave())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt-segredo")
                .hasMessageContaining("32 bytes");
    }

    @Test
    void recusaOSegredoDoAuthIdComMenosDe32Bytes() {
        String curto = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> com(SEGREDO_DE_32_BYTES, curto).authIdChave())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth-id-segredo")
                .hasMessageContaining("32 bytes");
    }

    @Test
    void recusaSegredoQueNaoEstaEmBase64() {
        assertThatThrownBy(() -> com("isto não é base64!!", SEGREDO_DE_32_BYTES).jwtChave())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("base64");
    }

    @Test
    void aAplicacaoNaoCriaOEncoderDeJwtComSegredoCurto() {
        String curto = Base64.getEncoder().encodeToString(new byte[8]);

        assertThatThrownBy(() -> new ConfiguracaoDeSeguranca().codificadorDeJwt(com(curto, SEGREDO_DE_32_BYTES)))
                .isInstanceOf(IllegalStateException.class);
    }
}

package br.com.autopecas.conta.internal.infra.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CalculadoraDeAuthIdHmacTest {

    private static final UUID USUARIO = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OUTRO_USUARIO = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static final String SEGREDO_UM = segredoDeBytesIguais(1);
    private static final String SEGREDO_DOIS = segredoDeBytesIguais(2);

    private static String segredoDeBytesIguais(int valor) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte) valor);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static CalculadoraDeAuthIdHmac calculadora(String authIdSegredo) {
        return new CalculadoraDeAuthIdHmac(
                new SegurancaProperties(SEGREDO_UM, authIdSegredo, Duration.ofHours(1), false));
    }

    @Test
    void calculaOHmacSha256EmHexadecimalDoIdDoUsuario() {
        // Valor de referência calculado à parte, com o HMAC da biblioteca padrão do Python.
        assertThat(calculadora(SEGREDO_UM).calcular(USUARIO))
                .isEqualTo("c253eb93bd51cef621f8c718edb24abef97764b3022cb631f484ef6264855249");
    }

    @Test
    void semprePodeSerLidoComoSessentaEQuatroHexadecimaisMinusculos() {
        assertThat(calculadora(SEGREDO_UM).calcular(UUID.randomUUID())).matches("[0-9a-f]{64}");
    }

    @Test
    void naoEOSha256SimplesDoId() {
        String sha256DoId = "7ac1b8d7010bb6cd3a3e84e7f90136b880bbc899e428ece49333372911ab9052";

        assertThat(calculadora(SEGREDO_UM).calcular(USUARIO)).isNotEqualTo(sha256DoId);
    }

    @Test
    void usuariosDiferentesTemHashesDiferentes() {
        CalculadoraDeAuthIdHmac calculadora = calculadora(SEGREDO_UM);

        assertThat(calculadora.calcular(USUARIO)).isNotEqualTo(calculadora.calcular(OUTRO_USUARIO));
    }

    @Test
    void segredosDiferentesGeramHashesDiferentesParaOMesmoUsuario() {
        assertThat(calculadora(SEGREDO_UM).calcular(USUARIO)).isNotEqualTo(calculadora(SEGREDO_DOIS).calcular(USUARIO));
    }

    @Test
    void oMesmoUsuarioEOMesmoSegredoSempreDaOMesmoHash() {
        assertThat(calculadora(SEGREDO_UM).calcular(USUARIO)).isEqualTo(calculadora(SEGREDO_UM).calcular(USUARIO));
    }

    @Test
    void confereAceitaOHashDoProprioUsuario() {
        CalculadoraDeAuthIdHmac calculadora = calculadora(SEGREDO_UM);

        assertThat(calculadora.confere(USUARIO, calculadora.calcular(USUARIO))).isTrue();
    }

    @Test
    void confereRecusaOHashDeOutroUsuario() {
        CalculadoraDeAuthIdHmac calculadora = calculadora(SEGREDO_UM);

        assertThat(calculadora.confere(USUARIO, calculadora.calcular(OUTRO_USUARIO))).isFalse();
    }

    @Test
    void confereRecusaOHashCalculadoComOutroSegredo() {
        String forjado = calculadora(SEGREDO_DOIS).calcular(USUARIO);

        assertThat(calculadora(SEGREDO_UM).confere(USUARIO, forjado)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "abc", "00000000-0000-0000-0000-000000000001"})
    void confereRecusaValoresQueNaoSaoOHash(String informado) {
        assertThat(calculadora(SEGREDO_UM).confere(USUARIO, informado)).isFalse();
    }

    @Test
    void confereRecusaOHashAlteradoDeQualquerJeito() {
        CalculadoraDeAuthIdHmac calculadora = calculadora(SEGREDO_UM);
        String hash = calculadora.calcular(USUARIO);

        assertThat(calculadora.confere(USUARIO, hash.toUpperCase())).isFalse();
        assertThat(calculadora.confere(USUARIO, hash + " ")).isFalse();
        assertThat(calculadora.confere(USUARIO, " " + hash)).isFalse();
        assertThat(calculadora.confere(USUARIO, hash.substring(1))).isFalse();
        assertThat(calculadora.confere(USUARIO, hash + "0")).isFalse();
    }
}

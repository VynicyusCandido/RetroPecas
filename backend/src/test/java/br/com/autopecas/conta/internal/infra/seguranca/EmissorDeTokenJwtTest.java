package br.com.autopecas.conta.internal.infra.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.autopecas.conta.internal.application.TokenEmitido;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class EmissorDeTokenJwtTest {

    private static final Instant AGORA = Instant.parse("2026-09-29T12:00:00Z");
    private static final Duration VALIDADE = Duration.ofMinutes(90);
    private static final UUID USUARIO = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final byte[] CHAVE = chave();

    private static byte[] chave() {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte) 7);
        return bytes;
    }

    private final EmissorDeTokenJwt emissor = new EmissorDeTokenJwt(
            new NimbusJwtEncoder(new ImmutableSecret<>(chaveSecreta())),
            new SegurancaProperties(
                    Base64.getEncoder().encodeToString(CHAVE),
                    Base64.getEncoder().encodeToString(CHAVE),
                    VALIDADE,
                    false),
            Clock.fixed(AGORA, ZoneOffset.UTC));

    private static SecretKey chaveSecreta() {
        return new SecretKeySpec(CHAVE, "HmacSHA256");
    }

    /** Decodifica só a assinatura: o relógio do teste é fixo e o token pode já estar "vencido" para o relógio real. */
    private static Jwt decodificar(String jwt) {
        NimbusJwtDecoder decodificador =
                NimbusJwtDecoder.withSecretKey(chaveSecreta()).macAlgorithm(MacAlgorithm.HS256).build();
        decodificador.setJwtValidator(token -> OAuth2TokenValidatorResult.success());
        return decodificador.decode(jwt);
    }

    @Test
    void oTokenCarregaEmissorUsuarioEEmissaoEValidade() {
        TokenEmitido token = emissor.emitir(USUARIO);

        Jwt jwt = decodificar(token.jwt());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("autopecas");
        assertThat(jwt.getSubject()).isEqualTo(USUARIO.toString());
        assertThat(jwt.getIssuedAt()).isEqualTo(AGORA);
        assertThat(jwt.getExpiresAt()).isEqualTo(AGORA.plus(VALIDADE));
        assertThat(token.expiraEm()).isEqualTo(AGORA.plus(VALIDADE));
    }

    @Test
    void oTokenEAssinadoComHs256() {
        Jwt jwt = decodificar(emissor.emitir(USUARIO).jwt());

        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
    }

    @Test
    void cadaTokenTemUmJtiDiferenteEValido() {
        Jwt primeiro = decodificar(emissor.emitir(USUARIO).jwt());
        Jwt segundo = decodificar(emissor.emitir(USUARIO).jwt());

        assertThat(UUID.fromString(primeiro.getId())).isNotNull();
        assertThat(primeiro.getId()).isNotEqualTo(segundo.getId());
    }

    @Test
    void oTokenNaoTrazDadosPessoaisAlemDoIdDoUsuario() {
        Jwt jwt = decodificar(emissor.emitir(USUARIO).jwt());

        assertThat(jwt.getClaims().keySet()).containsExactlyInAnyOrder("iss", "sub", "iat", "exp", "jti");
    }
}

package br.com.autopecas.conta.internal.infra.seguranca;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Segredos em base64, com pelo menos 32 bytes. O segredo do JWT e o do X-Auth-Id são diferentes de propósito:
 * quem descobrir um não consegue falsificar o outro. O cookie é Secure por padrão: só dev e testes o desligam.
 */
@Validated
@ConfigurationProperties(prefix = "app.seguranca")
public record SegurancaProperties(
        @NotBlank String jwtSegredo,
        @NotBlank String authIdSegredo,
        @NotNull Duration tokenValidade,
        @DefaultValue("true") boolean cookieSeguro) {

    static final int TAMANHO_MINIMO_DO_SEGREDO_EM_BYTES = 32;

    public byte[] jwtChave() {
        return decodificar(jwtSegredo, "jwt-segredo");
    }

    public byte[] authIdChave() {
        return decodificar(authIdSegredo, "auth-id-segredo");
    }

    /**
     * Falha na subida se os dois segredos tiverem os mesmos bytes: com o mesmo segredo, quem forjasse um JWT
     * também calcularia o X-Auth-Id. Compara os bytes decodificados (o texto em base64 pode variar) e a mensagem
     * não traz os valores.
     */
    public void exigirSegredosDistintos() {
        if (MessageDigest.isEqual(jwtChave(), authIdChave())) {
            throw new IllegalStateException(
                    "app.seguranca.jwt-segredo e app.seguranca.auth-id-segredo precisam ser diferentes.");
        }
    }

    private static byte[] decodificar(String base64, String nome) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException erro) {
            throw new IllegalStateException("app.seguranca." + nome + " precisa estar em base64.", erro);
        }
        if (bytes.length < TAMANHO_MINIMO_DO_SEGREDO_EM_BYTES) {
            throw new IllegalStateException(
                    "app.seguranca." + nome + " precisa ter pelo menos " + TAMANHO_MINIMO_DO_SEGREDO_EM_BYTES
                            + " bytes (em base64).");
        }
        return bytes;
    }
}

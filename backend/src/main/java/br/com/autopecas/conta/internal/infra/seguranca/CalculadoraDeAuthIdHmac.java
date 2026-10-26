package br.com.autopecas.conta.internal.infra.seguranca;

import static java.nio.charset.StandardCharsets.UTF_8;

import br.com.autopecas.conta.internal.application.CalculadoraDeAuthId;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** X-Auth-Id = HMAC-SHA256 do id do usuário, em hexadecimal, com o segredo do servidor. */
@Component
class CalculadoraDeAuthIdHmac implements CalculadoraDeAuthId {

    private static final String ALGORITMO = "HmacSHA256";

    private final SecretKeySpec chave;

    CalculadoraDeAuthIdHmac(SegurancaProperties propriedades) {
        this.chave = new SecretKeySpec(propriedades.authIdChave(), ALGORITMO);
    }

    @Override
    public String calcular(UUID usuarioId) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(chave);
            return HexFormat.of().formatHex(mac.doFinal(usuarioId.toString().getBytes(UTF_8)));
        } catch (GeneralSecurityException erro) {
            throw new IllegalStateException("HMAC-SHA256 indisponível", erro);
        }
    }

    /** Comparação em tempo constante, para o tempo de resposta não revelar quantos caracteres batem. */
    @Override
    public boolean confere(UUID usuarioId, String informado) {
        if (informado == null) {
            return false;
        }
        return MessageDigest.isEqual(calcular(usuarioId).getBytes(UTF_8), informado.getBytes(UTF_8));
    }
}

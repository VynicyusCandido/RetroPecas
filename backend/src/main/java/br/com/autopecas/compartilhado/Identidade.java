package br.com.autopecas.compartilhado;

import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** Quem está autenticado na requisição. O filtro de segurança já garantiu que o "sub" é um UUID. */
public final class Identidade {

    private Identidade() {
    }

    public static UUID usuarioId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}

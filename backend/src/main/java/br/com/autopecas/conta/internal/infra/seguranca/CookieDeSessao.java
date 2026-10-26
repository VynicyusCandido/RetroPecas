package br.com.autopecas.conta.internal.infra.seguranca;

import br.com.autopecas.conta.internal.application.TokenEmitido;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** O JWT vai num cookie HttpOnly: o JavaScript da página nunca lê o token. */
@Component
public class CookieDeSessao {

    public static final String NOME = "access_token";

    private final SegurancaProperties propriedades;

    CookieDeSessao(SegurancaProperties propriedades) {
        this.propriedades = propriedades;
    }

    public ResponseCookie criar(TokenEmitido token) {
        return base(token.jwt()).maxAge(propriedades.tokenValidade()).build();
    }

    public ResponseCookie apagar() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String valor) {
        return ResponseCookie.from(NOME, valor)
                .httpOnly(true)
                .secure(propriedades.cookieSeguro())
                .sameSite("Lax")
                .path("/");
    }
}

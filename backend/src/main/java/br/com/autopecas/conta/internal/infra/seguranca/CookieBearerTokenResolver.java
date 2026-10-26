package br.com.autopecas.conta.internal.infra.seguranca;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Set;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

/**
 * Lê o JWT só do cookie de sessão. O cabeçalho Authorization é ignorado de propósito.
 * Nas rotas públicas de entrada o cookie também é ignorado: um cookie antigo ou inválido
 * não pode impedir quem quer fazer login de novo.
 */
class CookieBearerTokenResolver implements BearerTokenResolver {

    private static final Set<String> ROTAS_PUBLICAS = Set.of("/api/v1/auth/login", "/api/v1/auth/registro");

    @Override
    public String resolve(HttpServletRequest requisicao) {
        if (ROTAS_PUBLICAS.contains(requisicao.getRequestURI())) {
            return null;
        }
        Cookie[] cookies = requisicao.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> CookieDeSessao.NOME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(valor -> valor != null && !valor.isBlank())
                .findFirst()
                .orElse(null);
    }
}

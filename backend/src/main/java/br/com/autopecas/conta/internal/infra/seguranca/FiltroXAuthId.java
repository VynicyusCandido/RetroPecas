package br.com.autopecas.conta.internal.infra.seguranca;

import br.com.autopecas.conta.internal.application.CalculadoraDeAuthId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Confirma o usuário da sessão: o cabeçalho X-Auth-Id precisa ser o HMAC do "sub" do JWT.
 * Só o nosso frontend sabe o valor (ele vem de GET /api/v1/me), e um cabeçalho personalizado não sai de um
 * formulário de outro site, então este filtro também protege contra CSRF.
 * Não é um @Component de propósito: assim ele roda só dentro da cadeia de segurança, e não duas vezes.
 */
class FiltroXAuthId extends OncePerRequestFilter {

    static final String CABECALHO = "X-Auth-Id";

    private final CalculadoraDeAuthId calculadora;

    FiltroXAuthId(CalculadoraDeAuthId calculadora) {
        this.calculadora = calculadora;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao instanceof JwtAuthenticationToken token && exigeConfirmacao(requisicao)) {
            UUID usuarioId = comoUuid(token.getName());
            if (usuarioId == null || !calculadora.confere(usuarioId, requisicao.getHeader(CABECALHO))) {
                SecurityContextHolder.clearContext();
                RespostaDeProblema.escrever(resposta, 401, "Não foi possível confirmar o usuário da sessão");
                return;
            }
        }
        cadeia.doFilter(requisicao, resposta);
    }

    /** GET /api/v1/me é a única exceção: é por ele que o frontend descobre o valor do X-Auth-Id. */
    private static boolean exigeConfirmacao(HttpServletRequest requisicao) {
        boolean ehMe = "GET".equals(requisicao.getMethod()) && "/api/v1/me".equals(requisicao.getRequestURI());
        return !ehMe;
    }

    private static UUID comoUuid(String texto) {
        try {
            return UUID.fromString(texto);
        } catch (IllegalArgumentException | NullPointerException erro) {
            return null;
        }
    }
}

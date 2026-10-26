package br.com.autopecas.conta.internal.web;

import br.com.autopecas.conta.internal.application.AutenticacaoService;
import br.com.autopecas.conta.internal.application.LogoutService;
import br.com.autopecas.conta.internal.application.RegistroService;
import br.com.autopecas.conta.internal.application.ResultadoDeLogin;
import br.com.autopecas.conta.internal.domain.Usuario;
import br.com.autopecas.conta.internal.infra.seguranca.CookieDeSessao;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final RegistroService registro;
    private final AutenticacaoService autenticacao;
    private final LogoutService logout;
    private final CookieDeSessao cookies;

    AuthController(
            RegistroService registro, AutenticacaoService autenticacao, LogoutService logout, CookieDeSessao cookies) {
        this.registro = registro;
        this.autenticacao = autenticacao;
        this.logout = logout;
        this.cookies = cookies;
    }

    @PostMapping("/registro")
    ResponseEntity<RegistroResponse> registrar(@Valid @RequestBody RegistroRequest requisicao) {
        Usuario usuario = registro.registrar(requisicao.nome(), requisicao.email(), requisicao.senha());
        return ResponseEntity.status(HttpStatus.CREATED).body(RegistroResponse.de(usuario));
    }

    @PostMapping("/login")
    ResponseEntity<SessaoResponse> entrar(@Valid @RequestBody LoginRequest requisicao) {
        ResultadoDeLogin resultado = autenticacao.autenticar(requisicao.email(), requisicao.senha());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.criar(resultado.token()).toString())
                .body(SessaoResponse.de(resultado.sessao()));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> sair(@AuthenticationPrincipal Jwt jwt) {
        logout.encerrar(UUID.fromString(jwt.getId()), jwt.getExpiresAt());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.apagar().toString())
                .build();
    }
}

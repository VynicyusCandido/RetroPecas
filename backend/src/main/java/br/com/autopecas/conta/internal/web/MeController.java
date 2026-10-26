package br.com.autopecas.conta.internal.web;

import br.com.autopecas.compartilhado.Identidade;
import br.com.autopecas.conta.internal.application.SessaoService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MeController {

    private final SessaoService sessoes;

    MeController(SessaoService sessoes) {
        this.sessoes = sessoes;
    }

    /** Só o cookie autentica esta rota (sem X-Auth-Id): é por aqui que o frontend descobre o authId. */
    @GetMapping("/api/v1/me")
    SessaoResponse me(@AuthenticationPrincipal Jwt jwt) {
        return SessaoResponse.de(sessoes.obter(Identidade.usuarioId(jwt)));
    }
}

package br.com.autopecas.conta.internal.infra.seguranca;

import br.com.autopecas.conta.internal.infra.persistencia.TokenRevogadoRepository;
import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** Recusa o token de uma sessão que já fez logout, mesmo que ele ainda esteja dentro da validade. */
class ValidadorDeTokenRevogado implements OAuth2TokenValidator<Jwt> {

    private final TokenRevogadoRepository revogados;

    ValidadorDeTokenRevogado(TokenRevogadoRepository revogados) {
        this.revogados = revogados;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        UUID jti;
        try {
            jti = UUID.fromString(jwt.getId());
        } catch (IllegalArgumentException | NullPointerException erro) {
            return falha("Token sem identificador válido.");
        }
        if (revogados.existsById(jti)) {
            return falha("Token revogado.");
        }
        return OAuth2TokenValidatorResult.success();
    }

    private static OAuth2TokenValidatorResult falha(String descricao) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", descricao, null));
    }
}

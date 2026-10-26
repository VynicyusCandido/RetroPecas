package br.com.autopecas.conta.internal.infra.seguranca;

import br.com.autopecas.conta.internal.application.EmissorDeToken;
import br.com.autopecas.conta.internal.application.TokenEmitido;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
class EmissorDeTokenJwt implements EmissorDeToken {

    private final JwtEncoder codificador;
    private final SegurancaProperties propriedades;
    private final Clock relogio;

    EmissorDeTokenJwt(JwtEncoder codificador, SegurancaProperties propriedades, Clock relogio) {
        this.codificador = codificador;
        this.propriedades = propriedades;
        this.relogio = relogio;
    }

    @Override
    public TokenEmitido emitir(UUID usuarioId) {
        Instant agora = relogio.instant();
        Instant expiraEm = agora.plus(propriedades.tokenValidade());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ConfiguracaoDeSeguranca.EMISSOR)
                .subject(usuarioId.toString())
                .issuedAt(agora)
                .expiresAt(expiraEm)
                .id(UUID.randomUUID().toString())
                .build();
        JwsHeader cabecalho = JwsHeader.with(MacAlgorithm.HS256).build();
        String jwt = codificador.encode(JwtEncoderParameters.from(cabecalho, claims)).getTokenValue();
        return new TokenEmitido(jwt, expiraEm);
    }
}

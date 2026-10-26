package br.com.autopecas.conta;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.autopecas.ApoioDeTeste;
import br.com.autopecas.TesteDeIntegracao;
import br.com.autopecas.conta.internal.domain.Usuario;
import br.com.autopecas.conta.internal.infra.seguranca.CookieDeSessao;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Cookie com o JWT + cabeçalho X-Auth-Id: toda a cadeia de segurança, com uma rota protegida só de teste. */
@TesteDeIntegracao
@Import(CadeiaDeSegurancaTest.ControladorProtegido.class)
class CadeiaDeSegurancaTest {

    private static final String ROTA = "/api/v1/teste/protegido";
    private static final String ROTA_COM_FALHA = ROTA + "/falha";
    private static final String DETALHE_INTERNO = "detalhe-interno-secreto";
    private static final MediaType PROBLEMA = MediaType.valueOf("application/problem+json");

    @RestController
    static class ControladorProtegido {

        @GetMapping(ROTA)
        String ler(@AuthenticationPrincipal Jwt jwt) {
            return jwt.getSubject();
        }

        @GetMapping(ROTA_COM_FALHA)
        String falhar() {
            throw new IllegalStateException(DETALHE_INTERNO);
        }

        @PostMapping(ROTA)
        ResponseEntity<Void> escrever() {
            return ResponseEntity.noContent().build();
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApoioDeTeste apoio;

    @Autowired
    JdbcTemplate jdbc;

    @Value("${app.seguranca.jwt-segredo}")
    String jwtSegredo;

    Usuario usuario;

    @BeforeEach
    void criarUsuario() {
        usuario = apoio.usuario();
    }

    private MockHttpServletRequestBuilder autenticada(MockHttpServletRequestBuilder requisicao) {
        return requisicao.cookie(apoio.cookieDe(usuario)).header("X-Auth-Id", apoio.authIdDe(usuario));
    }

    private static String token(
            String segredoBase64, String emissor, String subject, Instant emitidoEm, Instant expiraEm, String jti) {
        var chave = new SecretKeySpec(Base64.getDecoder().decode(segredoBase64), "HmacSHA256");
        var codificador = new NimbusJwtEncoder(new ImmutableSecret<>(chave));
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(emissor)
                .subject(subject)
                .issuedAt(emitidoEm)
                .expiresAt(expiraEm);
        if (jti != null) {
            claims.id(jti);
        }
        return codificador
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
    }

    private String tokenValidoDoUsuario(String jti, Instant expiraEm) {
        return token(
                jwtSegredo, "autopecas", usuario.getId().toString(), Instant.now().minusSeconds(5), expiraEm, jti);
    }

    private Cookie cookie(String jwt) {
        return new Cookie(CookieDeSessao.NOME, jwt);
    }

    @Test
    void semCookieARespostaEUmProblemaDeNaoAutenticado() throws Exception {
        mockMvc.perform(get(ROTA))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEMA))
                .andExpect(jsonPath("$.title").value("Não autenticado"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void cookieValidoMaisOAuthIdCorretoDeixaPassar() throws Exception {
        mockMvc.perform(autenticada(get(ROTA)))
                .andExpect(status().isOk())
                .andExpect(content().string(usuario.getId().toString()));
    }

    @Test
    void cookieValidoSemOAuthIdENegado() throws Exception {
        mockMvc.perform(get(ROTA).cookie(apoio.cookieDe(usuario)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEMA))
                .andExpect(jsonPath("$.title").value("Não foi possível confirmar o usuário da sessão"));
    }

    @Test
    void cookieValidoComOAuthIdDeOutroUsuarioENegado() throws Exception {
        Usuario outro = apoio.usuario();

        mockMvc.perform(get(ROTA).cookie(apoio.cookieDe(usuario)).header("X-Auth-Id", apoio.authIdDe(outro)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Não foi possível confirmar o usuário da sessão"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "abc", "00000000-0000-0000-0000-000000000001"})
    void cookieValidoComOAuthIdInventadoENegado(String forjado) throws Exception {
        mockMvc.perform(get(ROTA).cookie(apoio.cookieDe(usuario)).header("X-Auth-Id", forjado))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void oAuthIdEmMaiusculasOuComEspacoNaoConfere() throws Exception {
        String hash = apoio.authIdDe(usuario);

        mockMvc.perform(get(ROTA).cookie(apoio.cookieDe(usuario)).header("X-Auth-Id", hash.toUpperCase()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(ROTA).cookie(apoio.cookieDe(usuario)).header("X-Auth-Id", hash + " "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void oAuthIdSemOCookieNaoAutentica() throws Exception {
        mockMvc.perform(get(ROTA).header("X-Auth-Id", apoio.authIdDe(usuario)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Não autenticado"));
    }

    @Test
    void tokenExpiradoENegadoMesmoComAssinaturaValida() throws Exception {
        String expirado = token(
                jwtSegredo,
                "autopecas",
                usuario.getId().toString(),
                Instant.now().minus(2, ChronoUnit.HOURS),
                Instant.now().minus(1, ChronoUnit.HOURS),
                UUID.randomUUID().toString());

        mockMvc.perform(get(ROTA).cookie(cookie(expirado)).header("X-Auth-Id", apoio.authIdDe(usuario)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Não autenticado"));
    }

    @Test
    void tokenAssinadoComOutroSegredoENegado() throws Exception {
        String outroSegredo = Base64.getEncoder().encodeToString(new byte[32]);
        String forjado = token(
                outroSegredo,
                "autopecas",
                usuario.getId().toString(),
                Instant.now(),
                Instant.now().plusSeconds(600),
                UUID.randomUUID().toString());

        mockMvc.perform(get(ROTA).cookie(cookie(forjado)).header("X-Auth-Id", apoio.authIdDe(usuario)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenDeOutroEmissorENegado() throws Exception {
        String deOutroEmissor = token(
                jwtSegredo,
                "outro-sistema",
                usuario.getId().toString(),
                Instant.now(),
                Instant.now().plusSeconds(600),
                UUID.randomUUID().toString());

        mockMvc.perform(get(ROTA).cookie(cookie(deOutroEmissor)).header("X-Auth-Id", apoio.authIdDe(usuario)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSemJtiENegado() throws Exception {
        String semJti = token(
                jwtSegredo,
                "autopecas",
                usuario.getId().toString(),
                Instant.now(),
                Instant.now().plusSeconds(600),
                null);

        mockMvc.perform(get(ROTA).cookie(cookie(semJti)).header("X-Auth-Id", apoio.authIdDe(usuario)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenRevogadoENegadoAteDentroDaValidade() throws Exception {
        UUID jti = UUID.randomUUID();
        Instant expiraEm = Instant.now().plusSeconds(600);
        jdbc.update("insert into token_revogado (jti, expira_em) values (?, ?)", jti, Timestamp.from(expiraEm));

        mockMvc.perform(get(ROTA)
                        .cookie(cookie(tokenValidoDoUsuario(jti.toString(), expiraEm)))
                        .header("X-Auth-Id", apoio.authIdDe(usuario)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenCujoSubNaoEUmUuidENegado() throws Exception {
        String subInvalido = token(
                jwtSegredo,
                "autopecas",
                "nao-e-um-uuid",
                Instant.now(),
                Instant.now().plusSeconds(600),
                UUID.randomUUID().toString());

        mockMvc.perform(get(ROTA).cookie(cookie(subInvalido)).header("X-Auth-Id", "qualquer"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Não foi possível confirmar o usuário da sessão"));
    }

    @Test
    void oCabecalhoAuthorizationBearerEIgnorado() throws Exception {
        mockMvc.perform(get(ROTA)
                        .header("Authorization", "Bearer " + apoio.jwtDe(usuario))
                        .header("X-Auth-Id", apoio.authIdDe(usuario)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Não autenticado"));
    }

    @Test
    void tokenSemAssinaturaComAlgNoneENegado() throws Exception {
        Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
        String cabecalho = base64.encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(UTF_8));
        String corpo = base64.encodeToString(("{\"iss\":\"autopecas\",\"sub\":\"" + usuario.getId()
                        + "\",\"exp\":" + Instant.now().plusSeconds(600).getEpochSecond()
                        + ",\"jti\":\"" + UUID.randomUUID() + "\"}")
                .getBytes(UTF_8));

        mockMvc.perform(get(ROTA)
                        .cookie(cookie(cabecalho + "." + corpo + "."))
                        .header("X-Auth-Id", apoio.authIdDe(usuario)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenAssinadoComOutroAlgoritmoENegadoMesmoComOSegredoCerto() throws Exception {
        Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
        String cabecalho = base64.encodeToString("{\"alg\":\"HS512\",\"typ\":\"JWT\"}".getBytes(UTF_8));
        String corpo = base64.encodeToString(("{\"iss\":\"autopecas\",\"sub\":\"" + usuario.getId()
                        + "\",\"exp\":" + Instant.now().plusSeconds(600).getEpochSecond()
                        + ",\"jti\":\"" + UUID.randomUUID() + "\"}")
                .getBytes(UTF_8));
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(jwtSegredo), "HmacSHA512"));
        String assinatura = base64.encodeToString(mac.doFinal((cabecalho + "." + corpo).getBytes(UTF_8)));

        mockMvc.perform(get(ROTA)
                        .cookie(cookie(cabecalho + "." + corpo + "." + assinatura))
                        .header("X-Auth-Id", apoio.authIdDe(usuario)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void oBackendNaoHabilitaCorsParaOutrasOrigens() throws Exception {
        // A defesa contra CSRF depende de outro site não conseguir enviar o X-Auth-Id (cabeçalho personalizado).
        mockMvc.perform(options(ROTA)
                        .header("Origin", "https://site-malicioso.example")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "x-auth-id"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Headers"));

        mockMvc.perform(autenticada(get(ROTA)).header("Origin", "https://site-malicioso.example"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void cookieComOutroNomeNaoAutentica() throws Exception {
        mockMvc.perform(get(ROTA)
                        .cookie(new Cookie("sessao", apoio.jwtDe(usuario)))
                        .header("X-Auth-Id", apoio.authIdDe(usuario)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void metodosQueAlteramEstadoTambemExigemOAuthId() throws Exception {
        mockMvc.perform(post(ROTA).cookie(apoio.cookieDe(usuario)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Não foi possível confirmar o usuário da sessão"));

        mockMvc.perform(autenticada(post(ROTA))).andExpect(status().isNoContent());
    }

    @Test
    void rotaInexistenteAutenticadaResponde404EmPortugues() throws Exception {
        mockMvc.perform(autenticada(get("/api/v1/nao-existe")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEMA))
                .andExpect(jsonPath("$.title").value("Não encontrado"));
    }

    @Test
    void rotaInexistenteSemAutenticacaoResponde401ENaoVazaQueElaNaoExiste() throws Exception {
        mockMvc.perform(get("/api/v1/nao-existe")).andExpect(status().isUnauthorized());
    }

    @Test
    void metodoNaoPermitidoResponde405EmPortugues() throws Exception {
        mockMvc.perform(autenticada(delete(ROTA)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.title").value("Método não permitido"));
    }

    @Test
    void umErroInesperadoResponde500SemVazarDetalhesInternos() throws Exception {
        mockMvc.perform(autenticada(get(ROTA_COM_FALHA)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(PROBLEMA))
                .andExpect(jsonPath("$.title").value("Erro interno do servidor"))
                .andExpect(content().string(not(containsString(DETALHE_INTERNO))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("at br.com"))));
    }
}

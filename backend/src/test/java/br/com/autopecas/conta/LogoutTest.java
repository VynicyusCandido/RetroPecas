package br.com.autopecas.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.autopecas.ApoioDeTeste;
import br.com.autopecas.TesteDeIntegracao;
import br.com.autopecas.conta.internal.application.LimpezaDeTokensRevogados;
import br.com.autopecas.conta.internal.domain.Usuario;
import br.com.autopecas.conta.internal.infra.seguranca.CookieDeSessao;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@TesteDeIntegracao
class LogoutTest {

    private static final String ROTA = "/api/v1/auth/logout";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApoioDeTeste apoio;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtDecoder decodificador;

    @Autowired
    LimpezaDeTokensRevogados limpeza;

    @Test
    void logoutApagaOCookieERevogaOToken() throws Exception {
        Usuario usuario = apoio.usuario();
        Cookie cookie = apoio.cookieDe(usuario);
        String jti = decodificador.decode(cookie.getValue()).getId();

        String setCookie = mockMvc.perform(post(ROTA).cookie(cookie).header("X-Auth-Id", apoio.authIdDe(usuario)))
                .andExpect(status().isNoContent())
                .andReturn()
                .getResponse()
                .getHeader("Set-Cookie");

        assertThat(setCookie).startsWith(CookieDeSessao.NOME + "=;").contains("Max-Age=0").contains("HttpOnly");
        assertThat(jdbc.queryForObject("select count(*) from token_revogado where jti = ?", Long.class, UUID.fromString(jti)))
                .isEqualTo(1L);
    }

    @Test
    void depoisDoLogoutOMesmoTokenNaoAbreMaisNada() throws Exception {
        Usuario usuario = apoio.usuario();
        Cookie cookie = apoio.cookieDe(usuario);
        mockMvc.perform(get("/api/v1/me").cookie(cookie)).andExpect(status().isOk());

        mockMvc.perform(post(ROTA).cookie(cookie).header("X-Auth-Id", apoio.authIdDe(usuario)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/me").cookie(cookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Não autenticado"));
    }

    @Test
    void logoutDeUmaSessaoNaoDerrubaAsOutrasDoMesmoUsuario() throws Exception {
        Usuario usuario = apoio.usuario();
        Cookie notebook = apoio.cookieDe(usuario);
        Cookie celular = apoio.cookieDe(usuario);

        mockMvc.perform(post(ROTA).cookie(notebook).header("X-Auth-Id", apoio.authIdDe(usuario)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/me").cookie(celular)).andExpect(status().isOk());
    }

    @Test
    void segundoLogoutComOMesmoTokenENegado() throws Exception {
        Usuario usuario = apoio.usuario();
        Cookie cookie = apoio.cookieDe(usuario);
        String authId = apoio.authIdDe(usuario);
        mockMvc.perform(post(ROTA).cookie(cookie).header("X-Auth-Id", authId)).andExpect(status().isNoContent());

        mockMvc.perform(post(ROTA).cookie(cookie).header("X-Auth-Id", authId)).andExpect(status().isUnauthorized());
    }

    @Test
    void semOAuthIdOLogoutNaoAcontece() throws Exception {
        Usuario usuario = apoio.usuario();
        Cookie cookie = apoio.cookieDe(usuario);

        // Um site malicioso não consegue forçar o logout: falta o cabeçalho que só o nosso frontend envia.
        mockMvc.perform(post(ROTA).cookie(cookie)).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/me").cookie(cookie)).andExpect(status().isOk());
    }

    @Test
    void semCookieOLogoutENegado() throws Exception {
        Usuario usuario = apoio.usuario();

        mockMvc.perform(post(ROTA).header("X-Auth-Id", apoio.authIdDe(usuario)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutsSimultaneosComOMesmoTokenNuncaDao500() throws Exception {
        Usuario usuario = apoio.usuario();
        Cookie cookie = apoio.cookieDe(usuario);
        String authId = apoio.authIdDe(usuario);
        String jti = decodificador.decode(cookie.getValue()).getId();
        int requisicoes = 8;
        ExecutorService pool = Executors.newFixedThreadPool(requisicoes);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            // Um duplo clique: todas passam pela checagem de token revogado antes de qualquer uma gravar a revogação.
            List<Future<Integer>> resultados = IntStream.range(0, requisicoes)
                    .mapToObj(i -> pool.submit(() -> {
                        largada.await();
                        return mockMvc.perform(post(ROTA).cookie(cookie).header("X-Auth-Id", authId))
                                .andReturn()
                                .getResponse()
                                .getStatus();
                    }))
                    .toList();
            largada.countDown();
            List<Integer> status = new ArrayList<>();
            for (Future<Integer> resultado : resultados) {
                status.add(resultado.get(30, TimeUnit.SECONDS));
            }

            assertThat(status).doesNotContain(500).isSubsetOf(204, 401).contains(204);
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbc.queryForObject("select count(*) from token_revogado where jti = ?", Long.class, UUID.fromString(jti)))
                .isEqualTo(1L);
        mockMvc.perform(get("/api/v1/me").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void aLimpezaRemoveSoOsTokensRevogadosJaVencidos() {
        UUID vencido = UUID.randomUUID();
        UUID vigente = UUID.randomUUID();
        inserirRevogado(vencido, Instant.now().minus(10, ChronoUnit.MINUTES));
        inserirRevogado(vigente, Instant.now().plusSeconds(3600));

        int removidos = limpeza.removerExpirados();

        assertThat(removidos).isGreaterThanOrEqualTo(1);
        assertThat(revogado(vencido)).isZero();
        assertThat(revogado(vigente)).isEqualTo(1L);
    }

    /**
     * O JWT ainda é aceito por 60 s depois do "exp" (tolerância de relógio do validador), então uma revogação recém
     * vencida não pode sair da tabela: senão o token voltaria a valer.
     */
    @Test
    void aLimpezaMantemARevogacaoVencidaHaPoucoTempoDentroDaMargemDeRelogio() {
        UUID vencidoHaPouco = UUID.randomUUID();
        UUID vencidoHaMuito = UUID.randomUUID();
        inserirRevogado(vencidoHaPouco, Instant.now().minus(2, ChronoUnit.MINUTES));
        inserirRevogado(vencidoHaMuito, Instant.now().minus(10, ChronoUnit.MINUTES));

        limpeza.removerExpirados();

        assertThat(revogado(vencidoHaPouco)).isEqualTo(1L);
        assertThat(revogado(vencidoHaMuito)).isZero();
    }

    private void inserirRevogado(UUID jti, Instant expiraEm) {
        jdbc.update("insert into token_revogado (jti, expira_em) values (?, ?)", jti, Timestamp.from(expiraEm));
    }

    private Long revogado(UUID jti) {
        return jdbc.queryForObject("select count(*) from token_revogado where jti = ?", Long.class, jti);
    }
}

package br.com.autopecas.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.autopecas.ApoioDeTeste;
import br.com.autopecas.TesteDeIntegracao;
import br.com.autopecas.conta.internal.domain.Usuario;
import br.com.autopecas.conta.internal.infra.persistencia.UsuarioRepository;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@TesteDeIntegracao
class RegistroTest {

    private static final String ROTA = "/api/v1/auth/registro";
    private static final String SENHA_VALIDA = "senha-valida-1";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApoioDeTeste apoio;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    PasswordEncoder codificador;

    private static String json(String nome, String email, String senha) {
        return """
                {"nome": "%s", "email": "%s", "senha": "%s"}""".formatted(nome, email, senha);
    }

    private ResultActions registrar(String corpo) throws Exception {
        return mockMvc.perform(post(ROTA).contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    /** Captura tudo o que a aplicação registra em log (mensagem e exceção), de qualquer logger, enquanto o teste roda. */
    private static ListAppender<ILoggingEvent> comecarACapturarLogs() {
        ListAppender<ILoggingEvent> capturado = new ListAppender<>();
        capturado.start();
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).addAppender(capturado);
        return capturado;
    }

    private static List<String> pararDeCapturarLogs(ListAppender<ILoggingEvent> capturado) {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).detachAppender(capturado);
        capturado.stop();
        return capturado.list.stream()
                .map(evento -> evento.getFormattedMessage()
                        + (evento.getThrowableProxy() == null
                                ? ""
                                : ThrowableProxyUtil.asString(evento.getThrowableProxy())))
                .toList();
    }

    @Test
    void registraEDevolveOUsuarioSemDadosSecretos() throws Exception {
        String email = apoio.emailUnico();

        registrar(json("  Ana Souza ", email, SENHA_VALIDA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.nome").value("Ana Souza"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.senhaHash").doesNotExist())
                .andExpect(content().string(Matchers.not(Matchers.containsString(SENHA_VALIDA))));
    }

    @Test
    void guardaOEmailEmMinusculasEASenhaComBcrypt() throws Exception {
        String email = apoio.emailUnico();

        registrar(json("Ana", "  " + email.toUpperCase() + " ", SENHA_VALIDA)).andExpect(status().isCreated());

        Usuario salvo = usuarios.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(salvo.getEmail()).isEqualTo(email.toLowerCase());
        assertThat(salvo.getSenhaHash()).startsWith("$2").isNotEqualTo(SENHA_VALIDA);
        assertThat(codificador.matches(SENHA_VALIDA, salvo.getSenhaHash())).isTrue();
    }

    @Test
    void oRegistroNaoAbreSessao() throws Exception {
        registrar(json("Ana", apoio.emailUnico(), SENHA_VALIDA))
                .andExpect(status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .doesNotExist("Set-Cookie"));
    }

    @Test
    void emailJaCadastradoDa409() throws Exception {
        String email = apoio.emailUnico();
        registrar(json("Ana", email, SENHA_VALIDA)).andExpect(status().isCreated());

        registrar(json("Outra Ana", email, SENHA_VALIDA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Já existe uma conta com este e-mail."));
    }

    @Test
    void emailJaCadastradoIgnoraCaixaEEspacos() throws Exception {
        String email = apoio.emailUnico();
        registrar(json("Ana", email, SENHA_VALIDA)).andExpect(status().isCreated());

        registrar(json("Ana", " " + email.toUpperCase() + " ", SENHA_VALIDA)).andExpect(status().isConflict());
    }

    static Stream<Arguments> cadastrosInvalidos() {
        return Stream.of(
                Arguments.of("nome vazio", json("", "a@example.com", SENHA_VALIDA), "nome"),
                Arguments.of("nome só com espaços", json("   ", "a@example.com", SENHA_VALIDA), "nome"),
                Arguments.of("nome longo demais", json("x".repeat(121), "a@example.com", SENHA_VALIDA), "nome"),
                Arguments.of("e-mail vazio", json("Ana", "", SENHA_VALIDA), "email"),
                Arguments.of("e-mail sem arroba", json("Ana", "ana.example.com", SENHA_VALIDA), "email"),
                // O @Email do Hibernate Validator aceita estes cinco, mas o z.email() do frontend (Zod 4) não:
                // aceitá-los criaria uma conta cujo /me quebra o cliente para sempre.
                Arguments.of("e-mail sem ponto no domínio (typo comum)", json("Ana", "maria@hotmail", SENHA_VALIDA), "email"),
                Arguments.of("e-mail sem ponto no domínio (gmail)", json("Ana", "ana@gmail", SENHA_VALIDA), "email"),
                Arguments.of("e-mail com & na parte local", json("Ana", "ana&bia@exemplo.com", SENHA_VALIDA), "email"),
                Arguments.of("e-mail com domínio em IP entre colchetes", json("Ana", "ana@[127.0.0.1]", SENHA_VALIDA), "email"),
                Arguments.of("e-mail com acento na parte local", json("Ana", "joão@exemplo.com.br", SENHA_VALIDA), "email"),
                Arguments.of(
                        "e-mail longo demais", json("Ana", "a".repeat(250) + "@example.com", SENHA_VALIDA), "email"),
                Arguments.of("senha vazia", json("Ana", "a@example.com", ""), "senha"),
                Arguments.of("senha curta", json("Ana", "a@example.com", "1234567"), "senha"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cadastrosInvalidos")
    void dadosInvalidosDao400ComOCampoComErro(String descricao, String corpo, String campo) throws Exception {
        registrar(corpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Dados inválidos"))
                .andExpect(jsonPath("$.erros." + campo).isNotEmpty());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"ana.silva+loja@exemplo.com.br", "a@b.co"})
    void emailsQueOFrontendTambemAceitaContinuamDando201(String email) throws Exception {
        registrar(json("Ana", email, SENHA_VALIDA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void aSenhaDeExatamente72BytesEAceita() throws Exception {
        registrar(json("Ana", apoio.emailUnico(), "a".repeat(72))).andExpect(status().isCreated());
        registrar(json("Ana", apoio.emailUnico(), "é".repeat(36))).andExpect(status().isCreated());
    }

    @Test
    void aSenhaComMaisDe72BytesDa400EmVezDeErroInterno() throws Exception {
        registrar(json("Ana", apoio.emailUnico(), "a".repeat(73)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("A senha pode ter no máximo 72 bytes."));
        // 37 caracteres acentuados passam de 72 bytes em UTF-8, mesmo tendo menos de 72 caracteres.
        registrar(json("Ana", apoio.emailUnico(), "é".repeat(37)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("A senha pode ter no máximo 72 bytes."));
    }

    @Test
    void jsonMalformadoDa400() throws Exception {
        registrar("{\"nome\": ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Corpo da requisição inválido"));
    }

    @Test
    void semCorpoDa400() throws Exception {
        mockMvc.perform(post(ROTA).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Corpo da requisição inválido"));
    }

    @Test
    void semContentTypeJsonDa415() throws Exception {
        mockMvc.perform(post(ROTA).content(json("Ana", apoio.emailUnico(), SENHA_VALIDA)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.title").value("Tipo de conteúdo não suportado"));
    }

    /**
     * RNF-PRI-04: logs sem dados pessoais. Sem logServerErrorDetail=false, o pgjdbc põe o DETAIL do servidor
     * ("Key (lower(email::text))=(...) already exists") na exceção e o Hibernate o registra em WARN.
     * Aqui a violação é provocada direto no banco, então o teste não depende de ganhar a corrida.
     */
    @Test
    void aViolacaoDoEmailUnicoNaoEscreveOEmailNoLog() {
        String email = apoio.emailUnico();
        apoio.usuario(email);

        ListAppender<ILoggingEvent> capturado = comecarACapturarLogs();
        List<String> logs;
        try {
            assertThatThrownBy(() -> apoio.usuario(email)).isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            logs = pararDeCapturarLogs(capturado);
        }

        assertThat(logs)
                .as("a violação do índice único tem de ter sido registrada")
                .anyMatch(l -> l.contains("ux_usuario_email"));
        assertThat(logs).noneMatch(l -> l.contains(email));
    }

    @Test
    void duasRequisicoesSimultaneasComOMesmoEmailNuncaDao500() throws Exception {
        String email = apoio.emailUnico();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        ListAppender<ILoggingEvent> capturado = comecarACapturarLogs();
        List<Integer> status = new ArrayList<>();
        List<String> logs;
        try {
            List<Future<Integer>> resultados = IntStream.range(0, 2)
                    .mapToObj(i -> pool.submit(() -> {
                        largada.await();
                        return registrar(json("Ana", email, SENHA_VALIDA))
                                .andReturn()
                                .getResponse()
                                .getStatus();
                    }))
                    .toList();
            largada.countDown();
            for (Future<Integer> resultado : resultados) {
                status.add(resultado.get(30, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
            logs = pararDeCapturarLogs(capturado);
        }

        assertThat(status).containsExactlyInAnyOrder(201, 409);
        // RNF-PRI-04: nem o WARN do Hibernate sobre o índice único pode trazer o e-mail em texto.
        assertThat(logs).noneMatch(l -> l.contains(email));
    }
}

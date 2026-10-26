package br.com.autopecas;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.autopecas.conta.internal.domain.PerfilVendedor;
import br.com.autopecas.conta.internal.domain.StatusVendedor;
import br.com.autopecas.conta.internal.domain.TokenRevogado;
import br.com.autopecas.conta.internal.domain.Usuario;
import br.com.autopecas.conta.internal.infra.persistencia.PerfilVendedorRepository;
import br.com.autopecas.conta.internal.infra.persistencia.TokenRevogadoRepository;
import br.com.autopecas.conta.internal.infra.persistencia.UsuarioRepository;
import br.com.autopecas.notificacao.internal.domain.Notificacao;
import br.com.autopecas.notificacao.internal.domain.TipoNotificacao;
import br.com.autopecas.notificacao.internal.infra.persistencia.NotificacaoRepository;
import br.com.autopecas.notificacao.internal.infra.persistencia.NotificacaoRepository.ContagemPorTipo;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Os repositórios rodam contra o PostgreSQL real: cada valor dos enums passa pelos checks do banco e as consultas
 * são executadas de fato. Cada teste usa e-mails e ids aleatórios, então nada depende dos dados dos outros.
 */
@TesteDeIntegracao
class RepositoriosTest {

    private static final Instant BASE = Instant.parse("2026-03-10T12:00:00Z");

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    PerfilVendedorRepository perfis;

    @Autowired
    TokenRevogadoRepository tokensRevogados;

    @Autowired
    NotificacaoRepository notificacoes;

    @Autowired
    JdbcTemplate jdbc;

    private String emailNovo() {
        return "repo-" + UUID.randomUUID() + "@example.com";
    }

    private Usuario salvarUsuario() {
        return usuarios.saveAndFlush(Usuario.novo("Nome", emailNovo(), "hash", BASE));
    }

    private Notificacao salvarNotificacao(UUID usuarioId, TipoNotificacao tipo, String titulo, Instant criadaEm) {
        return notificacoes.saveAndFlush(Notificacao.nova(usuarioId, tipo, titulo, "", criadaEm));
    }

    /** Captura o SQL que o Hibernate manda ao banco enquanto a ação roda, sem imprimir nada no console. */
    private List<String> sqlExecutadoPor(Runnable acao) {
        Logger logger = (Logger) LoggerFactory.getLogger("org.hibernate.SQL");
        ListAppender<ILoggingEvent> capturado = new ListAppender<>();
        capturado.start();
        Level nivelAnterior = logger.getLevel();
        boolean aditivoAnterior = logger.isAdditive();
        logger.setLevel(Level.DEBUG);
        logger.setAdditive(false);
        logger.addAppender(capturado);
        try {
            acao.run();
        } finally {
            logger.detachAppender(capturado);
            logger.setAdditive(aditivoAnterior);
            logger.setLevel(nivelAnterior);
        }
        return capturado.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void usuarioNovoAparaONomeEGuardaOEmailEmMinusculasNoBanco() {
        String sufixo = UUID.randomUUID().toString();
        Usuario salvo = usuarios.saveAndFlush(
                Usuario.novo("  Maria da Silva  ", "  Maria.Silva-" + sufixo + "@Example.COM  ", "hash-bcrypt", BASE));

        Usuario lido = usuarios.findById(salvo.getId()).orElseThrow();

        assertThat(lido.getNome()).isEqualTo("Maria da Silva");
        assertThat(lido.getEmail()).isEqualTo("maria.silva-" + sufixo + "@example.com");
        assertThat(lido.getSenhaHash()).isEqualTo("hash-bcrypt");
        assertThat(lido.getCriadoEm()).isEqualTo(BASE);
        assertThat(jdbc.queryForObject("select email from usuario where id = ?", String.class, salvo.getId()))
                .isEqualTo("maria.silva-" + sufixo + "@example.com");
    }

    @Test
    void buscarPorEmailIgnoraACaixa() {
        Usuario salvo = salvarUsuario();

        assertThat(usuarios.findByEmailIgnoreCase(salvo.getEmail().toUpperCase()))
                .get()
                .extracting(Usuario::getId)
                .isEqualTo(salvo.getId());
        assertThat(usuarios.findByEmailIgnoreCase(emailNovo())).isEmpty();
    }

    @Test
    void verificarSeOEmailExisteIgnoraACaixa() {
        Usuario salvo = salvarUsuario();

        assertThat(usuarios.existsByEmailIgnoreCase(salvo.getEmail().toUpperCase())).isTrue();
        assertThat(usuarios.existsByEmailIgnoreCase(emailNovo())).isFalse();
    }

    /** A unicidade é um índice sobre lower(email): só um lower(...) na consulta consegue usá-lo. */
    @Test
    void asBuscasPorEmailComparamComLowerParaUsarOIndiceUnico() {
        Usuario salvo = salvarUsuario();

        List<String> sqlDaBusca = sqlExecutadoPor(() -> usuarios.findByEmailIgnoreCase(salvo.getEmail()));
        List<String> sqlDaExistencia = sqlExecutadoPor(() -> usuarios.existsByEmailIgnoreCase(salvo.getEmail()));

        assertThat(sqlDaBusca).hasSize(1).allSatisfy(sql -> assertThat(sql)
                .contains("lower(")
                .doesNotContainIgnoringCase("upper("));
        assertThat(sqlDaExistencia).hasSize(1).allSatisfy(sql -> assertThat(sql)
                .contains("lower(")
                .doesNotContainIgnoringCase("upper("));
    }

    @ParameterizedTest
    @EnumSource(StatusVendedor.class)
    void todoStatusDeVendedorPassaPelaJpaEPeloCheckDoBanco(StatusVendedor status) {
        Usuario usuario = salvarUsuario();

        perfis.saveAndFlush(PerfilVendedor.de(usuario.getId(), status, BASE));

        PerfilVendedor lido = perfis.findById(usuario.getId()).orElseThrow();
        assertThat(lido.getStatus()).isEqualTo(status);
        assertThat(jdbc.queryForObject(
                        "select status from perfil_vendedor where usuario_id = ?", String.class, usuario.getId()))
                .isEqualTo(status.name());
    }

    @ParameterizedTest
    @EnumSource(TipoNotificacao.class)
    void todoTipoDeNotificacaoPassaPelaJpaEPeloCheckDoBanco(TipoNotificacao tipo) {
        Usuario usuario = salvarUsuario();
        Notificacao salva = notificacoes.saveAndFlush(
                Notificacao.nova(usuario.getId(), tipo, "Título", "Resumo da notificação", BASE));

        Notificacao lida = notificacoes.findById(salva.getId()).orElseThrow();

        assertThat(lida.getTipo()).isEqualTo(tipo);
        assertThat(lida.getUsuarioId()).isEqualTo(usuario.getId());
        assertThat(lida.getTitulo()).isEqualTo("Título");
        assertThat(lida.getResumo()).isEqualTo("Resumo da notificação");
        assertThat(lida.getCriadaEm()).isEqualTo(BASE);
        assertThat(lida.isLida()).isFalse();
        assertThat(jdbc.queryForObject("select tipo from notificacao where id = ?", String.class, salva.getId()))
                .isEqualTo(tipo.name());
    }

    @Test
    void definirLidaPersisteEBuscarPorIdSoEnxergaAsNotificacoesDoDono() {
        Usuario dono = salvarUsuario();
        Usuario outro = salvarUsuario();
        Notificacao notificacao = salvarNotificacao(dono.getId(), TipoNotificacao.SISTEMA, "x", BASE);

        notificacao.definirLida(true);
        notificacoes.saveAndFlush(notificacao);

        assertThat(notificacoes.findByIdAndUsuarioId(notificacao.getId(), dono.getId()))
                .get()
                .extracting(Notificacao::isLida)
                .isEqualTo(true);
        assertThat(notificacoes.findByIdAndUsuarioId(notificacao.getId(), outro.getId()))
                .isEmpty();
    }

    @Test
    void contarNaoLidasPorTipoConsideraSoAsNaoLidasDoUsuario() {
        Usuario usuario = salvarUsuario();
        Usuario outro = salvarUsuario();
        salvarNotificacao(usuario.getId(), TipoNotificacao.CHAT, "chat 1", BASE);
        salvarNotificacao(usuario.getId(), TipoNotificacao.CHAT, "chat 2", BASE.plusSeconds(1));
        Notificacao chatLida = salvarNotificacao(usuario.getId(), TipoNotificacao.CHAT, "chat lida", BASE.plusSeconds(2));
        chatLida.definirLida(true);
        notificacoes.saveAndFlush(chatLida);
        salvarNotificacao(usuario.getId(), TipoNotificacao.PROPOSTA, "proposta", BASE.plusSeconds(3));
        salvarNotificacao(outro.getId(), TipoNotificacao.CHAT, "de outro usuário", BASE.plusSeconds(4));

        List<ContagemPorTipo> contagens = notificacoes.contarNaoLidasPorTipo(usuario.getId());

        assertThat(contagens.stream().collect(Collectors.toMap(ContagemPorTipo::getTipo, ContagemPorTipo::getTotal)))
                .isEqualTo(Map.of(TipoNotificacao.CHAT, 2L, TipoNotificacao.PROPOSTA, 1L));
        assertThat(notificacoes.contarNaoLidasPorTipo(UUID.randomUUID())).isEmpty();
    }

    @Test
    void listarPorTipoDevolveAsMaisRecentesPrimeiroEPagina() {
        Usuario usuario = salvarUsuario();
        Usuario outro = salvarUsuario();
        salvarNotificacao(usuario.getId(), TipoNotificacao.CHAT, "chat 1", BASE.plus(1, ChronoUnit.MINUTES));
        salvarNotificacao(usuario.getId(), TipoNotificacao.PROPOSTA, "proposta 2", BASE.plus(2, ChronoUnit.MINUTES));
        salvarNotificacao(usuario.getId(), TipoNotificacao.CHAT, "chat 3", BASE.plus(3, ChronoUnit.MINUTES));
        salvarNotificacao(usuario.getId(), TipoNotificacao.SISTEMA, "sistema 4", BASE.plus(4, ChronoUnit.MINUTES));
        salvarNotificacao(usuario.getId(), TipoNotificacao.CHAT, "chat 5", BASE.plus(5, ChronoUnit.MINUTES));
        salvarNotificacao(outro.getId(), TipoNotificacao.CHAT, "de outro usuário", BASE.plus(6, ChronoUnit.MINUTES));
        List<TipoNotificacao> tipos = List.of(TipoNotificacao.CHAT, TipoNotificacao.PROPOSTA);

        List<Notificacao> primeira = notificacoes.findByUsuarioIdAndTipoInOrderByCriadaEmDesc(
                usuario.getId(), tipos, PageRequest.of(0, 2));
        List<Notificacao> segunda = notificacoes.findByUsuarioIdAndTipoInOrderByCriadaEmDesc(
                usuario.getId(), tipos, PageRequest.of(1, 2));
        List<Notificacao> terceira = notificacoes.findByUsuarioIdAndTipoInOrderByCriadaEmDesc(
                usuario.getId(), tipos, PageRequest.of(2, 2));

        assertThat(primeira).extracting(Notificacao::getTitulo).containsExactly("chat 5", "chat 3");
        assertThat(segunda).extracting(Notificacao::getTitulo).containsExactly("proposta 2", "chat 1");
        assertThat(terceira).isEmpty();
    }

    /**
     * Instantes fixos e bem antigos: nenhum outro teste guarda tokens com vencimento tão distante, então a contagem
     * devolvida é exata mesmo com o banco compartilhado.
     */
    @Test
    void removerExpiradosApagaSoOsVencidosEDevolveQuantosForam() {
        Instant agora = Instant.parse("2001-01-01T00:00:00Z");
        TokenRevogado vencidoHaUmSegundo =
                tokensRevogados.saveAndFlush(new TokenRevogado(UUID.randomUUID(), agora.minusSeconds(1)));
        TokenRevogado vencidoHaUmDia =
                tokensRevogados.saveAndFlush(new TokenRevogado(UUID.randomUUID(), agora.minus(1, ChronoUnit.DAYS)));
        TokenRevogado venceExatamenteAgora =
                tokensRevogados.saveAndFlush(new TokenRevogado(UUID.randomUUID(), agora));
        TokenRevogado aindaValido =
                tokensRevogados.saveAndFlush(new TokenRevogado(UUID.randomUUID(), agora.plusSeconds(1)));

        int removidos = tokensRevogados.removerExpirados(agora);

        assertThat(removidos).isEqualTo(2);
        assertThat(tokensRevogados.existsById(vencidoHaUmSegundo.getJti())).isFalse();
        assertThat(tokensRevogados.existsById(vencidoHaUmDia.getJti())).isFalse();
        assertThat(tokensRevogados.existsById(venceExatamenteAgora.getJti())).isTrue();
        assertThat(tokensRevogados.existsById(aindaValido.getJti())).isTrue();
    }

    /** O logout depende disto: revogar de novo o mesmo jti não falha nem sobrescreve, para qualquer concorrência. */
    @Test
    void revogarGravaOTokenUmaVezEIgnoraORepetidoSemErro() {
        UUID jti = UUID.randomUUID();
        Instant expiraEm = Instant.parse("2031-05-06T07:08:09Z");

        int primeira = tokensRevogados.revogar(jti, expiraEm);
        int repetida = tokensRevogados.revogar(jti, expiraEm.plusSeconds(3600));

        assertThat(primeira).isEqualTo(1);
        assertThat(repetida).isZero();
        assertThat(tokensRevogados.findById(jti)).get().extracting(TokenRevogado::getExpiraEm).isEqualTo(expiraEm);
    }
}

package br.com.autopecas.notificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.autopecas.ApoioDeTeste;
import br.com.autopecas.TesteDeIntegracao;
import br.com.autopecas.conta.internal.domain.Usuario;
import br.com.autopecas.notificacao.internal.domain.Notificacao;
import br.com.autopecas.notificacao.internal.domain.TipoNotificacao;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@TesteDeIntegracao
class NotificacaoTest {

    private static final String ROTA = "/api/v1/notificacoes";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApoioDeTeste apoio;

    Usuario usuario;

    @BeforeEach
    void criarUsuario() {
        usuario = apoio.usuario();
    }

    private MockHttpServletRequestBuilder autenticada(MockHttpServletRequestBuilder requisicao, Usuario de) {
        return requisicao.cookie(apoio.cookieDe(de)).header("X-Auth-Id", apoio.authIdDe(de));
    }

    private MockHttpServletRequestBuilder autenticada(MockHttpServletRequestBuilder requisicao) {
        return autenticada(requisicao, usuario);
    }

    private Notificacao criar(TipoNotificacao tipo, boolean lida, long segundosAtras, String titulo) {
        return apoio.notificacao(usuario, tipo, lida, Instant.now().minusSeconds(segundosAtras), titulo);
    }

    private static String lida(boolean valor) {
        return "{\"lida\": " + valor + "}";
    }

    @Test
    void usuarioSemNotificacoesRecebeListaVaziaEContadoresZerados() throws Exception {
        mockMvc.perform(autenticada(get(ROTA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(0)))
                .andExpect(jsonPath("$.naoLidas.chat").value(0))
                .andExpect(jsonPath("$.naoLidas.outras").value(0))
                .andExpect(jsonPath("$.naoLidas.pecasAProcura").value(0));
    }

    @Test
    void oContratoTemOsCamposQueOFrontendConsome() throws Exception {
        Notificacao criada = criar(TipoNotificacao.CHAT, false, 60, "Auto Peças Silva");

        String criadaEm = mockMvc.perform(autenticada(get(ROTA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].id").value(criada.getId().toString()))
                .andExpect(jsonPath("$.itens[0].tipo").value("CHAT"))
                .andExpect(jsonPath("$.itens[0].titulo").value("Auto Peças Silva"))
                .andExpect(jsonPath("$.itens[0].resumo").value("Resumo de Auto Peças Silva"))
                .andExpect(jsonPath("$.itens[0].lida").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // criadaEm é um instante ISO 8601 em UTC (termina em Z), como o frontend valida.
        assertThat(criadaEm).containsPattern("\"criadaEm\":\"\\d{4}-\\d{2}-\\d{2}T[\\d:.]+Z\"");
    }

    @Test
    void listaDaMaisRecenteParaAMaisAntiga() throws Exception {
        criar(TipoNotificacao.CHAT, false, 3000, "antiga");
        criar(TipoNotificacao.PROPOSTA, false, 10, "nova");
        criar(TipoNotificacao.SISTEMA, true, 500, "meio");

        mockMvc.perform(autenticada(get(ROTA)))
                .andExpect(jsonPath("$.itens[0].titulo").value("nova"))
                .andExpect(jsonPath("$.itens[1].titulo").value("meio"))
                .andExpect(jsonPath("$.itens[2].titulo").value("antiga"));
    }

    @Test
    void cadaUsuarioSoVeAsProprias() throws Exception {
        criar(TipoNotificacao.CHAT, false, 10, "minha");
        Usuario outro = apoio.usuario();
        apoio.notificacao(outro, TipoNotificacao.CHAT, false, Instant.now(), "do outro");

        mockMvc.perform(autenticada(get(ROTA)))
                .andExpect(jsonPath("$.itens", hasSize(1)))
                .andExpect(jsonPath("$.itens[0].titulo").value("minha"))
                .andExpect(jsonPath("$.naoLidas.chat").value(1));
    }

    @Test
    void filtraPorTipo() throws Exception {
        criar(TipoNotificacao.CHAT, false, 10, "chat");
        criar(TipoNotificacao.PROPOSTA, false, 20, "proposta");
        criar(TipoNotificacao.PECA_A_PROCURA, false, 30, "procura");

        mockMvc.perform(autenticada(get(ROTA).param("tipos", "CHAT,PROPOSTA")))
                .andExpect(jsonPath("$.itens", hasSize(2)))
                .andExpect(jsonPath("$.itens[0].titulo").value("chat"))
                .andExpect(jsonPath("$.itens[1].titulo").value("proposta"));
    }

    @Test
    void semOParametroTiposDevolveTodosOsTipos() throws Exception {
        criar(TipoNotificacao.CHAT, false, 10, "chat");
        criar(TipoNotificacao.PECA_A_PROCURA, false, 20, "procura");

        mockMvc.perform(autenticada(get(ROTA))).andExpect(jsonPath("$.itens", hasSize(2)));
    }

    @Test
    void oLimiteCortaAListaEOPadraoEVinte() throws Exception {
        for (int i = 0; i < 25; i++) {
            criar(TipoNotificacao.SISTEMA, true, 10L + i, "n" + i);
        }

        mockMvc.perform(autenticada(get(ROTA))).andExpect(jsonPath("$.itens", hasSize(20)));
        mockMvc.perform(autenticada(get(ROTA).param("limite", "3"))).andExpect(jsonPath("$.itens", hasSize(3)));
    }

    @Test
    void osContadoresValemParaTodosOsTiposMesmoComFiltro() throws Exception {
        criar(TipoNotificacao.CHAT, false, 10, "chat 1");
        criar(TipoNotificacao.CHAT, false, 20, "chat 2");
        criar(TipoNotificacao.CHAT, true, 30, "chat lido");
        criar(TipoNotificacao.PROPOSTA, false, 40, "proposta");
        criar(TipoNotificacao.SISTEMA, true, 50, "sistema lido");
        criar(TipoNotificacao.PECA_A_PROCURA, false, 60, "procura 1");
        criar(TipoNotificacao.PECA_A_PROCURA, false, 70, "procura 2");

        mockMvc.perform(autenticada(get(ROTA).param("tipos", "CHAT")))
                .andExpect(jsonPath("$.itens", hasSize(3)))
                .andExpect(jsonPath("$.naoLidas.chat").value(2))
                .andExpect(jsonPath("$.naoLidas.outras").value(1))
                .andExpect(jsonPath("$.naoLidas.pecasAProcura").value(2));
    }

    @Test
    void cadaTipoNaoLidoAlimentaOContadorCerto() throws Exception {
        // Uma não lida de cada tipo (as lidas não contam): outras = PROPOSTA + SISTEMA.
        for (TipoNotificacao tipo : TipoNotificacao.values()) {
            criar(tipo, false, 10, tipo + " não lida");
            criar(tipo, true, 20, tipo + " lida");
        }

        mockMvc.perform(autenticada(get(ROTA)))
                .andExpect(jsonPath("$.itens", hasSize(TipoNotificacao.values().length * 2)))
                .andExpect(jsonPath("$.naoLidas.chat").value(1))
                .andExpect(jsonPath("$.naoLidas.outras").value(2))
                .andExpect(jsonPath("$.naoLidas.pecasAProcura").value(1));
    }

    @Test
    void marcarComoLidaAtualizaAListaEOsContadores() throws Exception {
        Notificacao chat = criar(TipoNotificacao.CHAT, false, 10, "chat");
        criar(TipoNotificacao.CHAT, false, 20, "outro chat");

        mockMvc.perform(autenticada(patch(ROTA + "/" + chat.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lida(true))))
                .andExpect(status().isNoContent());

        mockMvc.perform(autenticada(get(ROTA)))
                .andExpect(jsonPath("$.itens[0].lida").value(true))
                .andExpect(jsonPath("$.naoLidas.chat").value(1));
    }

    @Test
    void marcarDuasVezesEIdempotenteEDaParaDesmarcar() throws Exception {
        Notificacao chat = criar(TipoNotificacao.CHAT, false, 10, "chat");
        String rota = ROTA + "/" + chat.getId();

        mockMvc.perform(autenticada(patch(rota).contentType(MediaType.APPLICATION_JSON).content(lida(true))))
                .andExpect(status().isNoContent());
        mockMvc.perform(autenticada(patch(rota).contentType(MediaType.APPLICATION_JSON).content(lida(true))))
                .andExpect(status().isNoContent());
        mockMvc.perform(autenticada(patch(rota).contentType(MediaType.APPLICATION_JSON).content(lida(false))))
                .andExpect(status().isNoContent());

        mockMvc.perform(autenticada(get(ROTA))).andExpect(jsonPath("$.naoLidas.chat").value(1));
    }

    @Test
    void notificacaoDeOutroUsuarioResponde404ENaoMudaNada() throws Exception {
        Usuario outro = apoio.usuario();
        Notificacao doOutro = apoio.notificacao(outro, TipoNotificacao.CHAT, false, Instant.now(), "do outro");

        mockMvc.perform(autenticada(patch(ROTA + "/" + doOutro.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lida(true))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Notificação não encontrada."));

        mockMvc.perform(autenticada(get(ROTA), outro)).andExpect(jsonPath("$.naoLidas.chat").value(1));
    }

    @Test
    void notificacaoInexistenteResponde404() throws Exception {
        mockMvc.perform(autenticada(patch(ROTA + "/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lida(true))))
                .andExpect(status().isNotFound());
    }

    @Test
    void idQueNaoEUuidDa400() throws Exception {
        mockMvc.perform(autenticada(patch(ROTA + "/nao-e-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lida(true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requisição inválida"));
    }

    @Test
    void corpoSemOCampoLidaDa400ComOCampoComErro() throws Exception {
        Notificacao chat = criar(TipoNotificacao.CHAT, false, 10, "chat");

        mockMvc.perform(autenticada(patch(ROTA + "/" + chat.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Dados inválidos"))
                .andExpect(jsonPath("$.erros.lida").isNotEmpty());
    }

    @Test
    void lidaQueNaoEBooleanoEAusenciaDeCorpoDao400() throws Exception {
        Notificacao chat = criar(TipoNotificacao.CHAT, false, 10, "chat");
        String rota = ROTA + "/" + chat.getId();

        mockMvc.perform(autenticada(patch(rota).contentType(MediaType.APPLICATION_JSON).content("{\"lida\": \"talvez\"}")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(autenticada(patch(rota).contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(autenticada(patch(rota).contentType(MediaType.APPLICATION_JSON).content("{\"lida\": \"\"}")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(autenticada(patch(rota).contentType(MediaType.APPLICATION_JSON).content("{\"lida\": null}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.lida").isNotEmpty());
    }

    // O Jackson converte texto e número em Boolean por padrão; o contrato diz que lida não booleano é 400.
    @ParameterizedTest
    @ValueSource(strings = {"\"true\"", "\"false\"", "\"1\"", "1", "0", "2", "1.0"})
    void lidaQueEscalarNaoBooleanoDa400ENaoAlteraNada(String valor) throws Exception {
        Notificacao naoLida = criar(TipoNotificacao.CHAT, false, 10, "não lida");
        Notificacao jaLida = criar(TipoNotificacao.CHAT, true, 20, "já lida");

        for (Notificacao notificacao : new Notificacao[] {naoLida, jaLida}) {
            mockMvc.perform(autenticada(patch(ROTA + "/" + notificacao.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"lida\": " + valor + "}")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Corpo da requisição inválido"));
        }

        mockMvc.perform(autenticada(get(ROTA)))
                .andExpect(jsonPath("$.itens[0].titulo").value("não lida"))
                .andExpect(jsonPath("$.itens[0].lida").value(false))
                .andExpect(jsonPath("$.itens[1].titulo").value("já lida"))
                .andExpect(jsonPath("$.itens[1].lida").value(true))
                .andExpect(jsonPath("$.naoLidas.chat").value(1));
    }

    @Test
    void tipoDesconhecidoNaConsultaDa400() throws Exception {
        mockMvc.perform(autenticada(get(ROTA).param("tipos", "CHAT,EMAIL")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requisição inválida"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "101", "abc"})
    void limiteForaDoIntervaloDa400(String limite) throws Exception {
        mockMvc.perform(autenticada(get(ROTA).param("limite", limite))).andExpect(status().isBadRequest());
    }

    @Test
    void semAutenticacaoOuSemOAuthIdNadaAcontece() throws Exception {
        Notificacao chat = criar(TipoNotificacao.CHAT, false, 10, "chat");
        String rota = ROTA + "/" + chat.getId();

        mockMvc.perform(get(ROTA)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(ROTA).cookie(apoio.cookieDe(usuario))).andExpect(status().isUnauthorized());
        mockMvc.perform(patch(rota).contentType(MediaType.APPLICATION_JSON).content(lida(true)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch(rota)
                        .cookie(apoio.cookieDe(usuario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lida(true)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(autenticada(get(ROTA))).andExpect(jsonPath("$.naoLidas.chat").value(1));
    }
}

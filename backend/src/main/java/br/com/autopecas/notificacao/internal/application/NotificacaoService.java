package br.com.autopecas.notificacao.internal.application;

import br.com.autopecas.compartilhado.ErroDeNegocio;
import br.com.autopecas.notificacao.internal.application.PainelDeNotificacoes.ContadoresNaoLidas;
import br.com.autopecas.notificacao.internal.domain.Notificacao;
import br.com.autopecas.notificacao.internal.domain.TipoNotificacao;
import br.com.autopecas.notificacao.internal.infra.persistencia.NotificacaoRepository;
import br.com.autopecas.notificacao.internal.infra.persistencia.NotificacaoRepository.ContagemPorTipo;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificacaoService {

    private final NotificacaoRepository notificacoes;

    NotificacaoService(NotificacaoRepository notificacoes) {
        this.notificacoes = notificacoes;
    }

    @Transactional(readOnly = true)
    public PainelDeNotificacoes listar(UUID usuarioId, Collection<TipoNotificacao> tipos, int limite) {
        Collection<TipoNotificacao> pedidos = tipos == null || tipos.isEmpty() ? EnumSet.allOf(TipoNotificacao.class) : tipos;
        List<Notificacao> itens = notificacoes.findByUsuarioIdAndTipoInOrderByCriadaEmDesc(
                usuarioId, pedidos, PageRequest.of(0, limite));
        return new PainelDeNotificacoes(itens, contadores(usuarioId));
    }

    /** Uma notificação de outro usuário responde "não encontrada", para não revelar que ela existe. */
    @Transactional
    public void definirLida(UUID usuarioId, UUID notificacaoId, boolean lida) {
        Notificacao notificacao = notificacoes.findByIdAndUsuarioId(notificacaoId, usuarioId)
                .orElseThrow(() -> ErroDeNegocio.naoEncontrado("Notificação não encontrada."));
        notificacao.definirLida(lida);
    }

    private ContadoresNaoLidas contadores(UUID usuarioId) {
        long chat = 0;
        long outras = 0;
        long pecasAProcura = 0;
        for (ContagemPorTipo contagem : notificacoes.contarNaoLidasPorTipo(usuarioId)) {
            switch (contagem.getTipo()) {
                case CHAT -> chat += contagem.getTotal();
                case PECA_A_PROCURA -> pecasAProcura += contagem.getTotal();
                case PROPOSTA, SISTEMA -> outras += contagem.getTotal();
            }
        }
        return new ContadoresNaoLidas(chat, outras, pecasAProcura);
    }
}

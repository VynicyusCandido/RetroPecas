package br.com.autopecas.notificacao.internal.application;

import br.com.autopecas.notificacao.internal.domain.Notificacao;
import java.util.List;

/** Os contadores valem para todos os tipos, não só para os que vieram na lista. */
public record PainelDeNotificacoes(List<Notificacao> itens, ContadoresNaoLidas naoLidas) {

    public record ContadoresNaoLidas(long chat, long outras, long pecasAProcura) {
    }
}

package br.com.autopecas.notificacao.internal.infra.persistencia;

import br.com.autopecas.notificacao.internal.domain.Notificacao;
import br.com.autopecas.notificacao.internal.domain.TipoNotificacao;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificacaoRepository extends JpaRepository<Notificacao, UUID> {

    List<Notificacao> findByUsuarioIdAndTipoInOrderByCriadaEmDesc(
            UUID usuarioId, Collection<TipoNotificacao> tipos, Pageable pagina);

    Optional<Notificacao> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    @Query("select n.tipo as tipo, count(n) as total from Notificacao n "
            + "where n.usuarioId = :usuarioId and n.lida = false group by n.tipo")
    List<ContagemPorTipo> contarNaoLidasPorTipo(@Param("usuarioId") UUID usuarioId);

    interface ContagemPorTipo {

        TipoNotificacao getTipo();

        long getTotal();
    }
}

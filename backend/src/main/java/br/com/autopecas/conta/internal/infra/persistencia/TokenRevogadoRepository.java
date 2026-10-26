package br.com.autopecas.conta.internal.infra.persistencia;

import br.com.autopecas.conta.internal.domain.TokenRevogado;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface TokenRevogadoRepository extends JpaRepository<TokenRevogado, UUID> {

    /**
     * Grava a revogação numa só instrução, ignorando um jti já revogado. Um "existe? então salva" não serve: dois
     * logouts simultâneos com o mesmo token passariam os dois pela checagem e o segundo falharia na chave primária.
     *
     * @return 1 se gravou, 0 se o token já estava revogado
     */
    @Transactional
    @Modifying
    @Query(
            value = "insert into token_revogado (jti, expira_em) values (:jti, :expiraEm) on conflict (jti) do nothing",
            nativeQuery = true)
    int revogar(@Param("jti") UUID jti, @Param("expiraEm") Instant expiraEm);

    @Transactional
    @Modifying
    @Query("delete from TokenRevogado t where t.expiraEm < :agora")
    int removerExpirados(@Param("agora") Instant agora);
}

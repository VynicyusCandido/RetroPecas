package br.com.autopecas.conta.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Token cujo logout já foi feito. Fica guardado só até o vencimento natural do JWT. */
@Entity
@Table(name = "token_revogado")
public class TokenRevogado {

    @Id
    private UUID jti;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    protected TokenRevogado() {
    }

    public TokenRevogado(UUID jti, Instant expiraEm) {
        this.jti = jti;
        this.expiraEm = expiraEm;
    }

    public UUID getJti() {
        return jti;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }
}

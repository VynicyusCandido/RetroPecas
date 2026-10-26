package br.com.autopecas.conta.internal.application;

import br.com.autopecas.conta.internal.infra.persistencia.TokenRevogadoRepository;
import java.time.Clock;
import java.time.Duration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Um token vencido é recusado de qualquer jeito, então não precisa continuar na lista de revogados. */
@Component
public class LimpezaDeTokensRevogados {

    /**
     * Precisa ser maior que a tolerância de relógio padrão (60 s) do validador de "exp" do JWT: o token ainda é aceito
     * até "exp" + 60 s, e apagar a revogação antes disso faria um token com logout voltar a valer.
     */
    private static final Duration MARGEM_DE_RELOGIO = Duration.ofMinutes(5);

    private final TokenRevogadoRepository revogados;
    private final Clock relogio;

    LimpezaDeTokensRevogados(TokenRevogadoRepository revogados, Clock relogio) {
        this.revogados = revogados;
        this.relogio = relogio;
    }

    @Scheduled(initialDelayString = "PT5M", fixedDelayString = "PT1H")
    @Transactional
    public int removerExpirados() {
        return revogados.removerExpirados(relogio.instant().minus(MARGEM_DE_RELOGIO));
    }
}

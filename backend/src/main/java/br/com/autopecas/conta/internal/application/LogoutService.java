package br.com.autopecas.conta.internal.application;

import br.com.autopecas.conta.internal.infra.persistencia.TokenRevogadoRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LogoutService {

    private final TokenRevogadoRepository revogados;

    LogoutService(TokenRevogadoRepository revogados) {
        this.revogados = revogados;
    }

    /** Idempotente, inclusive com chamadas simultâneas para o mesmo token (um duplo clique no botão de sair). */
    @Transactional
    public void encerrar(UUID jti, Instant expiraEm) {
        revogados.revogar(jti, expiraEm);
    }
}

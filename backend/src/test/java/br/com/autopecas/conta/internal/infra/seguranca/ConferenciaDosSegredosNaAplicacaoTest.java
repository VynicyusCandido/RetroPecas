package br.com.autopecas.conta.internal.infra.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.autopecas.TesteDeIntegracao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@TesteDeIntegracao
class ConferenciaDosSegredosNaAplicacaoTest {

    @Autowired
    ConferenciaDosSegredos conferencia;

    /** Sem este bean na aplicação de verdade, a conferência nunca roda (e o contexto de teste sobe com segredos distintos). */
    @Test
    void aAplicacaoTemAConferenciaDosSegredosNaSubida() {
        assertThat(conferencia).isNotNull();
    }
}

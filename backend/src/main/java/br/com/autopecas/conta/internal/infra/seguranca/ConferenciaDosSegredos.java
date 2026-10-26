package br.com.autopecas.conta.internal.infra.seguranca;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/** Roda na subida: a aplicação não inicia se os segredos do JWT e do X-Auth-Id forem o mesmo. */
@Component
class ConferenciaDosSegredos implements InitializingBean {

    private final SegurancaProperties propriedades;

    ConferenciaDosSegredos(SegurancaProperties propriedades) {
        this.propriedades = propriedades;
    }

    @Override
    public void afterPropertiesSet() {
        propriedades.exigirSegredosDistintos();
    }
}

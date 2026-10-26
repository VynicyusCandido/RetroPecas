package br.com.autopecas.compartilhado;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/**
 * O contrato da API diz que um campo booleano que não seja {@code true} ou {@code false} é um 400, mas o Jackson,
 * por padrão, converte texto ({@code "true"}) e número ({@code 1}) em Boolean. Aqui só o Boolean fica estrito:
 * texto, inteiro e decimal viram erro de leitura do corpo (400 em Problem Details).
 */
@Component
class BooleanEstritoNoJson implements JsonMapperBuilderCustomizer {

    @Override
    public void customize(JsonMapper.Builder construtor) {
        construtor.withCoercionConfig(LogicalType.Boolean, config -> config
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail));
    }
}

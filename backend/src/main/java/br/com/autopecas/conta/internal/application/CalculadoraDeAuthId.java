package br.com.autopecas.conta.internal.application;

import java.util.UUID;

/** Porta: o hash de identificação (cabeçalho X-Auth-Id) que confirma o usuário da sessão. */
public interface CalculadoraDeAuthId {

    String calcular(UUID usuarioId);

    boolean confere(UUID usuarioId, String informado);
}

package br.com.autopecas.conta.internal.application;

import java.util.UUID;

/** Porta: quem sabe emitir o token de sessão de um usuário. */
public interface EmissorDeToken {

    TokenEmitido emitir(UUID usuarioId);
}

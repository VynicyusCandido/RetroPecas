package br.com.autopecas.conta.internal.web;

import br.com.autopecas.conta.internal.application.SessaoDoUsuario;

/** Contrato de GET /api/v1/me e do corpo do login. "vendedor" é null para quem só é comprador. */
record SessaoResponse(String id, String nome, String email, VendedorResponse vendedor, String authId) {

    record VendedorResponse(String status) {
    }

    static SessaoResponse de(SessaoDoUsuario sessao) {
        VendedorResponse vendedor =
                sessao.vendedor() == null ? null : new VendedorResponse(sessao.vendedor().name());
        return new SessaoResponse(sessao.id().toString(), sessao.nome(), sessao.email(), vendedor, sessao.authId());
    }
}

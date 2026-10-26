package br.com.autopecas.conta.internal.web;

import br.com.autopecas.conta.internal.domain.Usuario;

record RegistroResponse(String id, String nome, String email) {

    static RegistroResponse de(Usuario usuario) {
        return new RegistroResponse(usuario.getId().toString(), usuario.getNome(), usuario.getEmail());
    }
}

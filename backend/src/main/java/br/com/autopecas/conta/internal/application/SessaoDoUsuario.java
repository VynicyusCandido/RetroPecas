package br.com.autopecas.conta.internal.application;

import br.com.autopecas.conta.internal.domain.StatusVendedor;
import java.util.UUID;

/** vendedor é null para quem só tem o perfil de comprador. */
public record SessaoDoUsuario(UUID id, String nome, String email, StatusVendedor vendedor, String authId) {
}

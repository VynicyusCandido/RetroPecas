package br.com.autopecas.conta.internal.application;

public record ResultadoDeLogin(SessaoDoUsuario sessao, TokenEmitido token) {
}

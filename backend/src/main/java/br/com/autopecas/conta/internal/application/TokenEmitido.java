package br.com.autopecas.conta.internal.application;

import java.time.Instant;

public record TokenEmitido(String jwt, Instant expiraEm) {
}

package br.com.autopecas.conta.internal.web;

import jakarta.validation.constraints.NotBlank;

record LoginRequest(
        @NotBlank(message = "Informe o e-mail.") String email, @NotBlank(message = "Informe a senha.") String senha) {
}

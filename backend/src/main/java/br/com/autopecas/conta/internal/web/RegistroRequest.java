package br.com.autopecas.conta.internal.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record RegistroRequest(
        @NotBlank(message = "Informe o nome.") @Size(max = 120, message = "O nome pode ter no máximo 120 caracteres.")
                String nome,
        @NotBlank(message = "Informe o e-mail.")
                @Email(regexp = PADRAO_DE_EMAIL, message = "Informe um e-mail válido.")
                @Size(max = 254, message = "O e-mail pode ter no máximo 254 caracteres.")
                String email,
        @NotBlank(message = "Informe a senha.") @Size(min = 8, message = "A senha precisa ter pelo menos 8 caracteres.")
                String senha) {

    /**
     * O @Email do Hibernate Validator é mais frouxo que o z.email() do frontend (Zod 4): aceita "maria@hotmail",
     * "ana&bia@exemplo.com", "ana@[127.0.0.1]" e "joão@exemplo.com.br", e o /me dessa conta quebraria o cliente
     * (erro de contrato) para sempre. Este padrão é o regex de z.email() (zod/v4/core/regexes.js, `email`, Zod 4.6.5):
     * o cadastro só aceita o que o frontend também aceita.
     */
    static final String PADRAO_DE_EMAIL =
            "^(?:[A-Za-z0-9_'+\\-]+\\.)*[A-Za-z0-9_'+\\-]*[A-Za-z0-9_+-]@(?:[A-Za-z0-9][A-Za-z0-9\\-]*\\.)+[A-Za-z]{2,}$";

    /** Espaços nas pontas (o teclado do celular costuma deixar um) são removidos antes da validação. */
    RegistroRequest {
        nome = nome == null ? null : nome.trim();
        email = email == null ? null : email.trim();
    }
}

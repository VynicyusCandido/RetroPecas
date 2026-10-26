package br.com.autopecas.conta.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @Column(nullable = false)
    private String nome;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    protected Usuario() {
    }

    /** O e-mail é guardado em minúsculas: a unicidade no banco também ignora a caixa. */
    public static Usuario novo(String nome, String email, String senhaHash, Instant criadoEm) {
        Usuario usuario = new Usuario();
        usuario.id = UUID.randomUUID();
        usuario.nome = nome.trim();
        usuario.email = email.trim().toLowerCase(Locale.ROOT);
        usuario.senhaHash = senhaHash;
        usuario.criadoEm = criadoEm;
        return usuario;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public String getNome() {
        return nome;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}

package br.com.autopecas.notificacao.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notificacao")
public class Notificacao {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoNotificacao tipo;

    @Column(nullable = false)
    private String titulo;

    @Column(nullable = false)
    private String resumo;

    @Column(name = "criada_em", nullable = false, updatable = false)
    private Instant criadaEm;

    @Column(nullable = false)
    private boolean lida;

    protected Notificacao() {
    }

    public static Notificacao nova(
            UUID usuarioId, TipoNotificacao tipo, String titulo, String resumo, Instant criadaEm) {
        Notificacao notificacao = new Notificacao();
        notificacao.id = UUID.randomUUID();
        notificacao.usuarioId = usuarioId;
        notificacao.tipo = tipo;
        notificacao.titulo = titulo;
        notificacao.resumo = resumo;
        notificacao.criadaEm = criadaEm;
        notificacao.lida = false;
        return notificacao;
    }

    public void definirLida(boolean lida) {
        this.lida = lida;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public TipoNotificacao getTipo() {
        return tipo;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getResumo() {
        return resumo;
    }

    public Instant getCriadaEm() {
        return criadaEm;
    }

    public boolean isLida() {
        return lida;
    }
}

package br.com.autopecas.conta.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "perfil_vendedor")
public class PerfilVendedor {

    @Id
    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusVendedor status;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    protected PerfilVendedor() {
    }

    public static PerfilVendedor de(UUID usuarioId, StatusVendedor status, Instant criadoEm) {
        PerfilVendedor perfil = new PerfilVendedor();
        perfil.usuarioId = usuarioId;
        perfil.status = status;
        perfil.criadoEm = criadoEm;
        return perfil;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public StatusVendedor getStatus() {
        return status;
    }
}

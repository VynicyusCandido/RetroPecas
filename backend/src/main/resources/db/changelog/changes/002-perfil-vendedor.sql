--liquibase formatted sql

--changeset autopecas:002-criar-perfil-vendedor
CREATE TABLE perfil_vendedor (
    usuario_id  UUID        PRIMARY KEY REFERENCES usuario (id) ON DELETE CASCADE,
    status      VARCHAR(20) NOT NULL,
    criado_em   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_perfil_vendedor_status CHECK (status IN ('PENDENTE', 'APROVADO', 'REPROVADO', 'SUSPENSO'))
);
--rollback DROP TABLE perfil_vendedor;

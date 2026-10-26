--liquibase formatted sql

--changeset autopecas:001-criar-usuario
CREATE TABLE usuario (
    id          UUID         PRIMARY KEY,
    email       VARCHAR(254) NOT NULL,
    senha_hash  VARCHAR(100) NOT NULL,
    nome        VARCHAR(120) NOT NULL,
    criado_em   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_usuario_email ON usuario (lower(email));
--rollback DROP TABLE usuario;

--liquibase formatted sql

--changeset autopecas:003-criar-token-revogado
CREATE TABLE token_revogado (
    jti        UUID        PRIMARY KEY,
    expira_em  TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_token_revogado_expira_em ON token_revogado (expira_em);
--rollback DROP TABLE token_revogado;

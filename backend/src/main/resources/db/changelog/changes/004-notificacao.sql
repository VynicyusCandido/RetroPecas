--liquibase formatted sql

--changeset autopecas:004-criar-notificacao
CREATE TABLE notificacao (
    id          UUID         PRIMARY KEY,
    usuario_id  UUID         NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    tipo        VARCHAR(20)  NOT NULL,
    titulo      VARCHAR(200) NOT NULL,
    resumo      VARCHAR(500) NOT NULL DEFAULT '',
    criada_em   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    lida        BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_notificacao_tipo CHECK (tipo IN ('CHAT', 'PROPOSTA', 'PECA_A_PROCURA', 'SISTEMA'))
);

CREATE INDEX ix_notificacao_usuario_criada ON notificacao (usuario_id, criada_em DESC);
CREATE INDEX ix_notificacao_nao_lidas ON notificacao (usuario_id, tipo) WHERE NOT lida;
--rollback DROP TABLE notificacao;

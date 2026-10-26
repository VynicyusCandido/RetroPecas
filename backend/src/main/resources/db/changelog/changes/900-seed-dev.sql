--liquibase formatted sql

--changeset autopecas:900-seed-dev context:dev
-- Dados só de desenvolvimento (contexto "dev"): nunca rodam em produção nem nos testes.
-- Senha de todos os usuários: senha-dev-123
INSERT INTO usuario (id, email, senha_hash, nome) VALUES
    ('00000000-0000-0000-0000-000000000001', 'vynicyus@example.com', '$2a$10$JTgh2PgLgjDPB2ccXfY0buW3.uU8Uz9acfaKoB3JVSySGEoLm8v9e', 'Vynicyus Silva'),
    ('00000000-0000-0000-0000-000000000002', 'ana@example.com',       '$2a$10$JTgh2PgLgjDPB2ccXfY0buW3.uU8Uz9acfaKoB3JVSySGEoLm8v9e', 'Ana Souza'),
    ('00000000-0000-0000-0000-000000000003', 'pedro@example.com',     '$2a$10$JTgh2PgLgjDPB2ccXfY0buW3.uU8Uz9acfaKoB3JVSySGEoLm8v9e', 'Pedro Lima');

INSERT INTO perfil_vendedor (usuario_id, status) VALUES
    ('00000000-0000-0000-0000-000000000001', 'APROVADO'),
    ('00000000-0000-0000-0000-000000000003', 'PENDENTE');

INSERT INTO notificacao (id, usuario_id, tipo, titulo, resumo, criada_em, lida) VALUES
    ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'CHAT',           'Auto Peças Silva',                 'Tenho o farol esquerdo do Gol G6, posso mandar foto?', now() - interval '4 minutes',  FALSE),
    ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'CHAT',           'Desmanche Rota 116',               'Consigo fazer R$ 180 se você retirar hoje.',           now() - interval '45 minutes', FALSE),
    ('10000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', 'PROPOSTA',       'Nova proposta: Retrovisor direito', 'Moto Center ofereceu R$ 89,90.',                       now() - interval '3 hours',    FALSE),
    ('10000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000001', 'SISTEMA',        'Seu pedido expira amanhã',          'Farol esquerdo · Gol 2014',                            now() - interval '30 hours',   TRUE),
    ('10000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000001', 'CHAT',           'Pneus & Cia',                      'Ainda precisa do amortecedor?',                        now() - interval '10 days',    TRUE),
    ('10000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000001', 'PECA_A_PROCURA', 'Procuram: Farol esquerdo',         'Gol 1.0 2014 · Recife/PE',                             now() - interval '8 minutes',  FALSE),
    ('10000000-0000-0000-0000-000000000007', '00000000-0000-0000-0000-000000000001', 'PECA_A_PROCURA', 'Procuram: Amortecedor dianteiro',  'Uno 2012 · Olinda/PE',                                 now() - interval '90 minutes', FALSE),
    ('10000000-0000-0000-0000-000000000008', '00000000-0000-0000-0000-000000000001', 'PECA_A_PROCURA', 'Procuram: Bateria 60Ah',           'HB20 2016 · Recife/PE',                                now() - interval '5 hours',    TRUE);
--rollback DELETE FROM usuario WHERE id IN ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000003');

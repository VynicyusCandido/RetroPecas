# Diagramas

Diagramas em PlantUML derivados da spec [`2026-09-29-marketplace-autopecas-design.md`](../superpowers/specs/2026-09-29-marketplace-autopecas-design.md). Escopo: **MVP**. Itens de Fase 2 e Futuro só aparecem no ER, marcados.

| Arquivo | O que mostra | Requisitos da spec |
|---|---|---|
| [`01_casos_de_uso.puml`](01_casos_de_uso.puml) | Atores e casos de uso do MVP. Comprador e Vendedor herdam de um ator abstrato "Usuário" para os casos comuns (conta e chat) | Seção 7 (AUT, PED, CAT, CHT, LST, FEC, ADM) |
| [`02_contexto_c4.puml`](02_contexto_c4.puml) | Contexto C4 nível 1: pessoas, sistema e sistemas externos (FIPE, Google para o login, e-mail) | Seções 2 e 4 |
| [`03_sequencia_pedido_matching.puml`](03_sequencia_pedido_matching.puml) | Criação do pedido, fila e matching (catálogo e perfil) até a entrega | VEI-02, PED-01, PED-07, DIS-02 a DIS-05 |
| [`04_sequencia_negociacao_venda.puml`](04_sequencia_negociacao_venda.puml) | Chat, proposta e contraproposta, fechamento fora do sistema e marcação como Vendida | CHT-03 a CHT-06, FEC-01, FEC-02, FEC-06, RN-06, RN-11, RN-12 |
| [`05a_estados_pedido.puml`](05a_estados_pedido.puml) | Máquina de estados do pedido, com reabertura em até 30 dias | PED-04, PED-05, CHT-11, FEC-01, RN-07 |
| [`05b_estados_proposta.puml`](05b_estados_proposta.puml) | Máquina de estados da proposta: Enviada → Aceita → Vendida e saídas alternativas | CHT-05, CHT-06, RN-06, RN-07, RN-11, RN-12 |
| [`06_modelo_er.puml`](06_modelo_er.puml) | Modelo de dados **lógico** (rascunho, sem escolha de banco) | Seção 6, RNF-INT-01, RNF-SEG-07 |
| [`07_containers_c4.puml`](07_containers_c4.puml) | C4 nível 2: Next.js, API Spring Boot, PostgreSQL, armazenamento de imagens, FIPE, Google (login) e e-mail | Seção 3 da spec e [stack](../superpowers/specs/2026-09-29-stack-e-tdd-design.md) |
| [`08_componentes_api_c4.puml`](08_componentes_api_c4.puml) | C4 nível 3: módulos da API (Spring Modulith) e os eventos entre eles | RNF-MAN-01 e [stack](../superpowers/specs/2026-09-29-stack-e-tdd-design.md) |
| [`09_sequencia_autenticacao.puml`](09_sequencia_autenticacao.puml) | **Login com Google** (código + PKCE, validação do ID token) e a sessão com JWT em cookie HttpOnly e o cabeçalho `X-Auth-Id`: `/me`, chamada autenticada e logout | [Login com Google](../login-com-google-regras-de-seguranca.md) e [spec do backend](../superpowers/specs/2026-09-29-backend-autenticacao-design.md) |

## Como renderizar

- **VS Code:** extensão *PlantUML* (jebbs.plantuml), com `Alt+D` para pré-visualizar.
- **Linha de comando:** `java -jar plantuml.jar -tpng -charset UTF-8 *.puml`. Os diagramas de casos de uso, estados e ER precisam do Graphviz (`dot`).

## Decisões de modelagem que a spec deixava em aberto

- **Pedido "Concluído" tem resultado:** *Vendido* (final, não reabre) ou *Sem compra* (reabrível em até 30 dias).
- **Validade da proposta** vale só até ela ser Aceita. Depois disso, uma proposta Aceita só sai desse estado virando Vendida ou Expirada (quando outra proposta do pedido vira Vendida).
- **Cotação** é uma entidade própria no ER: o pedido visto na caixa do vendedor, sem conversa até ele responder.
- **Login só por Google:** o `Usuario` do ER guarda o `google_sub` (identidade) e não tem senha. "Vendedor com catálogo" e "sem catálogo" não são entidades: é uma situação calculada pelos itens ativos do catálogo (ver [stakeholders](../stakeholders.md)).

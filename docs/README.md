# Documentação do projeto

Marketplace reverso de autopeças (carros, motos e caminhões): o comprador lança o pedido da peça, e os vendedores respondem por chat. Projeto de TCC.

## Comece por aqui

| Documento | Para quê |
|---|---|
| [**Especificação do projeto**](especificacao-do-projeto.md) | Visão, escopo, decisões, **todos os requisitos**, regras, arquitetura, estado da implementação e próximos passos |
| [**Stakeholders**](stakeholders.md) | A lógica do comprador, do **vendedor com catálogo** e do **vendedor sem catálogo** |
| [**Login com Google: regras de segurança**](login-com-google-regras-de-seguranca.md) | Como a pessoa entra (somente pelo Google), regras `RL-01` a `RL-39`, modelo de ameaças e testes a escrever |

## Detalhamentos

| Documento | O que tem |
|---|---|
| [Spec do produto](superpowers/specs/2026-09-29-marketplace-autopecas-design.md) | Texto completo dos requisitos funcionais e não funcionais, regras de negócio, riscos e cenários de aceite |
| [Stack e TDD](superpowers/specs/2026-09-29-stack-e-tdd-design.md) | Tecnologias, arquitetura interna (módulos e camadas) e estratégia de testes |
| [Design das telas](superpowers/specs/2026-09-29-frontend-telas-design.md) | Rotas, navbar, dashboards do comprador e do vendedor, contratos do frontend |
| [Spec do backend (Plano 1)](superpowers/specs/2026-09-29-backend-autenticacao-design.md) | Sessão com JWT e `X-Auth-Id`, notificações e modelo de ameaças da sessão. **O login por senha descrito ali sai com o login com Google** |
| [Diagramas](diagramas/README.md) | 10 diagramas em PlantUML (casos de uso, C4, sequências, estados, ER e autenticação) |

## Planos de implementação

| Plano | Situação |
|---|---|
| [Backend, Plano 1: esqueleto e autenticação](superpowers/plans/2026-09-29-backend-01-esqueleto-e-autenticacao.md) | Executado (login por senha, a ser substituído pelo login com Google) |
| [Frontend, Plano 1: esqueleto e navbar](superpowers/plans/2026-09-29-frontend-01-esqueleto-e-navbar.md) | Escrito, ainda não executado |
| [Frontend, dashboard de pedidos](superpowers/plans/2026-09-29-frontend-dashboard-pedidos.md) | **Substituído** (as tarefas de "Meus pedidos" serão reaproveitadas no Plano 2) |

## Regras de trabalho

- **Nada de `git commit` nem `git add` sem a autorização do usuário.**
- Desenvolvimento com TDD: nenhum código de produção sem um teste falhando antes.
- Os documentos usam os mesmos IDs (AUT, VEI, PED, DIS, CAT, CHT, LST, NOT, FEC, ADM, RN, RNF, RL, R). Se um texto divergir de outro, vale o mais recente, e a divergência deve ser corrigida.

# Telas do frontend: navbar, dashboard do comprador e dashboard do vendedor

> Complementa a [spec do produto](2026-09-29-marketplace-autopecas-design.md) e o [documento de stack](2026-09-29-stack-e-tdd-design.md). Data: 2026-09-29. Atualizado em 2026-09-30 (tela de entrada com Google). Status: **rascunho para revisão**.
> Lógica do comprador e dos vendedores com e sem catálogo: [stakeholders](../../stakeholders.md).
> Os IDs (PED, CAT, LST, NOT, AUT, DIS) referem-se à spec do produto.

## 1. O que foi pedido

1. **Dashboard principal do comprador**, com barra de pesquisa e filtros.
2. **Dashboard do vendedor**, com um campo para inserir peças (ligado a um endpoint de catálogo que busca peças) e um **filtro lateral** para mostrar as peças que ele tem em estoque.
3. **Navbar para os dois perfis**, com acesso às configurações do usuário e um **campo de notificações que inclui o chat**. Para o vendedor, um **campo específico para as mensagens de peças à procura**.

**Decisões desta rodada (do usuário):**

| # | Decisão | Consequência |
|---|---|---|
| U1 | A pesquisa do comprador **mostra o catálogo e permite lançar o pedido** | Ao pesquisar (peça + filtros de veículo), aparecem as peças dos catálogos (PED-07). Um botão "Pedir aos vendedores" lança o pedido |
| U2 | **Só frontend, com API de mock** | Os contratos são definidos aqui e simulados com MSW e com um servidor de mock. O backend ganha plano próprio |
| U3 | **Três planos em sequência** | Seção 7 |

O dashboard "Meus pedidos", planejado antes, **não é mais a tela principal**. Ele vira uma aba do dashboard do comprador (Plano 2) e reaproveita as regras já escritas.

## 2. Mapa de rotas

| Rota | Quem acessa | Conteúdo | Plano |
|---|---|---|---|
| `/entrar` | visitante (sem sessão) | **Entrar com Google** e as mensagens de erro do login (`?erro=`). Só aceita `retorno` para rotas internas. Sem *script* de terceiros e sem `iframe` (login com Google, RL-07 e RL-35) | 1A (nova) |
| `/` | todos | Redireciona para `/comprador` | 1 |
| `/comprador` | todos (todo usuário é comprador, AUT-03) | Dashboard: pesquisa, filtros, resultados do catálogo e aba "Meus pedidos" | 2 (no Plano 1, uma página provisória) |
| `/vendedor` | vendedor **aprovado** (AUT-05) | Dashboard: catálogo, inserir peça e filtro lateral de estoque | 3 (no Plano 1, provisória) |
| `/vendedor/pecas-a-procura` | vendedor aprovado | Cotações recebidas dos compradores (LST-04, DIS-03) | 3 (no Plano 1, provisória) |
| `/configuracoes` | todos | Dados da conta e situação como vendedor | 1 |

Quem abre `/vendedor/**` sem ser vendedor aprovado (comprador, em análise, reprovado ou suspenso) volta para `/comprador`, sem ver o conteúdo do vendedor. Quem abre qualquer rota **sem sessão** vai para `/entrar?retorno=<rota>`. Com os **Termos pendentes** (`termosPendentes` em `/me`), só a tela de aceite e o logout abrem.

## 3. Navbar (Plano 1)

```
┌────────────────────────────────────────────────────────────────────────────┐
│ Autopeças   [Comprar]  Vender        Peças à procura (2)   Sino (3)   Config │
└────────────────────────────────────────────────────────────────────────────┘
   marca      alternar de perfil       só vendedor            todos      todos
```

No celular, os itens da direita mostram só o ícone. O nome acessível continua completo, por exemplo "Peças à procura, 2 não lidas".

| Item | Regra |
|---|---|
| **Comprar** | Sempre aparece. Leva a `/comprador` |
| **Vender** | Só existe se o usuário tem perfil de vendedor. Ativo se **aprovado**. "Vender (em análise)" e "Vender (indisponível)" (reprovado ou suspenso) aparecem **desabilitados** |
| **Peças à procura** | **Campo específico do vendedor**, só para aprovado. Mostra a contagem de não lidas, com "99+" acima de 99. Leva a `/vendedor/pecas-a-procura` |
| **Sino de notificações** | Todos. Abre um painel lateral com as abas **Todas** e **Chat**. O contador soma **chat + outras** e **não inclui peças à procura**, que têm o campo próprio acima |
| **Configurações** | Todos. Leva a `/configuracoes` |

**Painel do sino.** Cada notificação mostra título, resumo, tempo relativo ("há 5 min", "ontem") e um ponto quando não lida. Clicar em uma não lida a marca como lida. Clicar em uma já lida não faz nada. O painel atualiza a cada 30 s (SSE fica para depois, risco T3 do documento de stack). Enquanto o chat não existe, o clique não navega para lugar nenhum.

**Estados:** carregando, erro com "Tentar novamente" e vazio ("Nenhuma notificação." / "Nenhuma mensagem de chat."). Se a sessão expira (401), a página inteira mostra "Sua sessão expirou", sem navbar, com o botão que leva a `/entrar`.

### Contratos do Plano 1

| Endpoint | Resposta |
|---|---|
| `GET /api/v1/me` | `{ id, nome, email, vendedor: { status: "PENDENTE" \| "APROVADO" \| "REPROVADO" \| "SUSPENSO" } \| null, authId }`. O `authId` (64 caracteres hexadecimais) é o valor do cabeçalho `X-Auth-Id` |
| `GET /api/v1/notificacoes?tipos=CHAT,PROPOSTA,SISTEMA&limite=20` | `{ itens: [{ id, tipo: "CHAT" \| "PROPOSTA" \| "PECA_A_PROCURA" \| "SISTEMA", titulo, resumo, criadaEm, lida }], naoLidas: { chat, outras, pecasAProcura } }`. Os contadores valem para **todos** os tipos, não só para os pedidos em `tipos` |
| `PATCH /api/v1/notificacoes/{id}` | Corpo `{ "lida": true }`, resposta `204` |

**Autenticação:** o JWT vai no cookie `access_token` (HttpOnly), enviado sozinho pelo navegador. Toda chamada autenticada, menos `GET /api/v1/me`, também envia o cabeçalho **`X-Auth-Id`**, cujo valor o frontend recebe de `/me` e guarda **só em memória**. Sem ele, ou com um valor que não confere com o usuário do JWT, a resposta é `401`. Ver a [spec do backend](2026-09-29-backend-autenticacao-design.md).

## 4. Dashboard do comprador (Plano 2)

```
┌─ Navbar ───────────────────────────────────────────────────────────────────┐
│ [ Qual peça você procura?                                   ] [Pesquisar]  │
│ Veículo: [Tipo ▾] [Marca ▾] [Modelo ▾] [Versão ▾] [Ano ▾]                  │
│ Condição: (•) Qualquer ( ) Nova ( ) Usada ( ) Recondicionada               │
│ Cidade/UF: [ Recife / PE ]                                                 │
├────────────────────────────────────────────────────────────────────────────┤
│ [ Buscar peças ]  [ Meus pedidos ]                                         │
│ Resultados do catálogo (12)                                                │
│ ┌ Farol esquerdo · Gol G6 2013–2016 ········ R$ 180,00 · Usada ─────────┐  │
│ │ Auto Peças Silva · Recife/PE · garantia de 90 dias                    │  │
│ └───────────────────────────────────────────────────────────────────────┘  │
│ Não achou o que precisa?   [ Pedir aos vendedores ]                        │
└────────────────────────────────────────────────────────────────────────────┘
```

- **Pesquisa e filtros** cobrem PED-01 (veículo, peça, condição, cidade/UF) e VEI-01 (cascata tipo → marca → modelo → versão → ano, buscável).
- **Resultados imediatos do catálogo** (PED-07): peça, veículo compatível, vendedor, cidade, condição, preço (ou "sob consulta") e garantia.
- **"Pedir aos vendedores"** só habilita com veículo escolhido e descrição da peça (PED-01). Cria o pedido, que chega aos vendedores por perfil de atuação (DIS-03).
- **Aba "Meus pedidos":** o painel já desenhado (resumo, filtro por status, lista com validade), reaproveitado.
- Endpoints previstos (a detalhar no Plano 2): `GET /api/v1/catalogo/busca`, `GET /api/v1/veiculos/*` (tipos, marcas, modelos, versões e anos), `POST /api/v1/pedidos`, `GET /api/v1/pedidos`.

## 5. Dashboard do vendedor (Plano 3)

```
┌─ Navbar ───────────────────────────────────────────────────────────────────┐
│ FILTROS                │ Meu catálogo                    [ + Adicionar peça ]│
│ Estoque                │ [ Buscar no catálogo…                     ]         │
│  (•) Todas             │ ┌ Farol esquerdo · Gol G6 ····· 5 em estoque ────┐ │
│  ( ) Em estoque        │ │ Usada · R$ 180,00 · garantia de 90 dias        │ │
│  ( ) Sem estoque       │ └────────────────────────────────────────────────┘ │
│ Condição               │ ┌ Amortecedor · Uno 2012 ····· Sem estoque ──────┐ │
│ Categoria              │ └────────────────────────────────────────────────┘ │
└────────────────────────┴───────────────────────────────────────────────────┘
```

- **Filtro lateral de estoque:** Todas / Em estoque / Sem estoque (CAT-04: quantidade zerada inativa o item). Também condição e categoria.
- **Adicionar peça** (CAT-01, CAT-02, CAT-03): título, categoria, código OEM, condição, preço (vazio = "sob consulta"), quantidade, garantia, observações e compatibilidade (veículo ou faixa de anos). As fotos ficam para depois, porque o envio precisa do backend.
- **Peças à procura** (`/vendedor/pecas-a-procura`): lista das cotações recebidas, com veículo, peça, cidade e há quanto tempo chegou, e a ação de ignorar (DIS-06).
- Endpoints previstos (a detalhar no Plano 3): `GET` e `POST /api/v1/vendedor/catalogo/itens`, `GET /api/v1/catalogo/pecas?q=` (sugestão de peças e categorias ao digitar, PED-03) e `GET /api/v1/vendedor/pecas-a-procura`.

## 6. Contratos e estrutura

Estrutura do código, definida pelo usuário: componentes e páginas em `app/`; conexão com o backend em `api/`; regras de negócio em `services/`, fora de `app/`. Hooks do React (TanStack Query) ficam em `app/hooks/`, porque `services/` não depende de React. A dependência vai só de `app/` para `services/` e de `services/` para `api/`, verificada por um teste de arquitetura.

## 7. Divisão em planos

| Plano | Entrega | Depende de |
|---|---|---|
| **1. Esqueleto e navbar** | Projeto Next.js, cadeia de testes, cliente HTTP (com `X-Auth-Id`), sessão, navegação por perfil, sino de notificações com chat, contador de peças à procura, página de configurações e mock | — |
| **1A. Entrar com Google** (novo) | Tela `/entrar`, guarda de rota (sem sessão vai para `/entrar`), mensagens de erro do login, tela de aceite dos Termos e o mock de `entrar` e `retorno`. O esquema de `/me` deve aceitar qualquer e-mail que o Google devolva (relaxar `z.email()`) | Plano 1 e o backend do login com Google ([regras](../../login-com-google-regras-de-seguranca.md)) |
| **2. Dashboard do comprador** | Pesquisa, filtros e cascata de veículo, resultados do catálogo, lançar pedido, aba "Meus pedidos" | Plano 1 |
| **3. Dashboard do vendedor** | Catálogo, adicionar peça, filtro lateral de estoque, peças à procura | Plano 1 |

Os Planos 2 e 3 são independentes entre si. Cada um será escrito depois que o anterior estiver no ar, porque depende do código real do que veio antes.

## 8. Interpretações a confirmar

1. **"Campo específico para peças à procura"** foi entendido como uma **entrada própria na navbar**, com contador, separada do sino. A alternativa seria uma aba dentro do painel do sino.
2. **"Endpoint de catálogo que busca peças", no vendedor**, foi entendido como duas coisas: a lista do próprio catálogo do vendedor (com o filtro lateral) e uma **sugestão de peças ao digitar** no formulário de inserção. Fica para confirmar no Plano 3.
3. **"Guardar campo de notificação"** foi entendido como **reservar** o espaço do sino já com o chat incluído. A tela de chat em si ainda não existe, e os itens de chat só aparecem no painel.
4. **Configurações do usuário** começa como uma página simples de leitura (nome, e-mail, perfis). Editar dados e excluir a conta (AUT-06) vêm depois.
5. **Login:** o usuário pediu o login **somente pelo Google**, então não há formulário de e-mail e senha. A tela `/entrar` tem só o botão "Entrar com Google" (seguindo as diretrizes de marca do Google) e os avisos de erro.
6. **Vendedor com e sem catálogo** (RN-13): o dashboard `/vendedor` do vendedor sem catálogo mostra o **estado vazio** "Cadastre sua primeira peça", e "Peças à procura" é a entrada principal dele. O com catálogo vê a lista e o filtro de estoque.

## 9. Mapeamento para a spec do produto

| Tela ou campo | Requisitos |
|---|---|
| Pesquisa, filtros e "Pedir aos vendedores" | PED-01, PED-03, PED-07, VEI-01 |
| Aba "Meus pedidos" | PED-06 |
| Catálogo, inserir peça, filtro de estoque | CAT-01, CAT-02, CAT-03, CAT-04 |
| Peças à procura | LST-04, DIS-03, DIS-06 |
| Sino e chat | NOT-01, CHT-02 |
| Alternar comprador e vendedor, guarda de rota | AUT-03, AUT-05 |
| Configurações | AUT-06 |

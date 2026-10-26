# Stack, arquitetura técnica e estratégia de TDD

> Complementa a spec [2026-09-29-marketplace-autopecas-design.md](2026-09-29-marketplace-autopecas-design.md). Data: 2026-09-29. Atualizado em 2026-09-30 (login com Google). Status: **rascunho para revisão**.
> Os IDs (RF, RNF, RN, R) referem-se a essa spec. Diagramas em [`docs/diagramas/`](../../diagramas/README.md).

## 1. Decisões fixas (do usuário)

| Camada | Escolha |
|---|---|
| Linguagem e JDK | **Java 21** (LTS). *Virtual threads* ligadas na API, para sustentar muitas conexões SSE simultâneas (RNF-ESC-01) |
| Banco de dados | PostgreSQL, em imagem Docker no desenvolvimento |
| Backend / API | Spring Boot com Maven |
| Frontend | React com Next.js, Tailwind CSS e shadcn/ui |
| Migrações do banco | **Liquibase**, com os changelogs em **SQL** (*formatted SQL*) |
| Login | **Somente pelo Google** (OpenID Connect, e-mail verificado), sem senha própria. Regras em [login com Google](../../login-com-google-regras-de-seguranca.md) |
| Sessão | **JWT** de sessão de login, e o cabeçalho **`X-Auth-Id`** (hash de identificação do usuário) confirma o usuário da sessão. Detalhes na [spec do backend](2026-09-29-backend-autenticacao-design.md) |
| Processo | Desenvolvimento com **TDD** |

## 2. Decisões propostas (a confirmar)

Cada linha é o padrão que sigo se você não disser o contrário. Nenhuma trava o início da implementação, e a coluna da direita mostra o que muda se você preferir outra.

**Versões:** as linhas principais abaixo foram vistas em busca web em 2026-09-29 (fontes na seção 9). Na criação do projeto, use a **última estável** de cada uma e fixe no `pom.xml` e no `package-lock.json`.

| Tema | Proposta | Por quê | Alternativa |
|---|---|---|---|
| Framework | **Spring Boot 4.x** + **Spring Modulith 2.x** | Modulith verifica por teste as fronteiras do monólito modular (RNF-MAN-01) e persiste eventos de domínio no PostgreSQL | Pacotes simples + ArchUnit |
| Persistência | Spring Data JPA (Hibernate) + **Liquibase** (changelogs em SQL). Consultas do matching em SQL nativo | Migrações versionadas (RNF-MAN-02). O matching exige SQL que JPQL não expressa bem. O Hibernate só **confere** o esquema (`ddl-auto=validate`) | jOOQ nas consultas de matching |
| API | REST JSON em `/api/v1`, **OpenAPI** (springdoc), erros no formato Problem Details (RFC 9457), mensagens paginadas por cursor | Contrato único gerando o cliente TypeScript do front | — |
| Autenticação | **Login pelo Google** (OpenID Connect, código de autorização com PKCE, conduzido pelo backend com o cliente OAuth2 do Spring Security), sem senha própria. Depois do login, Spring Security (resource server) com **JWT HS256 em cookie HttpOnly, SameSite=Lax**. Toda chamada autenticada também envia **`X-Auth-Id`** = HMAC-SHA256 do id do usuário, conferido contra o `sub` do JWT. Logout revoga o `jti` | API sem estado (RNF-ESC-02). O JavaScript nunca lê o token, e o cabeçalho personalizado barra CSRF (RNF-SEG-01). O estado do login fica num cookie cifrado, e não na sessão do servidor | Bearer no cabeçalho `Authorization` (serviria a um app nativo, mas expõe o token a XSS). Serviço de identidade pronto (Firebase, Auth0), rejeitado por tirar a sessão das nossas mãos |
| Tempo real | **SSE** (servidor → navegador) + `POST` REST (navegador → servidor, idempotente por `id_cliente`) | Sobre HTTP puro, reconecta sozinho, e a queda para consulta periódica é natural (RNF-DIS-02, RNF-CON-01) | WebSocket/STOMP |
| Eventos e jobs | Eventos de domínio do **Spring Modulith** (registro persistido no PostgreSQL, com reprocessamento) + `@Scheduled` com **ShedLock** (JDBC) para FIPE e expiração | Matching assíncrono e confiável sem broker externo no MVP (RNF-DES-05) | RabbitMQ, quando houver mais de uma instância pesada |
| Busca e distância | PostgreSQL full-text `portuguese` + `unaccent` + `pg_trgm`. Distância entre cidades por tabela de municípios (IBGE) com latitude e longitude e fórmula de Haversine | Sem serviço de busca extra e sem PostGIS no MVP (R6) | PostGIS e Elasticsearch, na Fase 2 |
| Imagens | Upload `multipart` pela API, que valida o tipo real, remove EXIF ao regravar, redimensiona e gera miniatura. Armazenamento **S3-compatível** (MinIO no dev). Leitura por URL assinada | Controle de segurança no servidor (RNF-SEG-04, RN-09) | URL pré-assinada direta do navegador (Fase 2) |
| E-mail | Spring Mail. **Mailpit** no dev; SMTP transacional em produção (a definir) | Ver o e-mail no desenvolvimento sem enviar nada | — |
| Limite de taxa | Bucket4j em memória (uma instância no MVP) | RNF-SEG-05 | Redis, com várias instâncias |
| Observabilidade | Spring Boot Actuator + Micrometer, logs JSON | RNF-OBS-01 | — |
| Frontend | Next.js **App Router** + TypeScript. shadcn/ui (Combobox/Command na cascata de veículo, Form com `react-hook-form` + `zod`), **TanStack Query** para dados do servidor, `EventSource` para o chat. Cliente da API **gerado do OpenAPI** | Radix dá boa base de acessibilidade (RNF-USA-04). O cliente gerado quebra o CI se o contrato divergir | — |
| CI | **GitHub Actions**: `mvn verify` (com Testcontainers), lint, `tsc`, Vitest, build e Playwright | RNF-MAN-02 | — |
| Empacotamento | Backend: `spring-boot:build-image` (ou Jib). Frontend: `output: standalone` em Dockerfile | Hospedagem fica em aberto (seção 8) | — |

## 3. Arquitetura técnica

- **Contêineres:** [`07_containers_c4.puml`](../../diagramas/07_containers_c4.puml).
- **Módulos da API e eventos:** [`08_componentes_api_c4.puml`](../../diagramas/08_componentes_api_c4.puml).

Módulos do backend (pacotes verificados pelo Spring Modulith). Um módulo só enxerga a API pública dos outros, e a comunicação de efeito colateral é por evento.

| Módulo | Responsabilidade | Requisitos |
|---|---|---|
| `conta` | Usuário, perfil de vendedor, perfil de atuação, sessão, aceite de termos | AUT, DIS-01 |
| `veiculo` | Taxonomia local, adaptador da FIPE, sincronização | VEI, RNF-INT-01 |
| `catalogo` | Itens, compatibilidade, fotos, estoque | CAT |
| `pedido` | Pedido e seu estado, renovação e reabertura | PED |
| `matching` | Casamento de catálogo e de perfil, ranking, limite N, cotações | DIS |
| `negociacao` | Conversa, mensagens, proposta e suas versões, tempo real | CHT, LST, FEC |
| `notificacao` | Notificações no app e e-mail agrupado | NOT |
| `moderacao` | Aprovação de vendedor, denúncia, bloqueio, auditoria | ADM |
| `compartilhado` | Tratamento de imagens, erros, tipos comuns | RN-09 |

Eventos principais: `PedidoCriado` (pedido → matching) · `ItemCatalogoCasado` (matching → negociacao, cria a conversa com o cartão) · `CotacaoCriada` (matching → notificacao) · `PrimeiraResposta` e `PropostaVendida` (negociacao → pedido) · `PropostaVendida` (negociacao → catalogo, baixa de estoque) · `MensagemEnviada` e `PropostaAtualizada` (negociacao → notificacao).

### 3.1 Arquitetura interna: módulos por funcionalidade, com camadas dentro de cada um

MVC e camadas não são concorrentes, e nenhum dos dois responde sozinho à pergunta. O MVC é um padrão de **apresentação**, e aqui a "visão" é o Next.js. O backend é uma API REST, então o `Controller` é só a borda HTTP. As camadas (`controller` → `service` → `repository`) organizam o código **por tipo técnico**. Sozinhas, com o projeto crescendo, deixam tudo depender de tudo, e a regra de um módulo acaba espalhada pelos outros.

**Proposta:** monólito modular (já decidido, seção 3) com **camadas dentro de cada módulo**. As portas (interfaces) existem só nas fronteiras externas (FIPE, armazenamento de imagens, e-mail, tempo real e relógio), o que dá um toque de arquitetura hexagonal onde ela compensa.

```
pedido/                      # módulo (pacote raiz = API pública do módulo)
├─ PedidoCriado.java         # eventos e tipos que outros módulos podem usar
├─ PedidosApi.java           # interface pública do módulo
└─ internal/                 # invisível para os outros módulos
   ├─ web/          # controllers, requests e responses (DTOs)
   ├─ application/  # casos de uso, transações
   ├─ domain/       # entidades e regras: PedidoPeca e seu estado
   └─ infra/        # repositórios Spring Data, adaptadores
```

Regras de dependência, cada uma verificada por teste (ArchUnit e `ApplicationModules.verify()`):

1. `domain` não depende de `web`, `application` nem `infra`, e não usa Spring (só as anotações JPA, que são inertes). É onde ficam as regras RN-06, RN-07, RN-11 e RN-12, testadas em milissegundos, sem subir contexto.
2. `web` só chama `application`. Controller não acessa repositório.
3. Um módulo só usa a API pública de outro, ou reage a um evento dele.
4. Portas com implementação trocável nas fronteiras externas: `FonteDeVeiculos` (FIPE), `ArmazenamentoDeImagens`, `EnviadorDeEmail`, `PublicadorTempoReal` e `Clock`. Nos testes entram fakes ou WireMock.

**Domínio rico, sem segundo modelo.** As entidades JPA carregam o comportamento (`proposta.aceitar()`, `proposta.marcarVendida()`), em vez de serem sacos de dados manipulados por um `Service`. Não crie um modelo de domínio separado do JPA, com mappers entre eles, a menos que a dor apareça. Nos módulos simples (`catalogo`, `moderacao`), CRUD direto basta.

**O que fica de fora por ora:** Clean Architecture ou hexagonal completa em todo módulo (mais arquivos e mapeamento por camada do que o MVP paga) e CQRS ou *event sourcing* (nenhum requisito pede).

**Frontend:** organizado por funcionalidade (`features/pedido`, `features/chat`, `features/catalogo`), e não por tipo de arquivo. As rotas do App Router ficam finas e só compõem as funcionalidades.

## 4. Ambiente de desenvolvimento

`docker-compose.yml` na raiz com **PostgreSQL**, **MinIO** e **Mailpit**. O Spring Boot sobe o compose sozinho (suporte a Docker Compose do Boot) e os testes usam **Testcontainers** com a mesma imagem do PostgreSQL. O Next.js roda no host (`npm run dev`, porta 3000) e a API na 8080.

```
IdeiaDeTCC/
├─ backend/            # Maven, Spring Boot, módulos em src/main/java
├─ frontend/           # Next.js, Tailwind, shadcn/ui
├─ docker-compose.yml  # postgres, minio, mailpit
├─ docs/               # spec, stack e diagramas
└─ .github/workflows/  # CI
```

**Git:** a raiz do repositório do `ProjectCode` (a pasta pai) tem vários outros projetos sem commit, então foi criado um repositório **local** dentro de `IdeiaDeTCC` (branch `feat/backend-autenticacao`, sem remoto). Por decisão do usuário, **nenhum commit nem `git add` acontece sem autorização dele**.

## 5. Estratégia de TDD

**Ciclo:** vermelho (teste que falha pelo motivo certo) → verde (o mínimo para passar) → refatorar. Nenhum código de produção sem um teste falhando antes. Cada commit sai com a suíte verde.

**Pirâmide-alvo:** cerca de 70% de testes unitários de domínio (puros, sem Spring, rodam em milissegundos), 25% de integração e 5% de ponta a ponta.

| Camada | Ferramentas | O que cobre | Requisitos |
|---|---|---|---|
| Domínio (backend) | JUnit 5, AssertJ | Máquinas de estado do pedido e da proposta, ranking e limite N, limites anti-spam | RN-06, RN-07, RN-08, RN-11, RN-12, DIS-04 |
| Persistência | Testcontainers (PostgreSQL), Liquibase | Índices únicos, consultas de matching, busca textual, distância | RN-02, RN-12, DIS-02, DIS-03 |
| API e segurança | MockMvc ou REST Assured, Spring Security Test | Contrato HTTP e **autorização por objeto**: para cada rota protegida, um teste "outro usuário recebe 403/404" | RNF-SEG-02, RNF-SEG-03 |
| Assíncrono | Awaitility | Matching concluído em até 10 s | RNF-DES-05 |
| Fronteiras | `ApplicationModules.verify()` (Modulith), ArchUnit | Nenhum módulo acessa o interior de outro | RNF-MAN-01 |
| Integração externa | WireMock | Adaptador da FIPE (respostas reais gravadas, limites e falhas) | RNF-INT-01, R2 |
| Frontend | Vitest, Testing Library, user-event, MSW | Componentes e telas com a API simulada | RF de tela |
| Acessibilidade | axe (`jest-axe`, `@axe-core/playwright`) | WCAG 2.1 AA em pedido, chat e lista | RNF-USA-04 |
| Ponta a ponta | Playwright contra o compose, incluindo viewport mobile | Os cenários 1 a 10 da spec (seção 12) | Aceite do MVP |
| Contrato | OpenAPI gerado e cliente TypeScript gerado | O CI falha se o cliente gerado diferir do commitado | — |
| Desempenho | Lighthouse CI (LCP) e k6 (smoke antes do lançamento) | LCP < 2,5 s, p95 de criar pedido e de mensagem | RNF-DES-01 a 04 |

**Cobertura:** JaCoCo com meta de 70% nas regras de domínio como portão do CI (RNF-MAN-02). Teste de mutação (PIT) no domínio é opcional na Fase 2.

**Ordem de construção** (fatias verticais, começando pelo maior risco). Em cada fatia: o cenário de aceite falha primeiro, depois os testes unitários, a implementação, a refatoração e por fim a tela.

0. **Esqueleto:** repositório, compose e CI com um teste de fumaça verde (Testcontainers + Liquibase + endpoint de saúde; página inicial + Vitest).
1. `veiculo`: taxonomia e adaptador da FIPE, com a normalização de modelo e versão (**risco R1**).
2. `conta`: **login com Google**, sessão e aprovação de vendedor (cenários 7 e 11 a 16).
3. `catalogo`: itens, compatibilidade e fotos.
4. `pedido` + `matching` (cenários 1, 2 e 6).
5. `negociacao`: chat, proposta, contraproposta, SSE e Vendida (cenários 3, 4, 5 e 8).
6. `notificacao`, `moderacao`, reabertura e fechamento (cenários 9 e 10).

**Definição de pronto:** testes verdes, portão de cobertura, lint e `tsc` limpos, verificação de acessibilidade nas telas novas, OpenAPI e cliente atualizados, changelog Liquibase (SQL) versionado, nenhum `TODO` deixado.

## 6. Requisito não funcional → mecanismo

| RNF | Mecanismo |
|---|---|
| RNF-DES-02 (cascata < 300 ms) | Taxonomia local com índices e cache HTTP (ETag) |
| RNF-DES-03 (mensagem < 1 s) | SSE e virtual threads |
| RNF-DES-05 (matching ≤ 10 s) | Evento persistido e processamento assíncrono |
| RNF-CON-01 (sem duplicar) | Índice único em `id_cliente` e gravação antes da confirmação |
| RNF-SEG-01 / 03 | Spring Security, login com Google (OIDC, PKCE), JWT em cookie HttpOnly, `X-Auth-Id` (HMAC), papéis |
| RNF-SEG-02 (IDOR) | Verificação de participação no serviço e testes de acesso indevido por rota |
| RNF-SEG-04 (upload) | Validação do tipo real, tamanho, regravação da imagem, URL assinada |
| RNF-SEG-05 (taxa) | Bucket4j |
| RNF-INT-01 (FIPE) | Porta `FonteDeVeiculos` com adaptador da FIPE, espelho local e job agendado |
| RNF-OBS-01 | Actuator, Micrometer, logs JSON |
| RNF-MAN-02 | JaCoCo, GitHub Actions, springdoc |

## 7. Ajustes nos diagramas por causa da stack

- `04_sequencia_negociacao_venda.puml`: a foto sobe pela API (`multipart`), que valida, remove EXIF e grava no armazenamento. Antes estava como URL assinada.
- `06_modelo_er.puml`: `Proposta` ganhou `pedido_id` (desnormalizado) para o **índice único parcial** que garante no banco a regra RN-12 (uma Vendida por pedido).

## 8. Decisões em aberto e riscos técnicos

| # | Ponto | Encaminhamento |
|---|---|---|
| T1 | Hospedagem, provedor de e-mail e de S3 em produção | Não bloqueia o início. Decidir antes do primeiro deploy |
| T2 | Consistência entre módulos ao marcar Vendida (RN-11 e RN-12) | Transação única em `negociacao` com efeitos por evento, ou listener síncrono. O índice único parcial protege a regra no banco. Decidir no plano |
| T3 | SSE atrás do proxy do Next.js pode sofrer buffering | Conectar o `EventSource` direto na API do mesmo site (mesmo domínio registrável) e provar isso cedo com um teste E2E |
| T4 | Cookie de sessão com SSR do Next.js | O SSR precisa repassar o cookie do navegador à API. Definir no plano |
| T5 | ~~Sessão por cookie × JWT~~ | **Decidido:** JWT em cookie HttpOnly com `X-Auth-Id` ([spec do backend](2026-09-29-backend-autenticacao-design.md)). Um app nativo exigiria aceitar também o Bearer, e isso reabre a decisão |
| T6 | Normalização de modelo e versão da FIPE (R1) e limites da API (R2) | Primeira fatia da construção (seção 5, passo 1) |
| T7 | Login com Google: o `302` e o `Set-Cookie` do retorno precisam atravessar o proxy (`rewrites`) do Next.js, e o PKCE precisa ser aceito pelo cliente "Aplicativo da Web" do Google | Provar cedo, com um teste ponta a ponta e um teste de integração com o cliente real ([login com Google](../../login-com-google-regras-de-seguranca.md), seção 9) |

## 9. Referências

- [Spring Modulith 2.2 M1, 2.1.1, 2.0.8 e 1.4.13 (ago/2026)](https://spring.io/blog/2026/08/26/spring-modulith-2-2-m1-2-1-1-2-0-8-and-1-4-13-released/): linha 2.x estável em 2026-08-26.
- [Versões e suporte do Spring Boot (HeroDevs, abr/2026)](https://www.herodevs.com/blog-posts/spring-boot-versions-eol-dates-and-latest-releases-april-2026): linha 4.x. Outra fonte de julho/2026 cita a 4.1. **Confirmar a versão exata em start.spring.io.**
- [PostgreSQL: versões suportadas](https://www.instaclustr.com/education/postgresql/postgres-versions-supported-releases-eol-dates-upgrades/): 18.x estável e 19 em beta em ago/2026.
- [Guia do ecossistema Next.js 2026](https://www.pkgpulse.com/guides/nextjs-developer-ecosystem-guide-2026): App Router estável, Next.js 16.x, Tailwind v4 e shadcn/ui. Fonte secundária, **confirmar as versões no `create-next-app`.**

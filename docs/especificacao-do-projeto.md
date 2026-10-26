# Especificação do projeto: marketplace reverso de autopeças

> Versão consolidada de **2026-09-30**. Status: **rascunho para revisão**. Nome do produto: provisório.
> Este é o documento de entrada. Ele junta tudo o que foi decidido e aponta para os detalhes. Legenda de prioridade: **MVP** = primeira entrega (TCC) · **F2** = fase 2 · **F3** = futuro.

## 0. Como usar este documento

| Documento | O que tem |
|---|---|
| **Este** | Visão, escopo, decisões, requisitos (todos os IDs), regras, arquitetura, estado da implementação e próximos passos |
| [Stakeholders](stakeholders.md) | A lógica do comprador, do vendedor **com** catálogo e do vendedor **sem** catálogo |
| [Login com Google: regras de segurança](login-com-google-regras-de-seguranca.md) | Como a pessoa entra, e as regras de segurança do login |
| [Spec do produto](superpowers/specs/2026-09-29-marketplace-autopecas-design.md) | O texto completo dos requisitos, dos riscos e dos cenários de aceite |
| [Stack e TDD](superpowers/specs/2026-09-29-stack-e-tdd-design.md) | Tecnologias, arquitetura interna e estratégia de testes |
| [Design das telas](superpowers/specs/2026-09-29-frontend-telas-design.md) | Rotas, navbar, dashboards e contratos do frontend |
| [Spec do backend (Plano 1)](superpowers/specs/2026-09-29-backend-autenticacao-design.md) | Sessão com JWT e `X-Auth-Id`, notificações, modelo de ameaças da sessão |
| [Diagramas](diagramas/README.md) | 10 diagramas em PlantUML |
| [Planos de implementação](superpowers/plans/) | Backend Plano 1 (executado) e frontend Plano 1 (escrito) |

Se um texto divergir do outro, vale o **mais recente**, e a divergência deve ser corrigida. O histórico das decisões está na seção 3.

## 1. Visão

Quem precisa de uma peça de veículo hoje procura loja por loja, liga, manda mensagem e compara preço na mão. O vendedor, por sua vez, não sabe quem está procurando o que ele tem em estoque, nem o que está no pátio.

O produto **inverte a busca**. O **comprador lança o pedido da peça** informando o veículo (tipo, marca, modelo, versão e ano). O sistema entrega o pedido aos vendedores que podem atender, e cada um responde em **uma conversa de chat**, com um valor sugerido ou uma proposta estruturada. Se a peça já está no **catálogo** do vendedor, ela aparece para o comprador sem esperar resposta. Se o vendedor só tem a peça num veículo ainda no pátio, ele **manda a foto pelo próprio chat**. Veículos: **carros, motos e caminhões**.

**Resultado esperado:** o comprador abre um pedido em menos de 1 minuto e recebe, numa lista de conversas, várias ofertas comparáveis. O vendedor recebe só pedidos relevantes para o que ele vende.

| Para quem | Valor |
|---|---|
| Comprador | Um pedido em vez de dezenas de ligações. Ofertas comparáveis no mesmo lugar |
| Vendedor com catálogo | É encontrado por quem busca o que ele tem, e as conversas chegam abertas, com o item e o preço |
| Vendedor sem catálogo | Recebe só pedidos que o perfil dele cobre e responde com foto, sem ter de cadastrar nada |

## 2. Escopo

**Dentro (MVP):** login com Google; perfil de comprador e de vendedor (com aprovação); taxonomia de veículos (FIPE); pedido de peça; matching por catálogo e por perfil; catálogo do vendedor; chat com imagens; proposta, contraproposta, Aceita e Vendida; notificações; moderação básica; LGPD.

**Fase 2 (F2):** garagem do comprador, pátio do vendedor, reputação e avaliação, importação de catálogo, vitrine pública, *web push*, comparação lado a lado, varredura de malware, métricas.

**Futuro (F3):** placa e chassi, ERP, áudio e PDF no chat, moderação automática, assinatura do vendedor.

**Fora de escopo permanente (D3):** pagamento, frete, nota fiscal, custódia e mediação de disputa. A tabela FIPE é só a **taxonomia** de veículos, e não um preço de peças.

## 3. Decisões (histórico)

| # | Decisão | Origem | Data |
|---|---|---|---|
| D1 | Spec completa, com MVP marcado | Usuário | 2026-09-29 |
| D2 | Distribuição por **perfil de atuação + catálogo** | Usuário | 2026-09-29 |
| D3 | **Negócio sempre fechado fora da plataforma.** Aceita registra o valor combinado e **Vendida** registra o fechamento declarado pelo vendedor, sem pagamento no sistema | Usuário | 2026-09-29 |
| D4 | Banco **PostgreSQL** em Docker; backend **Spring Boot com Maven**, **Java 21**; frontend **React/Next.js** com **Tailwind** e **shadcn/ui**; desenvolvimento com **TDD** | Usuário | 2026-09-29 |
| D5 | Arquitetura: **monólito modular**, módulos por funcionalidade com **camadas dentro** de cada um (resposta à pergunta "MVC ou camadas?") | Usuário (pergunta) e proposta | 2026-09-29 |
| D6 | Frontend: componentes e páginas em `app/`; conexão em `api/` e regra de negócio em `services/`, **fora** de `app/` | Usuário | 2026-09-29 |
| D7 | Telas: dashboard do comprador (pesquisa e filtros), dashboard do vendedor (catálogo, inserir peça e filtro lateral de estoque) e navbar dos dois com configurações, notificações com chat e, para o vendedor, o campo **"Peças à procura"** | Usuário | 2026-09-29 |
| D8 | Sessão por **JWT** em **cookie HttpOnly, `SameSite=Lax`**, e o cabeçalho **`X-Auth-Id`** (HMAC-SHA256 do id do usuário) confirma o usuário da sessão | Usuário | 2026-09-29 |
| D9 | Migrações com **Liquibase**, changelogs em **SQL** (no lugar do Flyway) | Usuário | 2026-09-29 |
| D10 | **Nada de `git commit` nem `git add` sem autorização** do usuário | Usuário | 2026-09-29 |
| **D11** | **Login somente por Google** (por e-mail verificado), com regras de segurança. Sem senha própria | Usuário | 2026-09-30 |
| **D12** | **Vendedor "com catálogo" e "sem catálogo"** são duas **situações** do mesmo vendedor aprovado, e não dois tipos de conta | Usuário (pedido) e proposta | 2026-09-30 |

**Premissas a confirmar** (adotadas como padrão): (a) a mesma conta pode ter perfil de comprador e de vendedor; (b) a monetização futura é por assinatura ou destaque do vendedor, sem comissão; (c) o lançamento é numa região piloto.

## 4. Stakeholders

Detalhes em [stakeholders.md](stakeholders.md).

| Stakeholder | Resumo |
|---|---|
| **Comprador** | Lança pedidos, compara conversas, negocia e conclui. Todo usuário é comprador |
| **Vendedor com catálogo** | Aprovado, com pelo menos um item ativo. Recebe **conversas automáticas** pelo catálogo (com o cartão do item) e também as cotações do perfil. Aparece na pesquisa do comprador |
| **Vendedor sem catálogo** | Aprovado, sem item ativo (caso típico do desmanche). Recebe **cotações** pelo perfil e responde por chat, com foto e proposta. Não aparece na pesquisa |
| Administrador | Aprova vendedores, trata denúncias e mantém as taxonomias |
| Sistema | Sincroniza veículos, casa pedidos, expira e notifica |

## 5. Fluxo principal e estados

1. O comprador entra com o Google, escolhe tipo → marca → modelo → versão → ano, descreve a peça, informa cidade/UF e envia.
2. O sistema cria o pedido (**Aberto**) e processa o matching em fila.
3. **Match de catálogo:** itens compatíveis geram conversas automáticas com o cartão do item (foto e preço). O comprador as vê na hora.
4. **Match de perfil:** vendedores que atuam naquele veículo e região recebem a cotação.
5. O vendedor responde por texto, foto ou **proposta**. O comprador aceita, recusa ou contrapropõe, e ordena por menor preço, mais recente ou não lidas.
6. Com o valor acordado, a proposta fica **Aceita**. Pagamento e entrega acontecem **fora do sistema**. O vendedor marca a proposta como **Vendida**, o pedido vira **Concluído** e o item do catálogo baixa do estoque.

- **Pedido:** Aberto → Em negociação (primeira resposta) → Concluído · Cancelado · Expirado. Concluído tem resultado *Vendido* (automático) ou *Sem compra* (o comprador marca "Não comprei").
- **Proposta:** Enviada → **Aceita → Vendida**. Saídas alternativas: Recusada · Contraproposta · Retirada · Expirada. Só uma proposta Aceita pode virar Vendida.
- Diagramas: [`05a_estados_pedido`](diagramas/05a_estados_pedido.puml), [`05b_estados_proposta`](diagramas/05b_estados_proposta.puml), [`03_sequencia_pedido_matching`](diagramas/03_sequencia_pedido_matching.puml), [`04_sequencia_negociacao_venda`](diagramas/04_sequencia_negociacao_venda.puml).

## 6. Requisitos funcionais

Texto completo na [spec do produto](superpowers/specs/2026-09-29-marketplace-autopecas-design.md), seção 7. Aqui, o catálogo de todos os IDs.

### 6.1 Contas e perfis (AUT)
| ID | Requisito | Pri |
|---|---|---|
| AUT-01 | **Entrar com o Google** (e-mail verificado). O primeiro login cria a conta. Sem senha própria (D11) | MVP |
| AUT-02 | ~~Recuperação de senha~~ **Não se aplica**: não há senha. A recuperação da conta é a do Google | — |
| AUT-03 | A mesma conta tem perfil de comprador e, se quiser, de vendedor | MVP |
| AUT-04 | Perfil de vendedor: nome ou razão social, CPF/CNPJ, tipo (loja, desmanche, autônomo), cidade/UF, WhatsApp, logo | MVP |
| AUT-05 | O vendedor só recebe pedidos e publica catálogo depois de **aprovado por um administrador** | MVP |
| AUT-06 | Editar dados e excluir a conta, com anonimização do histórico (LGPD) | MVP |
| AUT-07 | ~~Login social (Google)~~ **Absorvido por AUT-01** | — |
| AUT-08 | Selo "Verificado" (CNPJ e documento) e verificação de telefone por OTP | F2 |

### 6.2 Veículo (VEI)
| ID | Requisito | Pri |
|---|---|---|
| VEI-01 | Selecionar tipo (carro, moto, caminhão), marca, modelo, versão e ano em cascata, com busca digitável | MVP |
| VEI-02 | Taxonomia em base local, sincronizada da FIPE por job. A consulta do usuário nunca depende da API externa | MVP |
| VEI-03 | O administrador corrige nomes e cadastra veículos ausentes | MVP |
| VEI-04 | "Minha garagem": veículos salvos do comprador | F2 |
| VEI-05 | Veículo não listado, em texto livre, enviado para curadoria | F2 |
| VEI-06 | Preenchimento por placa ou chassi | F3 |

### 6.3 Pedido de peça (PED)
| ID | Requisito | Pri |
|---|---|---|
| PED-01 | Criar pedido: veículo e descrição da peça (obrigatórios), categoria, código OEM, condição aceita, cidade/UF, alcance (cidade, estado, Brasil) e até 5 fotos | MVP |
| PED-02 | Um pedido tem uma peça e um veículo | MVP |
| PED-03 | Sugestão de categorias e peças ao digitar | MVP |
| PED-04 | Validade de 7 dias, renovável pelo comprador | MVP |
| PED-05 | Editar (antes da 1ª resposta), cancelar, duplicar e encerrar | MVP |
| PED-06 | Painel "Meus pedidos" com status e contadores | MVP |
| PED-07 | Resultados imediatos: os matches de catálogo aparecem sem esperar resposta | MVP |
| PED-08 | Carrinho de pedido (várias peças do mesmo veículo) | F2 |

### 6.4 Distribuição e matching (DIS)
| ID | Requisito | Pri |
|---|---|---|
| DIS-01 | Perfil de atuação do vendedor: tipos, marcas, modelos e anos, categorias e alcance | MVP |
| DIS-02 | **Match de catálogo:** item ativo compatível gera conversa automática com o cartão do item | MVP |
| DIS-03 | **Match de perfil:** vendedor cujo perfil cobre veículo, categoria e região recebe a cotação | MVP |
| DIS-04 | Ranking e **limite de 30 vendedores por pedido** (configurável): catálogo > modelo/ano > marca, distância e atividade | MVP |
| DIS-05 | O matching roda em fila. O pedido responde na hora e os matches chegam em segundos | MVP |
| DIS-06 | O vendedor ignora um pedido, e ele some da caixa dele | MVP |
| DIS-07 | Pedido aberto é reavaliado quando um item compatível é cadastrado depois | F2 |
| DIS-08 | Reputação no ranking. O vendedor silencia categorias | F2 |
| DIS-09 | **Pátio:** veículos em desmontagem, com "possível peça no pátio" | F2 |

### 6.5 Catálogo (CAT)
| ID | Requisito | Pri |
|---|---|---|
| CAT-01 | Criar, editar e remover item: título, categoria, código, condição, preço, quantidade, até 8 fotos, garantia, observações, ativo/inativo | MVP |
| CAT-02 | Compatibilidade: lista de veículos (marca, modelo, versão, faixa de anos) | MVP |
| CAT-03 | Item sem preço aparece como "sob consulta" | MVP |
| CAT-04 | Quantidade zerada inativa o item | MVP |
| CAT-05 | Importação em lote (CSV/XLSX) | F2 |
| CAT-06 | Vitrine pública do vendedor | F2 |
| CAT-07 | Integração com ERP por API | F3 |

### 6.6 Chat e negociação (CHT)
| ID | Requisito | Pri |
|---|---|---|
| CHT-01 | Uma conversa por par (pedido, vendedor), criada pelo match de catálogo ou quando o vendedor responde à cotação | MVP |
| CHT-02 | Texto em tempo real, com estado enviado e lido | MVP |
| CHT-03 | Imagens: até 6 por mensagem, câmera ou galeria | MVP |
| CHT-04 | **Proposta estruturada** como cartão: valor, quantidade, condição, garantia, prazo, validade e observação | MVP |
| CHT-05 | O comprador aceita, recusa ou contrapropõe. A proposta vira Aceita quando as duas partes concordam | MVP |
| CHT-06 | O vendedor atualiza ou retira a proposta. O histórico de versões é imutável | MVP |
| CHT-07 | Cartão de catálogo no chat (foto, preço, compatibilidade) | MVP |
| CHT-08 | "Compartilhar meu WhatsApp", por conversa e por decisão do comprador | MVP |
| CHT-09 | Bloquear usuário e denunciar mensagem ou conversa | MVP |
| CHT-10 | Mensagens de sistema (proposta aceita, pedido encerrado ou expirado) | MVP |
| CHT-11 | Pedido Cancelado, Expirado ou Concluído deixa as conversas somente leitura. Reabre em até 30 dias, **exceto** o Concluído com proposta Vendida | MVP |
| CHT-12 | Marca "foto do estoque real" | F2 |
| CHT-13 | Respostas rápidas, "digitando…" e vídeo curto | F2 |
| CHT-14 | Áudio e PDF | F3 |

### 6.7 Lista de conversas (LST)
| ID | Requisito | Pri |
|---|---|---|
| LST-01 | Comprador: lista de conversas do pedido (vendedor, origem Catálogo ou Cotação, melhor proposta, miniatura, não lidas, última atividade) | MVP |
| LST-02 | Ordenar e filtrar: menor preço, mais recente, não lidas | MVP |
| LST-03 | Filtros extras: só com foto, mais próximo, condição | F2 |
| LST-04 | Vendedor: "Cotações recebidas" e "Conversas", com filtro por status | MVP |
| LST-05 | Arquivar conversa | MVP |
| LST-06 | Comparação de propostas lado a lado | F2 |

### 6.8 Notificações (NOT)
| ID | Requisito | Pri |
|---|---|---|
| NOT-01 | Notificações no app, com contador | MVP |
| NOT-02 | E-mail agrupado para eventos-chave (cotação nova, proposta nova, mensagem não lida) | MVP |
| NOT-03 | *Web push* | F2 |
| NOT-04 | Preferências e resumo diário | F2 |
| NOT-05 | WhatsApp ou SMS | F3 |

### 6.9 Fechamento e reputação (FEC)
| ID | Requisito | Pri |
|---|---|---|
| FEC-01 | O comprador marca **Concluído** (indicando o vendedor) ou **Não comprei** (com motivo) | MVP |
| FEC-02 | O vendedor marca a proposta Aceita como **Vendida**. O estoque baixa e o comprador é notificado | MVP |
| FEC-03 | Alertas anti-golpe fixos no chat | MVP |
| FEC-04 | Avaliação do vendedor (nota e comentário) | F2 |
| FEC-05 | Reputação exibida: nota, negociações, tempo de resposta | F2 |
| FEC-06 | Ao marcar Vendida, o sistema **não pede nem registra** pagamento ou entrega. Só o valor da proposta fica gravado | MVP |

### 6.10 Administração (ADM) e monetização (MON)
| ID | Requisito | Pri |
|---|---|---|
| ADM-01 | Aprovar e reprovar vendedores, com motivo | MVP |
| ADM-02 | Tratar denúncias. Suspender ou banir | MVP |
| ADM-03 | Gerir taxonomia de veículos e categorias de peças | MVP |
| ADM-04 | Termos de uso e política de privacidade versionados, com aceite registrado (no primeiro login) | MVP |
| ADM-05 | Painel de métricas | F2 |
| ADM-06 | Moderação automática | F3 |
| MON-01 | Assinatura do vendedor, com limites e destaque no ranking | F3 |

## 7. Regras de negócio

| ID | Regra |
|---|---|
| RN-01 | Um pedido tem exatamente um veículo e uma peça |
| RN-02 | No máximo uma conversa por par (pedido, vendedor) |
| RN-03 | O vendedor vê do comprador só o primeiro nome e a cidade. O contato só por ação do comprador |
| RN-04 | Vendedor não aprovado não recebe pedidos nem publica catálogo |
| RN-05 | O preço do cartão de catálogo é indicativo. A **proposta** é o compromisso, dentro da validade |
| RN-06 | Aceita e Vendida **não** são contrato nem pagamento. A plataforma não é parte da venda (consta nos Termos) |
| RN-07 | Proposta Enviada ou em Contraproposta com validade vencida vira Expirada. Pedido sem renovação expira |
| RN-08 | Limites anti-spam por comprador: 10 pedidos abertos e 20 por dia (configuráveis) |
| RN-09 | Imagens sem EXIF e redimensionadas no envio |
| RN-10 | Peça de origem ilícita é removida, e o vendedor pode ser suspenso |
| RN-11 | Só uma proposta **Aceita** vira **Vendida**. Vendida é final. Só o administrador corrige, com auditoria |
| RN-12 | No máximo uma proposta Vendida por pedido. As demais ficam Expiradas |
| **RN-13** | **(nova)** "Com catálogo" = vendedor aprovado com **pelo menos um item ativo**. "Sem catálogo" = aprovado sem item ativo. A situação é calculada |
| **RN-14** | **(nova)** Pedido que casa com o catálogo **e** com o perfil do mesmo vendedor gera **uma** conversa, com origem Catálogo |
| **RN-15** | **(nova)** Não ter catálogo **nunca** exclui o vendedor do match de perfil |
| **RN-16** | **(nova)** Conversa criada pelo match de catálogo avisa o vendedor (notificação `CHAT`) e aparece em "Conversas" |
| **RN-17** | **(nova)** Ninguém recebe cotação nem match de catálogo de um pedido que ele mesmo criou |

## 8. Requisitos não funcionais

Os números são **metas de projeto** do MVP, a confirmar por teste.

| Área | Requisitos |
|---|---|
| **Desempenho** | Criar pedido p95 < 500 ms (RNF-DES-01). Cascata de veículo p95 < 300 ms, servida da base local (RNF-DES-02). Mensagem entre usuários online p95 < 1 s (RNF-DES-03). LCP < 2,5 s em 4G (RNF-DES-04). Matching em até 10 s em 95% dos casos (RNF-DES-05) |
| **Escala** | 10 mil usuários ativos, 1 mil conexões simultâneas de chat, 50 mil mensagens por dia (RNF-ESC-01). API sem estado e listas paginadas (RNF-ESC-02) |
| **Disponibilidade e confiabilidade** | ≥ 99,5% ao mês (RNF-DIS-01). Degradação graciosa (RNF-DIS-02). Mensagem persistida antes de confirmar e envio idempotente (RNF-CON-01). Backup diário, RPO ≤ 24 h e RTO ≤ 4 h (RNF-CON-02) |
| **Segurança** | TLS 1.2+, **sem senhas próprias** e sessão com expiração (RNF-SEG-01). **Autorização por objeto** em toda rota, com teste de IDOR (RNF-SEG-02). Papéis (RNF-SEG-03). Upload validado por tipo real, tamanho e quantidade, servido por URL assinada (RNF-SEG-04). Limite de taxa (RNF-SEG-05). Revisão contra o OWASP Top 10 (RNF-SEG-06). Auditoria de ações administrativas (RNF-SEG-07). Varredura de malware, F2 (RNF-SEG-08) |
| **Privacidade (LGPD)** | Coleta mínima e aceite registrado (RNF-PRI-01). Exportar e excluir dados (RNF-PRI-02). Retenção definida (RNF-PRI-03). **Logs sem dados pessoais** e fotos sem EXIF/GPS (RNF-PRI-04). Registros de acesso do Marco Civil (RNF-PRI-05) |
| **Usabilidade** | pt-BR, **mobile-first** (RNF-USA-01). Abrir pedido em até 4 passos (RNF-USA-02). Responder com foto em até 3 toques (RNF-USA-03). WCAG 2.1 AA nos fluxos principais (RNF-USA-04). Duas últimas versões de Chrome, Safari, Firefox e Edge (RNF-USA-05) |
| **Integração e manutenção** | FIPE atrás de um adaptador, com espelho local (RNF-INT-01). Logs estruturados, métricas e saúde (RNF-OBS-01). Monólito modular (RNF-MAN-01). CI, migrações versionadas, OpenAPI e cobertura ≥ 70% no domínio (RNF-MAN-02) |

## 9. Autenticação e segurança

Regras completas em [login com Google](login-com-google-regras-de-seguranca.md). Resumo:

| Tema | Decisão |
|---|---|
| Entrada | **Google (OpenID Connect)**, código de autorização com **PKCE**, conduzido pelo backend. A identidade guardada é o **`sub`** do Google, e o e-mail precisa estar **verificado** |
| Sessão | **JWT HS256** de 1 hora em cookie `access_token` (`HttpOnly; SameSite=Lax; Secure` em produção). Sem renovação no MVP |
| Confirmação do usuário | Cabeçalho **`X-Auth-Id`** (HMAC-SHA256 do id, com segredo próprio) em toda chamada autenticada, exceto `GET /api/v1/me`. Comparação em tempo constante |
| CSRF e CORS | O CSRF do Spring fica desligado de propósito (a defesa é `SameSite=Lax` mais `X-Auth-Id`). **Não há CORS** |
| Logout | Revoga o `jti` e apaga o cookie |
| Segredos | `JWT_SEGREDO`, `AUTH_ID_SEGREDO` e `LOGIN_STATE_SEGREDO` (base64, pelo menos 32 bytes, **distintos**, sem valor padrão), mais `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` e `GOOGLE_REDIRECT_URI` |
| Erros | Problem Details (RFC 9457), em português, sem *stack trace* |
| Logs | Sem segredos nem dados pessoais |
| Modelo de ameaças | Da sessão: [spec do backend](superpowers/specs/2026-09-29-backend-autenticacao-design.md), seção 3. Do login: [login com Google](login-com-google-regras-de-seguranca.md), seção 4 |
| Riscos assumidos | Limite de tentativas (plano seguinte), XSS (CSP no frontend), sem *refresh token*, dependência do Google, rotação dos segredos |

## 10. Arquitetura e stack

| Camada | Escolha |
|---|---|
| Linguagem | **Java 21** (LTS) |
| Backend | **Spring Boot 4.1**, Maven, Spring Security (*resource server*), Spring Modulith 2.1, Spring Data JPA |
| Banco | **PostgreSQL 18** (Docker no dev), **Liquibase** com changelogs em SQL, `ddl-auto=validate` |
| Frontend | **Next.js** (App Router, TypeScript), Tailwind CSS, shadcn/ui, TanStack Query, Zod |
| Tempo real | **SSE** (servidor → navegador) + `POST` REST idempotente (planejado) |
| Testes | TDD: JUnit 5, AssertJ, Testcontainers, MockMvc, ArchUnit e Modulith; Vitest, Testing Library, MSW e axe no frontend |
| Outros (planejado) | MinIO/S3 para imagens, Mailpit e SMTP para e-mail, Bucket4j para limite de taxa, Actuator e Micrometer |

**Arquitetura interna:** monólito modular, **módulos por funcionalidade com camadas dentro** (`domain` → `application` → `web`, e `infra`), verificado por `ApplicationModules.verify()` e por regras do ArchUnit.

| Módulo | Responsabilidade |
|---|---|
| `conta` | Usuário, perfil de vendedor, sessão, aceite de termos |
| `veiculo` | Taxonomia e adaptador da FIPE |
| `catalogo` | Itens, compatibilidade, fotos, estoque |
| `pedido` | Pedido e seu estado |
| `matching` | Match de catálogo e de perfil, ranking, limite |
| `negociacao` | Conversa, mensagens, proposta, tempo real |
| `notificacao` | Notificações no app e e-mail |
| `moderacao` | Aprovação, denúncias, bloqueio, auditoria |
| `compartilhado` | Erros, tipos comuns, imagens |

**Frontend (D6):** `app/` (componentes, páginas e `hooks/`), `api/` (conexão com o backend) e `services/` (regra de negócio, sem React), com a dependência só de `app/` para `services/` e de `services/` para `api/`, verificada por teste.

Diagramas: [contexto](diagramas/02_contexto_c4.puml), [contêineres](diagramas/07_containers_c4.puml), [componentes da API](diagramas/08_componentes_api_c4.puml).

## 11. Modelo de dados

**Conceitual:** `Usuário` 1—0..1 `PerfilVendedor` (com a atuação: tipos, marcas, modelos, categorias e região). `Veículo` é a hierarquia `Tipo → Marca → Modelo → Versão → Ano`. `PedidoPeça` tem 1 veículo e 1 peça, e gera N `Conversa` (uma por vendedor), cada uma com N `Mensagem` (texto, imagem, proposta, cartão de catálogo, sistema). `ItemCatálogo` tem N `Compatibilidade` e N fotos. `Notificação`, `Denúncia`, `Avaliação` (F2) e `VeículoPátio` (F2) completam. Diagrama: [`06_modelo_er`](diagramas/06_modelo_er.puml).

**Físico hoje** (Liquibase, backend Plano 1):

| Tabela | Colunas principais |
|---|---|
| `usuario` | `id`, `email`, `senha_hash`, `nome`, `criado_em`. **Vai mudar** para o login com Google: sai `senha_hash` e entram `google_sub`, `status`, `termos_versao_aceita`, `termos_aceitos_em`, `ultimo_login_em` |
| `perfil_vendedor` | `usuario_id`, `status` (`PENDENTE`, `APROVADO`, `REPROVADO`, `SUSPENSO`) |
| `token_revogado` | `jti`, `expira_em` |
| `notificacao` | `id`, `usuario_id`, `tipo` (`CHAT`, `PROPOSTA`, `PECA_A_PROCURA`, `SISTEMA`), `titulo`, `resumo`, `criada_em`, `lida` |

## 12. Frontend

Detalhes em [design das telas](superpowers/specs/2026-09-29-frontend-telas-design.md).

| Rota | Quem | Conteúdo |
|---|---|---|
| `/entrar` | visitante | **Entrar com Google** e mensagens de erro do login (nova) |
| `/` | todos | Leva a `/comprador` |
| `/comprador` | todos | Pesquisa, filtros, resultados do catálogo, "Pedir aos vendedores" e aba "Meus pedidos" |
| `/vendedor` | vendedor aprovado | Catálogo, inserir peça e filtro lateral de estoque |
| `/vendedor/pecas-a-procura` | vendedor aprovado | Cotações recebidas |
| `/configuracoes` | todos | Dados da conta e situação como vendedor |

**Navbar:** marca · Comprar · Vender (só com perfil de vendedor; desabilitada se em análise, reprovado ou suspenso) · **Peças à procura** (só vendedor aprovado, com contador) · sino de notificações (abas Todas e Chat; o contador soma chat mais outras e **não** inclui peças à procura) · Configurações.

**Planos do frontend:** 1) esqueleto e navbar (escrito, com mock), 2) dashboard do comprador, 3) dashboard do vendedor, e uma peça nova: a **tela de entrada com Google**.

## 13. API

| Endpoint | Autenticação | Estado |
|---|---|---|
| `GET /api/v1/auth/google/entrar`, `GET /api/v1/auth/google/retorno` | pública | **Planejado** (login com Google) |
| `POST /api/v1/termos/aceite` | cookie + `X-Auth-Id` | Planejado |
| `POST /api/v1/auth/registro`, `POST /api/v1/auth/login` | pública | **Implementado, mas vai sair** (senha) |
| `POST /api/v1/auth/logout` | cookie + `X-Auth-Id` | Implementado |
| `GET /api/v1/me` | só o cookie | Implementado (vai ganhar `termosPendentes`) |
| `GET /api/v1/notificacoes`, `PATCH /api/v1/notificacoes/{id}` | cookie + `X-Auth-Id` | Implementado |
| `GET /actuator/health` | pública | Implementado |
| Catálogo, veículos, pedidos, vendedor, conversas | cookie + `X-Auth-Id` | Planejado (planos 2 e 3 do frontend e backends seguintes) |

Erros em **Problem Details**: `{ "type": "about:blank", "title": "<texto em português>", "status": 400 }`.

## 14. Qualidade e TDD

- **Ciclo:** vermelho → verde → refatorar. Nenhum código de produção sem um teste falhando antes.
- **Pirâmide-alvo:** cerca de 70% de testes unitários de domínio, 25% de integração e 5% de ponta a ponta. Os testes de integração usam **PostgreSQL de verdade** (Testcontainers).
- **Autorização por objeto:** para cada rota protegida, um teste "outro usuário recebe 403 ou 404".
- **Definição de pronto:** testes verdes, cobertura do domínio, lint e `tsc` limpos, acessibilidade nas telas novas, OpenAPI e cliente atualizados, changelog Liquibase versionado e nenhum `TODO`.
- **Ordem de construção:** esqueleto → veículos (risco R1) → contas e login → catálogo → pedido e matching → negociação → notificações, moderação e fechamento.

## 15. Estado da implementação (2026-09-30)

| Área | Situação |
|---|---|
| **Documentação** | Spec do produto, stack, telas, backend, diagramas e estes três documentos. **Esta rodada ajusta tudo para o login com Google** |
| **Backend, Plano 1** | As 9 tarefas foram **executadas** (esqueleto, esquema, segredos, JWT, cadeia de segurança, cadastro e login **por senha**, `/me` e logout, notificações, seed de dev) com revisão por tarefa e uma revisão final. A última execução **completa** da suíte deu **158 testes verdes**. Depois, uma rodada final de correções acrescentou testes (184 no total pela contagem do Maven) e **a suíte completa ainda não foi repetida** (a última tentativa falhou só porque o Docker estava desligado). **O login por senha vai ser substituído pelo do Google** |
| **Git** | Repositório local criado em `IdeiaDeTCC` (branch `feat/backend-autenticacao`), **sem remoto**. Há **3 commits** (docs, tarefa 1, tarefa 2), feitos antes da ordem de não commitar. **Todo o resto está no working tree, sem `git add` nem commit** (D10) |
| **Frontend** | O Plano 1 está **escrito** e ajustado para `X-Auth-Id`. **Nenhum código de frontend existe ainda** |
| **Itens adiados pelas revisões** | Lista de rotas públicas duplicada (e `/actuator/health` com `401` se houver cookie), tamanho máximo dos campos do login, BCrypt dentro de transação (some com a senha), erros fora do MVC virando `401` (um banco fora do ar pareceria "sessão expirada"), regra "web só chama application" do documento de stack versus o código, `.gitattributes`. Ficam registrados no ledger da execução (`.superpowers/sdd/`) |

## 16. Próximos passos

1. **Revisão destes documentos pelo usuário** (pontos a confirmar nas seções 17 e nos dois documentos novos).
2. Plano de backend do **login com Google** (troca do cadastro e login por senha; tela `/entrar` no frontend).
3. Repetir a suíte completa do backend, com o Docker ligado, e decidir o que fazer com o trabalho não commitado.
4. Plano de backend da **taxonomia de veículos** (risco R1) e demais módulos, na ordem da seção 14.
5. Planos 2 e 3 do frontend.

## 17. Riscos e pontos em aberto

| # | Risco ou questão | Mitigação |
|---|---|---|
| R1 | A FIPE junta modelo e versão numa só *string*, e o pedido exige marca, modelo, versão e ano separados | Curadoria (ADM-03) e normalização no job. Primeira tarefa técnica dos veículos |
| R2 | A API pública da FIPE tem limite diário (a **confirmar**) e pode mudar | Espelho local e adaptador |
| R3 | **Partida a frio** de marketplace de duas pontas | Piloto numa região e nicho, com vendedores por convite. No TCC, dados de demonstração |
| R4 | Golpes, já que o pagamento é fora | Aprovação, denúncia, bloqueio, alertas e (F2) reputação |
| R5 | Spam de vendedores | Limite de 30 por pedido, ranking e opção de ignorar |
| R6 | Qualidade do matching com descrição livre | MVP: categorias e busca textual. F2: sinônimos. F3: IA |
| R7 | **Legal:** peças de desmanche são reguladas (Lei 12.977/2014) | Validar com fonte oficial antes de exigir credenciamento |
| R8 | Conteúdo indevido | Denúncia e moderação manual (MVP) |
| **R9** | **Dependência do Google:** fora do ar, ninguém entra. Conta Google comprometida, conta do aplicativo comprometida | Plano B em F2. Sessão curta. Recomendar verificação em duas etapas |
| **R10** | O aplicativo do Google em modo de teste só aceita contas de teste. PKCE com o cliente *Aplicativo da Web* a confirmar | Publicar o aplicativo antes de abrir ao público. Testar com o cliente real |

**Perguntas abertas:** região e nicho do piloto; a mesma conta é mesmo comprador e vendedor (premissa a); prazo padrão do pedido (7 dias); e os pontos das seções 10 do [stakeholders](stakeholders.md) e do [login](login-com-google-regras-de-seguranca.md).

## 18. Critérios de aceite do MVP

1. **Match de catálogo:** vendedor aprovado com "Farol esquerdo, Gol G6 2013–2016" no catálogo, pedido de "farol esquerdo" para um Gol 1.0 2014: aparece uma conversa com o cartão (foto e preço) em até 10 s.
2. **Match de perfil e foto:** vendedor de desmanche que atua em VW, **sem catálogo**: recebe a cotação, responde com foto da peça no carro e uma proposta, e o comprador vê as duas.
3. **Negociação:** contraproposta, atualização do valor (o histórico guarda as duas versões) e aceite. Fica **Aceita**, sem pedir pagamento.
4. **Vendida:** depois de Aceita, o vendedor marca **Vendida**. O pedido conclui, o comprador é notificado e o item do catálogo baixa. Proposta que não está Aceita não oferece a ação.
5. **Lista:** com 3 conversas de preços diferentes, a ordenação por menor preço as reordena, e o contador de não lidas bate.
6. **Moto e caminhão:** os cenários 1 a 4 funcionam com os dois tipos.
7. **Vendedor não aprovado** não recebe cotação e não aparece em lista alguma.
8. **Isolamento:** o vendedor B recebe 403 ou 404 ao abrir uma conversa entre o comprador e o vendedor A.
9. **Encerramento:** pedido expirado deixa as conversas somente leitura, e o comprador reabre em 30 dias.
10. **Fechamento pelo comprador:** "Concluído" indicando o vendedor, ou "Não comprei" com motivo. O vendedor indicado é avisado e pode marcar Vendida.
11. **Primeiro login:** entrar com o Google pela primeira vez cria a conta de comprador e pede o aceite dos Termos.
12. **E-mail não verificado:** o login é recusado com a mensagem correta.
13. **Retorno forjado:** um retorno sem o cookie de estado, ou com `state` diferente, é recusado.
14. **Mesma pessoa, segundo login:** reaproveita a conta (mesmo `sub`), inclusive se o e-mail mudou no Google.
15. **Conta suspensa** não abre sessão.
16. **Logout** invalida o token na hora.
17. **Vendedor com e sem catálogo:** o mesmo pedido gera conversa automática com o primeiro e cotação para o segundo. Um vendedor que casa pelos dois caminhos tem **uma só** conversa, com origem Catálogo.

## 19. Glossário

*Pedido* = uma peça para um veículo. *Cotação* = o pedido visto na caixa do vendedor. *Conversa* = chat entre um pedido e um vendedor. *Proposta* = oferta estruturada no chat. *Cartão de catálogo* = item do catálogo exibido no chat. *Pátio* = veículos que o vendedor tem para desmontar (F2). *Perfil de atuação* = tipos, marcas, modelos, categorias e região atendidos. *Vendedor com catálogo* = aprovado, com pelo menos um item ativo. *Vendedor sem catálogo* = aprovado, sem item ativo. *`sub`* = identificador único e estável da conta Google. *`X-Auth-Id`* = cabeçalho com o HMAC-SHA256 do id do usuário. *PKCE* = extensão do OAuth que amarra o código de autorização a quem o pediu.

## 20. Referências

- [Spec do produto](superpowers/specs/2026-09-29-marketplace-autopecas-design.md), [stack e TDD](superpowers/specs/2026-09-29-stack-e-tdd-design.md), [telas](superpowers/specs/2026-09-29-frontend-telas-design.md) e [backend](superpowers/specs/2026-09-29-backend-autenticacao-design.md), com as fontes de cada versão e da API FIPE.
- [OpenID Connect | Google for Developers](https://developers.google.com/identity/openid-connect/openid-connect) e [OAuth 2.0 para aplicativos de servidor web](https://developers.google.com/identity/protocols/oauth2/web-server).

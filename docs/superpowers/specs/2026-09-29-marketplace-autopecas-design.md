# Spec: Marketplace reverso de autopeças (carros, motos e caminhões)

> Nome do produto: provisório. Data: 2026-09-29. Atualizada em 2026-09-30 (**login só por Google**; vendedor **com** e **sem** catálogo). Status: **aprovada para desenho; aguardando revisão final do texto**.
> Visão consolidada de tudo: [especificação do projeto](../../especificacao-do-projeto.md). Lógica dos atores: [stakeholders](../../stakeholders.md). Login: [regras de segurança](../../login-com-google-regras-de-seguranca.md).
> Legenda de prioridade: **MVP** = primeira entrega (TCC) · **F2** = fase 2 · **F3** = futuro.

---

## 1. Contexto

Quem precisa de uma peça de veículo hoje procura loja por loja, liga, manda mensagem e compara preço na mão. O vendedor, por sua vez, não sabe quem está procurando o que ele tem em estoque, nem o que está no pátio.

O produto inverte a busca. O **comprador lança o pedido da peça** informando o veículo (tipo, marca, modelo, versão e ano). O sistema **entrega o pedido aos vendedores que podem atender**. Cada vendedor responde em **uma conversa de chat**, com um valor sugerido ou uma proposta estruturada. Se a peça já estiver no catálogo do vendedor, ela **aparece para o comprador sem esperar resposta**. Se o vendedor só tiver a peça num veículo ainda no pátio, ele **manda a foto pelo próprio chat**.

**Resultado esperado:** o comprador abre um pedido em menos de 1 minuto e recebe, numa lista de conversas, várias ofertas comparáveis. O vendedor recebe só pedidos relevantes para o que ele vende.

## 2. Decisões já tomadas (com o usuário)

| # | Decisão | Consequência na spec |
|---|---|---|
| D1 | Spec completa, com MVP marcado | Cada requisito tem prioridade (MVP/F2/F3) |
| D2 | Distribuição por **perfil de atuação + catálogo** | Vendedor com peça catalogada gera conversa automática, com preço. Vendedor que atua naquele veículo recebe a cotação e responde pelo chat |
| D3 | **Negócio sempre fechado fora da plataforma** | Sem pagamento, frete, nota fiscal ou custódia, em nenhuma fase. A plataforma conecta, conversa e registra o resultado declarado. Aumentam a importância de verificação de vendedor, reputação e alertas anti-golpe |
| D11 | **Login somente por Google** (e-mail verificado). Numeração do histórico completo na [especificação do projeto](../../especificacao-do-projeto.md) | Sem senha própria, sem cadastro por e-mail e senha e sem recuperação de senha. AUT-01 muda, AUT-02 e AUT-07 saem ou são absorvidos. Regras em [login com Google](../../login-com-google-regras-de-seguranca.md) |
| D12 | **Vendedor "com catálogo" e "sem catálogo"** são duas **situações** do mesmo vendedor aprovado | O vendedor com catálogo recebe conversas automáticas (match de catálogo) e também cotações. O sem catálogo recebe cotações pelo perfil de atuação. RN-13 a RN-17, detalhadas em [stakeholders](../../stakeholders.md) |

**Premissas a confirmar** (adotadas como padrão, fáceis de mudar): (a) a mesma conta pode ter perfil de comprador e de vendedor; (b) a monetização futura é por assinatura ou destaque do vendedor, sem comissão (coerente com D3); (c) o lançamento é em uma região piloto, sem cobertura nacional imediata.

## 3. Abordagem recomendada (entrega e arquitetura)

A abordagem de produto já está definida (D2 e D3). Falta escolher como entregar. A spec é agnóstica de stack, mas os requisitos não funcionais pressupõem esta escolha:

| Opção | Prós | Contras | Veredito |
|---|---|---|---|
| **A. Web responsivo/PWA + monólito modular + banco relacional + WebSocket + armazenamento de objetos** | Um único código, entrega rápida, matching e filtro por região em SQL, custo baixo, fácil de defender no TCC | Push nativo limitado no iOS; escala vertical primeiro | **Recomendada** |
| B. App nativo + microsserviços | Melhor experiência mobile, escala fina | Escopo e custo incompatíveis com o MVP | Rejeitada |
| C. BaaS (Firebase/Supabase) | Chat em tempo real e auth prontos | Regras de matching e autorização por objeto ficam espalhadas; dependência do fornecedor | Alternativa válida se a equipe for muito pequena |

Como o vendedor está no pátio com o celular na mão, a interface é **mobile-first**, mesmo sendo web.

## 4. Atores e glossário

| Ator | Descrição |
|---|---|
| **Comprador** | Lança pedidos, conversa, compara e conclui |
| **Vendedor** | Loja, desmanche ou autônomo. Mantém perfil de atuação e catálogo, responde cotações. Pode estar **com catálogo** (pelo menos um item ativo) ou **sem catálogo** (RN-13) |
| **Administrador** | Aprova vendedores, modera denúncias e mantém taxonomias |
| **Sistema** | Jobs de sincronização de veículos, matching, expiração e notificações |

**Glossário:** *Pedido* = uma peça para um veículo. *Cotação* = o pedido visto na caixa do vendedor. *Conversa* = chat 1:1 entre um pedido e um vendedor. *Proposta* = oferta estruturada dentro do chat. *Cartão de catálogo* = item do catálogo exibido no chat, com preço. *Pátio* = veículos que o vendedor tem para desmontar (F2). *Perfil de atuação* = tipos de veículo, marcas, modelos, categorias e região atendidos pelo vendedor. *Vendedor com catálogo* = aprovado, com pelo menos um item ativo. *Vendedor sem catálogo* = aprovado, sem item ativo (caso típico do desmanche). *`sub`* = identificador único e estável da conta Google.

## 5. Fluxo principal

1. O comprador escolhe tipo → marca → modelo → versão → ano, descreve a peça, informa cidade/UF e envia.
2. O sistema cria o pedido (status **Aberto**) e processa o matching de forma assíncrona.
3. **Match de catálogo:** itens compatíveis geram conversas automáticas com o cartão de catálogo (foto e preço). O comprador as vê na lista assim que forem criadas.
4. **Match de perfil:** os vendedores que atuam naquele veículo e região recebem a cotação e ficam livres para responder.
5. O vendedor responde por texto, foto ou **proposta** (valor, condição, garantia, prazo, validade). O comprador aceita, recusa ou faz uma contraproposta. Ele ordena as conversas por menor preço, mais recente ou não lidas.
6. Quando o valor está acordado, a proposta fica **Aceita**. Pagamento e entrega são combinados e feitos **fora do sistema, pelo meio que os dois escolherem**. O sistema não processa nem registra pagamento. Depois de fechar o negócio por esse meio externo, o vendedor marca a proposta como **Vendida**. O pedido passa a **Concluído** e o item do catálogo baixa do estoque.

**Estados do pedido:** Aberto → Em negociação (primeira resposta) → Concluído · Cancelado · Expirado. O estado Concluído tem um **resultado**: *Vendido* (automático quando uma proposta vira Vendida) ou *Sem compra* (o comprador marca "Não comprei", com motivo).
**Estados da proposta:** Enviada → **Aceita → Vendida**. Saídas alternativas: Recusada · Contraproposta · Retirada · Expirada.
Aceita significa "valor combinado" e Vendida significa "negócio fechado fora do sistema". Só uma proposta Aceita pode virar Vendida.

## 6. Modelo conceitual

`Usuário` 1—0..1 `PerfilVendedor` (com `AtuaçãoVendedor`: tipos, marcas, modelos, categorias e região).
`Veículo` é uma hierarquia: `TipoVeículo → Marca → Modelo → Versão → AnoVersão`, sincronizada da FIPE (RNF-INT-01).
`PedidoPeça` → 1 veículo (AnoVersão) + 1 descrição de peça. Ele gera N `Conversa` (uma por vendedor), cada uma com N `Mensagem` (texto, imagem, proposta, cartão de catálogo, sistema).
`ItemCatálogo` do vendedor tem N `Compatibilidade` (veículo/faixa de anos) e N fotos.
`Notificação`, `Denúncia`, `Avaliação` (F2) e `VeículoPátio` (F2) completam o modelo.

---

## 7. Requisitos funcionais

### 7.1 Contas e perfis (AUT)
| ID | Requisito | Pri |
|---|---|---|
| AUT-01 | **Entrar com o Google** (e-mail verificado). O primeiro login cria a conta. Sem senha própria (D11) | MVP |
| AUT-02 | ~~Recuperação de senha~~ **Não se aplica**: não há senha, e a recuperação da conta é a do Google | — |
| AUT-03 | A mesma conta pode ter perfil de comprador e de vendedor | MVP |
| AUT-04 | Perfil de vendedor: nome/razão social, CPF/CNPJ, tipo (loja, desmanche, autônomo), cidade/UF, WhatsApp, logo | MVP |
| AUT-05 | O vendedor só recebe pedidos e publica catálogo depois de **aprovado por um administrador** | MVP |
| AUT-06 | Editar dados e excluir a conta, com anonimização do histórico (LGPD) | MVP |
| AUT-07 | ~~Login social (Google)~~ **Absorvido por AUT-01** (passou de F2 para o MVP e é o único meio de entrar) | — |
| AUT-08 | Selo "Verificado" (validação de CNPJ e documento) e verificação de telefone por OTP | F2 |

### 7.2 Veículo (VEI)
| ID | Requisito | Pri |
|---|---|---|
| VEI-01 | Selecionar **tipo** (carro, moto, caminhão), **marca, modelo, versão e ano** em cascata, com busca digitável | MVP |
| VEI-02 | Taxonomia mantida em base local e sincronizada da FIPE por job periódico. A consulta do usuário nunca depende da API externa | MVP |
| VEI-03 | O administrador corrige nomes e cadastra veículos ausentes | MVP |
| VEI-04 | "Minha garagem": veículos salvos do comprador, reutilizáveis nos pedidos | F2 |
| VEI-05 | Veículo não listado, informado em texto livre e enviado para curadoria | F2 |
| VEI-06 | Preenchimento por placa ou chassi | F3 |

### 7.3 Pedido de peça (PED)
| ID | Requisito | Pri |
|---|---|---|
| PED-01 | Criar pedido com: veículo (obrigatório), descrição da peça (obrigatória), categoria (opcional, sugerida), código OEM/fabricante (opcional), condição aceita (nova, usada, recondicionada ou qualquer), cidade/UF (obrigatória), alcance (cidade, estado, Brasil), até 5 fotos (opcional) | MVP |
| PED-02 | Um pedido tem uma peça e um veículo. Várias peças exigem vários pedidos | MVP |
| PED-03 | Sugestão de categorias e peças enquanto o comprador digita | MVP |
| PED-04 | Validade padrão de 7 dias, com renovação pelo comprador | MVP |
| PED-05 | Editar (antes da 1ª resposta), cancelar, duplicar e encerrar | MVP |
| PED-06 | Painel "Meus pedidos" com status e contadores de conversas e propostas | MVP |
| PED-07 | Resultados imediatos: ao enviar, os matches de catálogo aparecem sem esperar resposta de vendedor | MVP |
| PED-08 | Carrinho de pedido, com várias peças do mesmo veículo em uma tela | F2 |

### 7.4 Distribuição e matching (DIS)
| ID | Requisito | Pri |
|---|---|---|
| DIS-01 | O vendedor define o **perfil de atuação**: tipos de veículo, marcas, modelos/anos (opcional), categorias de peças e alcance geográfico | MVP |
| DIS-02 | **Match de catálogo:** itens ativos compatíveis com o veículo e com a peça (texto, categoria ou código) geram uma conversa automática com o cartão do item | MVP |
| DIS-03 | **Match de perfil:** vendedores cujo perfil cobre veículo, categoria e região recebem o pedido em "Cotações recebidas" | MVP |
| DIS-04 | Ranqueamento e **limite de N vendedores por pedido** (padrão 30, configurável). Critérios: relevância (catálogo > modelo/ano > marca), distância, atividade | MVP |
| DIS-05 | O matching roda em fila. A criação do pedido responde na hora e os matches chegam em segundos | MVP |
| DIS-06 | O vendedor ignora um pedido e ele some da caixa dele | MVP |
| DIS-07 | Pedido aberto é reavaliado quando um item compatível é cadastrado depois | F2 |
| DIS-08 | Reputação entra no ranking. O vendedor pode silenciar categorias | F2 |
| DIS-09 | **Pátio:** o vendedor registra veículos em desmontagem, e os pedidos que casam são marcados como "possível peça no pátio" | F2 |

### 7.5 Catálogo do vendedor (CAT)
| ID | Requisito | Pri |
|---|---|---|
| CAT-01 | Criar, editar e remover item: título, categoria, código OEM/fabricante, condição, preço, quantidade, até 8 fotos, garantia, observações, ativo/inativo | MVP |
| CAT-02 | Compatibilidade do item: lista de veículos (marca, modelo, versão, faixa de anos) | MVP |
| CAT-03 | Item sem preço é exibido como "sob consulta" | MVP |
| CAT-04 | Quantidade zerada inativa o item | MVP |
| CAT-05 | Importação em lote por planilha (CSV/XLSX) | F2 |
| CAT-06 | Vitrine pública do vendedor | F2 |
| CAT-07 | Integração com ERP/estoque por API | F3 |

### 7.6 Chat e negociação (CHT)
| ID | Requisito | Pri |
|---|---|---|
| CHT-01 | Uma conversa por par (pedido, vendedor). Ela é criada pelo match de catálogo ou quando o vendedor responde à cotação | MVP |
| CHT-02 | Mensagens de texto em tempo real, com estado enviado e lido | MVP |
| CHT-03 | Envio de **imagens** (até 6 por mensagem, câmera ou galeria), com pré-visualização e ampliação | MVP |
| CHT-04 | **Proposta estruturada** exibida como cartão: valor, quantidade, condição, garantia, prazo de disponibilidade, validade e observação | MVP |
| CHT-05 | O comprador **aceita, recusa ou faz contraproposta** (valor). O vendedor, por sua vez, aceita, recusa ou atualiza a contraproposta. A proposta vira **Aceita** quando as duas partes concordam no valor | MVP |
| CHT-06 | O vendedor atualiza ou retira a proposta. O histórico de versões é imutável | MVP |
| CHT-07 | Cartão de catálogo dentro do chat, com foto, preço e compatibilidade | MVP |
| CHT-08 | Botão "compartilhar meu WhatsApp", por conversa e por decisão do comprador | MVP |
| CHT-09 | Bloquear usuário e denunciar mensagem ou conversa | MVP |
| CHT-10 | Mensagens de sistema (proposta aceita, pedido encerrado, pedido expirado) | MVP |
| CHT-11 | Pedido Cancelado, Expirado ou Concluído deixa as conversas somente leitura. O comprador pode reabrir, em até 30 dias, um pedido Expirado, Cancelado ou Concluído sem compra. **Pedido Concluído com proposta Vendida não reabre** (RN-11) | MVP |
| CHT-12 | Marca opcional "foto do estoque real" nas imagens do vendedor | F2 |
| CHT-13 | Respostas rápidas do vendedor, indicador "digitando…" e vídeo curto | F2 |
| CHT-14 | Áudio e documentos (PDF) | F3 |

### 7.7 Lista de conversas e comparação (LST)
| ID | Requisito | Pri |
|---|---|---|
| LST-01 | **Comprador:** lista de conversas do pedido. Cada linha mostra vendedor, origem (Catálogo/Cotação), melhor ou última proposta, miniatura, não lidas e última atividade | MVP |
| LST-02 | Ordenação e filtros: menor preço, mais recente, não lidas | MVP |
| LST-03 | Filtros extras: só com foto, mais próximo, condição da peça | F2 |
| LST-04 | **Vendedor:** duas caixas, "Cotações recebidas" (sem conversa iniciada) e "Conversas", com filtro por status | MVP |
| LST-05 | Arquivar conversa | MVP |
| LST-06 | Comparação de propostas lado a lado | F2 |

### 7.8 Notificações (NOT)
| ID | Requisito | Pri |
|---|---|---|
| NOT-01 | Notificações dentro do app, com contador | MVP |
| NOT-02 | E-mail para eventos-chave, agrupado para não gerar spam: nova cotação compatível, nova proposta, mensagem não lida há mais de X minutos | MVP |
| NOT-03 | Web push | F2 |
| NOT-04 | Preferências de notificação e resumo diário | F2 |
| NOT-05 | Notificação por WhatsApp ou SMS | F3 |

### 7.9 Fechamento e reputação (FEC)
| ID | Requisito | Pri |
|---|---|---|
| FEC-01 | O comprador marca o pedido como **Concluído** (indicando o vendedor escolhido, opcional) ou **Não comprei** (com motivo), que conclui o pedido com resultado *Sem compra*. Quando uma proposta vira Vendida (FEC-02), o pedido passa a Concluído (resultado *Vendido*) sozinho | MVP |
| FEC-02 | O vendedor marca uma proposta **Aceita** como **Vendida**, depois de fechar o negócio por meio externo. Se a proposta veio de item do catálogo, o estoque baixa. O comprador é notificado e as outras conversas do pedido ficam somente leitura | MVP |
| FEC-03 | **Alertas anti-golpe** fixos no chat (ex.: não pagar antecipado sem conferir o vendedor e a peça) | MVP |
| FEC-04 | Avaliação do vendedor (nota 1–5 e comentário), só por comprador que concluiu com ele | F2 |
| FEC-05 | Reputação exibida: nota média, negociações concluídas, tempo médio de resposta | F2 |
| FEC-06 | Ao marcar Vendida, o sistema **não pede nem registra** forma de pagamento, entrega ou valor pago. Só o valor da proposta aceita fica gravado, para métricas e reputação | MVP |

**Fora de escopo permanente (D3):** pagamento, frete, nota fiscal, custódia e mediação de disputa.

### 7.10 Administração e moderação (ADM)
| ID | Requisito | Pri |
|---|---|---|
| ADM-01 | Aprovar e reprovar vendedores, com registro de motivo | MVP |
| ADM-02 | Tratar denúncias (usuário, mensagem, item). Suspender ou banir | MVP |
| ADM-03 | Gerir taxonomia de veículos e categorias de peças | MVP |
| ADM-04 | Termos de uso e política de privacidade versionados, com aceite registrado **no primeiro login** | MVP |
| ADM-05 | Painel de métricas: pedidos, taxa de resposta, tempo até a 1ª proposta, conversão declarada | F2 |
| ADM-06 | Moderação automática de imagens e texto | F3 |

### 7.11 Monetização (MON)
| ID | Requisito | Pri |
|---|---|---|
| MON-01 | Assinatura do vendedor, planos com limites e destaque no ranking. O ranking (DIS-04) já é desenhado com espaço para um fator de impulsionamento | F3 |

## 8. Regras de negócio

| ID | Regra |
|---|---|
| RN-01 | Um pedido tem exatamente um veículo e uma peça |
| RN-02 | Existe no máximo uma conversa por par (pedido, vendedor) |
| RN-03 | O vendedor vê do comprador apenas o primeiro nome e a cidade. O contato só é revelado por ação do comprador (CHT-08) |
| RN-04 | Vendedor não aprovado não recebe pedidos nem publica catálogo público |
| RN-05 | O preço do cartão de catálogo é indicativo. A **proposta** é o compromisso do vendedor dentro da validade informada |
| RN-06 | "Aceita" registra o valor combinado e "Vendida" registra o fechamento declarado pelo vendedor. Nenhum dos dois é contrato nem pagamento. Pagamento e entrega ocorrem **fora do sistema, pelo meio escolhido pelas duas partes**, e a plataforma não é parte da venda (deve constar nos Termos de Uso) |
| RN-07 | Proposta Enviada ou em Contraproposta cujo prazo de validade vence muda sozinha para Expirada. A validade deixa de valer quando a proposta é Aceita. Pedido sem renovação expira no prazo (PED-04) |
| RN-08 | Limites anti-spam por comprador (padrão: 10 pedidos abertos e 20 pedidos por dia, configuráveis) |
| RN-09 | Imagens têm metadados EXIF removidos e são redimensionadas no envio |
| RN-10 | Peças de origem ilícita ou fora das regras da plataforma são removidas e o vendedor pode ser suspenso |
| RN-11 | Só uma proposta **Aceita** pode virar **Vendida**. Vendida é estado final. Só um administrador corrige, com registro em auditoria |
| RN-12 | Um pedido tem no máximo uma proposta Vendida. Ao marcar Vendida, o pedido passa a Concluído e as demais propostas ficam Expiradas |
| RN-13 | **(nova)** "Com catálogo" = vendedor **aprovado** com **pelo menos um item ativo** (publicado e com quantidade maior que zero). "Sem catálogo" = aprovado sem item ativo. A situação é **calculada**, muda sozinha e vale para os pedidos novos |
| RN-14 | **(nova)** Pedido que casa com o catálogo **e** com o perfil do mesmo vendedor gera **uma** conversa (RN-02), com origem **Catálogo**. A cotação não é duplicada |
| RN-15 | **(nova)** Não ter catálogo **nunca** exclui o vendedor do match de perfil |
| RN-16 | **(nova)** A conversa criada pelo match de catálogo avisa o vendedor (notificação `CHAT`) e aparece em "Conversas", e não em "Cotações recebidas" |
| RN-17 | **(nova)** Ninguém recebe cotação nem match de catálogo de um pedido que ele mesmo criou |

## 9. Requisitos não funcionais

Os números abaixo são **metas de projeto** para o MVP e devem ser confirmados por teste (seção 12).

### 9.1 Desempenho e escalabilidade
| ID | Requisito | Meta | Pri |
|---|---|---|---|
| RNF-DES-01 | Criar pedido (sem upload) | p95 < 500 ms | MVP |
| RNF-DES-02 | Cascata de veículo (marca/modelo/versão/ano) | p95 < 300 ms, servida da base local | MVP |
| RNF-DES-03 | Entrega de mensagem entre usuários online | p95 < 1 s | MVP |
| RNF-DES-04 | Carregamento em 4G | LCP < 2,5 s nas telas principais | MVP |
| RNF-DES-05 | Matching de um pedido concluído | em até 10 s em 95% dos casos | MVP |
| RNF-ESC-01 | Dimensionamento inicial | 10 mil usuários ativos, 1 mil conexões simultâneas de chat, 50 mil mensagens/dia | MVP |
| RNF-ESC-02 | API sem estado, escalável horizontalmente. Listas sempre paginadas | — | MVP |

### 9.2 Disponibilidade e confiabilidade
| ID | Requisito | Meta | Pri |
|---|---|---|---|
| RNF-DIS-01 | Disponibilidade mensal | ≥ 99,5% | MVP |
| RNF-DIS-02 | Degradação graciosa: se o tempo real falhar, o chat cai para consulta periódica. Se a FIPE cair, nada muda para o usuário | — | MVP |
| RNF-CON-01 | Mensagem persistida antes da confirmação de envio. Envio idempotente por id do cliente (sem duplicar em reenvio) | — | MVP |
| RNF-CON-02 | Backup diário, com RPO ≤ 24 h e RTO ≤ 4 h. Restauração testada | — | MVP |

### 9.3 Segurança
| ID | Requisito | Pri |
|---|---|---|
| RNF-SEG-01 | Tráfego só por TLS 1.2+. **Sem senhas próprias** (o login é pelo Google). Sessões com expiração. A renovação do token fica para depois do MVP | MVP |
| RNF-SEG-02 | **Autorização por objeto** em toda rota: um vendedor só acessa conversas e pedidos dos quais participa. Testes automatizados de acesso indevido (IDOR) | MVP |
| RNF-SEG-03 | Controle de acesso por papel (comprador, vendedor, admin) | MVP |
| RNF-SEG-04 | Upload validado por tipo real (não só extensão), tamanho (máx. 10 MB) e quantidade. Arquivos servidos por URL assinada | MVP |
| RNF-SEG-05 | Limite de taxa (login, criação de pedido, mensagens) e proteção contra automação | MVP |
| RNF-SEG-06 | Cobertura das vulnerabilidades do OWASP Top 10 verificada em revisão de segurança antes do lançamento | MVP |
| RNF-SEG-07 | Log de auditoria das ações administrativas | MVP |
| RNF-SEG-08 | Varredura de malware nos uploads | F2 |

### 9.4 Privacidade e conformidade (LGPD)
| ID | Requisito | Pri |
|---|---|---|
| RNF-PRI-01 | Coleta mínima de dados, com base legal e aceite registrado (ADM-04) | MVP |
| RNF-PRI-02 | Direitos do titular: exportar e excluir dados (AUT-06) | MVP |
| RNF-PRI-03 | Política de retenção definida (mensagens e imagens de pedidos encerrados) | MVP |
| RNF-PRI-04 | Logs sem dados pessoais em texto. EXIF/GPS das fotos removidos (RN-09) | MVP |
| RNF-PRI-05 | Guarda de registros de acesso conforme o Marco Civil da Internet | MVP |

### 9.5 Usabilidade e acessibilidade
| ID | Requisito | Meta | Pri |
|---|---|---|---|
| RNF-USA-01 | Interface em pt-BR, **mobile-first** e responsiva | — | MVP |
| RNF-USA-02 | Abrir um pedido | ≤ 4 passos, cerca de 1 min | MVP |
| RNF-USA-03 | Vendedor responder com foto | ≤ 3 toques a partir da conversa | MVP |
| RNF-USA-04 | Acessibilidade | WCAG 2.1 AA nos fluxos principais (pedido, chat, lista) | MVP |
| RNF-USA-05 | Navegadores | duas últimas versões de Chrome, Safari, Firefox e Edge; Android e iOS recentes | MVP |

### 9.6 Integração, observabilidade e manutenibilidade
| ID | Requisito | Pri |
|---|---|---|
| RNF-INT-01 | Fonte de veículos atrás de um **adaptador**, com espelho local, atualização periódica e tolerância aos limites de requisição da API pública da FIPE. Trocar a fonte não afeta o restante do sistema | MVP |
| RNF-OBS-01 | Logs estruturados, métricas básicas, rastreio de erros e verificação de saúde do serviço | MVP |
| RNF-MAN-01 | Monólito modular com fronteiras claras (contas, veículos, pedidos, matching, catálogo, chat, notificações, admin) | MVP |
| RNF-MAN-02 | Integração contínua com testes automatizados, migrações versionadas e API documentada (OpenAPI). Meta de cobertura ≥ 70% nas regras de domínio | MVP |

## 10. Fora de escopo

- Pagamento, frete, nota fiscal, custódia e disputa (D3, permanente).
- Preço de referência de peças. A tabela FIPE é **só a taxonomia** de veículos, não uma tabela de preço de peças.
- Reconhecimento de peça por foto (IA), compatibilidade automática por código OEM e app nativo (candidatos a F3).

## 11. Riscos e questões em aberto

| # | Risco / questão | Mitigação proposta |
|---|---|---|
| R1 | **A FIPE junta modelo e versão numa só string** (ex.: "Gol 1.0 Flex 12V 5p"). O pedido exige marca, modelo, versão e ano separados | Curadoria de "família de modelo" (ADM-03) e normalização no job de sincronização. Definir o algoritmo é a primeira tarefa técnica |
| R2 | A API pública da FIPE tem limite diário de requisições (informado na documentação de terceiros como 500 sem token e 1.000 com token; **confirmar**) e pode mudar | Espelho local (RNF-INT-01) e adaptador |
| R3 | **Partida a frio** de marketplace de duas pontas: sem vendedor o comprador não volta | Piloto numa região e nicho, com vendedores cadastrados por convite. No TCC, carga de dados de demonstração |
| R4 | Golpes, já que o pagamento é fora da plataforma | Aprovação de vendedor, denúncia, bloqueio, alertas (FEC-03), reputação (F2) |
| R5 | Spam de vendedores (o mesmo pedido chega a muita gente) | Limite N por pedido, ranking e opção de ignorar |
| R6 | Qualidade do matching com descrição livre da peça ("farol", "lanterna", "paralama") | MVP: categorias + busca textual. F2: sinônimos. F3: IA |
| R7 | **Legal:** o comércio de peças usadas de desmanche é regulado (Lei do Desmonte, Lei 12.977/2014) e há responsabilidade sobre a origem das peças | Validar com fonte oficial ou assessoria jurídica antes de decidir se a plataforma exige ou confere o credenciamento. Termos de uso (RN-06, RN-10) |
| R8 | Conteúdo indevido em imagens e textos | Denúncia e moderação manual (MVP), automática (F3) |
| R9 | **Dependência do Google:** fora do ar, ninguém entra. Conta Google comprometida, conta do aplicativo comprometida | Plano B em F2 (outro provedor). Sessão curta. Recomendar verificação em duas etapas no Google |
| R10 | O aplicativo do Google em modo de teste só aceita contas de teste, e o PKCE com o cliente "Aplicativo da Web" precisa ser confirmado | Publicar o aplicativo antes de abrir ao público. Testar com o cliente real |

**Perguntas abertas (não bloqueiam a spec):** região e nicho do piloto; se a mesma conta é mesmo comprador e vendedor (premissa a); valor da validade padrão do pedido (7 dias).

## 12. Critérios de aceite do MVP (cenários de verificação)

1. **Match de catálogo:** *Dado* um vendedor aprovado com item "Farol esquerdo — Gol G6 2013–2016" no catálogo, *quando* o comprador abre pedido de "farol esquerdo" para um Gol 1.0 2014, *então* aparece uma conversa com o cartão do item (foto e preço) em até 10 s.
2. **Match de perfil e foto:** *Dado* um vendedor de desmanche que atua em VW, sem o item cadastrado, *quando* o pedido é aberto, *então* ele recebe a cotação, responde com foto da peça no carro e uma proposta, e o comprador vê ambas na conversa.
3. **Negociação:** o comprador faz uma contraproposta, o vendedor atualiza o valor (o histórico guarda as duas versões) e o comprador aceita. O status muda para **Aceita** e nenhum pagamento é solicitado.
4. **Vendida:** depois de Aceita, o vendedor marca a proposta como **Vendida**. O pedido passa a Concluído, o comprador é notificado e o item do catálogo baixa. O sistema não pede forma de pagamento nem de entrega. Uma proposta que não está Aceita não oferece essa ação.
5. **Lista:** com 3 conversas de preços diferentes, a ordenação por menor preço as reordena corretamente e o contador de não lidas bate com a realidade.
6. **Moto e caminhão:** os cenários 1–4 funcionam com um veículo do tipo moto e outro do tipo caminhão, usando a mesma cascata de marca, modelo, versão e ano.
7. **Vendedor não aprovado** não recebe cotação e não aparece em nenhuma lista.
8. **Isolamento:** o vendedor B recebe 403/404 ao tentar abrir uma conversa entre o comprador e o vendedor A (RNF-SEG-02).
9. **Encerramento:** pedido expirado deixa as conversas somente leitura. O comprador reabre em 30 dias e a conversa volta a aceitar mensagens.
10. **Fechamento pelo comprador:** o comprador marca "Concluído" indicando um vendedor, ou "Não comprei" com motivo. O vendedor indicado recebe o aviso e pode marcar a proposta como Vendida.
11. **Primeiro login:** entrar com o Google pela primeira vez cria a conta de comprador e pede o aceite dos Termos.
12. **E-mail não verificado:** o login é recusado, com a mensagem correta.
13. **Retorno forjado:** um retorno do Google sem o cookie de estado, ou com `state` diferente, é recusado.
14. **Mesma pessoa, segundo login:** reaproveita a conta (mesmo `sub`), inclusive se o e-mail mudou no Google.
15. **Conta suspensa** não abre sessão.
16. **Logout** invalida o token na hora.
17. **Com e sem catálogo:** o mesmo pedido gera conversa automática com o vendedor com catálogo e cotação para o sem catálogo. O vendedor que casa pelos dois caminhos tem **uma só** conversa, com origem Catálogo (RN-14).

## 13. Próximos passos

1. Revisão do texto pelo usuário (ajustes de prioridade, premissas e perguntas abertas).
2. Diagramas em PlantUML gerados em [`docs/diagramas/`](../../diagramas/README.md): casos de uso, contexto C4, sequências (pedido e matching; negociação até Vendida), estados (pedido e proposta) e ER lógico.
3. Stack e estratégia de TDD definidas em [2026-09-29-stack-e-tdd-design.md](2026-09-29-stack-e-tdd-design.md), com os diagramas C4 de contêineres e de componentes.
4. Telas do frontend (navbar, dashboard do comprador e do vendedor) em [2026-09-29-frontend-telas-design.md](2026-09-29-frontend-telas-design.md), divididas em três planos de implementação.
5. Backend, Plano 1 (autenticação com JWT em cookie HttpOnly + `X-Auth-Id`, e notificações) em [2026-09-29-backend-autenticacao-design.md](2026-09-29-backend-autenticacao-design.md). Foi construído com login por senha, que **sai** pelo login com Google.
6. **Login com Google** (2026-09-30): regras de segurança em [login-com-google-regras-de-seguranca.md](../../login-com-google-regras-de-seguranca.md). Lógica do comprador e dos vendedores com e sem catálogo em [stakeholders.md](../../stakeholders.md). Tudo reunido na [especificação do projeto](../../especificacao-do-projeto.md).
7. Em seguida, o plano de backend do login com Google e o da taxonomia de veículos, que trata o risco R1 (modelo e versão da FIPE).

## Referências

- [Guia da API FIPE (TabNews)](https://www.tabnews.com.br/dedx/guia-completo-da-api-fipe-como-consultar-precos-de-veiculos-no-brasil): hierarquia referência → tipo → marca → modelo → ano, endpoint v2 e limites de uso.
- Tipos de veículo `cars`, `motorcycles` e `trucks`, limites de requisição e termos de uso da API pública: vieram de resultados de busca sobre a API v2 (`fipe.parallelum.com.br/api/v2`), sem leitura da documentação oficial. **Confirmar na documentação oficial antes de depender deles (R2).**

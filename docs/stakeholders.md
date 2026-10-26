# Stakeholders: a lógica do comprador, do vendedor com catálogo e do vendedor sem catálogo

> Data: 2026-09-30. Status: **rascunho para revisão**.
> Complementa a [especificação do projeto](especificacao-do-projeto.md) e a [spec do produto](superpowers/specs/2026-09-29-marketplace-autopecas-design.md). Os IDs (AUT, PED, DIS, CAT, CHT, LST, NOT, FEC, ADM, RN) vêm dessa spec. Regras novas deste documento são `RN-13` em diante e estão marcadas **(nova)**: precisam da sua confirmação (seção 10).
> Entrada no sistema: [login com Google](login-com-google-regras-de-seguranca.md).

## 1. Quem são

| Stakeholder | Papel | O que quer do sistema |
|---|---|---|
| **Comprador** | Precisa de uma peça para um carro, moto ou caminhão | Achar a peça sem ligar loja por loja e comparar ofertas no mesmo lugar |
| **Vendedor com catálogo** | Loja, desmanche ou autônomo aprovado que **tem peças cadastradas e ativas** | Ser encontrado por quem busca o que ele já tem, sem esperar ser procurado |
| **Vendedor sem catálogo** | Vendedor aprovado que **não tem nenhuma peça ativa cadastrada** (desmanche com carros no pátio, autônomo, loja que ainda não cadastrou, ou com tudo esgotado) | Receber só os pedidos que ele pode atender e responder rápido, por chat, com foto |
| *Administrador* | Apoio | Aprovar vendedores, tratar denúncias, manter as taxonomias (ADM) |
| *Sistema* | Apoio | Sincronizar veículos, casar pedidos, expirar e notificar |
| *Google* | Externo | Provar quem é a pessoa no login |
| *FIPE* | Externo | Fonte da taxonomia de veículos |

**Princípios que valem para os três:**

1. **Uma conta só**, criada no primeiro login com o Google. Todo usuário é comprador (AUT-03). Quem quiser vender cria um **perfil de vendedor** na mesma conta, e ele só funciona depois da **aprovação do administrador** (AUT-05, RN-04).
2. **"Com catálogo" e "sem catálogo" não são dois tipos de conta.** São uma **situação** do vendedor aprovado, calculada pelo estado do catálogo dele (seção 2.3). Ela muda sozinha, sem ninguém pedir.
3. **O negócio sempre fecha fora da plataforma** (D3, RN-06). O sistema conecta, conversa e registra o resultado declarado. Não há pagamento, frete nem custódia.
4. **Privacidade entre as partes** (RN-03): o vendedor vê do comprador só o primeiro nome e a cidade. O contato só aparece se o comprador compartilhar (CHT-08).

## 2. Modelo de papéis e estados

### 2.1 Comprador

Não tem estado próprio. Todo usuário com sessão ativa é comprador.

### 2.2 Vendedor: situação do perfil

| Situação | Como chega | O que pode fazer | O que aparece na navbar |
|---|---|---|---|
| Sem perfil de vendedor | Padrão | Só age como comprador | Sem o item "Vender" |
| **Pendente** | Enviou o cadastro de vendedor (AUT-04) e o perfil de atuação (DIS-01) | Só age como comprador. **Não recebe pedidos e não publica catálogo** (RN-04) | "Vender (em análise)", desabilitado |
| **Aprovado** | O administrador aprovou (ADM-01) | Tudo da seção 4 | "Vender" e "Peças à procura" ativos |
| **Reprovado** | O administrador recusou, com motivo | Só age como comprador. Pode corrigir e reenviar | "Vender (indisponível)" |
| **Suspenso** | O administrador suspendeu por denúncia (ADM-02, RN-10) | Só age como comprador. As conversas abertas ficam somente leitura para ele | "Vender (indisponível)" |

### 2.3 Vendedor aprovado: situação do catálogo **(nova, RN-13)**

| Situação | Condição | Como muda |
|---|---|---|
| **Com catálogo** | Tem **pelo menos um item ativo** (publicado, com quantidade maior que zero, CAT-04) | Vira "sem catálogo" quando o último item ativo é removido, inativado ou zera o estoque (inclusive por uma venda, FEC-02) |
| **Sem catálogo** | **Nenhum item ativo** (nunca cadastrou, removeu tudo ou esgotou tudo) | Vira "com catálogo" quando cadastra ou reativa o primeiro item |

- A situação vale **para os pedidos novos**. Conversas e propostas que já existem não mudam quando a situação muda.
- Um item sem preço ("sob consulta", CAT-03) **conta** como ativo. O que conta é estar publicado e ter quantidade.
- O vendedor **nunca é obrigado** a ter catálogo, e ser "sem catálogo" **nunca o exclui** dos pedidos que o perfil dele cobre (RN-15).

## 3. O comprador

### 3.1 Jornada

```
Entra com Google → /comprador (pesquisa + filtros)
   │
   ├─ vê resultados do catálogo na hora (PED-07) ──────────────┐
   │                                                           │
   └─ "Pedir aos vendedores" (veículo + peça + cidade/UF)      │
         │ cria o pedido (Aberto); o matching roda em fila     │
         ▼                                                     ▼
   Lista de conversas do pedido: Catálogo (já com preço) e Cotação (resposta do vendedor)
         │ ordena por menor preço, mais recente ou não lidas (LST-02)
         ▼
   Conversa: texto, fotos, proposta → aceita, recusa ou contrapropõe (CHT-05)
         │ valor combinado = proposta Aceita
         ▼
   Combina pagamento e entrega FORA do sistema
         │ o vendedor marca a proposta como Vendida (FEC-02)
         ▼
   Pedido Concluído (resultado Vendido). Ou o comprador marca "Não comprei" (FEC-01)
```

### 3.2 O que o comprador faz e vê

| Área | Regra |
|---|---|
| **Pedido** | Um pedido é **uma peça para um veículo** (RN-01). Informa tipo, marca, modelo, versão, ano, descrição da peça, condição aceita, cidade/UF e alcance (cidade, estado ou Brasil), com até 5 fotos (PED-01). Validade de 7 dias, renovável (PED-04). Edita antes da primeira resposta, cancela, duplica e encerra (PED-05) |
| **Limites** | No máximo 10 pedidos abertos e 20 por dia (RN-08, configuráveis) |
| **Dashboard** `/comprador` | Pesquisa e filtros, resultados imediatos do catálogo, botão "Pedir aos vendedores" e a aba "Meus pedidos" com status e contadores (PED-06) |
| **Conversas** | Uma por vendedor e por pedido (RN-02). Cada linha mostra o vendedor, a origem (**Catálogo** ou **Cotação**), a melhor ou última proposta, a miniatura, as não lidas e a última atividade (LST-01) |
| **Negociação** | Aceita, recusa ou contrapropõe. A proposta vira Aceita quando as duas partes concordam no valor (CHT-05). O histórico de versões é imutável (CHT-06) |
| **Contato** | O comprador decide compartilhar o WhatsApp, conversa por conversa (CHT-08) |
| **Segurança** | Alertas anti-golpe fixos no chat (FEC-03). Pode bloquear e denunciar (CHT-09) |
| **Fechamento** | "Concluído" indicando o vendedor (opcional) ou "Não comprei", com motivo (FEC-01). Um pedido Expirado, Cancelado ou Concluído sem compra pode ser **reaberto em até 30 dias**. Um pedido com proposta **Vendida não reabre** (CHT-11, RN-11) |
| **Notificações** | Sino com as abas Todas e Chat (NOT-01): respostas, propostas novas, mensagens, pedido expirando |

### 3.3 Situações especiais do comprador

| Situação | O que acontece |
|---|---|
| Nenhum vendedor atende ainda | O pedido fica Aberto, com a mensagem "Nenhum vendedor respondeu ainda". O comprador pode ampliar o alcance (cidade → estado → Brasil) e duplicar o pedido. Em F2, o pedido é reavaliado quando um item compatível é cadastrado (DIS-07) |
| Muitas respostas | O ranking limita a **30 vendedores por pedido** (DIS-04). O comprador ordena e filtra |
| Vendedor não responde | Nada acontece com o pedido. O comprador espera ou renova/duplica |
| O pedido expira | As conversas ficam somente leitura. Ele pode renovar (PED-04) ou reabrir em até 30 dias |
| O comprador também é vendedor | Nunca recebe cotação nem match de catálogo de um **pedido que ele mesmo criou** (RN-17, nova) |

## 4. O vendedor (comum aos dois tipos)

### 4.1 Entrada

```
Entra com Google (comprador) → "Quero vender" → cadastro do vendedor (AUT-04):
  nome ou razão social, CPF/CNPJ, tipo (loja, desmanche, autônomo), cidade/UF, WhatsApp, logo
→ perfil de atuação (DIS-01): tipos de veículo, marcas, modelos e anos (opcional), categorias de peças, alcance geográfico
→ situação Pendente → o administrador aprova (ADM-01) → Aprovado
```

### 4.2 O que o vendedor aprovado recebe e faz

| Área | Regra |
|---|---|
| **Cotações** | Os pedidos que o **perfil de atuação** cobre (veículo, categoria e região) chegam em "Cotações recebidas" (DIS-03). É o campo **"Peças à procura"** da navbar, com contador, e a tela `/vendedor/pecas-a-procura` |
| **Resposta** | Por chat, com **texto, fotos** (até 6 por mensagem, em até 3 toques a partir da conversa, RNF-USA-03) e **proposta estruturada**: valor, quantidade, condição, garantia, prazo, validade e observação (CHT-03, CHT-04) |
| **Ignorar** | O vendedor pode ignorar um pedido, e ele some da caixa dele (DIS-06) |
| **Caixas** | "Cotações recebidas" (sem conversa iniciada) e "Conversas", com filtro por status (LST-04). Pode arquivar (LST-05) |
| **Negociação** | Atualiza ou retira a proposta (o histórico fica). Aceita, recusa ou atualiza a contraproposta do comprador (CHT-05, CHT-06) |
| **Fechamento** | Depois de fechar o negócio por meio externo, marca a proposta **Aceita** como **Vendida** (FEC-02). O sistema **não pede** forma de pagamento, entrega nem valor pago (FEC-06). O pedido passa a Concluído e as outras propostas do pedido ficam Expiradas (RN-12) |
| **Privacidade** | Vê só o primeiro nome e a cidade do comprador (RN-03) |
| **Limite de alcance** | Um pedido chega a no máximo 30 vendedores (DIS-04). A ordem é: relevância (catálogo, depois modelo e ano, depois marca), distância e atividade |
| **Notificações** | Tipos `PECA_A_PROCURA` (campo próprio da navbar), `CHAT`, `PROPOSTA` e `SISTEMA` |

## 5. O vendedor **com** catálogo

É o vendedor aprovado com pelo menos um item ativo (RN-13).

**O que ele ganha:**

1. **Match de catálogo (DIS-02).** Quando um pedido casa com um item dele (veículo compatível e peça por texto, categoria ou código), o sistema **abre a conversa sozinho**, com o **cartão do item** (foto, preço e compatibilidade, CHT-07). O comprador vê isso **na hora**, sem esperar resposta (PED-07). O vendedor é avisado pela notificação de `CHAT` ("Nova conversa pelo seu catálogo") e a conversa aparece em "Conversas", com o selo **Catálogo**.
2. **Aparece na pesquisa do comprador.** Os itens ativos entram nos resultados de `/comprador`, com vendedor, cidade, condição, preço (ou "sob consulta") e garantia.
3. **Prioridade no ranking** dos 30 (catálogo vem antes de modelo/ano e de marca, DIS-04).
4. **Continua recebendo cotações** dos pedidos que o **perfil** cobre, mesmo que nenhum item dele case (por exemplo, uma peça que ele pode conseguir no pátio ou com um parceiro).

**O que ele precisa fazer:**

| Tarefa | Regra |
|---|---|
| Manter o catálogo | Criar, editar e remover itens, com título, categoria, código OEM ou do fabricante, condição, preço, quantidade, até 8 fotos, garantia, observações e ativo/inativo (CAT-01). Compatibilidade por veículo e faixa de anos (CAT-02). Sem preço mostra "sob consulta" (CAT-03). Quantidade zerada inativa o item (CAT-04) |
| Usar o dashboard `/vendedor` | Lista do catálogo, botão "Adicionar peça" e **filtro lateral de estoque** (Todas, Em estoque, Sem estoque), condição e categoria |
| **Enviar proposta** | O **preço do cartão é indicativo**. O compromisso do vendedor é a **proposta**, dentro da validade (RN-05) |
| Acompanhar o estoque | Marcar Vendida **baixa** a quantidade do item de origem (FEC-02). Se chegar a zero e era o último item ativo, o vendedor passa a "sem catálogo" (RN-13) |

**Casos especiais:**

| Situação | Regra |
|---|---|
| O mesmo pedido casa com o catálogo **e** com o perfil do vendedor | Existe **uma só conversa** (RN-02), com origem **Catálogo**. A cotação **não** é duplicada (RN-14, nova) |
| Um item com 1 unidade casa com dois pedidos | As duas conversas abrem. Quem for marcada Vendida primeiro baixa o estoque, e a quantidade nunca fica negativa. Marcar Vendida é uma declaração do vendedor e **não é bloqueada** pelo estoque |
| O preço do cartão e o da proposta diferem | Vale a proposta (RN-05) |
| O item é removido ou inativado com conversas abertas | As conversas continuam, com o cartão preservado como foi enviado |

## 6. O vendedor **sem** catálogo

É o vendedor aprovado que não tem nenhum item ativo (RN-13). É o caso típico do **desmanche com veículos no pátio**, do autônomo e do vendedor que ainda não cadastrou nada.

**O que ele recebe:**

1. **Só o match de perfil (DIS-03).** Toda vez que um pedido casa com o **perfil de atuação** (veículo, categoria, região), ele recebe a **cotação** em "Peças à procura". **Não é excluído por não ter catálogo** (RN-15).
2. **Nenhuma conversa automática.** A conversa só existe quando **ele responde** à cotação (CHT-01).

**O que ele faz:**

| Passo | Como |
|---|---|
| 1. Vê a cotação | Em `/vendedor/pecas-a-procura`: veículo, peça, cidade e há quanto tempo chegou |
| 2. Decide | Ignora (DIS-06) ou responde |
| 3. Responde | **Manda a foto da peça no carro pelo próprio chat** (até 6 por mensagem, câmera ou galeria) e uma **proposta** com valor, condição, garantia, prazo e validade |
| 4. Negocia e fecha | Igual ao vendedor com catálogo: contraproposta, Aceita, e depois Vendida (FEC-02). Não há estoque a baixar |

**Diferenças que ele sente:**

| Aspecto | Consequência |
|---|---|
| Visibilidade | **Não aparece** nos resultados de pesquisa do comprador (não há item). Só é alcançado pelas cotações |
| Velocidade | O comprador vê o **catálogo primeiro** (já com preço). Quem é "sem catálogo" precisa **responder rápido** para competir |
| Ranking | Fica depois dos que casam por catálogo (DIS-04), e compete pelas vagas restantes dos 30 por especificidade do perfil, distância e atividade |
| Dashboard `/vendedor` | Mostra o **estado vazio** "Cadastre sua primeira peça". Cadastrar o primeiro item o torna "com catálogo" na hora |
| Pátio (F2) | Poderá registrar veículos em desmontagem, e os pedidos que casam são marcados como "possível peça no pátio" (DIS-09) |

## 7. Comparação lado a lado

| Aspecto | Comprador | Vendedor **com** catálogo | Vendedor **sem** catálogo |
|---|---|---|---|
| Como entra | Login com Google | Login com Google + perfil de vendedor aprovado | Login com Google + perfil de vendedor aprovado |
| O que o move | Precisa de uma peça | Tem a peça pronta | Pode conseguir a peça (pátio, parceiro) |
| Como um pedido o encontra | Ele cria o pedido | **Match de catálogo** (conversa automática) e match de perfil | **Match de perfil** (cotação) |
| Quem inicia a conversa | O sistema (catálogo) ou o vendedor (cotação) | **O sistema**, com o cartão do item | **O vendedor**, ao responder |
| Preço na primeira aparição | — | Indicativo, no cartão do item | Nenhum, até ele mandar a proposta |
| Foto | Até 5 no pedido | Até 8 por item e até 6 por mensagem | Até 6 por mensagem (a foto do pátio) |
| Aparece na pesquisa do comprador | — | **Sim** | **Não** |
| Painel principal | `/comprador` | `/vendedor` (catálogo e filtro de estoque) | `/vendedor` (vazio) e `/vendedor/pecas-a-procura` |
| Campo "Peças à procura" | Não tem | Tem (cotações do perfil) | Tem (é a entrada principal) |
| Ranking (DIS-04) | — | Alto (catálogo) | Menor (perfil) |
| Estoque | — | Baixa ao marcar Vendida | Não se aplica |
| Como vira o outro | Cria o perfil de vendedor | Perde o último item ativo → sem catálogo | Cadastra o primeiro item → com catálogo |

## 8. Matriz de permissões

| Ação | Visitante | Comprador | Vendedor pendente, reprovado ou suspenso | Vendedor aprovado (com ou sem catálogo) | Administrador |
|---|---|---|---|---|---|
| Entrar com Google | sim | — | — | — | sim |
| Criar, renovar e fechar **seus** pedidos | não | sim | sim (como comprador) | sim (como comprador) | não |
| Pesquisar o catálogo | não | sim | sim | sim | sim |
| Criar perfil de vendedor | não | sim | reenviar, se reprovado | — | não |
| Receber cotações e match de catálogo | não | não | **não** (RN-04) | **sim** | não |
| Publicar e editar catálogo | não | não | **não** | sim (com catálogo) / cria o primeiro item (sem catálogo) | não |
| Responder cotação e enviar proposta | não | não | não | **sim** | não |
| Marcar proposta Vendida | não | não | não | sim (só a **Aceita**, RN-11) | corrige com auditoria |
| Ver conversa de outro par | não | **não** (RNF-SEG-02) | **não** | **não** | só para moderar, com auditoria |
| Aprovar vendedor, tratar denúncia, gerir taxonomias | não | não | não | não | **sim** |

## 9. Visibilidade entre as partes

| Quem vê | O que vê da outra parte |
|---|---|
| Vendedor → comprador | Primeiro nome e cidade. **Contato só se o comprador compartilhar** (CHT-08) |
| Comprador → vendedor | Nome ou razão social, tipo (loja, desmanche, autônomo), cidade, logo, e (F2) reputação. O WhatsApp só aparece se o vendedor o usar no chat |
| Vendedor → outros vendedores | **Nada.** Cada um só vê as próprias cotações e conversas |
| Administrador | Tudo o que for preciso para moderar, **com registro em auditoria** (RNF-SEG-07) |

## 10. Regras novas e pontos a confirmar

| ID | Regra (nova) | A confirmar |
|---|---|---|
| RN-13 | "Com catálogo" = vendedor **aprovado** com **pelo menos um item ativo**. "Sem catálogo" = aprovado sem item ativo. A situação é calculada, não escolhida | O conceito de "item ativo" (publicado e com quantidade maior que zero) está certo? |
| RN-14 | Um pedido que casa com o catálogo **e** com o perfil do mesmo vendedor gera **uma** conversa, com origem **Catálogo** | — |
| RN-15 | Não ter catálogo **nunca** exclui o vendedor do match de perfil | — |
| RN-16 | Uma conversa criada pelo match de catálogo **avisa o vendedor** (notificação `CHAT`) e aparece em "Conversas", não em "Cotações recebidas" | O aviso deveria ser `PECA_A_PROCURA`, como as cotações? |
| RN-17 | Ninguém recebe cotação nem match de catálogo de um pedido **que criou** | — |

Pontos em aberto herdados da spec: se a mesma conta é mesmo comprador e vendedor (premissa a), a região e o nicho do piloto, e o prazo padrão do pedido (7 dias).

## 11. Mapeamento para os requisitos

| Tema | Requisitos |
|---|---|
| Entrada e papéis | AUT-01, AUT-03, AUT-04, AUT-05, ADM-01, RN-04 |
| Pedido e dashboard do comprador | PED-01 a PED-07, VEI-01, LST-01, LST-02 |
| Match de catálogo e de perfil | DIS-01 a DIS-06, PED-07 |
| Catálogo | CAT-01 a CAT-04, CHT-07 |
| Chat, proposta e fechamento | CHT-01 a CHT-11, LST-04, LST-05, FEC-01 a FEC-03, FEC-06, RN-05, RN-06, RN-11, RN-12 |
| Privacidade e segurança | RN-03, CHT-08, CHT-09, RNF-SEG-02 |
| Telas | [Design das telas](superpowers/specs/2026-09-29-frontend-telas-design.md) |

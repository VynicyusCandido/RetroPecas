---
title: Marketplace reverso de autopeças
status: draft
created: 2026-10-25
updated: 2026-10-25
---

# PRD: Marketplace reverso de autopeças
*Nome provisório. Confirmar.*

## 0. Propósito do documento

Este PRD é para o dono do produto (TCC) e para quem vai fazer UX, arquitetura e histórias. Ele **reconcilia** o que foi decidido em 2026-10-25 (três stakeholders: Cliente, Loja e Autônomo) com a documentação já existente em `docs/` (especificação do projeto, stakeholders, login com Google, telas e backend). **Onde houver divergência, este PRD vale**, e a seção 10 lista o que precisa ser ajustado nos outros documentos. Vocabulário ancorado no Glossário (seção 3). Suposições ficam marcadas `[ASSUMPTION]` e indexadas na seção 9. Peso: TCC, entre interno e lançamento.

## 1. Visão

Quem precisa de uma peça de carro, moto ou caminhão hoje procura loja por loja, liga e compara na mão, e a peça rara ou o carro velho num quintal nunca aparece. O produto junta dois mundos num só app. De um lado, **lojas** anunciam peças como num OLX ou Mercado Livre, e o **cliente** acha pela pesquisa, com foto, valor, especificações e local. Do outro, a "grande sacada": o **autônomo** cadastra o **veículo inteiro** que quer desmontar ou vender, e quando um **pedido** de peça chega, ele é avisado com a faixa de valor que o cliente aceita pagar, decide se atende, e abre um chat com fotos.

Para o cliente, a peça comum está sempre numa loja, e a peça rara ainda pode aparecer de alguém que nunca cadastraria um catálogo. Para o vendedor, o pedido chega pronto, com veículo, peça, região e quanto o cliente quer pagar. Confiança vem de **nota, comentário e resposta do vendedor**. O negócio fecha **fora da plataforma**.

## 2. Usuário-alvo

### 2.1 Jobs To Be Done
- **Cliente:** "Quando preciso de uma peça, quero achar uma loja que já tenha, ou ser procurado por quem tenha, sem ligar para ninguém, e saber em quem confiar."
- **Loja:** "Quero que quem busca uma peça que eu tenho me encontre, e quero ser avisada de pedidos que consigo atender."
- **Autônomo:** "Tenho um carro velho ou abandonado e quero me desfazer dele peça por peça, sem cadastrar cada peça. Quero só ser avisado quando alguém procurar algo que ele possa ter."

### 2.2 Não-usuários (v1)
Quem quer pagar pelo app, frete ou entrega (o negócio fecha fora). Quem não tem conta Google.

### 2.3 Jornadas

- **UJ-1. Carlos acha o farol na pesquisa de uma loja.**
  - **Persona + contexto:** Carlos, motorista de aplicativo, bateu o farol esquerdo do Gol 2014 e precisa consertar hoje.
  - **Entrada:** logado com Google, no celular, na tela de pesquisa.
  - **Caminho:** filtra Volkswagen, Gol, 2014, digita "farol esquerdo". Vê anúncios de lojas com foto, valor, especificações e bairro. Abre um e toca em "Conversar".
  - **Clímax:** o chat abre com a loja, já com o anúncio no topo.
  - **Resolução:** combina valor e retirada fora do app. Depois avalia a loja.
  - **Caso de borda:** nenhum anúncio casa. O app oferece "Fazer um pedido" (UJ-2).

- **UJ-2. Carlos faz um pedido para a peça rara.**
  - **Persona + contexto:** Carlos precisa do retrovisor elétrico de um Gol G3, que nenhuma loja tem.
  - **Entrada:** logado, sem resultado na pesquisa.
  - **Caminho:** cria o pedido com veículo, peça, **faixa de valor** (R$ 120 a R$ 200) e localização (estado, cidade, bairro).
  - **Clímax:** alguns minutos depois, um autônomo aceita, e o chat abre já mostrando as fotos do estado geral do carro dele.
  - **Resolução:** o autônomo manda fotos da peça, e eles combinam fora do app.
  - **Caso de borda:** ninguém aceita. O pedido expira e o cliente pode renová-lo.

- **UJ-3. Seu Raimundo atende um pedido com o carro velho do quintal.**
  - **Persona + contexto:** Raimundo, autônomo, tem um Gol G3 abandonado e quer se desfazer dele.
  - **Entrada:** autônomo aprovado, com o veículo cadastrado com fotos.
  - **Caminho:** recebe a notificação "procuram retrovisor elétrico de Gol G3, R$ 120 a R$ 200, no seu estado". Abre o pedido, olha o carro e vê que tem a peça. Toca em "Aceitar".
  - **Clímax:** o chat abre com o cliente, que já vê as fotos do carro.
  - **Resolução:** manda as fotos da peça. Fecha a venda e o cliente o avalia.
  - **Caso de borda:** não tem a peça. Ignora o pedido, que some da caixa dele.

- **UJ-4. A Auto Peças Silva cadastra e é avisada.**
  - **Persona + contexto:** a loja Silva vende peças de Gol e Uno.
  - **Caminho:** cadastra um farol com foto, valor, especificações, veículos compatíveis e local. Depois recebe o aviso de um pedido compatível e abre o chat com o cliente.
  - **Clímax:** um cliente abre o chat a partir do anúncio.
  - **Resolução:** vende e responde a um comentário.

- **UJ-5. Carlos navega só pelo veículo.**
  - **Caminho:** escolhe marca, modelo e ano **sem dizer a peça** e vê tudo o que há para aquele veículo (anúncios de lojas). Depois refina com a peça.

## 3. Glossário

- **Cliente** — usuário que procura peças. Todo usuário é cliente.
- **Vendedor** — usuário com perfil de vendedor aprovado. Pode ser **Loja** ou **Autônomo**.
- **Loja** — vendedor que **cadastra peças** (**Anúncio**), cada uma ligada aos veículos compatíveis. Aparece na pesquisa. É avisada de pedidos.
- **Autônomo** — vendedor que **cadastra veículos inteiros** (**Veículo de Pátio**), não peças. É avisado de pedidos compatíveis e decide se atende.
- **Anúncio** — peça de uma Loja, com foto, valor, especificações (modelo), veículos compatíveis e local.
- **Veículo de Pátio** — veículo inteiro cadastrado por um Autônomo, com fotos do estado geral.
- **Pedido** — o cliente pede **uma peça** para **um veículo**, com **Faixa de valor** e **Localização**.
- **Faixa de valor** — mínimo e máximo que o cliente aceita pagar, visível ao vendedor avisado.
- **Localização** — estado, cidade e bairro.
- **Aviso** — notificação ao vendedor de um Pedido compatível.
- **Aceite** — o Autônomo, ao ver o Pedido, decide atender. Abre a Conversa.
- **Conversa** — chat entre cliente e vendedor. Abre por **Contato no Anúncio** (cliente, com Loja) ou por **Aceite** (Autônomo).
- **Avaliação** — nota e comentário do cliente sobre um vendedor. O vendedor pode responder.
- **Fechamento** — o negócio acontece **fora da plataforma**. O sistema não processa pagamento.

## 4. Funcionalidades

### 4.1 Entrada e perfis
**Descrição:** login só pelo Google (e-mail verificado). Todo usuário é cliente. Quem quer vender cria perfil de **Loja** ou de **Autônomo**, e só opera depois de **aprovado por um administrador**. Regras de segurança em `docs/login-com-google-regras-de-seguranca.md`.

#### FR-1: Entrar com Google
Visitante entra com Google. O primeiro login cria a conta de cliente. Realiza UJ-1.
**Consequências (testáveis):**
- E-mail não verificado é recusado.
- Não existe senha própria.

#### FR-2: Criar perfil de vendedor (Loja ou Autônomo)
Cliente cria perfil de Loja ou de Autônomo, com nome, documento, cidade/UF, WhatsApp. Fica pendente até aprovação. Realiza UJ-3, UJ-4.
**Consequências:**
- Vendedor pendente não recebe Aviso nem publica.
- Um usuário pode ter os dois perfis? `[ASSUMPTION: um usuário tem no máximo um perfil de vendedor]`.

### 4.2 Pesquisa do cliente
**Descrição:** o cliente pesquisa por **filtros** ou **campo livre**. Pode buscar **só pelo veículo** (marca, modelo e ano) sem dizer a peça. Os resultados são **Anúncios de Lojas** com foto, valor, especificações e Localização. Realiza UJ-1, UJ-5.

#### FR-3: Pesquisar anúncios por campo livre e filtros
Cliente digita a peça e filtra por veículo (tipo, marca, modelo, versão, ano), condição, valor e localização.
**Consequências:**
- Cada resultado mostra foto, valor, especificações e estado/cidade/bairro.
- Sem filtro de veículo, a pesquisa por texto funciona.

#### FR-4: Navegar só pelo veículo
Cliente escolhe marca, modelo e ano **sem informar a peça** e vê todos os Anúncios compatíveis. Realiza UJ-5.

### 4.3 Anúncios da Loja
#### FR-5: Cadastrar e gerir anúncio
Loja cria, edita e remove Anúncios: título, categoria, código, condição, valor (ou "sob consulta"), quantidade, fotos, especificações, veículos compatíveis e Localização. Quantidade zerada oculta o anúncio. Realiza UJ-4.

#### FR-6: Cliente abre Conversa pelo anúncio
Cliente toca em "Conversar" num Anúncio e abre a **Conversa** com a Loja. Realiza UJ-1.
**Consequências:**
- Só o cliente abre o chat com a Loja a partir do anúncio.
- O vendedor vê só o primeiro nome e a cidade do cliente. `[ASSUMPTION: regra RN-03 da spec mantida]`

### 4.4 Pedido
**Descrição:** quando a pesquisa não acha, o cliente faz um **Pedido** com **Faixa de valor** obrigatória. O sistema avisa Lojas e Autônomos compatíveis. Realiza UJ-2.

#### FR-7: Criar pedido
Cliente cria Pedido com veículo, peça, **Faixa de valor** (obrigatória) e **Localização** (estado, cidade, bairro). Validade padrão 7 dias, renovável.
**Consequências:**
- Sem faixa de valor, o pedido não é criado.
- O cliente edita, cancela e renova.

#### FR-8: Avisar vendedores compatíveis
O sistema envia **Aviso** a **Lojas** (Anúncios compatíveis com o veículo e a peça) e a **Autônomos** (Veículos de Pátio compatíveis), mostrando veículo, peça, Faixa de valor e região. Limite de N vendedores por pedido `[ASSUMPTION: 30, como na spec]`. Realiza UJ-3, UJ-4.
**Consequências:**
- O Aviso mostra a Faixa de valor.
- Ninguém recebe Aviso do próprio pedido.
- Vendedor pendente, reprovado ou suspenso não recebe.

### 4.5 Veículo de Pátio e Aceite do Autônomo
**Descrição:** o Autônomo não cadastra peças. Cadastra o veículo inteiro, com fotos da avaliação geral, para quem quer se desfazer de um carro velho ou abandonado. Realiza UJ-3.

#### FR-9: Cadastrar Veículo de Pátio
Autônomo cadastra veículo (tipo, marca, modelo, versão, ano, estado geral, Localização) com fotos.
**Consequências:**
- Sem cadastro de peças individuais.
- Mínimo de fotos `[ASSUMPTION: pelo menos 3, para a pré-avaliação do estado]`.

#### FR-10: Aceitar ou ignorar um Pedido
Autônomo vê o Pedido (com Faixa de valor), **verifica se o veículo tem a peça** e escolhe **Aceitar** ou **Ignorar**.
**Consequências:**
- Ao aceitar, a **Conversa** abre.
- O cliente vê, já na abertura, as fotos do **estado do veículo**.
- Ao ignorar, o pedido some da caixa dele.

### 4.6 Conversa
#### FR-11: Chat com texto e fotos
Cliente e vendedor trocam texto e fotos. O Autônomo envia depois as fotos da peça. Mensagens com estado enviado e lido.
**Consequências:**
- Fotos sem metadados EXIF.
- Alertas anti-golpe fixos no chat.
- Bloquear e denunciar.

#### FR-12: Fechar fora da plataforma
O sistema não pede nem registra pagamento ou entrega. `[ASSUMPTION: o vendedor pode marcar "vendido" para baixar estoque e liberar avaliação, como na spec]`

### 4.7 Confiança: nota, comentário e resposta
**Descrição:** o cliente **avalia** vendedores com nota e comentário. O vendedor **pode responder**. Entra no MVP. Realiza UJ-2, UJ-4.

#### FR-13: Avaliar vendedor
Cliente que **teve contato** (Conversa) com o vendedor avalia com nota e comentário. `[ASSUMPTION: basta ter havido Conversa, não é preciso concluir a compra]`
**Consequências:**
- Uma avaliação por cliente e vendedor.
- Risco de avaliação falsa: ver seção 8.

#### FR-14: Responder a avaliação
Vendedor responde a comentários sobre ele, uma resposta por avaliação.

#### FR-15: Mostrar reputação
Nota média, número de avaliações e comentários aparecem no perfil e nos resultados.

### 4.8 Notificações e moderação
#### FR-16: Notificar
Notificações no app (Aviso de pedido, mensagem, resposta) com contador.

#### FR-17: Aprovar vendedores e moderar
Administrador aprova ou reprova Lojas e Autônomos e trata denúncias, avaliações e anúncios.

**NFRs transversais:** mobile-first, pt-BR, WCAG 2.1 AA nos fluxos principais; logs sem dados pessoais (LGPD); autorização por objeto (um vendedor só vê as próprias Conversas); limite de taxa; sessão com JWT em cookie e `X-Auth-Id` (ver docs).

## 5. Não-objetivos
- Pagamento, frete, nota fiscal, custódia ou disputa: o negócio é fora da plataforma.
- Preço de referência de peças.
- Cadastro de peça por peça para o Autônomo.
- Login por senha.
- App nativo e IA de reconhecimento de peça.

## 6. Escopo do MVP
### 6.1 Dentro
Login Google; perfis Loja e Autônomo com aprovação; pesquisa por texto, filtros e só por veículo; anúncios da Loja; pedido com faixa de valor; Aviso; veículo de pátio e aceite; chat com fotos; avaliação com resposta; notificações; moderação básica.
### 6.2 Fora do MVP
Comparação lado a lado, web push, importação em lote, vitrine pública, métricas de admin, outro provedor de login.

## 7. Métricas de sucesso
**Primárias**
- **SM-1**: % de Pedidos com pelo menos um Aceite ou Conversa em 24 h (meta a definir no piloto). Valida FR-7, FR-8, FR-10.
- **SM-2**: % de pesquisas que terminam em Conversa com uma Loja. Valida FR-3, FR-6.
**Secundárias**
- **SM-3**: Autônomos com pelo menos um veículo cadastrado e um Aceite no mês. Valida FR-9, FR-10.
- **SM-4**: % de Conversas seguidas de avaliação. Valida FR-13.
**Contra-métricas (não otimizar)**
- **SM-C1**: número de Avisos por vendedor (excesso vira spam). Contrabalança SM-1.
- **SM-C2**: número de avaliações sem Conversa real. Contrabalança SM-4.

## 8. Riscos
- **Avaliação falsa ou vingativa:** como basta ter tido contato, um vendedor pode combinar notas, ou um concorrente derrubar a nota. Mitigação: uma avaliação por par, denúncia, moderação, e (a decidir) peso maior para quem fechou. Ver Pergunta 1.
- **Autônomo de má-fé ou fotos de outro carro:** aprovação do administrador, denúncia e fotos com EXIF removido.
- **Partida a frio:** sem vendedores, o cliente não volta. Piloto numa região.

## 9. Perguntas abertas
1. Avaliação: basta Conversa, ou concluir a compra dá mais peso?
2. Um usuário pode ser Loja e Autônomo ao mesmo tempo?
3. Quando vários Autônomos aceitam o mesmo pedido, o cliente abre várias Conversas? Há limite?
4. A Loja que recebe Aviso responde abrindo Conversa, ou só o cliente abre pelo Anúncio?
5. A Faixa de valor também filtra os Anúncios na pesquisa?

## 10. Suposições (índice)
- 4.1 FR-2: um usuário, no máximo um perfil de vendedor.
- 4.3 FR-6: privacidade do cliente (primeiro nome e cidade).
- 4.4 FR-8: limite de 30 vendedores por Pedido.
- 4.5 FR-9: mínimo de 3 fotos por Veículo de Pátio.
- 4.6 FR-12: "vendido" opcional, como na spec.
- 4.7 FR-13: basta ter havido Conversa para avaliar.
- Modo fast path e peso TCC, assumidos sem confirmação explícita.

## 11. Ajustes necessários na documentação existente
Substituir RN-13 a RN-17 e "com/sem catálogo" por **Loja versus Autônomo**; subir o **Pátio** (DIS-09) e a **reputação** (FEC-04 e FEC-05) para o MVP; acrescentar **faixa de valor** e **bairro** ao pedido (PED-01); acrescentar **busca só por veículo** (VEI-01/PED-03); tipo de perfil de vendedor em AUT-04.

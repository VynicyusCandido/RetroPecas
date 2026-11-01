# RetroPeças — Plano do produto (PRD)

> **"O retrô que mantém seu carro na pista."**
> Rascunho de 2026-10-26. Plataforma de conexão por demanda para autopeças usadas (carros). Projeto de TCC.
> Este arquivo é o ponto de partida: visão, stakeholders, requisitos, regras, riscos e critérios de aceite. Tecnologia, diagramas e telas vêm **depois**, a partir daqui. Marcações `[ASSUMPTION]` são suposições a confirmar.
>
> **Atualização:** o PRD (`docs/prd/prd.md`) e a spec (`docs/spec/SPEC.md`) são a fonte de verdade. Este plano é o ponto de partida histórico e hoje resume o PRD; onde houver conflito, vale o PRD.

## 1. Visão

Quem precisa de uma peça usada procura loja por loja, liga e compara na mão, e a peça rara, ou o carro velho esquecido num quintal, nunca aparece. O público central são **donos de carros antigos ou raros**, para quem achar peça é difícil.

A RetroPeças é uma **plataforma de conexão por demanda para autopeças usadas** (carros, para donos de carros antigos e raros) que liga quem **procura** a quem **tem**:

- **Anúncio:** o vendedor publica uma peça ou um veículo (inteiro ou parcial), com fotos. O comprador acha pela pesquisa.
- **Pedido (a grande sacada):** quando o comprador não acha, publica um pedido com o veículo, a peça e a **faixa de valor** que aceita pagar. Quem tem item compatível cadastrado é avisado, vê a faixa de valor e decide se atende abrindo uma conversa. O pedido também vale para veículos.

A plataforma **só liga usuários**: não processa pagamento nem entrega, e não tem vínculo com a peça, o veículo nem a origem deles (isso vai nos Termos de Uso, redigidos para afastar a responsabilidade da plataforma). O negócio fecha **fora da plataforma**. A confiança vem da **avaliação do perfil**: estrelas, X, comentário e resposta do dono do perfil.

## 2. Modelo de usuário

Não há tipos de conta. Todo usuário tem a **mesma conta** e escolhe o que está fazendo: **Comprar** ou **Vender**. Quem tem um catálogo grande e quem tem poucas peças raras usam a mesma conta.

| Item cadastrável | O que é |
|---|---|
| **Peça** | O que se cadastra para vender: marca, modelo, versão e faixa de anos do veículo (digitados, sem FIPE), a parte, fotos, valor digitado pelo vendedor, especificações e local. Se for o veículo inteiro, marca isso e diz se está parcial ou completo. Qualquer usuário |
| **Pedido** | Peça ou veículo que a pessoa procura, com faixa de valor e localização. Qualquer usuário |

- **Identidade:** login com Google. O **CPF** é informado no primeiro acesso, validado só pelo dígito verificador, e existe para haver um responsável legal por conta. Nenhum usuário o vê. O administrador só o vê em denúncia grave ou ordem judicial.
- **Administrador:** só moderador (denúncias, avaliações, conteúdo indevido, suspensão). Não há aprovação prévia nem selo para vendedores.
- **Conversas:** duas caixas, **Compras** (sempre disponível) e **Vendas** (só aparece para quem tem item cadastrado). O comprador pode abrir conversa com todos que aceitarem um pedido, sem limite.
- **Aviso:** um pedido avisa quem tem item compatível. Ninguém é avisado do próprio pedido.
- **Avaliação do perfil:** cada avaliação distribui 5 pontos entre **estrelas** e **X** (cada X tira uma estrela; estrelas + X = 5; nota = estrelas). Só avalia quem teve conversa com mais de 10 mensagens. O dono do perfil responde uma vez por avaliação. Quem não tem item cadastrado é avaliado só como comprador. Cada avaliação mostra o tipo de interação (compra ou venda).
- **Fechamento:** ao fechar uma conversa com mais de 10 mensagens, abre um modal com **Fechado** (leva à avaliação do perfil) e **Não fechado** (só registra). Sem lembrete de 24h. Quando o vendedor marca o item como vendido, ele pode avaliar o comprador, que recebe uma notificação para avaliá-lo. O item vendido sai dos anúncios, e o vendedor pode excluí-lo do catálogo depois.
- **Busca v1:** por zona do veículo, com lista de peças por zona. Imagem clicável fica para depois.

## 3. Jornadas

> Jornadas atualizadas ao PRD: todo mundo é o mesmo usuário no modo **Vender**. O chat é só texto e as imagens vêm do anúncio.

- **J1. Pesquisa e conversa.** Carlos (motorista de aplicativo) bateu o farol esquerdo do Gol 2014. Filtra marca, modelo e ano, escolhe a zona Frente e a parte "farol", vê anúncios com foto, valor, especificações e localização, e toca em "Conversar". O chat abre com o vendedor, com a foto e o nome do item no cabeçalho. Combina fora do app e depois avalia o perfil.
- **J2. Pedido da peça rara.** Carlos precisa do retrovisor elétrico de um Gol G3 e ninguém tem. Cria o pedido com veículo, peça e **faixa de valor** (R$ 120 a R$ 200). Um vendedor aceita e o chat abre.
- **J3. Vendedor atende.** Seu Raimundo tem um Gol G3 no quintal, cadastrado com fotos. Recebe o aviso (no app e por e-mail) com nome da peça, cidade e faixa de valor. Se tem a peça, toca em **Aceitar** e o chat abre. Se não tem, ignora e o pedido some da caixa dele.
- **J4. Quem tem catálogo.** A Silva cadastra um farol com foto, valor, especificações e local. Recebe o aviso de um pedido compatível e abre o chat. Um cliente também abre o chat a partir do anúncio. Ela vende, marca como vendido e responde a uma avaliação.
- **J5. Só pelo veículo.** Carlos escolhe marca, modelo e ano sem dizer a peça e vê tudo o que há para aquele carro. Depois refina com a zona e a parte.

## 4. Glossário

**Comprador** e **Vendedor** (modos de uso da mesma conta, não tipos de conta), **Peça** (item cadastrado; pode ser marcada como **veículo inteiro**, parcial ou completo), **Pedido** (peça ou veículo que se procura, com faixa de valor), **Faixa de valor** (mínimo e máximo aceitos, visível ao vendedor avisado), **Aviso** (notificação de pedido compatível, no app e por e-mail), **Aceite** (o vendedor decide atender e abre a conversa), **Conversa** (chat só de texto), **Avaliação** (estrelas e X somando 5, com resposta do dono do perfil).

## 5. Requisitos funcionais

> **Os requisitos funcionais vivem no PRD** (`docs/prd/prd.md`, seção 3, RF-01 a RF-38). Esta seção é só um resumo para não perder o contexto. Os RF antigos deste plano foram substituídos.

- **Conta e acesso (RF-01 a RF-05):** login com Google, CPF com dígito verificador, Termos de Uso versionados, excluir conta (LGPD), modos Comprar e Vender.
- **Catálogo do vendedor (RF-06 a RF-09):** cadastrar sempre uma peça (marca, modelo, versão, faixa de anos, parte, estado, até 5 fotos), marcar veículo inteiro (parcial ou completo), editar, excluir e marcar como vendido.
- **Busca (RF-10 a RF-13):** veículo digitado pelo usuário (sem FIPE), zona do veículo, filtros incluindo faixa de valor (sem texto livre), navegar só pelo veículo.
- **Pedido e aviso (RF-14 a RF-18):** faixa de valor obrigatória, validade de 7 dias. Compatível quando marca, modelo e parte batem e o ano do pedido está na faixa do item (versão não conta). Aceitar ou ignorar, expiração.
- **Conversas (RF-19 a RF-26):** chat só de texto, caixas Compras e Vendas, alertas contra golpe, bloquear e denunciar, cabeçalho com foto do item, perfil da conversa e perfil do usuário com catálogo. Sem pagamento nem entrega.
- **Fechamento e avaliação (RF-27 a RF-31):** modal com mais de 10 mensagens, vendedor avalia o comprador, estrelas e X somando 5, resposta do dono do perfil, nota pela média.
- **Notificações e moderação (RF-32 a RF-38):** painel de métricas do administrador, notificações com contador, CPF só em denúncia grave ou ordem judicial (com auditoria), denúncia com motivo e estados, perfil bloqueado se comprovada, contestação.

## 6. Regras de negócio

> As regras de negócio vivem no PRD (seção 5, RN-01 em diante). Esta seção não as repete.

## 7. Requisitos não funcionais

> **Os RNF vivem no PRD** (seção 4, RNF-01 a RNF-24). Resumo: prioridade a velocidade e facilidade de uso. A v1 é web pensada para desktop, com layout que se adapta a telas menores, sessão por JWT no cabeçalho, link no ar até a divulgação das notas, com tecnologias migráveis para mobile (substitui o "mobile-first" antigo). Escala de TCC (centenas de usuários, dezenas de conversas simultâneas) e **sem meta numérica de disponibilidade**. Metas de desempenho (pedido, sugestão de veículo, mensagem e aviso), CPF criptografado com auditoria, logs sem dado pessoal, LGPD, testes com cobertura 75% servidor e 25% interface, ambientes separados e CI/CD.

## 8. Fora de escopo (v1)

> Vive no PRD (seção 6). Em resumo: pagamento, frete, nota fiscal, custódia e disputa; FIPE; moto e caminhão; app nativo; web push; importação em lote; outro provedor de login; carrinho, checkout e estoque.

## 9. Riscos

> Vivem no PRD (seção 7), já ajustados: sem aprovação prévia de vendedor, partida a frio de uma plataforma de duas pontas, grafias digitadas sem FIPE, dependência do Google e risco legal de peças de desmanche.

## 10. Critérios de aceite do MVP

> Vivem no PRD (seção 8, 17 critérios). Esta seção não os repete.

## 11. Perguntas abertas

Resolvidas pelo PRD: avaliação exige conversa com mais de 10 mensagens, sem peso extra para quem fechou; conta única, sem tipos de conta; o comprador abre conversa com todos que aceitarem, sem limite; o PRD está sem perguntas abertas.

Ainda abertas: região e nicho do piloto, e nome definitivo do produto.

## 12. Próximos passos

1. Revisar este plano e responder às perguntas da seção 11.
2. Fechar o PRD com o BMad (revisão de RF, RNF e riscos).
3. UX das telas e diagramas.
4. Escolher a tecnologia e a arquitetura.
5. Épicos e histórias, e só então código.

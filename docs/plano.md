# RetroPeças — Plano do produto (PRD)

> **"O retrô que mantém seu carro na pista."**
> Rascunho de 2026-10-26. Marketplace reverso de autopeças usadas (carros). Projeto de TCC.
> Este arquivo é o ponto de partida: visão, stakeholders, requisitos, regras, riscos e critérios de aceite. Tecnologia, diagramas e telas vêm **depois**, a partir daqui. Marcações `[ASSUMPTION]` são suposições a confirmar.
>
> **Atualização de 2026-10-30:** o PRD em andamento (`docs/prd/prd.md`, com decisões em `.memlog.md`) **substitui este plano nos pontos abaixo**. As seções 1, 2, 4, 5 e 7 já refletem o PRD. As jornadas (3), as regras de negócio (6), o fora de escopo (8), os riscos (9) e os critérios de aceite (10) ainda são do plano original e serão limpos na revisão geral. Onde houver conflito, vale o PRD.

## 1. Visão

Quem precisa de uma peça usada procura loja por loja, liga e compara na mão, e a peça rara, ou o carro velho esquecido num quintal, nunca aparece. O público central são **donos de carros antigos ou raros**, para quem achar peça é difícil.

A RetroPeças é um **plataforma de conexão por demanda para autopeças usadas** (carros, para donos de carros antigos e raros) que liga quem **procura** a quem **tem**:

- **Anúncio:** o vendedor publica uma peça ou um veículo (inteiro ou parcial), com fotos. O comprador acha pela pesquisa.
- **Pedido (a grande sacada):** quando o comprador não acha, publica um pedido com o veículo, a peça e a **faixa de valor** que aceita pagar. Quem tem item compatível cadastrado é avisado, vê a faixa de valor e decide se atende abrindo uma conversa. O pedido também vale para veículos.

A plataforma **só liga usuários**: não processa pagamento nem entrega, e não tem vínculo com a peça, o veículo nem a origem deles (isso vai nos Termos de Uso, redigidos para afastar a responsabilidade da plataforma). O negócio fecha **fora da plataforma**. A confiança vem da **avaliação do perfil**: estrelas, X, comentário e resposta do dono do perfil.

## 2. Modelo de usuário

Não há tipos de conta como "Loja" ou "Autônomo". Todo usuário tem a **mesma conta** e escolhe o que está fazendo: **Comprar** ou **Vender**. Uma loja é alguém com um catálogo grande. Um colecionador é alguém com poucas peças raras.

| Item cadastrável | O que é |
|---|---|
| **Peça** | O que se cadastra para vender: marca, modelo, versão e faixa de anos do veículo (digitados, sem FIPE), a parte, fotos, valor digitado pelo vendedor, especificações e local. Se for o veículo inteiro, marca isso e diz se está parcial ou completo. Qualquer usuário |
| **Pedido** | Peça ou veículo que a pessoa procura, com faixa de valor e localização. Qualquer usuário |

- **Identidade:** login com Google. O **CPF** é informado no primeiro acesso, validado só pelo dígito verificador, e existe para haver um responsável legal por conta. Nenhum usuário o vê. O administrador só o vê em denúncia grave ou ordem judicial.
- **Administrador:** só moderador (denúncias, avaliações, conteúdo indevido, suspensão). Não há aprovação prévia nem selo de loja.
- **Conversas:** duas caixas, **Compras** (sempre disponível) e **Vendas** (só aparece para quem tem item cadastrado). O comprador pode abrir conversa com todos que aceitarem um pedido, sem limite.
- **Aviso:** um pedido avisa quem tem item compatível. Ninguém é avisado do próprio pedido.
- **Avaliação do perfil:** cada avaliação distribui 5 pontos entre **estrelas** e **X** (cada X tira uma estrela; estrelas + X = 5; nota = estrelas). Só avalia quem teve conversa com mais de 10 mensagens. O dono do perfil responde uma vez por avaliação. Quem não tem item cadastrado é avaliado só como comprador. Cada avaliação mostra o tipo de interação (compra ou venda).
- **Fechamento:** ao fechar uma conversa com mais de 10 mensagens, abre um modal com **Fechado** (leva à avaliação do perfil) e **Não fechado** (só registra). Sem lembrete de 24h. Quando o vendedor marca o item como vendido, ele pode avaliar o comprador, que recebe uma notificação para avaliá-lo. O item vendido sai dos anúncios, e o vendedor pode excluí-lo do catálogo depois.
- **Busca v1:** por zona do veículo, com lista de peças por zona. Imagem clicável fica para depois.

## 3. Jornadas

> As jornadas abaixo usam os termos antigos "loja" e "autônomo". No PRD, ambos são o mesmo usuário no modo **Vender**.

- **J1. Pesquisa e loja.** Carlos (motorista de aplicativo) bateu o farol esquerdo do Gol 2014. Filtra marca, modelo e ano, digita "farol esquerdo", vê anúncios com foto, valor, especificações e bairro, e toca em "Conversar". O chat abre com a loja, já com o anúncio no topo. Combina fora do app e depois avalia a loja.
- **J2. Pedido da peça rara.** Carlos precisa do retrovisor elétrico de um Gol G3 e nenhuma loja tem. Cria o pedido com veículo, peça, **faixa de valor** (R$ 120 a R$ 200) e localização (estado, cidade e bairro). Um autônomo aceita e o chat abre já mostrando as fotos do estado do carro dele. Depois chegam as fotos da peça.
- **J3. Autônomo atende.** Seu Raimundo tem um Gol G3 no quintal, cadastrado com fotos. Chega o aviso "procuram retrovisor elétrico de Gol G3, R$ 120 a R$ 200, no seu estado". Ele olha o carro, vê que tem a peça e toca em **Aceitar**. O chat abre com o cliente, ele manda as fotos da peça e fecha a venda. Se não tem a peça, ignora, e o pedido some da caixa dele.
- **J4. Loja cadastra e é avisada.** A loja Silva cadastra um farol com foto, valor, especificações, veículos compatíveis e local. Recebe o aviso de um pedido compatível e abre o chat. Um cliente também abre o chat a partir do anúncio. Ela vende e responde a um comentário.
- **J5. Só pelo veículo.** Carlos escolhe marca, modelo e ano sem dizer a peça e vê tudo o que há para aquele carro. Depois refina com a peça.

## 4. Glossário

**Cliente** (comprador), **Vendedor** (quem cadastrou item; não é um tipo de conta), **Loja** e **Autônomo** (termos do plano original, hoje só descrevem o uso), **Anúncio** (peça de uma Loja, com foto, valor, especificações, veículos compatíveis e local), **Veículo inteiro** (anúncio de peça marcado como veículo inteiro, parcial ou completo), **Pedido** (uma peça para um veículo, com faixa de valor e localização), **Faixa de valor** (mínimo e máximo que o cliente aceita pagar, visível ao vendedor avisado), **Localização** (estado, cidade e bairro), **Aviso** (notificação de um Pedido compatível), **Aceite** (o Autônomo decide atender e abre a Conversa), **Conversa** (chat entre cliente e vendedor), **Avaliação** (nota e comentário do cliente sobre o vendedor, que pode responder).

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

- **RN-01** Um pedido tem exatamente um veículo e uma peça.
- **RN-02** No máximo uma conversa por par (pedido ou anúncio, vendedor).
- **RN-03** O vendedor vê do cliente só o primeiro nome e a cidade. O contato só aparece se o cliente compartilhar.
- **RN-04** Vendedor não aprovado não recebe aviso e não publica.
- **RN-05** O valor do anúncio é indicativo. A proposta combinada na conversa é o compromisso.
- **RN-06** O negócio sempre fecha **fora da plataforma**. A plataforma não é parte da venda.
- **RN-07** Limites anti-spam por cliente: 10 pedidos abertos e 20 por dia (configuráveis).
- **RN-08** Pedido sem renovação expira em 7 dias. Conversas ficam somente leitura.
- **RN-09** Peça de origem ilícita é removida, e o vendedor pode ser suspenso.

## 7. Requisitos não funcionais

> **Os RNF vivem no PRD** (seção 4, RNF-01 a RNF-22). Resumo: prioridade a velocidade e facilidade de uso. A v1 é web pensada para desktop, com layout que se adapta a telas menores, sessão por JWT no cabeçalho, link no ar até a divulgação das notas, com tecnologias migráveis para mobile (substitui o "mobile-first" antigo). Escala de TCC (centenas de usuários, dezenas de conversas simultâneas) e **sem meta numérica de disponibilidade**. Metas de desempenho (pedido, sugestão de veículo, mensagem e aviso), CPF criptografado com auditoria, logs sem dado pessoal, LGPD, testes com cobertura 75% servidor e 25% interface, ambientes separados e CI/CD.

## 8. Fora de escopo (v1)

Pagamento, frete, nota fiscal, custódia e disputa. Preço de referência de peças e lista oficial de veículos (FIPE). Cadastro de peça por peça para o autônomo. Login por senha. App nativo e reconhecimento de peça por foto. Comparação lado a lado, web push, importação em lote, vitrine pública, painel de métricas e outro provedor de login ficam para depois do MVP.

## 9. Riscos

| # | Risco | Mitigação |
|---|---|---|
| R1 | Avaliação falsa ou vingativa, porque basta ter tido contato | Uma avaliação por par, denúncia e moderação, e decidir se quem fechou o negócio pesa mais |
| R2 | Autônomo de má-fé ou fotos de outro carro | Aprovação do administrador, denúncia, EXIF removido |
| R3 | Partida a frio de marketplace de duas pontas | Piloto numa região, vendedores por convite, dados de demonstração |
| R4 | Golpes, já que o pagamento é fora | Aprovação, denúncia, bloqueio, alertas no chat e reputação |
| R5 | Spam de avisos para os vendedores | Limite de 30 por pedido, ranking e ignorar |
| R6 | Marca, modelo, versão e ano são digitados (sem FIPE), então a mesma coisa vira grafias diferentes e a busca não encontra | Sugestão a partir de valores já usados e busca que ignora maiúsculas e acentos |
| R7 | Legal: peças de desmanche são reguladas (Lei 12.977/2014) | A plataforma só liga usuários e o vendedor responde pela origem. Termos de Uso redigidos para afastar a responsabilidade da plataforma. Sem validação jurídica prevista na v1 |
| R8 | Dependência do Google: fora do ar, ninguém entra | Plano B (outro provedor) depois do MVP. Sessão curta |
| R9 | Aplicativo do Google em modo de teste só aceita contas de teste | Publicar antes de abrir ao público. Confirmar o PKCE com o cliente "Aplicativo da Web" |
| R10 | Conteúdo indevido em fotos e textos | Denúncia e moderação manual |

## 10. Critérios de aceite do MVP

1. **Pesquisa e loja:** filtro por veículo e texto mostra anúncios com foto, valor, especificações e local, e o cliente abre o chat pelo anúncio.
2. **Só pelo veículo:** marca, modelo e ano sem peça listam os anúncios compatíveis.
3. **Pedido com faixa de valor:** sem faixa de valor, o pedido não é criado.
4. **Aviso:** loja e autônomo compatíveis recebem o aviso com a faixa de valor. Vendedor não aprovado não recebe.
5. **Aceite do autônomo:** ao aceitar, a conversa abre e o cliente vê as fotos do estado do veículo. Depois chegam as fotos da peça.
6. **Ignorar:** o pedido ignorado some da caixa do vendedor.
7. **Isolamento:** um vendedor recebe 403 ou 404 ao abrir conversa de outro par.
8. **Avaliação:** o cliente avalia após a conversa e o vendedor responde ao comentário.
9. **Login:** primeiro login cria a conta, e-mail não verificado é recusado, retorno sem `state` válido é recusado, conta suspensa não entra, logout invalida o token.
10. **Fechamento fora do app:** o sistema nunca pede pagamento nem entrega.
11. **Contestação:** o usuário bloqueado consegue contestar, e o administrador reanalisa a denúncia.

## 11. Perguntas abertas

Resolvidas pelo PRD: avaliação exige conversa com mais de 10 mensagens, sem peso extra para quem fechou; conta única, sem Loja ou Autônomo; o comprador abre conversa com todos que aceitarem, sem limite.

Ainda abertas:
1. Uma loja avisada de um pedido responde abrindo a conversa, ou só o comprador abre?
2. Região e nicho do piloto. Nome definitivo do produto.
3. Nenhuma outra por enquanto (o catálogo de carros está no `addendum.md` do PRD; moto e caminhão ficam fora da v1).

## 12. Próximos passos

1. Revisar este plano e responder às perguntas da seção 11.
2. Fechar o PRD com o BMad (revisão de RF, RNF e riscos).
3. UX das telas e diagramas.
4. Escolher a tecnologia e a arquitetura.
5. Épicos e histórias, e só então código.

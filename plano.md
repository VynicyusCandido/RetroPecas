# RetroPeças — Plano do produto (PRD)

> **"O retrô que mantém seu carro na pista."**
> Rascunho de 2026-10-26. Marketplace reverso de autopeças usadas (carros, motos e caminhões). Projeto de TCC.
> Este arquivo é o ponto de partida: visão, stakeholders, requisitos, regras, riscos e critérios de aceite. Tecnologia, diagramas e telas vêm **depois**, a partir daqui. Marcações `[ASSUMPTION]` são suposições a confirmar.

## 1. Visão

Quem precisa de uma peça usada hoje procura loja por loja, liga e compara na mão, e a peça rara, ou o carro velho esquecido num quintal, nunca aparece. A RetroPeças junta dois mundos:

- **Lojas** anunciam peças como no OLX ou Mercado Livre. O cliente acha pela pesquisa, com foto, valor, especificações e local.
- **Autônomos** cadastram o **veículo inteiro** que querem desmontar. Quando um **pedido** chega, o autônomo é avisado com a **faixa de valor** que o cliente aceita pagar, decide se atende e abre um chat com fotos. **Essa é a grande sacada.**

A loja é a opção previsível, sempre disponível. O autônomo é a peça rara, que pode nunca mais aparecer. A confiança vem de **nota, comentário e resposta do vendedor**. O negócio fecha **fora da plataforma**.

## 2. Stakeholders

| Stakeholder | O que faz |
|---|---|
| **Cliente** | Entra no app e procura a peça, por filtros ou direto no campo de busca. Pode navegar só pelo veículo (marca, modelo, ano) sem dizer a peça. Abre chat com a loja pelo anúncio. Se não acha, faz um pedido |
| **Loja** | Cadastra **peças** (anúncios) ligadas aos veículos compatíveis. Aparece na pesquisa com foto, valor, modelo e especificações, e local (estado, cidade e bairro). **Também é avisada de pedidos** compatíveis |
| **Autônomo** | Cadastra **veículos inteiros**, com fotos do estado geral. **Não** cadastra peça por peça (seria ruim para quem tem um carro velho abandonado e quer se desfazer). Recebe o aviso do pedido com a faixa de valor, verifica se o carro tem a peça e **aceita ou ignora** |
| Administrador (apoio) | Aprova lojas e autônomos, trata denúncias e avaliações |

Todo usuário é cliente. Loja e autônomo são **perfis de vendedor distintos** e só operam depois de aprovados pelo administrador. `[ASSUMPTION: um usuário tem no máximo um perfil de vendedor]`

## 3. Jornadas

- **J1. Pesquisa e loja.** Carlos (motorista de aplicativo) bateu o farol esquerdo do Gol 2014. Filtra marca, modelo e ano, digita "farol esquerdo", vê anúncios com foto, valor, especificações e bairro, e toca em "Conversar". O chat abre com a loja, já com o anúncio no topo. Combina fora do app e depois avalia a loja.
- **J2. Pedido da peça rara.** Carlos precisa do retrovisor elétrico de um Gol G3 e nenhuma loja tem. Cria o pedido com veículo, peça, **faixa de valor** (R$ 120 a R$ 200) e localização (estado, cidade e bairro). Um autônomo aceita e o chat abre já mostrando as fotos do estado do carro dele. Depois chegam as fotos da peça.
- **J3. Autônomo atende.** Seu Raimundo tem um Gol G3 no quintal, cadastrado com fotos. Chega o aviso "procuram retrovisor elétrico de Gol G3, R$ 120 a R$ 200, no seu estado". Ele olha o carro, vê que tem a peça e toca em **Aceitar**. O chat abre com o cliente, ele manda as fotos da peça e fecha a venda. Se não tem a peça, ignora, e o pedido some da caixa dele.
- **J4. Loja cadastra e é avisada.** A loja Silva cadastra um farol com foto, valor, especificações, veículos compatíveis e local. Recebe o aviso de um pedido compatível e abre o chat. Um cliente também abre o chat a partir do anúncio. Ela vende e responde a um comentário.
- **J5. Só pelo veículo.** Carlos escolhe marca, modelo e ano sem dizer a peça e vê tudo o que há para aquele carro. Depois refina com a peça.

## 4. Glossário

**Cliente**, **Vendedor** (Loja ou Autônomo), **Loja** (cadastra peças), **Autônomo** (cadastra veículos), **Anúncio** (peça de uma Loja, com foto, valor, especificações, veículos compatíveis e local), **Veículo de Pátio** (veículo inteiro de um Autônomo, com fotos), **Pedido** (uma peça para um veículo, com faixa de valor e localização), **Faixa de valor** (mínimo e máximo que o cliente aceita pagar, visível ao vendedor avisado), **Localização** (estado, cidade e bairro), **Aviso** (notificação de um Pedido compatível), **Aceite** (o Autônomo decide atender e abre a Conversa), **Conversa** (chat entre cliente e vendedor), **Avaliação** (nota e comentário do cliente sobre o vendedor, que pode responder).

## 5. Requisitos funcionais

### Entrada e perfis
- **RF-01** Entrar **somente com Google** (e-mail verificado). O primeiro login cria a conta de cliente. Não há senha própria nem cadastro por e-mail e senha.
- **RF-02** Criar perfil de **Loja** ou de **Autônomo** (nome ou razão social, documento, cidade/UF, WhatsApp). Fica pendente até o administrador aprovar. Pendente, reprovado ou suspenso não recebe aviso nem publica.
- **RF-03** O administrador aprova ou reprova vendedores, com motivo, e suspende ou bane.
- **RF-04** Termos de uso e política de privacidade versionados, com aceite registrado no primeiro login.
- **RF-05** Editar dados e excluir a conta, com anonimização do histórico (LGPD).

### Pesquisa do cliente
- **RF-06** Pesquisar anúncios por **campo livre** e por **filtros**: veículo (tipo, marca, modelo, versão, ano), condição, valor e localização. Cada resultado mostra foto, valor, especificações e estado/cidade/bairro.
- **RF-07** **Navegar só pelo veículo**, sem informar a peça.
- **RF-08** Seleção de veículo em cascata (tipo, marca, modelo, versão e ano), com busca digitável, servida de uma base local sincronizada da FIPE.

### Loja
- **RF-09** Cadastrar, editar e remover anúncios: título, categoria, código, condição, valor (ou "sob consulta"), quantidade, fotos, especificações, veículos compatíveis e localização. Quantidade zerada oculta o anúncio.
- **RF-10** O cliente **abre a conversa pelo anúncio**. Só o cliente abre o chat com a loja a partir do anúncio.

### Pedido e aviso
- **RF-11** Criar **pedido** com veículo, peça, **faixa de valor obrigatória** e localização. Validade padrão de 7 dias, renovável. Editar antes da primeira resposta, cancelar e duplicar.
- **RF-12** O sistema avisa **lojas** (anúncios compatíveis) e **autônomos** (veículos de pátio compatíveis), mostrando veículo, peça, faixa de valor e região. Limite de 30 vendedores por pedido `[ASSUMPTION]`. Ninguém recebe aviso do próprio pedido.
- **RF-13** O vendedor ignora um pedido, e ele some da caixa dele.

### Autônomo
- **RF-14** Cadastrar **veículo de pátio**: tipo, marca, modelo, versão, ano, estado geral e localização, com fotos (pelo menos 3 `[ASSUMPTION]`). Sem cadastro de peças individuais.
- **RF-15** Ver o pedido, **verificar se o veículo tem a peça** e **Aceitar** ou **Ignorar**. Ao aceitar, a conversa abre e o cliente já vê as **fotos do estado do veículo**.

### Conversa
- **RF-16** Chat com texto e fotos, com estado enviado e lido. O autônomo envia depois as fotos da peça. Fotos sem metadados EXIF.
- **RF-17** Alertas anti-golpe fixos no chat. Bloquear e denunciar.
- **RF-18** O sistema **não** processa nem registra pagamento ou entrega. `[ASSUMPTION: o vendedor pode marcar "vendido" para baixar o estoque e liberar a avaliação]`

### Confiança
- **RF-19** O cliente **avalia** o vendedor com nota e comentário. Basta ter havido conversa com ele (não é preciso concluir a compra). Uma avaliação por cliente e vendedor `[ASSUMPTION]`.
- **RF-20** O vendedor **responde** ao comentário, uma resposta por avaliação.
- **RF-21** Nota média, número de avaliações e comentários aparecem no perfil e nos resultados.

### Notificações
- **RF-22** Notificações no app, com contador (aviso de pedido, mensagem, resposta).

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

## 7. Requisitos não funcionais (metas do MVP, a confirmar por teste)

- **Desempenho:** criar pedido p95 < 500 ms. Cascata de veículo p95 < 300 ms, da base local. Mensagem entre usuários online p95 < 1 s. LCP < 2,5 s em 4G. Aviso entregue em até 10 s em 95% dos casos.
- **Escala:** 10 mil usuários ativos, 1 mil conexões simultâneas de chat. API sem estado, listas paginadas.
- **Disponibilidade:** 99,5% ao mês. Mensagem persistida antes de confirmar, envio idempotente. Backup diário (RPO ≤ 24 h, RTO ≤ 4 h).
- **Segurança:** TLS 1.2+. Login só pelo Google, com `state`, `nonce` e PKCE, identidade pelo `sub`, e-mail verificado. Sessão com expiração. **Autorização por objeto** em toda rota (um vendedor só vê as próprias conversas), com teste de acesso indevido. Limite de taxa. Upload validado por tipo real, tamanho e quantidade. Auditoria das ações do administrador. Revisão contra o OWASP Top 10 antes do lançamento.
- **Privacidade (LGPD):** coleta mínima, aceite registrado, exportar e excluir dados, retenção definida, **logs sem dados pessoais**, fotos sem EXIF/GPS.
- **Usabilidade:** pt-BR, **mobile-first**, acessibilidade WCAG 2.1 AA nos fluxos principais, abrir pedido em até 4 passos, duas últimas versões de Chrome, Safari, Firefox e Edge.
- **Manutenção:** monólito modular, testes automatizados (TDD), migrações versionadas, API documentada, logs estruturados, fonte de veículos atrás de um adaptador.

## 8. Fora de escopo (v1)

Pagamento, frete, nota fiscal, custódia e disputa. Preço de referência de peças (a FIPE é só taxonomia de veículos). Cadastro de peça por peça para o autônomo. Login por senha. App nativo e reconhecimento de peça por foto. Comparação lado a lado, web push, importação em lote, vitrine pública, painel de métricas e outro provedor de login ficam para depois do MVP.

## 9. Riscos

| # | Risco | Mitigação |
|---|---|---|
| R1 | Avaliação falsa ou vingativa, porque basta ter tido contato | Uma avaliação por par, denúncia e moderação, e decidir se quem fechou o negócio pesa mais |
| R2 | Autônomo de má-fé ou fotos de outro carro | Aprovação do administrador, denúncia, EXIF removido |
| R3 | Partida a frio de marketplace de duas pontas | Piloto numa região, vendedores por convite, dados de demonstração |
| R4 | Golpes, já que o pagamento é fora | Aprovação, denúncia, bloqueio, alertas no chat e reputação |
| R5 | Spam de avisos para os vendedores | Limite de 30 por pedido, ranking e ignorar |
| R6 | A FIPE junta modelo e versão numa só *string*, e o pedido exige separar | Curadoria e normalização na sincronização. Limites da API a confirmar |
| R7 | Legal: peças de desmanche são reguladas (Lei 12.977/2014) | Validar com fonte oficial antes de exigir credenciamento |
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
11. **Moto e caminhão** funcionam como carro.

## 11. Perguntas abertas

1. Avaliação: basta ter havido conversa, ou concluir a compra deve pesar mais?
2. Um usuário pode ser Loja e Autônomo ao mesmo tempo?
3. Quando vários autônomos aceitam o mesmo pedido, o cliente abre várias conversas? Há limite?
4. Uma loja avisada de um pedido responde abrindo a conversa, ou só o cliente abre pelo anúncio?
5. A faixa de valor também filtra os anúncios na pesquisa?
6. Região e nicho do piloto. Nome definitivo do produto.

## 12. Próximos passos

1. Revisar este plano e responder às perguntas da seção 11.
2. Fechar o PRD com o BMad (revisão de RF, RNF e riscos).
3. UX das telas e diagramas.
4. Escolher a tecnologia e a arquitetura.
5. Épicos e histórias, e só então código.

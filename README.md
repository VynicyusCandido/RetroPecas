<p align="center">
  <img src="assets/LogoComFundoPreto.jpg" alt="Logo da RetroPeças" width="420">
</p>

# RetroPeças

> **"O retrô que mantém seu carro na pista."**

Plataforma de conexão por demanda para **autopeças usadas** de carros, feita para donos de carros antigos e raros. Projeto de TCC.

## O problema

Quem precisa de uma peça usada procura loja por loja, liga e compara na mão. A peça rara, ou o carro velho esquecido num quintal, nunca aparece. Do outro lado, quem tem um veículo parado ou peças sobrando não tem onde mostrá-los a quem os procura.

## A ideia

A RetroPeças liga quem **procura** a quem **tem**, de duas formas:

- **Anúncio.** O vendedor publica uma peça ou um veículo (inteiro ou parcial), com fotos. O comprador encontra pela busca.
- **Pedido (a grande sacada).** Quando o comprador não acha, ele publica um pedido com o veículo, a peça e a **faixa de valor** que aceita pagar. Quem tem um item compatível cadastrado é avisado, vê a faixa de valor e decide se atende abrindo uma conversa.

O pedido também vale para **veículos**: quem desmonta pode pedir um carro inteiro ou parcial, e quem tem um carro parado vê a demanda.

A plataforma só **conecta** as pessoas. O negócio fecha **fora dela**.

## Para quem é

O público central são **donos de carros antigos ou raros**, para quem achar peça é difícil e a loja comum não resolve.

- **Quem procura uma peça** encontra pelo veículo e pela peça, ou publica um pedido e espera ser procurado.
- **Quem tem peças ou um veículo parado** anuncia sem cadastrar peça por peça e recebe aviso quando há procura compatível.
- **Quem desmonta veículos**, por exemplo uma loja, procura carros inteiros ou parciais para abastecer o estoque.

## O que a plataforma faz

### Uma conta, dois modos

Não existem tipos de conta como "loja" ou "autônomo". O usuário entra com o **Google**, informa o CPF uma vez e alterna entre **Comprar** e **Vender** na mesma conta. Uma loja é só alguém com um catálogo grande, e um colecionador é alguém com poucas peças raras.

### Catálogo do vendedor

Tudo é cadastrado como uma **peça**: marca, modelo, versão e faixa de anos do veículo (por exemplo, 1974 a 1978), a parte, título, condição, descrição do estado, valor (ou "sob consulta"), quantidade, até 5 fotos, especificações e localização. Quem tem o **veículo inteiro** marca isso, diz se está **parcial** ou **completo** e anuncia com no mínimo 3 fotos, sem cadastrar as peças dele uma a uma. O vendedor edita, exclui e marca o item como **vendido**, e ele sai da busca e dos avisos.

Marca, modelo, versão e ano são **digitados**, porque muitos carros são antigos ou abandonados e não estão em listas oficiais. O campo sugere valores já usados por outros usuários, para a busca não se dividir em grafias diferentes.

### Busca do comprador

- **Por zona do veículo:** frente, traseira, lateral direita, lateral esquerda, interior, motor e fundo. A zona escolhida mostra a lista de partes dela.
- **Por filtros:** veículo, veículo inteiro (parcial ou completo), condição, faixa de valor e localização. Não há busca por texto livre.
- **Só pelo veículo:** sem dizer a peça, o comprador vê tudo o que existe para aquele carro.

Cada resultado mostra foto, valor, especificações e localização.

### Pedido por demanda

O pedido leva o veículo (marca, modelo, versão e ano), a peça ou só o veículo, a localização e uma **faixa de valor obrigatória**. Ele vale por **7 dias** e pode ser renovado, editado antes da primeira resposta, cancelado e duplicado. Cada usuário pode ter até 10 pedidos abertos e criar 20 por dia, contra spam. Pedido expirado sai dos avisos, e as conversas dele ficam somente leitura.

### Aviso de pedido compatível

Um item é compatível quando **marca, modelo e parte são iguais** e o **ano do pedido está dentro da faixa de anos do item**. A versão não conta. Quem tem um veículo inteiro da mesma marca, modelo e ano também é avisado. Cada pedido avisa no máximo 30 vendedores, dos itens mais recentes para os mais antigos, e ninguém é avisado do próprio pedido.

O aviso chega no app e **por e-mail**, e mostra o nome da peça, a cidade e a faixa de valor. O vendedor **aceita**, e a conversa abre, ou **ignora**, e o pedido some só da caixa dele.

### Conversas

Chat **só de texto**, com estado enviado e lido. As imagens são as do anúncio, e quem quiser ver mais combina fora da aplicação. Cada usuário tem duas caixas: **Compras**, sempre disponível, e **Vendas**, que só aparece para quem tem item cadastrado. Há no máximo uma conversa por anúncio ou pedido e usuário.

O cabeçalho, como no WhatsApp, mostra a foto do item, o nome da peça, o vendedor e a localização. Tocar nele abre o perfil da conversa, com as demais fotos e o acesso ao perfil do vendedor, que mostra nome, imagem, nota, comentários e catálogo. O chat traz alertas contra golpe e permite **bloquear** e **denunciar**.

### Fechamento e avaliação

Ao encerrar uma conversa com **mais de 10 mensagens**, o usuário responde **Fechado** (e vai avaliar o perfil) ou **Não fechado** (só registra). Quando o vendedor marca o item como vendido, ele pode avaliar o comprador, e o comprador é notificado para avaliar o vendedor.

A avaliação distribui **5 pontos entre estrelas e X**, com comentário e o tipo de interação (compra ou venda). Há uma avaliação por usuário e por conversa, e o dono do perfil responde uma vez a cada uma. A nota do perfil é a média das avaliações.

### Notificações

Contador no app para aviso de pedido, mensagem, resposta a avaliação e pedido de avaliação.

### Moderação e painel do administrador

Qualquer usuário denuncia outro usuário, um anúncio, uma avaliação ou uma conversa, com um motivo (como golpe, chantagem, ameaça, anúncio falso, perfil falso ou spam) e uma mensagem. A denúncia passa por **aberta**, **em análise** e **resolvida**, e o denunciante é avisado do resultado. Denúncia comprovada bloqueia o perfil. O usuário bloqueado pode **contestar** em até 7 dias, uma vez por bloqueio, e o administrador reanalisa.

O administrador é só moderador. O painel de métricas mostra conversas encerradas e a porcentagem que terminou em **Fechado**, nota média, pedidos criados e atendidos, e denúncias por motivo e estado.

## Confiança e segurança

- **Responsável por conta.** O CPF é validado pelo dígito verificador, guardado criptografado e invisível a outros usuários. O administrador só o consulta em denúncia grave ou ordem judicial, e cada acesso fica em auditoria.
- **Negócio fora da plataforma.** A RetroPeças não processa pagamento nem entrega, não tem vínculo com a peça nem com a origem dela e não certifica a procedência. Isso consta nos Termos de Uso.
- **Reputação.** A confiança vem da avaliação do perfil e da denúncia. Não há selo de loja nem aprovação prévia do vendedor.
- **Privacidade.** Exclusão de conta com anonimização do histórico, exportação dos dados do usuário (LGPD), fotos sem EXIF nem GPS e logs sem dado pessoal.

## Como a v1 deve se comportar

- Aplicação **web para desktop**, em português do Brasil, com layout que se adapta a telas menores e acessibilidade WCAG 2.1 AA nos fluxos principais.
- Dimensionada para **500 usuários** e **50 conversas simultâneas**, com a regra de negócio separada da tela para permitir uma versão mobile no futuro.
- Metas de desempenho: pedido criado em menos de 500 ms, busca em menos de 1 s, mensagem entregue em menos de 1 s e aviso de pedido em até 10 s, em 95% dos casos.
- Publicar um pedido leva no máximo 4 passos.
- Teste de usabilidade com usuários reais antes da entrega.

## Como medimos o sucesso

Pelo menos **metade das conversas** com mais de 10 mensagens termina em **Fechado**, medido no painel do administrador. Também contam a nota dos perfis depois da conversa, a facilidade de publicar um pedido e a rapidez com que um pedido compatível chega a quem tem o item.

## Fora do escopo da v1

Pagamento, frete, nota fiscal, custódia e disputa. Certificação de origem das peças. Preço de referência de peças. Lista oficial de veículos (FIPE). Envio de fotos no chat. Escolher a peça clicando na imagem do carro. Motos e caminhões. Aplicativo nativo. Reconhecimento de peça por foto. Web push. Importação de anúncios em lote. Outro provedor de login além do Google. Carrinho, checkout e gestão de estoque. Peça que serve em mais de um modelo.

## Documentação

- [Plano do produto](docs/plano.md)
- [PRD](docs/prd/prd.md)
- [Spec](docs/spec/SPEC.md) e [histórias](docs/spec/stories.yaml)

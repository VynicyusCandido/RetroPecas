---
title: RetroPeças — PRD
status: final
created: 2026-10-29
updated: 2026-10-30
---

# RetroPeças — PRD

> **"O retrô que mantém seu carro na pista."**

_Versão final. Insumo: `plano.md`. Decisões em `.memlog.md`; material de apoio em `addendum.md`._

## 1. Visão

### O problema

Quem precisa de uma peça usada procura loja por loja, liga, compara na mão, e a peça rara, ou o carro velho esquecido num quintal, nunca aparece. Do outro lado, quem tem um veículo parado ou peças sobrando não tem onde mostrá-los a quem os procura.

### A ideia

A RetroPeças é uma **plataforma de conexão por demanda para autopeças usadas** (carros), feita para donos de carros antigos e raros. Ela liga quem **procura** a quem **tem**, de duas formas:

- **Anúncio:** o vendedor publica uma peça ou um veículo (inteiro ou parcial), com fotos, e o comprador encontra pela pesquisa.
- **Pedido (a grande sacada):** quando o comprador não acha, ele publica um **pedido** com o veículo, a peça e a **faixa de valor** que aceita pagar. Quem tem um item compatível cadastrado é avisado, vê a faixa de valor e decide se atende abrindo uma conversa.

O pedido também vale para **veículos**: quem desmonta pode pedir um carro inteiro ou parcial, e quem tem um carro parado vê a demanda.

A plataforma só **conecta** as pessoas. O negócio fecha **fora dela**: ela não processa pagamento nem entrega, e não certifica a procedência das peças. A confiança vem da **avaliação do perfil**, com nota, comentário e resposta do dono do perfil.

### Quem usa e o que muda para eles

O público central são **donos de carros antigos ou raros**, para quem achar peça é difícil e a loja comum não resolve. Do outro lado estão quem tem peças ou veículos parados e quem desmonta veículos.

- **Quem procura uma peça:** encontra pelo veículo e pela peça, ou publica um pedido e espera ser procurado.
- **Quem tem peças ou um veículo:** anuncia sem precisar cadastrar peça por peça, e recebe aviso quando há procura compatível.
- **Quem desmonta veículos (por exemplo, uma loja):** procura carros inteiros ou parciais para abastecer o estoque.

### Resultados esperados

O sucesso é medido pela **nota dos perfis depois da conversa** e pela **confirmação de que o negócio fechou** (ver "Avaliação e fechamento" na seção 2).

- Meta: pelo menos **metade das conversas** com mais de 10 mensagens termina em **Fechado**.
- O comprador consegue publicar um pedido em poucos passos.
- Um pedido compatível chega a quem tem o item cadastrado, em pouco tempo.
- A conversa entre comprador e vendedor acontece dentro da plataforma, e o fechamento fora dela.

### Fora do escopo desta visão

Pagamento, frete, nota fiscal, custódia e disputa. Certificação de origem das peças. Preço de referência de peças e lista oficial de veículos (FIPE).

## 2. Modelo de usuário

### Princípio: uma conta, vários "chapéus"

Todo usuário tem **a mesma conta**. Não existem tipos de conta como "Loja" ou "Autônomo". O que existe é **o que a pessoa está fazendo agora**:

| Modo | O que a pessoa faz |
|---|---|
| **Comprar** | Pesquisa peças ou veículos, publica pedidos, conversa com vendedores |
| **Vender** | Anuncia peças ou veículos (inteiros ou parciais), recebe avisos de pedidos compatíveis, conversa com compradores |

A mesma pessoa pode estar nos dois modos. Uma loja é, na prática, alguém com um catálogo grande. Um colecionador ou garimpador é alguém com poucas peças raras. A plataforma não distingue os dois.

### Identidade e confiança

- O login é feito com **Google**, com e-mail verificado.
- No primeiro acesso, o usuário informa o **CPF**. Ele existe só para garantir que há um **responsável legal** por cada conta. **Nenhum usuário tem acesso a ele.** O administrador só o vê em caso de **denúncia grave ou ordem judicial**.
- A plataforma **só liga usuários**: não tem vínculo com a peça nem com o veículo, nem com a origem deles. Isso consta nos **Termos de Uso**, redigidos para afastar a responsabilidade da plataforma. Não há validação jurídica prevista para a v1.
- A v1 valida o CPF só pelo **dígito verificador**. A consulta a fonte oficial fica para depois.
- Não há selo de loja nem aprovação prévia do vendedor. A confiança vem do CPF vinculado e da avaliação do perfil.
- O administrador é **só moderador**: trata denúncias, avaliações, conteúdo indevido e suspende contas.

### O que o usuário pode cadastrar

| Item | O que é | Quem pode |
|---|---|---|
| **Peça** | O que o usuário cadastra para vender. Leva marca, modelo, versão e faixa de anos do veículo, a **parte** (qual peça é), descrição do estado, até 5 fotos, valor, especificações e local. Se for o **veículo inteiro**, o usuário marca isso e diz se está **parcial** ou **completo** | Qualquer usuário |
| **Pedido** | Uma peça ou um veículo que a pessoa procura, com faixa de valor e localização | Qualquer usuário |

Não existe um cadastro separado de veículo: quem tem um carro parado cadastra o veículo inteiro pelo mesmo fluxo da peça, sem cadastrar as peças dele uma a uma. Uma peça avulsa também pode ser cadastrada por quem não tem o veículo (colecionador ou garimpador).

### Regra do aviso

- Um pedido avisa quem tem **item compatível cadastrado** (peça ou veículo). A regra de compatibilidade está no RF-16.
- **Ninguém recebe aviso do próprio pedido.** Isso vale também entre os "chapéus" da mesma conta.
- O comprador pode abrir conversa com **todos** os vendedores que aceitarem, sem limite.
- A faixa de valor é visível a quem foi avisado.
- Cada pedido avisa **no máximo 30 vendedores**, escolhidos entre os itens compatíveis, dos **cadastrados ou atualizados mais recentemente** para os mais antigos.

### Conversas

Cada usuário tem **duas caixas de conversa**:

- **Compras:** as conversas dos pedidos que a pessoa fez e das peças que procura. Está sempre disponível.
- **Vendas:** as conversas e os avisos sobre os itens que a pessoa cadastrou. **Só aparece** para quem tem item cadastrado.

### Avaliação e fechamento

A confiança vem da avaliação **do perfil**, não da conversa: estrelas e X que somam 5, comentário e resposta do dono do perfil. A avaliação nasce quando uma conversa de mais de 10 mensagens é encerrada, ou quando o vendedor marca o item como vendido, e o vendedor tem prioridade nesse fechamento. O sucesso do produto é medido por essas respostas. As regras completas estão nos requisitos RF-27 a RF-31, e o desenho futuro da avaliação (anjinhos) está no `addendum.md`.

### Telas de entrada (a validar na etapa de UX)

- A tela inicial tem os modos **Comprar** e **Vender**.
- Para vender, um **botão fixo** na navbar ou no menu abre um **modal** de cadastro (peça ou veículo inteiro).

## 3. Requisitos funcionais

Os IDs são globais e estáveis. Os requisitos descrevem o que o sistema faz, não como é feito.

### 3.1 Conta e acesso

- **RF-01** O usuário entra com **Google** (e-mail verificado). O primeiro login cria a conta. Não há senha própria.
- **RF-02** No primeiro acesso, o usuário informa o **CPF**, validado pelo dígito verificador. Um CPF só pode estar em uma conta. O CPF nunca aparece a outros usuários.
- **RF-03** Os **Termos de Uso** e a política de privacidade têm versão, e o aceite é registrado no primeiro login.
- **RF-04** O usuário edita seus dados e exclui a conta, com anonimização do histórico (LGPD).
- **RF-05** O usuário alterna entre os modos **Comprar** e **Vender** na mesma conta.

### 3.2 Catálogo do vendedor

- **RF-06** O usuário cadastra o que quer vender pelo modal aberto pelo botão fixo, sempre como uma **peça**: marca, modelo, versão e **faixa de anos** do veículo em que a peça serve (por exemplo, 1974 a 1978), a **parte** (qual peça é), título, condição, **descrição do estado da peça**, valor (ou "sob consulta"), quantidade, **até 5 fotos**, especificações e localização (estado, cidade e bairro). A parte não é obrigatória quando o anúncio é de um veículo inteiro (RF-07). Quantidade zerada oculta o anúncio. O valor é digitado pelo vendedor, sem preço de referência, e é indicativo: vale o combinado na conversa.
- **RF-07** O usuário indica se o anúncio é de um **veículo inteiro**. Se for, escolhe se está **parcial** ou **completo**, e a busca passa a filtrar por isso. Não é preciso cadastrar as peças do veículo. O veículo inteiro exige **no mínimo 3 fotos**.
- **RF-08** O usuário edita e exclui os itens do próprio catálogo.
- **RF-09** O vendedor marca um item como **vendido**. O item sai da busca e dos avisos e continua no catálogo dele até ele excluí-lo.

### 3.3 Busca do comprador

- **RF-10** O usuário **digita** marca, modelo, versão e ano do veículo. Não há lista oficial (FIPE): muitos veículos são antigos, estão abandonados ou não rodam mais, e existem para ser desmontados. O campo sugere valores já digitados por outros usuários, para a busca não se dividir por grafias diferentes.
- **RF-11** O comprador escolhe a **zona do veículo** (frente, traseira, lateral direita, lateral esquerda, interior, motor ou fundo) e vê a lista de **partes** daquela zona. O catálogo completo está em `addendum.md`. O catálogo da v1 cobre carros e é mantido pela equipe.
- **RF-12** O comprador pesquisa **peças e veículos inteiros** por **filtros**: zona e parte, veículo (marca, modelo, versão e ano), veículo inteiro (parcial ou completo), condição, **faixa de valor** (mínimo e máximo) e localização. Não há busca por texto livre. Cada resultado mostra foto, valor, especificações e localização.
- **RF-13** O comprador pode **navegar só pelo veículo**, sem dizer a peça, e ver tudo que há para ele.

### 3.4 Pedido e aviso

- **RF-14** O comprador cria um **pedido** com marca, modelo, versão e **ano** do veículo (um só) e a **parte** procurada, ou só o veículo quando procura um carro inteiro. A **faixa de valor é obrigatória** (mínimo menor ou igual ao máximo), e a localização também. Sem faixa de valor, o pedido não é criado. A faixa é visível só a quem foi avisado. Cada usuário pode ter no máximo **10 pedidos abertos** e criar no máximo **20 por dia** (RN-06).
- **RF-15** O pedido vale **7 dias** e pode ser renovado. O comprador o edita antes da primeira resposta, cancela e duplica.
- **RF-16** O sistema **avisa** quem tem item compatível cadastrado. Um item é **compatível** quando **marca, modelo e parte** são iguais e o **ano do pedido está dentro da faixa de anos do item**. A **versão não conta**. Quem tem um **veículo inteiro** da mesma marca, modelo e ano também é avisado do pedido de uma peça dele e, ao aceitar, confere se a peça ainda está lá. O aviso mostra veículo, peça, faixa de valor e região. Ninguém recebe aviso do próprio pedido, mesmo com itens compatíveis em outro "chapéu" da mesma conta. Cada pedido avisa **no máximo 30 vendedores**, dos itens mais recentes para os mais antigos.
- **RF-17** Quem foi avisado **aceita** (a conversa abre) ou **ignora** (o pedido some só da caixa dele). Antes de decidir, o vendedor avisado vê do pedido o **nome da peça**, a **cidade** e a **faixa de valor** escolhida pelo comprador.
- **RF-18** Pedido sem renovação **expira** ao fim do prazo: sai dos avisos e das caixas, e as conversas dele ficam somente leitura.

### 3.5 Conversas

- **RF-19** A conversa abre por um **anúncio** (o comprador) ou por um **aceite de pedido** (o vendedor). Há no máximo uma conversa por par (anúncio ou pedido, e usuário).
- **RF-20** O chat tem **só texto**, com estado enviado e lido. Ninguém envia foto no chat: as imagens são as que o vendedor colocou no anúncio. Para ver mais imagens, as pessoas combinam **fora da aplicação**.
- **RF-21** Cada usuário tem a caixa **Compras**, sempre disponível, e a caixa **Vendas**, que só aparece para quem tem item cadastrado.
- **RF-22** O chat mostra alertas fixos contra golpe e permite **bloquear** e **denunciar** o outro usuário (RF-35). Bloquear tira a conversa das caixas e impede nova conversa e novos avisos entre os dois. Conversa com pedido expirado ou conta suspensa fica somente leitura.
- **RF-23** O **cabeçalho da conversa** (como no WhatsApp) mostra a **foto do item** ao lado do **nome da peça**, o **nome do vendedor** abaixo e a **localização** ao lado, mais um **botão com uma estrela** que leva à avaliação do perfil da outra pessoa. O botão só fica habilitado com mais de 10 mensagens (RF-29).
- **RF-24** Abrir o **perfil da conversa** (tocando no cabeçalho) mostra as **demais fotos do item** e o nome do vendedor, que pode ser aberto para ver o perfil dele.
- **RF-25** O **perfil do usuário** mostra o nome e a imagem, a nota, os comentários e o **catálogo** dele, ao lado ou abaixo do nome.
- **RF-26** A plataforma não processa nem registra pagamento ou entrega.

### 3.6 Fechamento e avaliação

- **RF-27** Ao **encerrar uma conversa** (ação explícita do usuário) **com mais de 10 mensagens**, abre um modal com **Fechado** (leva à avaliação do perfil) e **Não fechado** (só registra, sem levar à avaliação). As respostas são guardadas e alimentam a métrica de conversas que fecharam. O modal não volta se a conversa não for reaberta, e não há lembrete de 24h.
- **RF-28** Ao marcar o item como vendido (RF-09), o vendedor pode **avaliar o comprador**, e o comprador recebe uma notificação para avaliar o vendedor.
- **RF-29** A avaliação do perfil tem estrelas e X (somam 5), comentário e o **tipo de interação** (compra ou venda). Só avalia quem teve conversa com mais de 10 mensagens ou foi notificado pelo RF-28. Uma avaliação por usuário e por conversa.
- **RF-30** O dono do perfil **responde** a cada avaliação, uma resposta por avaliação.
- **RF-31** O perfil e os resultados mostram a **nota** (média das avaliações), o número de avaliações, a contagem de X e os comentários. Quem não tem item cadastrado aparece só como comprador.

### 3.7 Notificações e moderação

- **RF-32** O app tem **notificações** com contador: aviso de pedido, mensagem, resposta a avaliação e pedido de avaliação. O **aviso de pedido** também é enviado por **e-mail** ao vendedor (o e-mail verificado do login Google).
- **RF-33** O administrador trata denúncias (RF-35) e avaliações, remove conteúdo indevido e suspende contas. Conta suspensa não entra, não publica e não recebe aviso.
- **RF-34** O administrador só acessa o CPF em **denúncia grave ou ordem judicial**, e cada acesso é registrado em auditoria.
- **RF-35** Qualquer usuário **denuncia** outro usuário, um anúncio, uma avaliação ou uma conversa, escolhendo um **motivo** e escrevendo uma **mensagem**. Motivos: preço fora da realidade, chantagem, golpe, xingamento ou ofensa, ameaça ou assédio, anúncio falso (foto ou peça que não existe), perfil falso, spam ou propaganda, conteúdo ilegal e outro.
- **RF-36** Uma denúncia passa por estados: **aberta**, **em análise** e **resolvida** (procedente ou improcedente). O denunciante é avisado do resultado. Só o administrador responsável vê a conversa denunciada, e apenas para analisar aquela denúncia. **Denúncia comprovada bloqueia o perfil.** Conforme a gravidade, o administrador pode antes advertir, remover o conteúdo ou suspender por tempo. Golpe, chantagem e ameaça contam como **denúncia grave** (RF-34).
- **RF-37** O usuário bloqueado pode **contestar** a decisão com uma mensagem, e o administrador reanalisa a denúncia. Perfil bloqueado não entra, não publica e não recebe aviso até a reanálise. A contestação pode ser enviada em até **7 dias** após o bloqueio, **uma por bloqueio**, e a decisão da reanálise é definitiva na v1.
- **RF-38** O administrador vê um **painel de métricas**: conversas encerradas e a porcentagem que terminou em **Fechado** (a meta da seção 1), nota média da plataforma, pedidos criados e atendidos, e denúncias por motivo e por estado. Só o administrador vê o painel.

## 4. Requisitos não funcionais

**Prioridades:** velocidade e facilidade de uso. A v1 é uma aplicação **web para desktop**, com tecnologias que permitam levá-la ao celular se virar produto. As metas abaixo valem para o ambiente de demonstração e cada uma tem de ser medida ou testada. Os números de desempenho vêm do plano original e são confirmados por teste.

### Desempenho e escala

- **RNF-01** Criar um pedido leva menos de 500 ms em 95% dos casos.
- **RNF-02** A sugestão de marca, modelo e versão aparece em menos de 300 ms em 95% dos casos.
- **RNF-03** Uma busca retorna resultados em menos de 1 s em 95% dos casos.
- **RNF-04** Uma mensagem entre usuários online chega em menos de 1 s em 95% dos casos.
- **RNF-05** O aviso de um pedido compatível chega em até 10 s em 95% dos casos.
- **RNF-06** A v1 é dimensionada para até **500 usuários cadastrados** e **50 conversas simultâneas**. O sistema não guarda estado na sessão do servidor, para poder crescer sem reescrita, mas só garante o que for testado.

### Disponibilidade e dados

- **RNF-07** O link da aplicação fica no ar **até a divulgação das notas**. Não há meta numérica de disponibilidade.
- **RNF-08** Toda mensagem é gravada antes de ser confirmada ao remetente, e o reenvio não duplica mensagem.
- **RNF-09** Há backup diário dos dados. A perda máxima aceita é de 24 h.

### Usabilidade

- **RNF-10** A interface é em **português do Brasil**, pensada para desktop, e o layout se **adapta a telas menores**. Funciona nas duas últimas versões de Chrome, Edge, Firefox e Safari.
- **RNF-11** Publicar um pedido leva no máximo **4 passos**.
- **RNF-12** As regras de negócio ficam atrás de uma API, sem depender da tela, para que uma versão mobile reaproveite o mesmo servidor.
- **RNF-13** Os fluxos principais (buscar, pedir, conversar, avaliar) seguem acessibilidade WCAG 2.1 nível AA.

### Segurança e privacidade

- **RNF-14** Todo tráfego usa TLS. O login com Google é validado (estado, nonce e PKCE), e a sessão é um **token JWT enviado no cabeçalho de cada requisição**, com expiração. A aplicação se protege contra XSS e CSRF.
- **RNF-15** Cada rota confere se o usuário pode acessar aquele objeto (por exemplo, só quem participa vê a conversa), com teste de acesso indevido.
- **RNF-16** O **CPF** é guardado criptografado, e cada acesso do administrador a ele é registrado em auditoria (RF-34).
- **RNF-17** Os logs não têm dado pessoal. As fotos não guardam EXIF nem GPS.
- **RNF-18** Uploads são validados por tipo real, tamanho e quantidade, e há limite de requisições por usuário contra abuso.
- **RNF-19** O usuário exporta e exclui seus dados (LGPD, RF-04).

### Qualidade e operação

- **RNF-20** O código é desenvolvido com **TDD** e tem testes automatizados, com cobertura mínima de **75% no servidor e 25% na interface** (critério do Playbook do TCC).
- **RNF-21** Há ambientes de desenvolvimento e de produção separados, integração e entrega automatizadas, análise estática de segurança, logs estruturados e observabilidade básica (critérios do Playbook do TCC).
- **RNF-22** A API é documentada, e as migrações do banco são versionadas.
- **RNF-23** A produção não usa banco embutido (SQLite ou H2) nem plataforma que entrega banco, login e API prontos. A hospedagem é pública e estável até a divulgação das notas, com deploy automatizado. As restrições completas do Playbook estão em `addendum.md` (critérios do Playbook do TCC).
- **RNF-24** Antes da entrega, a interface passa por **teste de usabilidade com usuários reais**, documentado.

## 5. Regras de negócio

- **RN-01** Um pedido tem exatamente um item procurado: uma peça ou um veículo.
- **RN-02** Há no máximo uma conversa por par (anúncio ou pedido, e usuário).
- **RN-03** O valor do anúncio é indicativo. O que vale é o combinado na conversa.
- **RN-04** O negócio sempre fecha **fora da plataforma**. A plataforma não é parte da venda, não processa pagamento nem entrega, e não tem vínculo com a peça, o veículo nem a origem deles (Termos de Uso).
- **RN-05** Um pedido sem renovação expira em 7 dias, e as conversas dele ficam somente leitura.
- **RN-06** Cada usuário tem limite de **10 pedidos abertos e 20 por dia**, contra spam.
- **RN-07** Uma avaliação só existe depois de uma conversa com mais de 10 mensagens, ou de uma venda marcada pelo vendedor. Há uma avaliação por usuário e por conversa.
- **RN-08** Conteúdo indevido (fotos, textos, peças de origem ilícita) é removido após denúncia, e a conta pode ser suspensa.

## 6. Fora de escopo (v1)

Pagamento, frete, nota fiscal, custódia e disputa. Certificação de origem das peças. Preço de referência de peças. Envio de fotos no chat. Escolher a peça clicando na imagem do carro. Moto e caminhão (a v1 atende só carros). Aplicativo nativo. Reconhecimento de peça por foto. Notificação por web push. Importação de anúncios em lote. Outro provedor de login além do Google. Aprovação prévia de vendedor e selo de loja. Carrinho, checkout, gestão de estoque e gestão financeira.

## 7. Riscos

| # | Risco | Mitigação |
|---|---|---|
| R1 | Avaliação falsa ou vingativa | Só avalia quem teve mais de 10 mensagens, uma avaliação por conversa, denúncia e moderação |
| R2 | Golpe, já que o pagamento é fora da plataforma | Alertas no chat, bloquear e denunciar, CPF vinculado, nota e X no perfil |
| R3 | Partida a frio de uma plataforma de duas pontas, com público de nicho (carros antigos) | Piloto numa região, convite a colecionadores e clubes de carros antigos |
| R4 | Spam de avisos para os vendedores | Limite de avisados por pedido, ignorar, limite de pedidos por usuário |
| R5 | Como marca, modelo, versão e ano são digitados, a mesma coisa vira grafias diferentes ("Gol G3", "gol g 3") e a busca não encontra | Sugestão a partir de valores já usados e busca que ignora maiúsculas e acentos |
| R6 | Legal: peças de desmanche são reguladas (Lei 12.977/2014) e a responsabilidade pela origem não foi validada juridicamente | Plataforma só liga usuários, Termos de Uso redigidos para afastar a responsabilidade. Risco residual aceito na v1 |
| R7 | Vazamento do CPF | Criptografia, acesso do administrador só em denúncia grave ou ordem judicial, com auditoria |
| R8 | Dependência do Google: fora do ar, ninguém entra. Aplicativo em modo de teste só aceita contas de teste | Publicar o aplicativo antes de abrir ao público. Plano B de login depois da v1 |
| R9 | Conteúdo indevido em fotos e textos | Denúncia e moderação manual |

## 8. Critérios de aceite do MVP

1. **Conta:** o primeiro login com Google pede o CPF. CPF com dígito inválido é recusado. Conta suspensa não entra.
2. **Cadastro e busca:** peça e veículo inteiro cadastrados aparecem na busca por zona do veículo, por filtros e só pelo veículo. Item vendido ou com quantidade zerada não aparece. Mais de 5 fotos por peça é recusado.
3. **Pedido:** sem faixa de valor, ou com mínimo maior que o máximo, o pedido não é criado.
4. **Aviso:** quem tem item compatível é avisado e vê o nome da peça, a cidade e a faixa de valor. O criador do pedido não é avisado.
5. **Aceite:** ao aceitar, a conversa abre com a foto do item, o nome da peça, o nome do vendedor e a localização no cabeçalho. O perfil da conversa mostra as demais fotos e leva ao perfil e ao catálogo do vendedor.
6. **Ignorar:** o pedido ignorado some só da caixa de quem ignorou.
7. **Isolamento:** um terceiro recebe acesso negado ao abrir a conversa de outros dois usuários.
8. **Chat:** só texto. Não há envio de foto.
9. **Fechamento:** ao encerrar uma conversa com mais de 10 mensagens, o modal aparece. **Fechado** leva à avaliação e **Não fechado** só registra. Com 10 mensagens ou menos, o modal não aparece.
10. **Avaliação:** estrelas e X somam 5. Há uma avaliação por conversa, uma resposta do dono do perfil e o tipo de interação (compra ou venda) à vista.
11. **Venda:** ao marcar vendido, o item sai da busca e o comprador é notificado para avaliar.
12. **CPF:** nenhum usuário vê o CPF. Cada acesso do administrador gera um registro de auditoria.
13. **Fora da plataforma:** o sistema nunca pede pagamento nem entrega.
14. **Expiração:** o pedido expira em 7 dias, e as conversas dele ficam somente leitura.
15. **Compatibilidade:** um pedido com marca, modelo, ano e parte avisa o item com a mesma marca, modelo e parte cuja faixa de anos contém o ano do pedido, mesmo com versão diferente. Um item de outro modelo ou de ano fora da faixa não é avisado.
16. **Contestação:** o usuário bloqueado consegue contestar, e o administrador reanalisa a denúncia.
17. **Métricas:** o painel do administrador mostra a porcentagem de conversas que terminaram em *Fechado*.

## Perguntas abertas

Nenhuma por enquanto. Na v1, o anúncio de uma peça tem um modelo só; peça que serve em mais de um modelo fica para depois.
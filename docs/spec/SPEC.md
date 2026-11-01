---
id: SPEC-retropecas
companions:
  - ../prd/prd.md
  - ../prd/addendum.md
sources: []
---

> **Canonical contract.** Esta SPEC e os arquivos em `companions:` são o contrato completo do que construir, testar e validar. `prd.md` detém os requisitos numerados (RF, RNF, RN) e os critérios de aceite; `addendum.md` detém o catálogo de zonas e partes e as restrições do Playbook. Cada CAP abaixo cita os RF que o detalham.

# RetroPeças

## Why

Oportunidade e dor. Donos de carros antigos ou raros procuram peça usada loja por loja, e a peça rara nunca aparece. Quem tem peça ou carro parado não tem onde mostrá-los a quem procura. A RetroPeças liga os dois lados, e o diferencial é o **pedido**: o comprador publica o que procura com uma faixa de valor, e quem tem item compatível é avisado. A plataforma só conecta pessoas; o negócio fecha fora dela. É também um TCC com restrições de entrega do Playbook.

## Capabilities

- **CAP-1** Conta e acesso (RF-01..05)
  - **intent:** O usuário entra com Google, informa o CPF uma vez e alterna entre os modos Comprar e Vender na mesma conta.
  - **success:** Primeiro login pede CPF; dígito inválido ou CPF repetido é recusado; conta suspensa não entra; exclusão anonimiza o histórico.
- **CAP-2** Catálogo do vendedor (RF-06..09)
  - **intent:** O vendedor cadastra peça ou veículo inteiro (parcial ou completo), com fotos, e o mantém, marcando como vendido quando fechar.
  - **success:** Mais de 5 fotos por peça é recusado; veículo inteiro exige 3 fotos; item vendido ou com quantidade zero some da busca e dos avisos.
- **CAP-3** Busca do comprador (RF-10..13)
  - **intent:** O comprador encontra peças e veículos por zona do veículo, filtros ou só pelo veículo.
  - **success:** Itens cadastrados aparecem pelos três caminhos; resultado mostra foto, valor, especificações e localização; busca p95 < 1 s (RNF-03).
- **CAP-4** Pedido por demanda (RF-14,15,18)
  - **intent:** O comprador publica um pedido de peça ou veículo com faixa de valor obrigatória, que vale 7 dias e pode ser renovado.
  - **success:** Sem faixa, ou com mínimo > máximo, o pedido não é criado; limite de 10 abertos e 20 por dia; expirado vira somente leitura; criação p95 < 500 ms e em até 4 passos.
- **CAP-5** Aviso de compatibilidade (RF-16,17)
  - **intent:** Quem tem item compatível é avisado, vê peça, cidade e faixa de valor, e aceita (abre conversa) ou ignora.
  - **success:** O vendedor recebe o aviso no app e por e-mail. Marca, modelo e parte iguais com o ano do pedido dentro da faixa do item avisam, mesmo com versão diferente; outro modelo ou ano fora da faixa não; o criador nunca é avisado; no máximo 30 avisados por pedido; aviso em até 10 s p95.
- **CAP-6** Conversas (RF-19..26)
  - **intent:** Comprador e vendedor conversam por texto, em caixas Compras e Vendas, com cabeçalho do item, perfil, bloqueio e denúncia.
  - **success:** Uma conversa por par; sem envio de foto; terceiro recebe acesso negado; mensagem p95 < 1 s e gravada antes da confirmação.
- **CAP-7** Fechamento e avaliação (RF-27..31)
  - **intent:** Ao encerrar conversa com mais de 10 mensagens ou marcar venda, as partes registram Fechado ou Não fechado e avaliam o perfil, e o dono responde.
  - **success:** Modal só aparece com mais de 10 mensagens; estrelas + X = 5; uma avaliação por usuário e por conversa; uma resposta por avaliação.
- **CAP-8** Notificações (RF-32)
  - **intent:** O usuário vê, com contador no app, avisos de pedido, mensagens, respostas e pedidos de avaliação.
  - **success:** Cada evento gera notificação com contador que zera ao ler.
- **CAP-9** Moderação (RF-33..37)
  - **intent:** O administrador trata denúncias, remove conteúdo, suspende ou bloqueia contas, e o bloqueado pode contestar.
  - **success:** Denúncia percorre aberta, em análise e resolvida; contestação em até 7 dias, uma por bloqueio; acesso do admin ao CPF só em denúncia grave ou ordem judicial e gera registro de auditoria.
- **CAP-10** Painel de métricas (RF-38)
  - **intent:** O administrador mede o sucesso do produto.
  - **success:** O painel mostra % de conversas encerradas que terminaram em Fechado, nota média, pedidos criados e atendidos, e denúncias por motivo e estado, visível só ao administrador.

## Constraints

- A plataforma não processa pagamento nem entrega e não certifica origem; o sistema nunca pede nenhum dos dois. Termos de Uso afastam a responsabilidade (RN-04).
- Chat só texto; fotos vêm apenas do anúncio.
- O aviso de pedido chega ao vendedor também por e-mail, porque a v1 é desktop e web push está fora de escopo.
- Marca, modelo, versão e ano são digitados (sem FIPE); a sugestão por valores já usados evita grafias duplicadas.
- Login só com Google; sessão por JWT no cabeçalho; CPF criptografado e invisível a usuários.
- Regras de negócio atrás de uma API independente da tela, para reuso mobile; sem estado de sessão no servidor.
- Web para desktop em PT-BR, responsiva, WCAG 2.1 AA nos fluxos principais.
- Playbook do TCC: TDD com cobertura 75% servidor / 25% UI; sem SQLite/H2 nem BaaS em produção; hospedagem pública estável fora da lista "Não usar"; CI/CD; ambientes dev e prod; deploy sem SSH/FTP (addendum).
- Antes da entrega, a interface passa por teste de usabilidade com usuários reais, documentado (RNF-24).
- Dimensionada para 500 usuários cadastrados e 50 conversas simultâneas.
- Catálogo de zonas e partes da v1 cobre só carros e é mantido pela equipe (addendum).
- Escolha em cascata: a zona escolhida define a lista de partes mostrada (RF-11).

## Non-goals

- Pagamento, frete, nota fiscal, custódia, disputa; certificação de origem; preço de referência.
- Foto no chat; escolher a peça clicando na imagem do carro; reconhecimento por foto.
- Moto e caminhão; aplicativo nativo; web push; importação em lote; outro provedor de login.
- Selo de loja e aprovação prévia de vendedor; carrinho, checkout, estoque e gestão financeira.
- Peça que serve em mais de um modelo (um modelo por anúncio na v1).

## Success signal

Pelo menos metade das conversas com mais de 10 mensagens termina em Fechado, medido no painel, e os 17 critérios de aceite do PRD (seção 8) passam em demonstração no ambiente de produção público.

## Assumptions

- Metas de desempenho vêm do plano original e são confirmadas por teste.
- Os limites de 30 avisados, 7 dias de contestação e 500 usuários foram propostos pelo assistente e aprovados pelo usuário.

## Open Questions

- Nenhuma. O catálogo de Motor e Fundo foi aprovado. A stack fica para a etapa de arquitetura.

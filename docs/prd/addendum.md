# Addendum — RetroPeças

Material do usuário que pertence a UX/design e não ao corpo do PRD.

## Ideia de UI da avaliação (futuro)

- A avaliação mostra **5 anjinhos fixos**. Cada imagem é dividida em duas metades.
- Clicar na **metade direita** troca o anjinho por **meio capetinha**.
- Clicar na **metade esquerda** troca por **capetinha inteiro**.
- Para voltar ao anjinho (ou desfazer um clique errado), o usuário **desfaz os cliques em ordem inversa**.
- Cada metade vale 0,5 ponto. A nota é o total de anjinhos (0 a 5, de meio em meio ponto). O X é o que sobra: estrelas + X = 5.
- Decisão de produto: no PRD a avaliação continua descrita como estrelas e X. Este desenho substitui só a forma de exibir e de coletar.

## Catálogo de zonas e partes do veículo (carros)

Usado na busca por zona (RF-11) e no cadastro da parte (RF-06). Itens marcados com (*) foram acrescentados pelo assistente e precisam de aprovação.

- **Frente:** capô, farol, parachoque dianteiro, pisca, farol de milha, grade, esguicho, parabrisa.
- **Traseira:** porta-malas, lanterna, parachoque traseiro, parabrisa traseiro, spoiler.
- **Lateral direita e lateral esquerda** (mesma lista nas duas): porta dianteira, porta traseira, lateral, paralama, retrovisor, vidros.
- **Interior:** banco, volante, painel, acabamentos internos.
- **Motor** (*):
  - Bloco e partes internas: bloco, cabeçote, cárter, pistão e biela, virabrequim, comando de válvulas, junta.
  - Alimentação: carburador ou corpo de borboleta, bico injetor, bomba de combustível, coletor de admissão, turbina.
  - Arrefecimento: radiador, bomba d'água, ventoinha, reservatório.
  - Parte elétrica: alternador, motor de partida, bateria, bobina e velas, distribuidor, módulo de injeção.
  - Transmissão: câmbio, embreagem, correia dentada.
  - Escapamento: coletor de escape, catalisador, silencioso.
  - Ar-condicionado: compressor.
- **Fundo** (*):
  - Suspensão: amortecedor, mola, bandeja, pivô, barra estabilizadora, eixo traseiro.
  - Freios: disco, pinça, tambor, cilindro de roda, cilindro mestre, servo-freio.
  - Direção: caixa de direção, bomba hidráulica, barra de direção.
  - Transmissão: cardã, diferencial, semi-eixo.
  - Tanque de combustível.
  - Rodas e calotas.

Moto e caminhão têm zonas e partes diferentes e ficam para definir. O catálogo da v1 cobre carros.

## Restrições e entregáveis do Playbook do professor (para a arquitetura e a RFC)

Fonte: leitura do repositório do professor em 2026-10-30 (linha Web Apps e critérios gerais). Os critérios podem mudar a cada semestre. Fica aqui porque são decisões de tecnologia e de entrega, não requisitos do produto.

- **Hospedagem:** nuvem pública e estável até a divulgação final das notas. Vercel, Netlify, Firebase e Render estão em "Não usar" desde 2026-02. Plataformas de deploy automático por GitHub ficam em "Evitar". Link fora do ar é reprovação direta.
- **Proibido:** plataforma com banco, autenticação e APIs prontos (BaaS), SQLite ou H2 em produção, deploy por SSH ou FTP, no-code.
- **Processo:** Kanban é preferido para projeto individual. Scrum é "Evitar". Cascata é eliminatório. TDD obrigatório, com cobertura de 75% no servidor e 25% na interface, conferida na prova de autoria.
- **Entregáveis:** ambientes de desenvolvimento e produção separados, CI/CD, análise estática e de segurança, estratégia de observabilidade, documentação na Wiki do repositório (Notion e Obsidian não valem), diagrama C4, modelo de dados e instruções de deploy.
- **Evitar:** interface não responsiva, falta de feedback ao usuário (carregamento, erro, confirmação), tema pronto sem personalização, PHP, e-commerce e lojas virtuais (critério geral).
- **Diferenciais úteis:** OAuth2 (o login com Google já conta), JWT, proteção contra XSS e CSRF, painel de métricas, mensageria assíncrona, infraestrutura como código, domínio próprio, teste de usabilidade documentado.
- **RFC (7º período):** evidência de demanda (parceiro, pesquisa ou carta de intenção), benchmark de 3 a 5 soluções (por exemplo OLX, Mercado Livre, Webmotors, grupos de carros antigos), métricas verificáveis, stack justificada item a item, LGPD e acessibilidade.

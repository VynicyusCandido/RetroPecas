# Backend: autenticação com JWT e `X-Auth-Id`, e notificações (Plano 1 do backend)

> Complementa a [spec do produto](2026-09-29-marketplace-autopecas-design.md), o [documento de stack](2026-09-29-stack-e-tdd-design.md) e o [design das telas](2026-09-29-frontend-telas-design.md). Data: 2026-09-29. Status: **rascunho para revisão**.
> Fluxo em diagrama: [`09_sequencia_autenticacao.puml`](../../diagramas/09_sequencia_autenticacao.puml).
>
> **Atualização de 2026-09-30: o login passa a ser somente pelo Google** ([regras de segurança](../../login-com-google-regras-de-seguranca.md)). Esta spec descreve o que foi **construído** no Plano 1, com login por senha. **Continuam valendo:** a sessão (JWT em cookie HttpOnly, `X-Auth-Id`), o logout com revogação, o `/me`, as notificações, o Liquibase, a arquitetura, os segredos e a limpeza de tokens. **Saem:** o cadastro e o login por senha (seção 1, "padrões que adotei"; seção 2, passo 1; na seção 3, as linhas de enumeração de e-mails, senha de 72 bytes e cadastro duplicado; seção 4, as rotas `registro` e `login` e a validação do cadastro; seção 5, a coluna `senha_hash` e a senha da seed; seção 9, o login pelo console). O impacto completo está na seção 8 do documento de login.

## 1. O que foi decidido

| # | Decisão | Origem |
|---|---|---|
| B1 | Backend em **Spring Boot 4.1.1**, **Java 21**, Maven, PostgreSQL 18 (Docker no desenvolvimento) | Usuário (Java 21, Spring Boot, Postgres) e versões atuais verificadas |
| B2 | Sessão de login por **JWT** | Usuário |
| B3 | O JWT fica em **cookie HttpOnly, `SameSite=Lax`** | Usuário |
| B4 | Toda chamada autenticada envia o cabeçalho **`X-Auth-Id`** com o hash de identificação do usuário, e o servidor **confirma que ele pertence ao usuário da sessão** | Usuário |
| B5 | O `X-Auth-Id` é o **HMAC-SHA256 do id do usuário**, com um segredo só do servidor | Usuário |
| B6 | Migrações com **Liquibase**, changelogs em **SQL** (*formatted SQL*), no lugar do Flyway do documento de stack | Usuário |
| B7 | Escopo deste plano: esqueleto, autenticação, `/me` e notificações (os contratos que o Plano 1 do frontend consome) | Usuário |

Padrões que adotei e você pode mudar: ~~cadastro por e-mail e senha (BCrypt)~~ (**substituído pelo login com Google**, ver o aviso no topo), JWT de **1 hora sem renovação**, logout com revogação do token, e JWT assinado com **HS256** (chave simétrica, porque só este backend emite e valida).

## 2. Como a sessão funciona

1. **Login** (`POST /api/v1/auth/login`): confere e-mail e senha. Responde `200` com o corpo da sessão (que inclui o `authId`) e o cabeçalho `Set-Cookie: access_token=<jwt>; HttpOnly; SameSite=Lax; Path=/; Max-Age=3600` (com `Secure` em produção).
2. **Abrir a página** (`GET /api/v1/me`): só o cookie autentica. A resposta traz o `authId`, que o frontend guarda **em memória** (não em `localStorage`).
3. **Chamadas autenticadas:** o navegador manda o cookie sozinho e o frontend acrescenta `X-Auth-Id: <authId>`. O servidor valida o JWT (assinatura, emissor, validade e revogação), recalcula o HMAC a partir do `sub` do JWT e compara **em tempo constante** com o cabeçalho. Sem o cabeçalho, ou com um valor que não confere, responde `401`.
4. **Logout** (`POST /api/v1/auth/logout`): grava o `jti` do token numa lista de revogados até o vencimento natural e apaga o cookie. O mesmo token deixa de valer na hora.

`GET /api/v1/me` é a **única** rota autenticada que dispensa o `X-Auth-Id`, porque é por ela que o frontend descobre o valor.

O `X-Auth-Id` **não é um segredo do usuário**: um XSS na página consegue lê-lo da memória, como consegue agir em nome do usuário de qualquer jeito. Ele existe para (a) confirmar que o usuário do cookie é o mesmo que o frontend acredita ser e (b) barrar CSRF, porque um cabeçalho personalizado não sai de um formulário de outro site.

## 3. Modelo de ameaças

Cada linha aponta para o teste que a prova (todos na suíte do backend).

| Ameaça | Defesa | Teste |
|---|---|---|
| XSS rouba o token | Cookie `HttpOnly`: o JavaScript não lê o JWT | `LoginTest.oCookieDeSessaoEHttpOnlySameSiteLaxEValeUmaHora` |
| CSRF (outro site dispara `POST`/`PATCH` com o cookie) | `SameSite=Lax` e `X-Auth-Id` obrigatório, que outro site não consegue enviar. O CSRF do Spring fica **desligado de propósito**, e o backend **não habilita CORS** (outra origem nunca recebe `Access-Control-Allow-Origin`) | `CadeiaDeSegurancaTest.metodosQueAlteramEstadoTambemExigemOAuthId`, `CadeiaDeSegurancaTest.oBackendNaoHabilitaCorsParaOutrasOrigens`, `LogoutTest.semOAuthIdOLogoutNaoAcontece` |
| Token interceptado na rede | Cookie `Secure` em produção: o padrão do `application.properties` é `true` e o padrão do código também (`@DefaultValue("true")`), então esquecer a propriedade não desliga o `Secure` | `ConfiguracaoDeProducaoTest.oCookieDeSessaoESecurePorPadrao`, `SegurancaPropertiesTest.semAPropriedadeOCookieSeguroVemLigado`, `LoginCookieSeguroTest.oCookieDeSessaoVemMarcadoComoSecure` |
| JWT forjado | HS256 com segredo de pelo menos 32 bytes. Recusa assinatura errada, emissor errado, token expirado, token sem `jti`, `alg: none` e outro algoritmo (HS512) mesmo com o segredo certo | `CadeiaDeSegurancaTest` (`tokenAssinadoComOutroSegredoENegado`, `tokenDeOutroEmissorENegado`, `tokenExpiradoENegadoMesmoComAssinaturaValida`, `tokenSemJtiENegado`, `tokenSemAssinaturaComAlgNoneENegado`, `tokenAssinadoComOutroAlgoritmoENegadoMesmoComOSegredoCerto`), `SegurancaPropertiesTest.recusaOSegredoDoJwtComMenosDe32Bytes` |
| `X-Auth-Id` forjado | HMAC com um segredo **diferente** do JWT: sem o segredo não dá para calcular. A aplicação não sobe se os dois segredos forem iguais | `CalculadoraDeAuthIdHmacTest` (`calculaOHmacSha256EmHexadecimalDoIdDoUsuario`, `confereRecusaOHashCalculadoComOutroSegredo`), `CadeiaDeSegurancaTest.cookieValidoComOAuthIdInventadoENegado`, `SegurancaPropertiesTest.recusaOMesmoSegredoNoJwtENoAuthId`, `ConferenciaDosSegredosTest.aAplicacaoNaoSobeComOMesmoSegredoNoJwtENoAuthId`, `ConferenciaDosSegredosNaAplicacaoTest` |
| Usar o cookie de um usuário com o `X-Auth-Id` de outro | O HMAC é conferido contra o `sub` do JWT | `CadeiaDeSegurancaTest.cookieValidoComOAuthIdDeOutroUsuarioENegado` |
| Cabeçalho `Authorization: Bearer` usado para contornar o cookie | O token só é lido do cookie | `CadeiaDeSegurancaTest.oCabecalhoAuthorizationBearerEIgnorado` |
| Token reaproveitado depois do logout | Revogação do `jti` na lista `token_revogado` | `LogoutTest.depoisDoLogoutOMesmoTokenNaoAbreMaisNada` |
| Logout forçado por outro site | O logout também exige o `X-Auth-Id` | `LogoutTest.semOAuthIdOLogoutNaoAcontece` |
| Descobrir quais e-mails têm conta | **Só o login não vaza:** senha errada e e-mail inexistente dão a mesma resposta, e um hash falso é conferido sempre, para o tempo de resposta não denunciar. **O cadastro vaza:** `POST /registro` responde `409` para e-mail já cadastrado (é o contrato), então dá para enumerar e-mails por ele até haver limite de tentativas e verificação de e-mail (plano seguinte) | `LoginTest.senhaErradaEEmailInexistenteDaoARespostaIdentica`, `AutenticacaoServiceTest` (`emailInexistenteConfereUmHashFalsoExatamenteUmaVez`, `senhaErradaComEmailExistenteTambemConfereUmaVez`, `emailInexistenteESenhaErradaDaoExatamenteAMesmaResposta`); o `409` do cadastro: `RegistroTest.emailJaCadastradoDa409` |
| Comparação do hash revelando caracteres por tempo | `MessageDigest.isEqual` (tempo constante) | `CalculadoraDeAuthIdHmacTest` |
| Senha com mais de 72 bytes (limite do BCrypt) virando erro 500 ou senha truncada | Registro responde `400` e login responde `401`, e a senha longa nem chega ao BCrypt. A conta em UTF-8 conta bytes, não caracteres | `RegistroTest.aSenhaDeExatamente72BytesEAceita`, `RegistroTest.aSenhaComMaisDe72BytesDa400EmVezDeErroInterno`, `LoginTest.senhaComMaisDe72BytesDa401EmVezDeErroInterno`, `AutenticacaoServiceTest.senhaComMaisDe72BytesNaoChegaAoBcrypt` |
| Cookie antigo ou inválido impedindo um novo login | Nas rotas públicas de entrada o cookie é ignorado | `LoginTest.umCookieAntigoOuInvalidoNaoImpedeOLogin`, `LoginTest.umCookieInvalidoTambemNaoImpedeOCadastro` |
| Cadastro duplicado por corrida | Índice único em `lower(email)` e o erro do banco vira `409`. O pgjdbc não põe o DETAIL do servidor (com o e-mail) na exceção (`logServerErrorDetail=false`), então o e-mail também não vai para o log (RNF-PRI-04) | `RegistroTest.duasRequisicoesSimultaneasComOMesmoEmailNuncaDao500`, `RegistroTest.aViolacaoDoEmailUnicoNaoEscreveOEmailNoLog` |
| Segredo ausente ou fraco em produção | Não há valor padrão (o `application.properties` só lê `${JWT_SEGREDO}` e `${AUTH_ID_SEGREDO}`), e a aplicação **não sobe** com menos de 32 bytes ou fora do base64 | `ConfiguracaoDeProducaoTest` (`oSegredoDoJwtVemSoDaVariavelDeAmbienteSemValorPadrao`, `oSegredoDoAuthIdVemSoDaVariavelDeAmbienteSemValorPadrao`), `SegurancaPropertiesTest` (`recusaOSegredoDoJwtComMenosDe32Bytes`, `recusaOSegredoDoAuthIdComMenosDe32Bytes`, `recusaSegredoQueNaoEstaEmBase64`, `aAplicacaoNaoCriaOEncoderDeJwtComSegredoCurto`) |
| Seed de desenvolvimento chegando à produção | O contexto padrão do Liquibase é `producao`. A seed só roda em `dev` | `ConfiguracaoDeProducaoTest.oLiquibaseSoRodaOsChangesetsDeProducaoPorPadrao` (o padrão do arquivo de produção), `InicializacaoTest.oLiquibaseAplicouOsChangelogsSemASeedDeDesenvolvimento` (fora do `dev` a seed não roda), `SeedDeDesenvolvimentoTest` (no `dev` ela roda) |
| Ler a notificação de outro usuário | A consulta filtra por usuário, e o `PATCH` de uma notificação alheia responde `404` | `NotificacaoTest.cadaUsuarioSoVeAsProprias`, `notificacaoDeOutroUsuarioResponde404ENaoMudaNada` |
| Vazar detalhes internos nos erros | Toda falha sai como Problem Details com texto fixo em português, sem *stack trace* | `CadeiaDeSegurancaTest.umErroInesperadoResponde500SemVazarDetalhesInternos`, `CadeiaDeSegurancaTest.rotaInexistenteAutenticadaResponde404EmPortugues`, `CadeiaDeSegurancaTest.metodoNaoPermitidoResponde405EmPortugues` |

**Não coberto neste plano (riscos assumidos):**
- **Força bruta no login:** falta limite de tentativas (RNF-SEG-05). Entra no próximo plano de backend, com Bucket4j.
- **XSS na página:** um XSS age em nome do usuário e lê o `authId` da memória. A defesa é uma *Content Security Policy* e o escape de conteúdo no frontend, fora deste plano.
- **Renovação do token:** o JWT vale 1 hora e depois é preciso entrar de novo. Um *refresh token* com rotação é um plano à parte.
- **Verificação de e-mail e recuperação de senha** (AUT-01, AUT-02).
- **Rotação dos segredos:** trocar `JWT_SEGREDO` derruba todas as sessões, e trocar `AUTH_ID_SEGREDO` invalida os `authId` guardados no navegador (o `/me` devolve o novo).

## 4. Contratos da API

Erros no formato **Problem Details** (RFC 9457): `{ "type": "about:blank", "title": "<texto em português>", "status": 400 }`. Erros de validação trazem também `"erros": { "<campo>": "<mensagem>" }`.

| Endpoint | Autenticação | Resposta |
|---|---|---|
| `POST /api/v1/auth/registro` `{ nome, email, senha }` | pública | `201 { id, nome, email }`. `400` dados inválidos. `409` e-mail já cadastrado |
| `POST /api/v1/auth/login` `{ email, senha }` | pública | `200 { id, nome, email, vendedor, authId }` + `Set-Cookie`. `400` campos em branco. `401` "E-mail ou senha inválidos." |
| `POST /api/v1/auth/logout` | cookie + `X-Auth-Id` | `204` + `Set-Cookie` que apaga o cookie |
| `GET /api/v1/me` | só o cookie | `200 { id, nome, email, vendedor, authId }`. `vendedor` é `null` ou `{ "status": "PENDENTE\|APROVADO\|REPROVADO\|SUSPENSO" }` |
| `GET /api/v1/notificacoes?tipos=CHAT,PROPOSTA&limite=20` | cookie + `X-Auth-Id` | `200 { itens: [{ id, tipo, titulo, resumo, criadaEm, lida }], naoLidas: { chat, outras, pecasAProcura } }`. Os contadores valem para **todos** os tipos. `limite` de 1 a 100 (padrão 20) |
| `PATCH /api/v1/notificacoes/{id}` `{ "lida": true }` | cookie + `X-Auth-Id` | `204`. `404` se não existe ou é de outro usuário. `400` corpo ou id inválido |
| `GET /actuator/health` | pública | `200 { "status": "UP" }` |

Regras de validação do cadastro: nome de 1 a 120 caracteres, e-mail válido de até 254 (no cadastro, o mesmo padrão do `z.email()` do Zod 4 que o frontend usa, para o backend nunca aceitar um e-mail que quebra o `/me` no cliente), senha de **8 a 72 bytes** em UTF-8. Espaços nas pontas do nome e do e-mail são removidos, e o e-mail é guardado em minúsculas.

## 5. Dados (Liquibase, SQL)

Os changelogs ficam em `db/changelog/changes/`, um arquivo `NNN-descricao.sql` por mudança, no formato `--liquibase formatted sql`, com `--rollback`. O arquivo mestre `db.changelog-master.yaml` só inclui a pasta (`includeAll`).

| Tabela | Colunas principais | Regras no banco |
|---|---|---|
| `usuario` | `id` UUID, `email`, `senha_hash`, `nome`, `criado_em` | Índice único em `lower(email)` |
| `perfil_vendedor` | `usuario_id` UUID, `status`, `criado_em` | `status` em (`PENDENTE`, `APROVADO`, `REPROVADO`, `SUSPENSO`). Some com o usuário (`ON DELETE CASCADE`) |
| `token_revogado` | `jti` UUID, `expira_em` | Índice em `expira_em`. Limpeza agendada a cada hora |
| `notificacao` | `id` UUID, `usuario_id`, `tipo`, `titulo`, `resumo`, `criada_em`, `lida` | `tipo` em (`CHAT`, `PROPOSTA`, `PECA_A_PROCURA`, `SISTEMA`). Índices por usuário e data e um parcial para não lidas. Some com o usuário |

**Contextos do Liquibase:** `producao` é o padrão do `application.properties`, `dev` no perfil de desenvolvimento (com a **seed**: 3 usuários e 8 notificações, senha `senha-dev-123`) e `teste` nos testes. Sem contexto explícito o Liquibase roda **todos** os changesets, inclusive a seed, e por isso o padrão é `producao`. O Hibernate só confere o esquema (`ddl-auto=validate`).

## 6. Arquitetura

Segue o documento de stack (seção 3.1): módulos por funcionalidade, com camadas dentro de cada um, verificados por `ApplicationModules.verify()` (Spring Modulith) e por regras do ArchUnit.

```
br.com.autopecas
├─ compartilhado/      # ErroDeNegocio, TratadorDeErros (Problem Details), Identidade
├─ conta/internal/
│  ├─ domain/          # Usuario, PerfilVendedor, StatusVendedor, TokenRevogado
│  ├─ application/     # Registro, Autenticacao, Sessao, Logout, LimpezaDeTokensRevogados + portas (EmissorDeToken, CalculadoraDeAuthId)
│  ├─ web/             # AuthController, MeController e seus DTOs
│  └─ infra/
│     ├─ persistencia/ # repositórios Spring Data
│     └─ seguranca/    # cadeia de segurança, filtro X-Auth-Id, emissor de JWT, HMAC, cookie
└─ notificacao/internal/{domain,application,web,infra/persistencia}
```

Regras verificadas: o `domain` não depende de outras camadas nem do Spring, a `web` não acessa repositórios, a `application` não depende da `web`, e os módulos só se falam pela API pública.

## 7. Configuração e segredos

| Propriedade | Como |
|---|---|
| `app.seguranca.jwt-segredo` | Variável `JWT_SEGREDO`: base64 com **pelo menos 32 bytes** (`openssl rand -base64 32`). **Sem valor padrão** |
| `app.seguranca.auth-id-segredo` | Variável `AUTH_ID_SEGREDO`, também base64 com 32 bytes ou mais, **diferente** do anterior (a aplicação não sobe se os bytes forem iguais). Sem valor padrão |
| `app.seguranca.token-validade` | `PT1H` |
| `app.seguranca.cookie-seguro` | `true` (o perfil `dev` e os testes usam `false`, porque rodam em HTTP) |
| Banco | `spring.datasource.url`, `username` e `password` (o perfil `dev` aponta para o `docker-compose`) |

Os segredos dos perfis `dev` e `test` estão no repositório de propósito e **não servem para produção**.

## 8. Validação feita antes do plano

O backend inteiro foi construído num projeto descartável, e a suíte tinha **128 testes verdes** (no backend real, depois das revisões por tarefa, a última execução completa deu **158**, e uma rodada final de correções acrescentou mais testes). Também foi executado de verdade, com o PostgreSQL 18 do `docker compose` e o perfil `dev`, conferindo por `curl`: login com cookie `HttpOnly; SameSite=Lax`, `/me` só com o cookie, `401` nas notificações sem `X-Auth-Id`, `200` com ele, `PATCH` atualizando os contadores e o token morto depois do logout. O plano reproduz esse código, e os testes do plano são os mesmos.

Descobertas que o plano já incorpora:
- O Boot 4 renomeou os starters (`spring-boot-starter-webmvc`, `spring-boot-starter-security-oauth2-resource-server`, `spring-boot-starter-liquibase`) e mudou o pacote do `@AutoConfigureMockMvc` para `org.springframework.boot.webmvc.test.autoconfigure`. O `PostgreSQLContainer` agora é `org.testcontainers.postgresql.PostgreSQLContainer`.
- A imagem `postgres:18` guarda os dados em `/var/lib/postgresql` (não em `.../data`).
- `Jwt.getIssuer()` converte o `iss` em URL, e o nosso emissor (`autopecas`) não é uma URL. O código lê a claim como texto.
- O filtro do `X-Auth-Id` **não** pode ser um `@Component`, ou o Boot o registra também fora da cadeia de segurança.
- Um e-mail com espaço nas pontas era recusado pela validação, então o `RegistroRequest` apara `nome` e `email` antes de validar.
- O Zod 4 do frontend aceita os microssegundos que o PostgreSQL devolve (`...14.595676Z`).

## 9. Impacto no plano do frontend

O Plano 1 do frontend foi escrito para cookie com CSRF. Ele passa a usar `X-Auth-Id`:
- `api/cliente.ts` envia `X-Auth-Id` em toda chamada quando o valor está definido (`definirAuthId`, `limparAuthId`), em vez do token CSRF.
- `GET /api/v1/me` passa a trazer `authId`, e `services/sessao.ts` o entrega ao cliente ao carregar a sessão.
- O servidor de mock passa a exigir o cabeçalho, como o backend real.
- Ainda **não existe tela de login**. Enquanto o login é por senha (estado atual do código), dá para experimentar o frontend contra o backend real fazendo o login pelo console do navegador, em `http://localhost:3000` (o `Set-Cookie` passa pelo proxy do Next): `fetch('/api/v1/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email: 'vynicyus@example.com', senha: 'senha-dev-123' }) })`. **Isso sai com o login com Google:** a tela `/entrar` (botão "Entrar com Google") entra no [design das telas](2026-09-29-frontend-telas-design.md) e vira um plano à parte.

## 10. Referências

- Versões verificadas em 2026-09-29: Spring Boot 4.1.1 (versão estável padrão do Spring Initializr), Spring Modulith 2.1.1, ArchUnit 1.5.1, PostgreSQL 18, Liquibase 5.x (gerenciado pelo Boot).
- [Spring Modulith 2.1.1 (blog do Spring)](https://spring.io/blog/2026/08/26/spring-modulith-2-2-m1-2-1-1-2-0-8-and-1-4-13-released/)

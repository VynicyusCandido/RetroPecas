# Login com Google: regras de segurança

> Data: 2026-09-30. Status: **rascunho para revisão**. Nenhum código foi escrito a partir deste documento.
> Complementa a [especificação do projeto](especificacao-do-projeto.md) e a [spec do backend](superpowers/specs/2026-09-29-backend-autenticacao-design.md). Fluxo em diagrama: [`09_sequencia_autenticacao.puml`](diagramas/09_sequencia_autenticacao.puml).
> Os IDs AUT, ADM, RNF e RN vêm da [spec do produto](superpowers/specs/2026-09-29-marketplace-autopecas-design.md). As regras deste documento são `RL-nn` (Regra de Login).

## 1. O que este documento decide

O usuário pediu que o login seja **pelo Google, identificando a pessoa pelo e-mail, com regras de segurança**. Este documento transforma esse pedido em decisões, regras e testes a escrever.

| # | Decisão | Origem |
|---|---|---|
| LG-01 | O login é **somente por Google** (OpenID Connect). O sistema **não tem senha própria**, nem cadastro por e-mail e senha, nem recuperação de senha | Usuário |
| LG-02 | A pessoa é identificada pelo e-mail **verificado** que o Google informa, mas a identidade guardada no banco é o **`sub`** do Google, e não o e-mail (o `sub` nunca muda nem é reaproveitado, e o e-mail pode mudar) | Usuário (e-mail) e recomendação do Google (`sub`) |
| LG-03 | Fluxo **Authorization Code com PKCE (S256)**, conduzido pelo **backend**, que é um cliente confidencial (guarda o `client_secret`). Nenhum token do Google chega ao navegador | Proposta |
| LG-04 | Escopos mínimos: `openid`, `email` e `profile`. Sem `access_type=offline` e sem chamar APIs do Google. **Nenhum token do Google é guardado**: o ID token é validado e descartado | Proposta |
| LG-05 | Depois de confirmar quem é, o sistema abre a **sessão própria** já construída: JWT HS256 em cookie HttpOnly, `SameSite=Lax`, mais o cabeçalho `X-Auth-Id`. O Google só prova a identidade, e não participa das chamadas seguintes | Decisão anterior do usuário (sessão) |
| LG-06 | O endereço de retorno do Google fica no **mesmo site do frontend** (`/api/v1/auth/google/retorno`, pelo proxy do Next.js), para o cookie ser gravado no domínio do aplicativo | Proposta |
| LG-07 | O primeiro login **cria a conta** de comprador. Virar vendedor é um passo à parte, com **aprovação do administrador** (AUT-04, AUT-05) | AUT-03 a AUT-05 |
| LG-08 | Em produção o emissor aceito é **fixo** (`https://accounts.google.com`). Só dev e testes podem apontar para um provedor OIDC de mentira, e só por perfil | Proposta |

**Consequência para o que já existe:** o cadastro e o login por senha do Plano 1 do backend (`/api/v1/auth/registro` e `/api/v1/auth/login`) saem. Tudo o que vem depois do login (emissão do JWT, `X-Auth-Id`, `/me`, logout com revogação, notificações) **continua valendo**. A seção 8 lista o impacto.

### Alternativas consideradas

| Opção | Prós | Contras | Veredito |
|---|---|---|---|
| **A. Código de autorização no backend** (redirecionamento para o Google e retorno no backend) | O `client_secret` e todos os tokens ficam no servidor. Um único lugar valida o ID token. Funciona sem JavaScript de terceiros na página | Mais peças (estado do login, endereço de retorno) | **Escolhida** |
| B. *Sign in with Google* no navegador (botão ou One Tap) e o ID token enviado ao backend | Menos redirecionamentos, experiência mais suave | O ID token passa pelo JavaScript da página (exposto a XSS). Exige proteger o endpoint que recebe o token contra CSRF e conferir `nonce` e `aud` do mesmo jeito | Fica como melhoria futura |
| C. Serviço pronto (Firebase Auth, Supabase Auth, Auth0) | Menos código | Dependência de outro fornecedor, e a sessão passaria a ser dele, contra a decisão do JWT próprio com `X-Auth-Id` | Rejeitada |

**Como implementar a opção A (recomendação para o plano):** usar o **cliente OAuth2 do Spring Security** (`oauth2Login`, com OIDC, PKCE e validação do ID token) em vez de escrever à mão a validação do token. Um `AuthenticationSuccessHandler` próprio emite o JWT, o cookie e o redirecionamento. O estado da autorização fica em um cookie (regra RL-05), e não na sessão do servidor, para a API continuar sem estado (RNF-ESC-02). A escolha final é feita no plano de implementação.

## 2. Fluxo

```
Usuário         Frontend              API (nosso backend)                  Google
  │ clica em        │                        │                                │
  │ "Entrar com     │                        │                                │
  │  Google"        │                        │                                │
  ├────────────────►│ GET /api/v1/auth/google/entrar?retorno=/comprador       │
  │                 ├───────────────────────►│ valida "retorno" (RL-07)        │
  │                 │                        │ gera state, nonce, code_verifier│
  │                 │ 302 ao Google          │ Set-Cookie: login_state (RL-05) │
  │                 │◄───────────────────────┤                                │
  │                 ├────────────────────────────────────────────────────────►│ autentica e pede
  │◄──────────────────────────────────────────────────────────────────────────┤ consentimento
  │ escolhe a conta │                        │                                │
  ├───────────────────────────────────────────────────────────────────────────►│
  │                 │ 302 GET /api/v1/auth/google/retorno?code=…&state=…      │
  │                 ├───────────────────────►│ lê e APAGA login_state          │
  │                 │                        │ confere state (RL-03)           │
  │                 │                        │ POST /token (code + verifier +  │
  │                 │                        │   client_secret), servidor→servidor
  │                 │                        ├───────────────────────────────►│
  │                 │                        │◄─────────── ID token ──────────┤
  │                 │                        │ valida o ID token (RL-11 a 17)  │
  │                 │                        │ aplica as regras de conta (RL-18 a 25)
  │                 │ 302 ao "retorno"       │ emite JWT novo (jti novo)       │
  │                 │ Set-Cookie: access_token (HttpOnly, SameSite=Lax)        │
  │                 │◄───────────────────────┤                                │
  │                 │ GET /api/v1/me (cookie) │                                │
  │                 ├───────────────────────►│ devolve o authId               │
  │                 │ guarda o authId em memória                              │
```

Cancelamento pelo usuário, falha ou recusa em qualquer passo termina em `302` para `/entrar?erro=<codigo>` (regra RL-31), sempre apagando o `login_state`.

## 3. Regras de segurança

### 3.1 Fluxo OAuth e OpenID Connect

| ID | Regra | Por quê |
|---|---|---|
| RL-01 | Só o fluxo **Authorization Code**. O fluxo implícito não existe no sistema, e nenhum token do Google passa pelo navegador | Tokens na URL ou no JavaScript vazam por histórico, *referrer* e XSS |
| RL-02 | **PKCE com `S256`** em toda autorização, mesmo com `client_secret` (defesa em profundidade, como recomenda o RFC 9700). O `code_verifier` tem entre 43 e 128 caracteres de fonte criptograficamente segura e fica **só** no cookie de estado | Um código de autorização interceptado não serve sem o verificador |
| RL-03 | `state` aleatório (pelo menos 128 bits, `SecureRandom`), novo a cada tentativa, **comparado em tempo constante** no retorno. Sem `state` válido, o retorno é recusado **antes** de qualquer chamada ao Google | Barra o *login CSRF* (forçar a vítima a entrar na conta do atacante). O Google recomenda o `state` para isso |
| RL-04 | `nonce` aleatório (pelo menos 128 bits), novo a cada tentativa. O ID token precisa trazer o mesmo `nonce` | Protege contra repetição de um ID token. O Google exige o parâmetro |
| RL-05 | O estado do login (`state`, `nonce`, `code_verifier`, `retorno`, instante) vive no cookie **`login_state`**: `HttpOnly`, `SameSite=Lax` (precisa viajar na volta do Google, que é uma navegação de topo por `GET`), `Path=/api/v1/auth/google`, `Max-Age` de **10 minutos**, `Secure` em produção, conteúdo **cifrado e autenticado** (AES-GCM) com um segredo próprio (`LOGIN_STATE_SEGREDO`). É **apagado no retorno**, dê certo ou não. Não há sessão de servidor | Mantém a API sem estado (RNF-ESC-02), e o navegador não lê nem altera o conteúdo |
| RL-06 | O `redirect_uri` é **fixo por ambiente**, lido da configuração, nunca de um parâmetro da requisição. No Console do Google ficam só as URIs exatas (sem curingas). Em produção só `https`. `http://localhost` só em dev | O Google compara a URI caractere a caractere (esquema, caixa e barra final) e recusa curingas, fragmentos e HTTP (este, exceto em `localhost`) |
| RL-07 | O parâmetro `retorno` (para onde voltar) só aceita **caminho interno** da lista de rotas do frontend (`/comprador`, `/vendedor`, `/configuracoes`...). Qualquer outra coisa (URL absoluta, `//host`, `/\host`, esquema como `javascript:`) cai em `/comprador` | Evita *open redirect* |
| RL-08 | O `client_id` é público. O **`client_secret` só existe em variável de ambiente** (`GOOGLE_CLIENT_SECRET`): nunca no repositório, nunca em log, nunca no frontend. O cliente OAuth de desenvolvimento é **diferente** do de produção | O Google manda guardar o arquivo de segredos fora do código-fonte |
| RL-09 | A troca do `code` no endpoint de token é **servidor a servidor**, por HTTPS, com verificação de certificado, tempo limite curto (conexão 2 s, leitura 5 s) e **sem nova tentativa com o mesmo `code`** (o código vale uma vez). Falha vira erro genérico | Evita travar a thread e reenviar códigos queimados |
| RL-10 | O endereço de retorno aceita só `GET` com `code` e `state` (ou `error` e `state`) e ignora qualquer outro parâmetro. O `code` nunca é registrado em log | Reduz a superfície e o vazamento |

### 3.2 Validação do ID token

O Google manda conferir assinatura, emissor, público e validade, e recomenda validar **localmente** com as chaves públicas dele, em vez de chamar o `tokeninfo`, em produção.

| ID | Regra |
|---|---|
| RL-11 | **Assinatura** conferida com as chaves do `jwks_uri` do documento de descoberta do Google, com *cache* que respeita o cabeçalho de cache da resposta e recarga quando aparece um `kid` desconhecido (rotação de chaves), no máximo uma recarga por minuto. **Só RS256** é aceito: `alg: none` e HS256 (com o `client_secret` como chave) são recusados |
| RL-12 | `iss` é `https://accounts.google.com` ou `accounts.google.com`. Em produção o emissor é uma **constante** no código (não é configurável) |
| RL-13 | `aud` contém o `client_id` do sistema. Se houver mais de uma audiência, `azp` precisa ser o `client_id` |
| RL-14 | `exp` no futuro, e `iat` não pode estar no futuro além de 60 s de tolerância de relógio |
| RL-15 | `nonce` do token igual ao `nonce` guardado no cookie de estado |
| RL-16 | `sub` presente e não vazio. `email` presente e **`email_verified` igual a `true`**. Caso contrário, o login é recusado com o código `email-nao-verificado` |
| RL-17 | O ID token é usado **uma vez** (para abrir a sessão) e descartado. Não é gravado em banco, cookie ou log |

### 3.3 Conta e identidade

| ID | Regra |
|---|---|
| RL-18 | A identidade é o **`sub`**, nunca o e-mail. Coluna `google_sub` **única** |
| RL-19 | Primeiro login (`sub` desconhecido): cria o usuário com nome, e-mail e `google_sub`, como comprador (AUT-03). A criação é segura sob concorrência: o índice único em `google_sub` decide, e quem perde a corrida **relê** o usuário e segue, sem erro |
| RL-20 | Se o e-mail do Google mudou desde o último login (mesmo `sub`), o sistema **atualiza** o e-mail e o nome guardados (o novo e-mail precisa estar verificado). Se o **novo e-mail já pertence a outro usuário**, o login é **recusado** (`conflito-de-email`) e o evento é registrado para o administrador |
| RL-21 | Um `sub` **novo** com e-mail que já está guardado para **outro** `sub` também é recusado (`conflito-de-email`). **Nunca há fusão nem vinculação automática de contas por e-mail.** Se um dia houver outro provedor de login, vincular exigirá estar logado na conta existente e confirmar o segundo provedor |
| RL-22 | Usuário **suspenso ou banido** não abre sessão. A verificação é no retorno, depois de validar o ID token, e a mensagem é neutra (`conta-suspensa`). Usuário **excluído** (AUT-06) que voltar com o mesmo `sub` recebe uma conta **nova**, sem histórico |
| RL-23 | Entrar **não dá poder de vendedor.** O perfil de vendedor exige cadastro e aprovação do administrador (AUT-04, AUT-05). Nenhuma informação do Google (nem o `hd`, domínio do Google Workspace) concede papel |
| RL-24 | O **aceite dos Termos de uso e da política de privacidade** (ADM-04) é exigido no **primeiro login**, com versão e instante registrados. Sem aceite, a sessão só libera a tela de aceite e o logout. O `/me` informa `termosPendentes` |
| RL-25 | **Dados mínimos** (RNF-PRI-01): guarda-se só `sub`, e-mail e nome. A foto (`picture`) não é guardada. Nenhum token do Google é gravado |

### 3.4 Sessão

| ID | Regra |
|---|---|
| RL-26 | A sessão é a já definida: JWT HS256 de **1 hora** em `access_token` (`HttpOnly; SameSite=Lax; Path=/`, `Secure` em produção), e `X-Auth-Id` (HMAC-SHA256 do id do usuário) exigido em toda chamada autenticada, **exceto `GET /api/v1/me`**. Cada login emite um `jti` **novo** |
| RL-27 | O sistema **não renova** o token (sem *refresh token* no MVP). Ao expirar, o frontend leva a `/entrar`. O pedido ao Google usa `prompt=select_account`, que mostra o seletor de conta: é um passo a mais, mas evita entrar sem querer na conta errada em computador compartilhado |
| RL-28 | **Logout** revoga o `jti` e apaga o cookie (como já construído). Ele **não** encerra a sessão da pessoa no Google, e a tela de saída diz isso |
| RL-29 | Ações sensíveis (excluir a conta, AUT-06) exigem **login recente**: sessão emitida há no máximo 5 minutos. Senão, o sistema pede um novo login com o Google |

### 3.5 Abuso, erros e registros

| ID | Regra |
|---|---|
| RL-30 | **Limite de taxa por IP** no início do login e no retorno (Bucket4j, RNF-SEG-05), por exemplo 10 por minuto. Acima disso, `429` |
| RL-31 | Erros do fluxo **nunca** mostram detalhe técnico nem a mensagem do Google. O usuário vê um texto fixo por código curto em `/entrar?erro=<codigo>`: `cancelado`, `estado-invalido`, `email-nao-verificado`, `conta-suspensa`, `conflito-de-email`, `limite`, `falha`. O detalhe fica no log do servidor |
| RL-32 | **Logs sem segredos nem dados pessoais** (RNF-PRI-04): nunca registrar `code`, `state`, `nonce`, `code_verifier`, ID token, `client_secret`, cookies, e-mail ou nome. Registrar só o evento, o resultado, o código do motivo, o id do usuário (UUID) e um identificador de correlação |
| RL-33 | **Eventos de segurança** vão para auditoria (RNF-SEG-07): login aceito, login recusado (com o código), conta criada, conflito de e-mail, limite de taxa, conta suspensa tentando entrar. Sem dados pessoais |
| RL-34 | As respostas das rotas do login levam `Cache-Control: no-store` e `Referrer-Policy: no-referrer`. O **CORS continua desligado** de propósito |
| RL-35 | A tela `/entrar` não carrega *script* de terceiros e não pode ser colocada em `iframe` (CSP com `frame-ancestors 'none'`, definida no plano do frontend) |

### 3.6 Configuração e ambientes

| ID | Regra |
|---|---|
| RL-36 | **Um cliente OAuth por ambiente** no Google Cloud (desenvolvimento, demonstração e produção), cada um com as suas URIs de retorno e o seu segredo. Um segredo nunca serve para dois ambientes |
| RL-37 | A aplicação **não sobe** em produção sem `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI` e `LOGIN_STATE_SEGREDO` válidos, **sem valor padrão**. O `LOGIN_STATE_SEGREDO` (base64, pelo menos 32 bytes) precisa ser **diferente** do `JWT_SEGREDO` e do `AUTH_ID_SEGREDO`, e a aplicação confere isso na subida |
| RL-38 | Em dev e nos testes o provedor pode ser trocado por um servidor OIDC de mentira (por exemplo *mock-oauth2-server* ou WireMock), **só por perfil**. O arquivo de produção não aceita outro emissor, e um teste garante que o padrão de produção é o Google |
| RL-39 | Tela de consentimento do Google: tipo de usuário **Externo**, com só os escopos `openid`, `email` e `profile`. Enquanto o aplicativo estiver em modo de teste, só entram as contas cadastradas como usuários de teste (serve para a demonstração do TCC). Para abrir ao público, o aplicativo precisa ser publicado. A tela "app não verificado" pode aparecer até o Google verificar o aplicativo. **Confirmar no Console** antes da demonstração |

## 4. Modelo de ameaças e testes a escrever

Seguindo o TDD do projeto, cada linha vira um teste **antes** do código. Os testes de protocolo usam um provedor OIDC de mentira (RL-38), e os de unidade cobrem a validação do ID token.

| Ameaça | Defesa | Teste a escrever |
|---|---|---|
| *Login CSRF*: a vítima cai num retorno forjado com o código do atacante | `state` amarrado ao cookie de estado do próprio navegador (RL-03, RL-05) | `retornoSemCookieDeEstadoERecusado`, `retornoComStateDiferenteERecusado` |
| Repetição do retorno (mesmo `code` e `state`) | Cookie apagado no primeiro retorno, código de uso único (RL-05, RL-09) | `retornoReutilizadoERecusado` |
| Interceptação do código de autorização | PKCE `S256` (RL-02) | `aAutorizacaoSaiComCodeChallengeS256`, `aTrocaDoCodigoEnviaOCodeVerifier` |
| ID token emitido para **outro** aplicativo | Conferir `aud` e `azp` (RL-13) | `idTokenDeOutroClientIdERecusado` |
| Emissor falso | Conferir `iss` (RL-12) | `idTokenDeOutroEmissorERecusado` |
| Token forjado: sem assinatura, HS256 com o segredo do cliente, assinatura errada | Só RS256 com as chaves do Google (RL-11) | `idTokenComAlgNoneERecusado`, `idTokenHs256ERecusado`, `idTokenComAssinaturaInvalidaERecusado` |
| Repetição de um ID token | `nonce` (RL-04, RL-15) | `idTokenComNonceDiferenteERecusado` |
| Token expirado | `exp` (RL-14) | `idTokenExpiradoERecusado` |
| Rotação das chaves do Google | Recarga do JWKS em `kid` desconhecido, no máximo 1 por minuto (RL-11) | `kidDesconhecidoRecarregaAsChavesUmaVez`, `kidDesconhecidoNaoRecarregaMaisQueUmaVezPorMinuto` |
| E-mail não verificado ou ausente | `email_verified` (RL-16) | `emailNaoVerificadoERecusado`, `semEmailERecusado` |
| Tomada de conta pela troca ou reaproveitamento do e-mail | Identidade por `sub`, sem fusão por e-mail (RL-18, RL-20, RL-21) | `mesmoSubComEmailNovoMantemAContaEAtualizaOEmail`, `subNovoComEmailDeOutraContaERecusadoSemFundir` |
| *Open redirect* pelo `retorno` | Só caminho interno permitido (RL-07) | `retornoComUrlExternaCaiNoPadrao` (parametrizado: `https://x.com`, `//x.com`, `/\x.com`, `javascript:alert(1)`) |
| Elevação para vendedor pelo login | O login nunca cria perfil de vendedor (RL-23) | `oLoginNaoCriaPerfilDeVendedor` |
| Conta suspensa entrando | Verificação no retorno (RL-22) | `contaSuspensaNaoAbreSessao` |
| Duas primeiras entradas simultâneas do mesmo `sub` | Índice único e releitura (RL-19) | `doisRetornosSimultaneosDoMesmoSubCriamUmaContaSo` |
| Cookie de estado adulterado, expirado ou de outro segredo | Cifra autenticada e validade (RL-05) | `cookieDeEstadoAdulteradoERecusado`, `cookieDeEstadoExpiradoERecusado` |
| Atributos do cookie de estado | RL-05 | `oCookieDeEstadoEHttpOnlySameSiteLaxComCaminhoRestrito` |
| Vazamento de segredos e dados pessoais em log | RL-32 | `oFluxoDeLoginNaoEscreveCodeStateTokensNemEmailNoLog` (captura com *appender* do Logback, como já feito no cadastro) |
| Abuso do início e do retorno | Limite de taxa (RL-30) | `oLimiteDeTaxaNoInicioEnoRetornoDa429` |
| Segredo ausente, curto ou repetido; provedor falso em produção | RL-37, RL-38 | `ConfiguracaoDeProducaoTest` (estendido: sem valor padrão, emissor do Google), `ConferenciaDosSegredos` (estendida a três segredos) |
| Sessão sem aceite dos termos usada para o resto do sistema | RL-24 | `semAceiteDosTermosSoLiberaAceiteELogout` |
| Sessão de 1 hora reaproveitada depois do logout | Revogação do `jti` (RL-26, RL-28) | Já cobertos por `LogoutTest` |
| Uso do cookie com `X-Auth-Id` de outro usuário, `alg: none`, `Authorization: Bearer`... | Cadeia de segurança existente (RL-26) | Já cobertos por `CadeiaDeSegurancaTest` |

Não se aplicam mais (saem junto com a senha): enumeração de e-mails pelo login, limite de 72 bytes do BCrypt e força bruta de senha. A enumeração pelo cadastro também deixa de existir, porque não há formulário de e-mail.

## 5. Contratos da API

| Endpoint | Autenticação | Resposta |
|---|---|---|
| `GET /api/v1/auth/google/entrar?retorno=/comprador` | pública | `302` para o Google, com `Set-Cookie: login_state`. `429` acima do limite |
| `GET /api/v1/auth/google/retorno?code=…&state=…` | pública (exige o cookie `login_state`) | `302` para o `retorno`, com `Set-Cookie: access_token` (login aceito), ou `302` para `/entrar?erro=<codigo>` (recusado). Sempre apaga o `login_state` |
| `POST /api/v1/auth/logout` | cookie + `X-Auth-Id` | `204` e cookie apagado (**sem mudança**) |
| `GET /api/v1/me` | só o cookie | `200 { id, nome, email, vendedor, authId, termosPendentes }` (acrescenta `termosPendentes`) |
| `POST /api/v1/termos/aceite` `{ versao }` | cookie + `X-Auth-Id` | `204`. `400` se a versão não é a vigente |
| ~~`POST /api/v1/auth/registro`~~ e ~~`POST /api/v1/auth/login`~~ | | **Removidos** |

`GET /actuator/health` e as demais rotas não mudam. Como as duas rotas do Google são navegações de topo, não há corpo JSON, e o CORS segue desligado.

## 6. Dados

Mudanças no esquema (changelogs Liquibase em SQL, a escrever no plano):

| Tabela | Mudança |
|---|---|
| `usuario` | **Acrescenta** `google_sub` (texto, **único**, obrigatório), `status` (`ATIVO`, `SUSPENSO`, `EXCLUIDO`, para RL-22), `termos_versao_aceita` e `termos_aceitos_em` (RL-24), `ultimo_login_em`. **Remove** `senha_hash`. O índice único em `lower(email)` continua (um e-mail, uma conta) |
| `evento_seguranca` (nova) | `id`, `usuario_id` (pode ser nulo), `tipo`, `resultado`, `motivo`, `criado_em`. **Sem e-mail, nome, código nem token** (RL-32, RL-33) |
| `token_revogado`, `perfil_vendedor`, `notificacao` | Sem mudança |
| Seed de dev | Os três usuários passam a ter um `google_sub` fixo de mentira (por exemplo `dev-sub-vynicyus`), e deixa de existir a senha `senha-dev-123` |

## 7. Configuração

| Variável | Uso | Regra |
|---|---|---|
| `GOOGLE_CLIENT_ID` | Identifica o aplicativo no Google | Sem valor padrão |
| `GOOGLE_CLIENT_SECRET` | Troca do `code` (RL-08) | Só variável de ambiente, sem valor padrão |
| `GOOGLE_REDIRECT_URI` | Por exemplo `https://<dominio>/api/v1/auth/google/retorno`. Em dev, `http://localhost:3000/api/v1/auth/google/retorno` (pelo proxy do Next.js) | Igual, caractere a caractere, ao cadastrado no Console (RL-06) |
| `LOGIN_STATE_SEGREDO` | Cifra do cookie `login_state` (RL-05) | Base64, pelo menos 32 bytes, **diferente** dos outros dois segredos (RL-37) |
| `JWT_SEGREDO`, `AUTH_ID_SEGREDO` | Já existentes | Sem mudança |

**Passos no Google Cloud Console** (uma vez por ambiente): criar o projeto, configurar a tela de consentimento (Externo; escopos `openid`, `email` e `profile`), criar as credenciais **ID do cliente OAuth** do tipo *Aplicativo da Web*, cadastrar a URI de retorno exata e, no modo de teste, adicionar os e-mails de teste. Conferir os textos na documentação do Google no momento da configuração.

## 8. Impacto no que já existe

| Parte | O que acontece |
|---|---|
| `RegistroService`, `AutenticacaoService`, `RegistroRequest`, `LoginRequest`, `RegistroResponse`, bean `PasswordEncoder` (BCrypt), rotas `registro` e `login` do `AuthController` | **Saem** |
| Testes `RegistroTest`, `LoginTest`, `AutenticacaoServiceTest` e a parte de senha de `SeedDeDesenvolvimentoTest` | **Saem ou são reescritos** para o fluxo do Google. `LoginCookieSeguroTest` (atributos do cookie) e `LoginTest` (cookie de sessão) são reaproveitados no retorno do Google |
| `ApoioDeTeste` (cria usuário com senha), `Usuario.novo(...)` | Passam a criar usuário com `google_sub` |
| `EmissorDeToken`, `CookieDeSessao`, `FiltroXAuthId`, `CookieBearerTokenResolver` (a lista de rotas públicas passa a ser `/api/v1/auth/google/**`), revogação de `jti`, logout, `/me`, notificações, `BooleanEstritoNoJson`, Liquibase, arquitetura | **Continuam** |
| `ConferenciaDosSegredos` | Estendida para três segredos distintos |
| Plano 1 do frontend | Ganha a tela `/entrar` e a guarda de rota (ver o [design das telas](superpowers/specs/2026-09-29-frontend-telas-design.md)). O passo "login pelo console com senha" deixa de existir. O mock do servidor passa a simular `entrar` e `retorno`. O esquema de `/me` deve aceitar qualquer e-mail que o Google devolva: relaxar `z.email()` |
| A regra "e-mail compatível com o Zod" do cadastro | Perde o motivo de existir no backend (o e-mail vem do Google) |

## 9. Fora do escopo e riscos assumidos

| Item | Situação |
|---|---|
| Dependência do Google | Se o Google ficar fora do ar, **ninguém entra**. Plano B (outro provedor ou link por e-mail) é Fase 2 |
| Conta Google comprometida | Quem controla a conta Google controla a conta do aplicativo. Mitigação: sessão curta (1 h) e recomendação de verificação em duas etapas no Google |
| Usuário sem conta Google | Fica de fora. É raro em celular Android, mas acontece |
| Verificação em duas etapas própria, *refresh token*, *One Tap* e outros provedores | Fora do MVP |
| XSS | Um XSS ainda lê o `authId` da memória e age pelo usuário. Defesa: CSP e escape no frontend |
| PKCE com o tipo *Aplicativo da Web* do Google | O Google usa `client_secret` nesse tipo de cliente, e relatos de terceiros dizem que ele também aceita PKCE. **Confirmar em teste de integração com o cliente real** antes de depender disso. Se não aceitar, RL-02 fica como recomendação e o `state` e o `nonce` seguram o fluxo |
| Proxy do Next.js e cookies | O `302` e o `Set-Cookie` precisam passar pelo proxy (`rewrites`). **Provar cedo** com um teste ponta a ponta, como os riscos T3 e T4 do documento de stack |

## 10. Pontos a confirmar com o usuário

1. O Google é o **único** meio de entrar (sem e-mail e senha), como o pedido indica?
2. Aceitar **qualquer** conta Google (Gmail e Workspace), sem restringir domínio?
3. O aceite dos Termos **bloqueante no primeiro login** (RL-24) está bom?
4. Usar `prompt=select_account` **sempre** (RL-27), ou só no primeiro login?
5. As notificações por e-mail (NOT-02) vão para o **e-mail do Google**?
6. Usar o **cliente OAuth2 do Spring Security** (recomendado) em vez de código próprio?
7. Sessão de 1 h sem renovação aceitável para o MVP, já que o retorno ao Google costuma ser rápido?

## 11. Referências

- [OpenID Connect | Google for Developers](https://developers.google.com/identity/openid-connect/openid-connect): endpoints, parâmetros (`nonce` obrigatório, `state` recomendado), validação do ID token, e a orientação de usar `sub`, e não o e-mail, como identificador.
- [Using OAuth 2.0 for Web Server Applications | Google for Developers](https://developers.google.com/identity/protocols/oauth2/web-server): `state` contra CSRF, regras das URIs de redirecionamento (HTTPS exceto `localhost`, sem curingas, sem fragmento, sem IP) e cuidado com o segredo do cliente.
- RFC 6749 (OAuth 2.0), RFC 7636 (PKCE) e RFC 9700 (*Best Current Practice for OAuth 2.0 Security*).
- OpenID Connect Core 1.0, seção 3.1.3.7 (validação do ID token).
- OWASP Cheat Sheet Series: *Cross-Site Request Forgery Prevention*, *Session Management* e *Unvalidated Redirects and Forwards*.
- Relato de terceiros sobre `client_secret` com PKCE no Google: [ktaka.blog.ccmp.jp](https://ktaka.blog.ccmp.jp/2025/07/oogle-oauth2-and-pkce-understanding.html). **Não é fonte oficial**: confirmar.

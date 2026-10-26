# Backend — Marketplace de Autopeças

Spring Boot 4 (Java 21), Spring Security (JWT em cookie HttpOnly + `X-Auth-Id`), PostgreSQL 18, Liquibase (changelogs em SQL). Desenvolvido com TDD.

> **Aviso (2026-09-30):** o login descrito abaixo (e-mail e senha, `senha-dev-123`) é o **estado atual do código** e **vai ser substituído pelo login com Google**. A sessão (JWT, `X-Auth-Id`, logout), o `/me` e as notificações continuam. Regras: [`docs/login-com-google-regras-de-seguranca.md`](../docs/login-com-google-regras-de-seguranca.md).

## Pré-requisitos

- JDK 21 e Maven 3.9 (`java -version`, `mvn -v`)
- Docker em execução (o banco de desenvolvimento e os testes de integração usam PostgreSQL de verdade)

## Rodar em desenvolvimento

```bash
docker compose up -d --wait                    # PostgreSQL 18 em localhost:5432 (na raiz do repositório)
cd backend
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

As aspas em volta do `-D...` são necessárias no PowerShell (sem elas, o argumento é cortado no ponto) e funcionam igual no bash.

O perfil `dev` aponta para o banco do `docker compose`, usa o contexto `dev` do Liquibase (que carrega a **seed**) e traz segredos de desenvolvimento. A API sobe em `http://localhost:8080`.

Usuários da seed (senha de todos: `senha-dev-123`):

| E-mail | Perfil |
|---|---|
| `vynicyus@example.com` | comprador e vendedor **aprovado**, com 8 notificações |
| `ana@example.com` | só comprador |
| `pedro@example.com` | comprador e vendedor **em análise** |

Exemplo de sessão completa, no Git Bash. No PowerShell, use `curl.exe` (e não o alias `curl`), troque as continuações de linha `\` pelo acento grave (`` ` ``) ou escreva o comando em uma linha só, e coloque o JSON do `-d` entre aspas simples com as aspas duplas de dentro escapadas (`\"`):

```bash
curl -s -c cookies.txt -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"vynicyus@example.com","senha":"senha-dev-123"}'
curl -s -b cookies.txt http://localhost:8080/api/v1/me                 # devolve o authId
curl -s -b cookies.txt -H "X-Auth-Id: <authId>" http://localhost:8080/api/v1/notificacoes
```

## Testes

```bash
cd backend
mvn test
```

Os testes de integração sobem um PostgreSQL 18 com o Testcontainers (o Docker precisa estar ligado). A suíte inclui a verificação da arquitetura (Spring Modulith e ArchUnit).

## Configuração

| Propriedade | Como |
|---|---|
| `JWT_SEGREDO` | base64 de pelo menos 32 bytes (`openssl rand -base64 32`). **Sem valor padrão** |
| `AUTH_ID_SEGREDO` | base64 de pelo menos 32 bytes, **diferente** do anterior. Sem valor padrão |
| `app.seguranca.token-validade` | `PT1H` |
| `app.seguranca.cookie-seguro` | `true` (o perfil `dev` e os testes usam `false`) |
| `spring.datasource.*` | URL, usuário e senha do PostgreSQL |
| `spring.liquibase.contexts` | `producao` (padrão), `dev` ou `teste`. **A seed só roda em `dev`** |

**Segurança que vale lembrar:**

- **Nunca ative o perfil `dev` fora de uma máquina local.** Ele traz segredos públicos, o cookie sem `Secure` e a seed com contas de senha pública (uma delas de vendedor aprovado).
- **O CORS está desligado de propósito.** O navegador deve chamar a API pelo proxy (`rewrites`) do Next.js, no mesmo site. Habilitar CORS com credenciais quebraria a defesa contra CSRF, que depende de outro site não conseguir enviar o cabeçalho `X-Auth-Id`.

## Banco de dados

Os changelogs ficam em `src/main/resources/db/changelog/changes/`, um arquivo `NNN-descricao.sql` por mudança, no formato `--liquibase formatted sql`, com `--rollback`. Para uma nova mudança, crie o próximo número. O arquivo mestre `db.changelog-master.yaml` inclui a pasta inteira, em ordem alfabética. O Hibernate só confere o esquema (`ddl-auto=validate`), quem cria as tabelas é o Liquibase.

## Estrutura

```
br.com.autopecas
├─ compartilhado/   # ErroDeNegocio, TratadorDeErros (Problem Details), Identidade, BooleanEstritoNoJson
├─ conta/           # usuários, perfil de vendedor, sessão (JWT, X-Auth-Id)
└─ notificacao/     # notificações do usuário
```

Dentro de cada módulo, `internal/{domain,application,web,infra}`. Um módulo só usa a API pública de outro (pacote raiz do módulo), e o `domain` não depende do Spring. Ambas as regras são verificadas por testes.

Especificação: `docs/superpowers/specs/2026-09-29-backend-autenticacao-design.md`.

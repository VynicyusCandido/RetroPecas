# Backend, Plano 1: esqueleto, autenticação (JWT + `X-Auth-Id`) e notificações — Plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Status (2026-09-30):** este plano foi **executado** (9 tarefas, com revisão por tarefa e uma revisão final; nada commitado depois da tarefa 2, por ordem do usuário). **O login por senha das tarefas 6 e 9 (cadastro, login, BCrypt, seed com senha) será substituído pelo login com Google** ([regras](../../login-com-google-regras-de-seguranca.md), seção 8). O restante (esqueleto, esquema, JWT, `X-Auth-Id`, `/me`, logout, notificações) continua valendo. Este plano fica como registro do que foi construído; o plano do login com Google será outro documento.

**Goal:** Criar o backend Spring Boot com autenticação por **JWT em cookie HttpOnly** confirmada pelo cabeçalho **`X-Auth-Id`** (HMAC-SHA256 do id do usuário), e entregar os endpoints que o frontend já consome: cadastro, login, logout, `/me` e notificações. Desenvolvido com TDD.

**Architecture:** Monólito modular (Spring Modulith) com os módulos `compartilhado`, `conta` e `notificacao`. Dentro de cada módulo, camadas `domain` → `application` → `web` e `infra` (persistência e segurança). Esquema do banco com **Liquibase, changelogs em SQL**. Segurança com Spring Security como *resource server*: um resolvedor lê o JWT do cookie, e um filtro confere o `X-Auth-Id` contra o `sub` do JWT.

**Tech Stack:** Java 21, Spring Boot 4.1.1, Spring Security 7 (resource server, Nimbus JWT), Spring Data JPA (Hibernate), Liquibase, PostgreSQL 18, Testcontainers, JUnit 5, AssertJ, Spring Modulith 2.1.1, ArchUnit 1.5.1, Maven 3.9.

**Specs:**
- [Spec do backend](../specs/2026-09-29-backend-autenticacao-design.md) (modelo de ameaças, contratos, dados)
- [Stack e TDD](../specs/2026-09-29-stack-e-tdd-design.md) (seção 3.1: camadas e módulos)
- [Spec do produto](../specs/2026-09-29-marketplace-autopecas-design.md) (AUT-01, AUT-05, NOT-01, RNF-SEG-01)
- Diagrama: [`09_sequencia_autenticacao.puml`](../../diagramas/09_sequencia_autenticacao.puml)

## Global Constraints

- **JWT em cookie HttpOnly, `SameSite=Lax`, `Path=/`**, validade de 1 hora (`PT1H`), com `Secure` em produção (`cookie-seguro=true` é o padrão; só o perfil `dev` e os testes usam `false`). O token é lido **só do cookie**: o cabeçalho `Authorization` é ignorado.
- JWT **HS256**, emissor `autopecas`, `sub` = id do usuário (UUID), `jti` único por token. Só as claims `iss`, `sub`, `iat`, `exp` e `jti`.
- **`X-Auth-Id` = HMAC-SHA256 do id do usuário, em hexadecimal minúsculo (64 caracteres)**, com um segredo **diferente** do JWT, conferido em **tempo constante** contra o `sub`. Toda chamada autenticada o exige, **menos `GET /api/v1/me`**, que devolve o valor.
- Segredos (`JWT_SEGREDO`, `AUTH_ID_SEGREDO`): base64 de **pelo menos 32 bytes**, **sem valor padrão**. A aplicação não sobe com segredo ausente, fora do base64 ou curto.
- CSRF do Spring **desligado de propósito** (a defesa é `SameSite=Lax` + `X-Auth-Id`), e **nenhum CORS** habilitado.
- Migrações com **Liquibase, changelogs em SQL** (`--liquibase formatted sql`, com `--rollback`). Contexto padrão `producao`; a seed só roda em `dev`. O Hibernate só confere o esquema (`ddl-auto=validate`).
- Erros da API em **Problem Details** (RFC 9457), com o título em português e sem *stack trace*.
- Senha: **8 a 72 bytes** em UTF-8, BCrypt. Login com e-mail inexistente e com senha errada dão a **mesma** resposta.
- Estrutura (documento de stack, seção 3.1): módulos por funcionalidade com `internal/{domain,application,web,infra}`. O `domain` não depende de outras camadas nem do Spring, a `web` não acessa repositórios, a `application` não depende da `web`, e os módulos só se falam pela API pública. Tudo verificado por teste.
- TDD: nenhum código de produção sem um teste falhando antes. Testes de integração usam **PostgreSQL de verdade** (Testcontainers), com o Docker em execução.
- Versões: Java **21**, Spring Boot **4.1.1** (estável em 2026-09-29). Os starters do Boot 4 têm nomes novos (`spring-boot-starter-webmvc`, `spring-boot-starter-security-oauth2-resource-server`, `spring-boot-starter-liquibase`), e o `@AutoConfigureMockMvc` fica em `org.springframework.boot.webmvc.test.autoconfigure`.

## Review Focus

Falhas que a spec implica mas as tarefas "óbvias" não exercitam. Cada linha tem o teste na tarefa dona.

1. **Senha de exatamente 72 bytes × 73 bytes, e acentos:** 36 caracteres `é` são 72 bytes e passam, 37 são 74 bytes e dão `400` no cadastro (e `401` no login), em vez de um `500` do BCrypt ou de uma senha truncada em silêncio. *(Tarefa 6)*
2. **Cookie antigo, inválido ou de outro segredo no navegador:** nunca pode impedir um novo login nem um cadastro. *(Tarefa 6)*
3. **Cadastro simultâneo do mesmo e-mail:** duas requisições em paralelo resultam em um `201` e um `409`, nunca em `500`. *(Tarefa 6)*
4. **Token com `alg: none` ou assinado com outro algoritmo (HS512) usando o segredo certo:** recusado. *(Tarefa 5)*
5. **Sem CORS:** uma origem externa nunca recebe `Access-Control-Allow-Origin`, porque a defesa contra CSRF depende de outro site não conseguir enviar o `X-Auth-Id`. *(Tarefa 5)*

## Fora do escopo deste plano

Limite de tentativas de login (RNF-SEG-05, plano de backend seguinte), renovação do token com *refresh token*, verificação de e-mail e recuperação de senha, aprovação de vendedores e demais módulos do produto (veículos, catálogo, pedidos, chat), tela de login no frontend e implantação. Os riscos assumidos estão na seção 3 da spec do backend.

## Contratos da API

Resumo (a tabela completa, com os códigos de erro, está na seção 4 da [spec do backend](../specs/2026-09-29-backend-autenticacao-design.md)):

| Endpoint | Autenticação |
|---|---|
| `POST /api/v1/auth/registro`, `POST /api/v1/auth/login`, `GET /actuator/health` | pública |
| `GET /api/v1/me` | só o cookie |
| `POST /api/v1/auth/logout`, `GET /api/v1/notificacoes`, `PATCH /api/v1/notificacoes/{id}` | cookie + `X-Auth-Id` |

## Estrutura de arquivos

```
IdeiaDeTCC/
├─ docker-compose.yml                 # PostgreSQL 18 de desenvolvimento
└─ backend/
   ├─ pom.xml · README.md · .gitignore
   └─ src/
      ├─ main/
      │  ├─ java/br/com/autopecas/
      │  │  ├─ AutopecasApplication.java
      │  │  ├─ compartilhado/          # ErroDeNegocio, TratadorDeErros, Identidade
      │  │  ├─ conta/internal/
      │  │  │  ├─ domain/              # Usuario, PerfilVendedor, StatusVendedor, TokenRevogado
      │  │  │  ├─ application/         # serviços e portas (EmissorDeToken, CalculadoraDeAuthId)
      │  │  │  ├─ web/                 # AuthController, MeController e DTOs
      │  │  │  └─ infra/{persistencia,seguranca}/
      │  │  └─ notificacao/internal/{domain,application,web,infra/persistencia}/
      │  └─ resources/
      │     ├─ application.properties · application-dev.properties
      │     └─ db/changelog/{db.changelog-master.yaml, changes/NNN-*.sql}
      └─ test/
         ├─ java/br/com/autopecas/     # TesteDeIntegracao, ApoioDeTeste, testes por módulo
         └─ resources/application-test.properties
```

---
### Task 1: Esqueleto do backend

**Files:**
- Create: `backend/pom.xml`, `backend/.gitignore`, `backend/src/main/java/br/com/autopecas/AutopecasApplication.java`, `backend/src/main/resources/application.properties`, `backend/src/main/resources/application-dev.properties`, `backend/src/test/resources/application-test.properties`, `backend/src/main/resources/db/changelog/db.changelog-master.yaml`, `backend/src/main/resources/db/changelog/changes/001-usuario.sql`, `docker-compose.yml` (raiz do repositório)
- Test: `backend/src/test/java/br/com/autopecas/TestcontainersConfiguration.java`, `TesteDeIntegracao.java`, `InicializacaoTest.java`, `ArquiteturaTest.java`

**Interfaces:**
- Produces:
  - `@TesteDeIntegracao` (meta-anotação em `br.com.autopecas`): `@SpringBootTest`, `@AutoConfigureMockMvc`, perfil `test` e um PostgreSQL 18 real, iniciado pelo Testcontainers. Todo teste de integração das próximas tarefas a usa.
  - Contextos do Liquibase: `producao` (padrão), `dev` e `teste`. Tabela `usuario` (índice único em `lower(email)`).
  - `ArquiteturaTest`: `ApplicationModules.verify()` (Spring Modulith) e três regras do ArchUnit (o `domain` não depende de outras camadas nem do Spring; a `web` não acessa repositórios; a `application` não depende da `web`). As regras aceitam pacotes ainda vazios, e passam a valer conforme o código chega.
  - `docker-compose.yml` com o PostgreSQL 18 de desenvolvimento (`localhost:5432`, banco, usuário e senha `autopecas`).

- [ ] **Step 1: Conferir o ambiente**

```bash
java -version
mvn -v
docker info --format '{{.ServerVersion}}'
```
Expected: `java version "21..."`, `Apache Maven 3.9...` (rodando com o Java 21) e uma versão do Docker (por exemplo `29.1.2`). Se o `docker info` falhar, abra o Docker Desktop e espere ele terminar de iniciar: os testes de integração e o banco de desenvolvimento dependem dele. A primeira execução do Maven baixa as dependências (alguns minutos), e a primeira do Testcontainers baixa a imagem `postgres:18-alpine`.

- [ ] **Step 2: Garantir o repositório Git em `IdeiaDeTCC`**

A partir daqui, todos os comandos rodam a partir de `IdeiaDeTCC/backend`, salvo indicação. Antes, na pasta `IdeiaDeTCC`:

```bash
git rev-parse --show-toplevel
```
Expected: o caminho de `IdeiaDeTCC`. **Se imprimir a pasta pai (`ProjectCode`), ou der erro**, o repositório deste projeto ainda não existe (o Plano 1 do frontend também o cria): rode `git init -b main`, `git add docs` e `git commit -m "docs: spec, stack, diagramas, design das telas e planos"` (com o co-autor abaixo) e confira de novo.

Crie a pasta do projeto e entre nela: `mkdir backend` e `cd backend`.

- [ ] **Step 3: Criar o `pom.xml` e o `.gitignore`**

Ainda sem código de produção: só a configuração do build, que os testes precisam para compilar.

Create `backend/pom.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.1</version>
        <relativePath/>
    </parent>
    <groupId>br.com.autopecas</groupId>
    <artifactId>autopecas</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>autopecas</name>
    <description>Marketplace reverso de autopeças: API</description>

    <properties>
        <java.version>21</java.version>
        <spring-modulith.version>2.1.1</spring-modulith.version>
        <archunit.version>1.5.1</archunit.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-liquibase</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.modulith</groupId>
            <artifactId>spring-modulith-starter-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-testcontainers</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-postgresql</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.modulith</groupId>
            <artifactId>spring-modulith-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.tngtech.archunit</groupId>
            <artifactId>archunit-junit5</artifactId>
            <version>${archunit.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.modulith</groupId>
                <artifactId>spring-modulith-bom</artifactId>
                <version>${spring-modulith.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

Create `backend/.gitignore`:
```
target/
*.log
.idea/
*.iml
.vscode/
cookies.txt
```

- [ ] **Step 4: Escrever os testes**

Create `backend/src/test/java/br/com/autopecas/TestcontainersConfiguration.java`:
```java
package br.com.autopecas;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** PostgreSQL real, da mesma versão do docker-compose de desenvolvimento. */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
    }
}
```

Create `backend/src/test/java/br/com/autopecas/TesteDeIntegracao.java`:
```java
package br.com.autopecas;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Sobe a aplicação inteira com um PostgreSQL real (Testcontainers) e o MockMvc. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public @interface TesteDeIntegracao {
}
```

Create `backend/src/test/java/br/com/autopecas/InicializacaoTest.java`:
```java
package br.com.autopecas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@TesteDeIntegracao
class InicializacaoTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void aSaudeDaAplicacaoFicaDisponivelSemAutenticacao() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void oLiquibaseAplicouOsChangelogsSemASeedDeDesenvolvimento() {
        List<String> aplicados =
                jdbc.queryForList("select id from databasechangelog order by orderexecuted", String.class);

        assertThat(aplicados)
                .containsExactly("001-criar-usuario");
    }
}
```

Create `backend/src/test/java/br/com/autopecas/ArquiteturaTest.java`:
```java
package br.com.autopecas;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** As fronteiras entre módulos e entre camadas são verificadas em todo build. */
class ArquiteturaTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("br.com.autopecas");

    @Test
    void osModulosSoSeEnxergamPelaApiPublica() {
        ApplicationModules.of(AutopecasApplication.class).verify();
    }

    @Test
    void oDominioNaoDependeDeOutrasCamadasNemDoSpring() {
        noClasses()
                .that()
                .resideInAPackage("..internal.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "..internal.web..", "..internal.application..", "..internal.infra..", "org.springframework..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void aCamadaWebNaoAcessaARepositorios() {
        noClasses()
                .that()
                .resideInAPackage("..internal.web..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..internal.infra.persistencia..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void aCamadaDeAplicacaoNaoDependeDaWeb() {
        noClasses()
                .that()
                .resideInAPackage("..internal.application..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..internal.web..")
                .allowEmptyShould(true)
                .check(classes);
    }
}
```

- [ ] **Step 5: Rodar e ver falhar**

Run: `mvn -q test-compile`
Expected: FAIL com erro de compilação: `cannot find symbol` em `ArquiteturaTest` (a classe `AutopecasApplication` ainda não existe).

- [ ] **Step 6: Criar a aplicação, a configuração, o changelog e o compose**

O Hibernate só confere o esquema (`ddl-auto=validate`). O padrão do Liquibase é o contexto `producao`, para a seed de desenvolvimento nunca rodar sem querer.

Create `backend/src/main/java/br/com/autopecas/AutopecasApplication.java`:
```java
package br.com.autopecas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AutopecasApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutopecasApplication.class, args);
    }
}
```

Create `backend/src/main/resources/application.properties`:
```properties
spring.application.name=autopecas
spring.threads.virtual.enabled=true

# Persistência: o esquema é do Liquibase, o Hibernate só confere.
spring.jpa.open-in-view=false
spring.jpa.hibernate.ddl-auto=validate
spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yaml
# Sem contexto explícito o Liquibase roda TODOS os changesets, inclusive a seed de desenvolvimento.
spring.liquibase.contexts=producao

management.endpoints.web.exposure.include=health
```

Create `backend/src/main/resources/application-dev.properties`:
```properties
# Só para desenvolvimento local (docker compose up -d). Os segredos abaixo NÃO servem para produção.
spring.datasource.url=jdbc:postgresql://localhost:5432/autopecas
spring.datasource.username=autopecas
spring.datasource.password=autopecas
spring.liquibase.contexts=dev
```

Create `backend/src/test/resources/application-test.properties`:
```properties
spring.liquibase.contexts=teste
```

Create `backend/src/main/resources/db/changelog/db.changelog-master.yaml`:
```yaml
databaseChangeLog:
  - includeAll:
      path: db/changelog/changes/
```

Create `backend/src/main/resources/db/changelog/changes/001-usuario.sql`:
```sql
--liquibase formatted sql

--changeset autopecas:001-criar-usuario
CREATE TABLE usuario (
    id          UUID         PRIMARY KEY,
    email       VARCHAR(254) NOT NULL,
    senha_hash  VARCHAR(100) NOT NULL,
    nome        VARCHAR(120) NOT NULL,
    criado_em   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_usuario_email ON usuario (lower(email));
--rollback DROP TABLE usuario;
```

Create `docker-compose.yml`:
```yaml
services:
  postgres:
    image: postgres:18-alpine
    environment:
      POSTGRES_DB: autopecas
      POSTGRES_USER: autopecas
      POSTGRES_PASSWORD: autopecas
    ports:
      # Só no loopback: o banco de desenvolvimento tem superusuário com senha pública, e sem o 127.0.0.1 o Docker
      # o publicaria em todas as interfaces (qualquer pessoa na mesma rede conectaria).
      - "127.0.0.1:5432:5432"
    volumes:
      # A partir do PostgreSQL 18 a imagem guarda os dados em /var/lib/postgresql.
      - postgres-dados:/var/lib/postgresql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U autopecas -d autopecas"]
      interval: 3s
      timeout: 3s
      retries: 20

volumes:
  postgres-dados:
```

- [ ] **Step 7: Rodar tudo e ver passar**

Run: `mvn test`
Expected: `BUILD SUCCESS` e `Tests run: 6, Failures: 0, Errors: 0` (6 testes ao todo até aqui). O `InicializacaoTest` prova que a saúde responde `UP` sem autenticação e que **só** o changelog `001-criar-usuario` foi aplicado.

- [ ] **Step 8: Commit**

```bash
cd ..
git add backend docker-compose.yml
git commit -m "chore(backend): esqueleto Spring Boot 4, Liquibase em SQL, PostgreSQL 18 e testes de arquitetura" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
cd backend
```


---

### Task 2: Esquema do banco e entidades JPA

**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/002-perfil-vendedor.sql`, `003-token-revogado.sql`, `004-notificacao.sql` (mesma pasta); em `backend/src/main/java/br/com/autopecas/`: `conta/internal/domain/{StatusVendedor,Usuario,PerfilVendedor,TokenRevogado}.java`, `conta/internal/infra/persistencia/{UsuarioRepository,PerfilVendedorRepository,TokenRevogadoRepository}.java`, `notificacao/internal/domain/{TipoNotificacao,Notificacao}.java`, `notificacao/internal/infra/persistencia/NotificacaoRepository.java`
- Test: `backend/src/test/java/br/com/autopecas/EsquemaDoBancoTest.java`
- Modify: `backend/src/test/java/br/com/autopecas/InicializacaoTest.java` (passa a esperar os quatro changelogs)

**Interfaces:**
- Consumes: `@TesteDeIntegracao`.
- Produces:
  - Tabelas `perfil_vendedor`, `token_revogado` e `notificacao`, com as regras no banco (checks de `status` e `tipo`, `ON DELETE CASCADE`, índices).
  - Entidades JPA: `Usuario.novo(String nome, String email, String senhaHash, Instant criadoEm)` (apara o nome, guarda o e-mail em minúsculas); `PerfilVendedor.de(UUID usuarioId, StatusVendedor status, Instant criadoEm)`; `TokenRevogado(UUID jti, Instant expiraEm)`; `Notificacao.nova(UUID usuarioId, TipoNotificacao tipo, String titulo, String resumo, Instant criadaEm)` e `definirLida(boolean)`.
  - Repositórios: `UsuarioRepository.findByEmailIgnoreCase(String)` e `existsByEmailIgnoreCase(String)`; `TokenRevogadoRepository.removerExpirados(Instant): int`; `NotificacaoRepository.findByUsuarioIdAndTipoInOrderByCriadaEmDesc(UUID, Collection<TipoNotificacao>, Pageable)`, `findByIdAndUsuarioId(UUID, UUID)` e `contarNaoLidasPorTipo(UUID)` (projeção `ContagemPorTipo { getTipo(), getTotal() }`).

- [ ] **Step 1: Escrever os testes**

Create `backend/src/test/java/br/com/autopecas/EsquemaDoBancoTest.java`:
```java
package br.com.autopecas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** O esquema é do Liquibase: estas regras valem no banco, mesmo que a aplicação erre. */
@TesteDeIntegracao
class EsquemaDoBancoTest {

    @Autowired
    JdbcTemplate jdbc;

    private UUID inserirUsuario(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into usuario (id, email, senha_hash, nome) values (?, ?, 'hash', 'Nome')", id, email);
        return id;
    }

    @Test
    void oEmailEUnicoIgnorandoACaixa() {
        String base = "caixa-" + UUID.randomUUID();
        inserirUsuario(base + "@example.com");

        assertThatThrownBy(() -> inserirUsuario(base.toUpperCase() + "@EXAMPLE.COM"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void oStatusDoVendedorSoAceitaOsValoresConhecidos() {
        UUID usuario = inserirUsuario("status-" + UUID.randomUUID() + "@example.com");

        assertThatThrownBy(() ->
                        jdbc.update("insert into perfil_vendedor (usuario_id, status) values (?, 'TALVEZ')", usuario))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void oTipoDaNotificacaoSoAceitaOsValoresConhecidos() {
        UUID usuario = inserirUsuario("tipo-" + UUID.randomUUID() + "@example.com");

        assertThatThrownBy(() -> jdbc.update(
                        "insert into notificacao (id, usuario_id, tipo, titulo) values (?, ?, 'EMAIL', 'x')",
                        UUID.randomUUID(),
                        usuario))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void removerUmUsuarioRemoveOPerfilEAsNotificacoes() {
        UUID usuario = inserirUsuario("cascata-" + UUID.randomUUID() + "@example.com");
        jdbc.update("insert into perfil_vendedor (usuario_id, status) values (?, 'APROVADO')", usuario);
        jdbc.update(
                "insert into notificacao (id, usuario_id, tipo, titulo) values (?, ?, 'CHAT', 'x')",
                UUID.randomUUID(),
                usuario);

        jdbc.update("delete from usuario where id = ?", usuario);

        assertThat(jdbc.queryForObject("select count(*) from perfil_vendedor where usuario_id = ?", Long.class, usuario))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from notificacao where usuario_id = ?", Long.class, usuario))
                .isZero();
    }

    @Test
    void oResumoDaNotificacaoTemValorPadraoVazioELidaComecaFalsa() {
        UUID usuario = inserirUsuario("padrao-" + UUID.randomUUID() + "@example.com");
        UUID id = UUID.randomUUID();

        jdbc.update("insert into notificacao (id, usuario_id, tipo, titulo) values (?, ?, 'SISTEMA', 'x')", id, usuario);

        assertThat(jdbc.queryForObject("select resumo from notificacao where id = ?", String.class, id)).isEmpty();
        assertThat(jdbc.queryForObject("select lida from notificacao where id = ?", Boolean.class, id)).isFalse();
    }
}
```

Replace `backend/src/test/java/br/com/autopecas/InicializacaoTest.java`:
```java
package br.com.autopecas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@TesteDeIntegracao
class InicializacaoTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void aSaudeDaAplicacaoFicaDisponivelSemAutenticacao() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void oLiquibaseAplicouOsChangelogsSemASeedDeDesenvolvimento() {
        List<String> aplicados =
                jdbc.queryForList("select id from databasechangelog order by orderexecuted", String.class);

        assertThat(aplicados)
                .containsExactly(
                        "001-criar-usuario",
                        "002-criar-perfil-vendedor",
                        "003-criar-token-revogado",
                        "004-criar-notificacao");
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn -q test -Dtest=EsquemaDoBancoTest,InicializacaoTest`
Expected: FAIL: o `EsquemaDoBancoTest` falha porque as tabelas `perfil_vendedor` e `notificacao` não existem, e o `InicializacaoTest` falha porque só o changelog `001` foi aplicado.

- [ ] **Step 3: Criar os changelogs 002 a 004**

Um arquivo por mudança, no formato `--liquibase formatted sql`, cada um com o `--rollback` correspondente.

Create `backend/src/main/resources/db/changelog/changes/002-perfil-vendedor.sql`:
```sql
--liquibase formatted sql

--changeset autopecas:002-criar-perfil-vendedor
CREATE TABLE perfil_vendedor (
    usuario_id  UUID        PRIMARY KEY REFERENCES usuario (id) ON DELETE CASCADE,
    status      VARCHAR(20) NOT NULL,
    criado_em   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_perfil_vendedor_status CHECK (status IN ('PENDENTE', 'APROVADO', 'REPROVADO', 'SUSPENSO'))
);
--rollback DROP TABLE perfil_vendedor;
```

Create `backend/src/main/resources/db/changelog/changes/003-token-revogado.sql`:
```sql
--liquibase formatted sql

--changeset autopecas:003-criar-token-revogado
CREATE TABLE token_revogado (
    jti        UUID        PRIMARY KEY,
    expira_em  TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_token_revogado_expira_em ON token_revogado (expira_em);
--rollback DROP TABLE token_revogado;
```

Create `backend/src/main/resources/db/changelog/changes/004-notificacao.sql`:
```sql
--liquibase formatted sql

--changeset autopecas:004-criar-notificacao
CREATE TABLE notificacao (
    id          UUID         PRIMARY KEY,
    usuario_id  UUID         NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    tipo        VARCHAR(20)  NOT NULL,
    titulo      VARCHAR(200) NOT NULL,
    resumo      VARCHAR(500) NOT NULL DEFAULT '',
    criada_em   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    lida        BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_notificacao_tipo CHECK (tipo IN ('CHAT', 'PROPOSTA', 'PECA_A_PROCURA', 'SISTEMA'))
);

CREATE INDEX ix_notificacao_usuario_criada ON notificacao (usuario_id, criada_em DESC);
CREATE INDEX ix_notificacao_nao_lidas ON notificacao (usuario_id, tipo) WHERE NOT lida;
--rollback DROP TABLE notificacao;
```

- [ ] **Step 4: Rodar e ver passar (só o esquema)**

Run: `mvn test -Dtest=EsquemaDoBancoTest,InicializacaoTest`
Expected: `BUILD SUCCESS`, com 7 testes passando.

- [ ] **Step 5: Criar as entidades e os repositórios**

As entidades são conferidas contra o esquema na subida do contexto (`ddl-auto=validate`): um nome de coluna errado derruba o `InicializacaoTest`.

Create `backend/src/main/java/br/com/autopecas/conta/internal/domain/StatusVendedor.java`:
```java
package br.com.autopecas.conta.internal.domain;

public enum StatusVendedor {
    PENDENTE,
    APROVADO,
    REPROVADO,
    SUSPENSO
}
```

Create `backend/src/main/java/br/com/autopecas/conta/internal/domain/Usuario.java`:
```java
package br.com.autopecas.conta.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @Column(nullable = false)
    private String nome;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    protected Usuario() {
    }

    /** O e-mail é guardado em minúsculas: a unicidade no banco também ignora a caixa. */
    public static Usuario novo(String nome, String email, String senhaHash, Instant criadoEm) {
        Usuario usuario = new Usuario();
        usuario.id = UUID.randomUUID();
        usuario.nome = nome.trim();
        usuario.email = email.trim().toLowerCase(Locale.ROOT);
        usuario.senhaHash = senhaHash;
        usuario.criadoEm = criadoEm;
        return usuario;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public String getNome() {
        return nome;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
```

Create `backend/src/main/java/br/com/autopecas/conta/internal/domain/PerfilVendedor.java`:
```java
package br.com.autopecas.conta.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "perfil_vendedor")
public class PerfilVendedor {

    @Id
    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusVendedor status;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    protected PerfilVendedor() {
    }

    public static PerfilVendedor de(UUID usuarioId, StatusVendedor status, Instant criadoEm) {
        PerfilVendedor perfil = new PerfilVendedor();
        perfil.usuarioId = usuarioId;
        perfil.status = status;
        perfil.criadoEm = criadoEm;
        return perfil;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public StatusVendedor getStatus() {
        return status;
    }
}
```

Create `backend/src/main/java/br/com/autopecas/conta/internal/domain/TokenRevogado.java`:
```java
package br.com.autopecas.conta.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Token cujo logout já foi feito. Fica guardado só até o vencimento natural do JWT. */
@Entity
@Table(name = "token_revogado")
public class TokenRevogado {

    @Id
    private UUID jti;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    protected TokenRevogado() {
    }

    public TokenRevogado(UUID jti, Instant expiraEm) {
        this.jti = jti;
        this.expiraEm = expiraEm;
    }

    public UUID getJti() {
        return jti;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }
}
```

Create `backend/src/main/java/br/com/autopecas/conta/internal/infra/persistencia/UsuarioRepository.java`:
```java
package br.com.autopecas.conta.internal.infra.persistencia;

import br.com.autopecas.conta.internal.domain.Usuario;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
```

Create `backend/src/main/java/br/com/autopecas/conta/internal/infra/persistencia/PerfilVendedorRepository.java`:
```java
package br.com.autopecas.conta.internal.infra.persistencia;

import br.com.autopecas.conta.internal.domain.PerfilVendedor;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerfilVendedorRepository extends JpaRepository<PerfilVendedor, UUID> {
}
```

Create `backend/src/main/java/br/com/autopecas/conta/internal/infra/persistencia/TokenRevogadoRepository.java`:
```java
package br.com.autopecas.conta.internal.infra.persistencia;

import br.com.autopecas.conta.internal.domain.TokenRevogado;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TokenRevogadoRepository extends JpaRepository<TokenRevogado, UUID> {

    @Modifying
    @Query("delete from TokenRevogado t where t.expiraEm < :agora")
    int removerExpirados(@Param("agora") Instant agora);
}
```

Create `backend/src/main/java/br/com/autopecas/notificacao/internal/domain/TipoNotificacao.java`:
```java
package br.com.autopecas.notificacao.internal.domain;

public enum TipoNotificacao {
    CHAT,
    PROPOSTA,
    PECA_A_PROCURA,
    SISTEMA
}
```

Create `backend/src/main/java/br/com/autopecas/notificacao/internal/domain/Notificacao.java`:
```java
package br.com.autopecas.notificacao.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notificacao")
public class Notificacao {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoNotificacao tipo;

    @Column(nullable = false)
    private String titulo;

    @Column(nullable = false)
    private String resumo;

    @Column(name = "criada_em", nullable = false, updatable = false)
    private Instant criadaEm;

    @Column(nullable = false)
    private boolean lida;

    protected Notificacao() {
    }

    public static Notificacao nova(
            UUID usuarioId, TipoNotificacao tipo, String titulo, String resumo, Instant criadaEm) {
        Notificacao notificacao = new Notificacao();
        notificacao.id = UUID.randomUUID();
        notificacao.usuarioId = usuarioId;
        notificacao.tipo = tipo;
        notificacao.titulo = titulo;
        notificacao.resumo = resumo;
        notificacao.criadaEm = criadaEm;
        notificacao.lida = false;
        return notificacao;
    }

    public void definirLida(boolean lida) {
        this.lida = lida;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public TipoNotificacao getTipo() {
        return tipo;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getResumo() {
        return resumo;
    }

    public Instant getCriadaEm() {
        return criadaEm;
    }

    public boolean isLida() {
        return lida;
    }
}
```

Create `backend/src/main/java/br/com/autopecas/notificacao/internal/infra/persistencia/NotificacaoRepository.java`:
```java
package br.com.autopecas.notificacao.internal.infra.persistencia;

import br.com.autopecas.notificacao.internal.domain.Notificacao;
import br.com.autopecas.notificacao.internal.domain.TipoNotificacao;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificacaoRepository extends JpaRepository<Notificacao, UUID> {

    List<Notificacao> findByUsuarioIdAndTipoInOrderByCriadaEmDesc(
            UUID usuarioId, Collection<TipoNotificacao> tipos, Pageable pagina);

    Optional<Notificacao> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    @Query("select n.tipo as tipo, count(n) as total from Notificacao n "
            + "where n.usuarioId = :usuarioId and n.lida = false group by n.tipo")
    List<ContagemPorTipo> contarNaoLidasPorTipo(@Param("usuarioId") UUID usuarioId);

    interface ContagemPorTipo {

        TipoNotificacao getTipo();

        long getTotal();
    }
}
```

- [ ] **Step 6: Rodar tudo e ver passar**

Run: `mvn test`
Expected: `BUILD SUCCESS` e `Tests run: 11, Failures: 0, Errors: 0` (11 testes ao todo até aqui). O `ArquiteturaTest` já enxerga o `domain` e a `persistencia`, e confirma que o `domain` só usa `jakarta.persistence`.

- [ ] **Step 7: Commit**

```bash
cd ..
git add backend
git commit -m "feat(backend): esquema Liquibase (SQL) de perfil de vendedor, token revogado e notificação, com entidades JPA" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
cd backend
```


---

### Task 3: Segredos e o hash `X-Auth-Id` (HMAC)

**Files:**
- Create: em `backend/src/main/java/br/com/autopecas/conta/internal/`: `infra/seguranca/{SegurancaProperties,CalculadoraDeAuthIdHmac,ConfiguracaoDeSeguranca}.java`, `application/CalculadoraDeAuthId.java`
- Modify: `backend/src/main/resources/application.properties`, `application-dev.properties`, `backend/src/test/resources/application-test.properties`
- Test: `backend/src/test/java/br/com/autopecas/conta/internal/infra/seguranca/{CalculadoraDeAuthIdHmacTest,SegurancaPropertiesTest}.java`

**Interfaces:**
- Produces:
  - `SegurancaProperties(String jwtSegredo, String authIdSegredo, Duration tokenValidade, boolean cookieSeguro)` (prefixo `app.seguranca`), com `byte[] jwtChave()` e `byte[] authIdChave()`: decodificam o base64 e **recusam** segredo fora do base64 ou com menos de 32 bytes.
  - Porta `CalculadoraDeAuthId { String calcular(UUID usuarioId); boolean confere(UUID usuarioId, String informado); }`. Implementação `CalculadoraDeAuthIdHmac`: `calcular` devolve o **HMAC-SHA256 do id do usuário em hexadecimal minúsculo** (64 caracteres), com o segredo `authIdSegredo`. `confere` compara em tempo constante e devolve `false` para `null`, vazio, maiúsculas, espaços e tamanho diferente.
  - `ConfiguracaoDeSeguranca` (por ora só registra as propriedades) e a constante `EMISSOR = "autopecas"`.
  - Nos três arquivos de propriedades: `app.seguranca.*`. **Sem valor padrão** no `application.properties` (`${JWT_SEGREDO}`, `${AUTH_ID_SEGREDO}`), segredos de desenvolvimento no perfil `dev` e de teste no perfil `test`.

- [ ] **Step 1: Escrever os testes**

Create `backend/src/test/java/br/com/autopecas/conta/internal/infra/seguranca/CalculadoraDeAuthIdHmacTest.java`:
```java
package br.com.autopecas.conta.internal.infra.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CalculadoraDeAuthIdHmacTest {

    private static final UUID USUARIO = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OUTRO_USUARIO = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static final String SEGREDO_UM = segredoDeBytesIguais(1);
    private static final String SEGREDO_DOIS = segredoDeBytesIguais(2);

    private static String segredoDeBytesIguais(int valor) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte) valor);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static CalculadoraDeAuthIdHmac calculadora(String authIdSegredo) {
        return new CalculadoraDeAuthIdHmac(
                new SegurancaProperties(SEGREDO_UM, authIdSegredo, Duration.ofHours(1), false));
    }

    @Test
    void calculaOHmacSha256EmHexadecimalDoIdDoUsuario() {
        // Valor de referência calculado à parte, com o HMAC da biblioteca padrão do Python.
        assertThat(calculadora(SEGREDO_UM).calcular(USUARIO))
                .isEqualTo("c253eb93bd51cef621f8c718edb24abef97764b3022cb631f484ef6264855249");
    }

    @Test
    void semprePodeSerLidoComoSessentaEQuatroHexadecimaisMinusculos() {
        assertThat(calculadora(SEGREDO_UM).calcular(UUID.randomUUID())).matches("[0-9a-f]{64}");
    }

    @Test
    void naoEOSha256SimplesDoId() {
        String sha256DoId = "7ac1b8d7010bb6cd3a3e84e7f90136b880bbc899e428ece49333372911ab9052";

        assertThat(calculadora(SEGREDO_UM).calcular(USUARIO)).isNotEqualTo(sha256DoId);
    }

    @Test
    void usuariosDiferentesTemHashesDiferentes() {
        CalculadoraDeAuthIdHmac calculadora = calculadora(SEGREDO_UM);

        assertThat(calculadora.calcular(USUARIO)).isNotEqualTo(calculadora.calcular(OUTRO_USUARIO));
    }

    @Test
    void segredosDiferentesGeramHashesDiferentesParaOMesmoUsuario() {
        assertThat(calculadora(SEGREDO_UM).calcular(USUARIO)).isNotEqualTo(calculadora(SEGREDO_DOIS).calcular(USUARIO));
    }

    @Test
    void oMesmoUsuarioEOMesmoSegredoSempreDaOMesmoHash() {
        assertThat(calculadora(SEGREDO_UM).calcular(USUARIO)).isEqualTo(calculadora(SEGREDO_UM).calcular(USUARIO));
    }

    @Test
    void confereAceitaOHashDoProprioUsuario() {
        CalculadoraDeAuthIdHmac calculadora = calculadora(SEGREDO_UM);

        assertThat(calculadora.confere(USUARIO, calculadora.calcular(USUARIO))).isTrue();
    }

    @Test
    void confereRecusaOHashDeOutroUsuario() {
        CalculadoraDeAuthIdHmac calculadora = calculadora(SEGREDO_UM);

        assertThat(calculadora.confere(USUARIO, calculadora.calcular(OUTRO_USUARIO))).isFalse();
    }

    @Test
    void confereRecusaOHashCalculadoComOutroSegredo() {
        String forjado = calculadora(SEGREDO_DOIS).calcular(USUARIO);

        assertThat(calculadora(SEGREDO_UM).confere(USUARIO, forjado)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "abc", "00000000-0000-0000-0000-000000000001"})
    void confereRecusaValoresQueNaoSaoOHash(String informado) {
        assertThat(calculadora(SEGREDO_UM).confere(USUARIO, informado)).isFalse();
    }

    @Test
    void confereRecusaOHashAlteradoDeQualquerJeito() {
        CalculadoraDeAuthIdHmac calculadora = calculadora(SEGREDO_UM);
        String hash = calculadora.calcular(USUARIO);

        assertThat(calculadora.confere(USUARIO, hash.toUpperCase())).isFalse();
        assertThat(calculadora.confere(USUARIO, hash + " ")).isFalse();
        assertThat(calculadora.confere(USUARIO, " " + hash)).isFalse();
        assertThat(calculadora.confere(USUARIO, hash.substring(1))).isFalse();
        assertThat(calculadora.confere(USUARIO, hash + "0")).isFalse();
    }
}
```

Create `backend/src/test/java/br/com/autopecas/conta/internal/infra/seguranca/SegurancaPropertiesTest.java`:
```java
package br.com.autopecas.conta.internal.infra.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SegurancaPropertiesTest {

    private static final String SEGREDO_DE_32_BYTES = Base64.getEncoder().encodeToString(new byte[32]);

    private static SegurancaProperties com(String jwtSegredo, String authIdSegredo) {
        return new SegurancaProperties(jwtSegredo, authIdSegredo, Duration.ofHours(1), false);
    }

    @Test
    void aceitaSegredosDe32BytesEmBase64() {
        SegurancaProperties propriedades = com(SEGREDO_DE_32_BYTES, SEGREDO_DE_32_BYTES);

        assertThat(propriedades.jwtChave()).hasSize(32);
        assertThat(propriedades.authIdChave()).hasSize(32);
    }

    @Test
    void recusaOSegredoDoJwtComMenosDe32Bytes() {
        String curto = Base64.getEncoder().encodeToString(new byte[31]);

        assertThatThrownBy(() -> com(curto, SEGREDO_DE_32_BYTES).jwtChave())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt-segredo")
                .hasMessageContaining("32 bytes");
    }

    @Test
    void recusaOSegredoDoAuthIdComMenosDe32Bytes() {
        String curto = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> com(SEGREDO_DE_32_BYTES, curto).authIdChave())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth-id-segredo")
                .hasMessageContaining("32 bytes");
    }

    @Test
    void recusaSegredoQueNaoEstaEmBase64() {
        assertThatThrownBy(() -> com("isto não é base64!!", SEGREDO_DE_32_BYTES).jwtChave())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("base64");
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn -q test-compile`
Expected: FAIL com erro de compilação: `cannot find symbol` em `CalculadoraDeAuthIdHmacTest` e `SegurancaPropertiesTest` (`SegurancaProperties` e `CalculadoraDeAuthIdHmac` ainda não existem).

- [ ] **Step 3: Implementar as propriedades e o cálculo do `X-Auth-Id`**

O `authId` é um HMAC com um segredo **diferente** do JWT. O valor de referência do teste (`c253eb93…5249`) foi calculado à parte, com o HMAC do Python, para provar o algoritmo e não só a consistência interna.

Create `backend/src/main/java/br/com/autopecas/conta/internal/infra/seguranca/SegurancaProperties.java`:
```java
package br.com.autopecas.conta.internal.infra.seguranca;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Segredos em base64, com pelo menos 32 bytes. O segredo do JWT e o do X-Auth-Id são diferentes de propósito:
 * quem descobrir um não consegue falsificar o outro.
 */
@Validated
@ConfigurationProperties(prefix = "app.seguranca")
public record SegurancaProperties(
        @NotBlank String jwtSegredo,
        @NotBlank String authIdSegredo,
        @NotNull Duration tokenValidade,
        boolean cookieSeguro) {

    static final int TAMANHO_MINIMO_DO_SEGREDO_EM_BYTES = 32;

    public byte[] jwtChave() {
        return decodificar(jwtSegredo, "jwt-segredo");
    }

    public byte[] authIdChave() {
        return decodificar(authIdSegredo, "auth-id-segredo");
    }

    private static byte[] decodificar(String base64, String nome) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException erro) {
            throw new IllegalStateException("app.seguranca." + nome + " precisa estar em base64.", erro);
        }
        if (bytes.length < TAMANHO_MINIMO_DO_SEGREDO_EM_BYTES) {
            throw new IllegalStateException(
                    "app.seguranca." + nome + " precisa ter pelo menos " + TAMANHO_MINIMO_DO_SEGREDO_EM_BYTES
                            + " bytes (em base64).");
        }
        return bytes;
    }
}
```

Create `backend/src/main/java/br/com/autopecas/conta/internal/application/CalculadoraDeAuthId.java`:
```java
package br.com.autopecas.conta.internal.application;

import java.util.UUID;

/** Porta: o hash de identificação (cabeçalho X-Auth-Id) que confirma o usuário da sessão. */
public interface CalculadoraDeAuthId {

    String calcular(UUID usuarioId);

    boolean confere(UUID usuarioId, String informado);
}
```

Create `backend/src/main/java/br/com/autopecas/conta/internal/infra/seguranca/CalculadoraDeAuthIdHmac.java`:
```java
package br.com.autopecas.conta.internal.infra.seguranca;

import static java.nio.charset.StandardCharsets.UTF_8;

import br.com.autopecas.conta.internal.application.CalculadoraDeAuthId;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** X-Auth-Id = HMAC-SHA256 do id do usuário, em hexadecimal, com o segredo do servidor. */
@Component
class CalculadoraDeAuthIdHmac implements CalculadoraDeAuthId {

    private static final String ALGORITMO = "HmacSHA256";

    private final SecretKeySpec chave;

    CalculadoraDeAuthIdHmac(SegurancaProperties propriedades) {
        this.chave = new SecretKeySpec(propriedades.authIdChave(), ALGORITMO);
    }

    @Override
    public String calcular(UUID usuarioId) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(chave);
            return HexFormat.of().formatHex(mac.doFinal(usuarioId.toString().getBytes(UTF_8)));
        } catch (GeneralSecurityException erro) {
            throw new IllegalStateException("HMAC-SHA256 indisponível", erro);
        }
    }

    /** Comparação em tempo constante, para o tempo de resposta não revelar quantos caracteres batem. */
    @Override
    public boolean confere(UUID usuarioId, String informado) {
        if (informado == null) {
            return false;
        }
        return MessageDigest.isEqual(calcular(usuarioId).getBytes(UTF_8), informado.getBytes(UTF_8));
    }
}
```

Create `backend/src/main/java/br/com/autopecas/conta/internal/infra/seguranca/ConfiguracaoDeSeguranca.java`:
```java
package br.com.autopecas.conta.internal.infra.seguranca;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SegurancaProperties.class)
class ConfiguracaoDeSeguranca {

    static final String EMISSOR = "autopecas";
}
```

Replace `backend/src/main/resources/application.properties`:
```properties
spring.application.name=autopecas
spring.threads.virtual.enabled=true

# Persistência: o esquema é do Liquibase, o Hibernate só confere.
spring.jpa.open-in-view=false
spring.jpa.hibernate.ddl-auto=validate
spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yaml
# Sem contexto explícito o Liquibase roda TODOS os changesets, inclusive a seed de desenvolvimento.
spring.liquibase.contexts=producao

management.endpoints.web.exposure.include=health

# Segurança. Não há valor padrão: sem segredos a aplicação não sobe.
app.seguranca.jwt-segredo=${JWT_SEGREDO}
app.seguranca.auth-id-segredo=${AUTH_ID_SEGREDO}
app.seguranca.token-validade=PT1H
app.seguranca.cookie-seguro=true
```

Replace `backend/src/main/resources/application-dev.properties`:
```properties
# Só para desenvolvimento local (docker compose up -d). Os segredos abaixo NÃO servem para produção.
spring.datasource.url=jdbc:postgresql://localhost:5432/autopecas
spring.datasource.username=autopecas
spring.datasource.password=autopecas
spring.liquibase.contexts=dev
app.seguranca.jwt-segredo=/V3Q+im44QmhWqAJXJLBsZ31f4t38FgAT/PUkigeqs8=
app.seguranca.auth-id-segredo=HNHwpAtheE8q0SQ3ZtH5zv6RXfsMG+U45sgFn7aLG3M=
app.seguranca.cookie-seguro=false
```

Replace `backend/src/test/resources/application-test.properties`:
```properties
spring.liquibase.contexts=teste
app.seguranca.jwt-segredo=0cWQAYKOvG7NQGoNR1gAvvWjpgFUOarxHd9wymk/hqU=
app.seguranca.auth-id-segredo=KHszLdzZqvCeOHQTMd9agNa+MhP7FmLLUW5vybYsfoo=
app.seguranca.cookie-seguro=false
```

- [ ] **Step 4: Rodar tudo e ver passar**

Run: `mvn test`
Expected: `BUILD SUCCESS` e `Tests run: 30, Failures: 0, Errors: 0` (30 testes ao todo até aqui). Os testes de integração já sobem o contexto com `SegurancaProperties` ligado.

- [ ] **Step 5: Commit**

```bash
cd ..
git add backend
git commit -m "feat(backend): segredos validados e X-Auth-Id como HMAC-SHA256 do id do usuário" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
cd backend
```


---

### Task 4: Emissão do JWT

**Files:**
- Modify: `backend/pom.xml`, `backend/src/main/java/br/com/autopecas/conta/internal/infra/seguranca/ConfiguracaoDeSeguranca.java`
- Create: `backend/src/main/java/br/com/autopecas/conta/internal/application/{TokenEmitido,EmissorDeToken}.java`, `.../infra/seguranca/EmissorDeTokenJwt.java`
- Test: `backend/src/test/java/br/com/autopecas/conta/internal/infra/seguranca/EmissorDeTokenJwtTest.java`
- Modify: `.../SegurancaPropertiesTest.java` (ganha o teste de que a aplicação não cria o *encoder* com segredo curto)

**Interfaces:**
- Consumes: `SegurancaProperties` (Tarefa 3).
- Produces:
  - `record TokenEmitido(String jwt, Instant expiraEm)` e a porta `EmissorDeToken { TokenEmitido emitir(UUID usuarioId); }`.
  - `EmissorDeTokenJwt`: JWT **HS256** com `iss` = `autopecas`, `sub` = id do usuário, `iat`, `exp` = `iat` + `tokenValidade` e um `jti` novo por token. **Só essas cinco claims**, nenhum dado pessoal.
  - Beans `Clock relogio` (UTC) e `JwtEncoder codificadorDeJwt`, que **falha na subida** com segredo curto.

- [ ] **Step 1: Adicionar a dependência do JWT**

Só o módulo `spring-security-oauth2-jose` (o *encoder* do JWT). Os starters do Spring Security entram na Tarefa 5, junto com a configuração que os torna seguros: sem ela, o Boot protegeria todas as rotas com uma senha gerada.

Replace `backend/pom.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.1</version>
        <relativePath/>
    </parent>
    <groupId>br.com.autopecas</groupId>
    <artifactId>autopecas</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>autopecas</name>
    <description>Marketplace reverso de autopeças: API</description>

    <properties>
        <java.version>21</java.version>
        <spring-modulith.version>2.1.1</spring-modulith.version>
        <archunit.version>1.5.1</archunit.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-liquibase</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.modulith</groupId>
            <artifactId>spring-modulith-starter-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-oauth2-jose</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-testcontainers</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-postgresql</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.modulith</groupId>
            <artifactId>spring-modulith-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.tngtech.archunit</groupId>
            <artifactId>archunit-junit5</artifactId>
            <version>${archunit.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.modulith</groupId>
                <artifactId>spring-modulith-bom</artifactId>
                <version>${spring-modulith.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Escrever os testes**

Create `backend/src/test/java/br/com/autopecas/conta/internal/infra/seguranca/EmissorDeTokenJwtTest.java`:
```java
package br.com.autopecas.conta.internal.infra.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.autopecas.conta.internal.application.TokenEmitido;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class EmissorDeTokenJwtTest {

    private static final Instant AGORA = Instant.parse("2026-09-29T12:00:00Z");
    private static final Duration VALIDADE = Duration.ofMinutes(90);
    private static final UUID USUARIO = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final byte[] CHAVE = chave();

    private static byte[] chave() {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte) 7);
        return bytes;
    }

    private final EmissorDeTokenJwt emissor = new EmissorDeTokenJwt(
            new NimbusJwtEncoder(new ImmutableSecret<>(chaveSecreta())),
            new SegurancaProperties(
                    Base64.getEncoder().encodeToString(CHAVE),
                    Base64.getEncoder().encodeToString(CHAVE),
                    VALIDADE,
                    false),
            Clock.fixed(AGORA, ZoneOffset.UTC));

    private static SecretKey chaveSecreta() {
        return new SecretKeySpec(CHAVE, "HmacSHA256");
    }

    /** Decodifica só a assinatura: o relógio do teste é fixo e o token pode já estar "vencido" para o relógio real. */
    private static Jwt decodificar(String jwt) {
        NimbusJwtDecoder decodificador =
                NimbusJwtDecoder.withSecretKey(chaveSecreta()).macAlgorithm(MacAlgorithm.HS256).build();
        decodificador.setJwtValidator(token -> OAuth2TokenValidatorResult.success());
        return decodificador.decode(jwt);
    }

    @Test
    void oTokenCarregaEmissorUsuarioEEmissaoEValidade() {
        TokenEmitido token = emissor.emitir(USUARIO);

        Jwt jwt = decodificar(token.jwt());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("autopecas");
        assertThat(jwt.getSubject()).isEqualTo(USUARIO.toString());
        assertThat(jwt.getIssuedAt()).isEqualTo(AGORA);
        assertThat(jwt.getExpiresAt()).isEqualTo(AGORA.plus(VALIDADE));
        assertThat(token.expiraEm()).isEqualTo(AGORA.plus(VALIDADE));
    }

    @Test
    void oTokenEAssinadoComHs256() {
        Jwt jwt = decodificar(emissor.emitir(USUARIO).jwt());

        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
    }

    @Test
    void cadaTokenTemUmJtiDiferenteEValido() {
        Jwt primeiro = decodificar(emissor.emitir(USUARIO).jwt());
        Jwt segundo = decodificar(emissor.emitir(USUARIO).jwt());

        assertThat(UUID.fromString(primeiro.getId())).isNotNull();
        assertThat(primeiro.getId()).isNotEqualTo(segundo.getId());
    }

    @Test
    void oTokenNaoTrazDadosPessoaisAlemDoIdDoUsuario() {
        Jwt jwt = decodificar(emissor.emitir(USUARIO).jwt());

        assertThat(jwt.getClaims().keySet()).containsExactlyInAnyOrder("iss", "sub", "iat", "exp", "jti");
    }
}
```

Replace `backend/src/test/java/br/com/autopecas/conta/internal/infra/seguranca/SegurancaPropertiesTest.java`:
```java
package br.com.autopecas.conta.internal.infra.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SegurancaPropertiesTest {

    private static final String SEGREDO_DE_32_BYTES = Base64.getEncoder().encodeToString(new byte[32]);

    private static SegurancaProperties com(String jwtSegredo, String authIdSegredo) {
        return new SegurancaProperties(jwtSegredo, authIdSegredo, Duration.ofHours(1), false);
    }

    @Test
    void aceitaSegredosDe32BytesEmBase64() {
        SegurancaProperties propriedades = com(SEGREDO_DE_32_BYTES, SEGREDO_DE_32_BYTES);

        assertThat(propriedades.jwtChave()).hasSize(32);
        assertThat(propriedades.authIdChave()).hasSize(32);
    }

    @Test
    void recusaOSegredoDoJwtComMenosDe32Bytes() {
        String curto = Base64.getEncoder().encodeToString(new byte[31]);

        assertThatThrownBy(() -> com(curto, SEGREDO_DE_32_BYTES).jwtChave())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt-segredo")
                .hasMessageContaining("32 bytes");
    }

    @Test
    void recusaOSegredoDoAuthIdComMenosDe32Bytes() {
        String curto = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> com(SEGREDO_DE_32_BYTES, curto).authIdChave())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth-id-segredo")
                .hasMessageContaining("32 bytes");
    }

    @Test
    void recusaSegredoQueNaoEstaEmBase64() {
        assertThatThrownBy(() -> com("isto não é base64!!", SEGREDO_DE_32_BYTES).jwtChave())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("base64");
    }

    @Test
    void aAplicacaoNaoCriaOEncoderDeJwtComSegredoCurto() {
        String curto = Base64.getEncoder().encodeToString(new byte[8]);

        assertThatThrownBy(() -> new ConfiguracaoDeSeguranca().codificadorDeJwt(com(curto, SEGREDO_DE_32_BYTES)))
                .isInstanceOf(IllegalStateException.class);
    }
}
```

- [ ] **Step 3: Rodar e ver falhar**

Run: `mvn -q test-compile`
Expected: FAIL com erro de compilação: `cannot find symbol` em `EmissorDeTokenJwtTest` (`EmissorDeTokenJwt` e `TokenEmitido` ainda não existem).

- [ ] **Step 4: Implementar a emissão do JWT**

O `EmissorDeTokenJwt` depende da porta e das propriedades. O teste usa um relógio fixo e decodifica só a assinatura, porque um token de 2026-09-29 já pode estar "vencido" para o relógio real.

Create `backend/src/main/java/br/com/autopecas/conta/internal/application/TokenEmitido.java`:
```java
package br.com.autopecas.conta.internal.application;

import java.time.Instant;

public record TokenEmitido(String jwt, Instant expiraEm) {
}
```

Create `backend/src/main/java/br/com/autopecas/conta/internal/application/EmissorDeToken.java`:
```java
package br.com.autopecas.conta.internal.application;

import java.util.UUID;

/** Porta: quem sabe emitir o token de sessão de um usuário. */
public interface EmissorDeToken {

    TokenEmitido emitir(UUID usuarioId);
}
```

Create `backend/src/main/java/br/com/autopecas/conta/internal/infra/seguranca/EmissorDeTokenJwt.java`:
```java
package br.com.autopecas.conta.internal.infra.seguranca;

import br.com.autopecas.conta.internal.application.EmissorDeToken;
import br.com.autopecas.conta.internal.application.TokenEmitido;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
class EmissorDeTokenJwt implements EmissorDeToken {

    private final JwtEncoder codificador;
    private final SegurancaProperties propriedades;
    private final Clock relogio;

    EmissorDeTokenJwt(JwtEncoder codificador, SegurancaProperties propriedades, Clock relogio) {
        this.codificador = codificador;
        this.propriedades = propriedades;
        this.relogio = relogio;
    }

    @Override
    public TokenEmitido emitir(UUID usuarioId) {
        Instant agora = relogio.instant();
        Instant expiraEm = agora.plus(propriedades.tokenValidade());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ConfiguracaoDeSeguranca.EMISSOR)
                .subject(usuarioId.toString())
                .issuedAt(agora)
                .expiresAt(expiraEm)
                .id(UUID.randomUUID().toString())
                .build();
        JwsHeader cabecalho = JwsHeader.with(MacAlgorithm.HS256).build();
        String jwt = codificador.encode(JwtEncoderParameters.from(cabecalho, claims)).getTokenValue();
        return new TokenEmitido(jwt, expiraEm);
    }
}
```

Replace `backend/src/main/java/br/com/autopecas/conta/internal/infra/seguranca/ConfiguracaoDeSeguranca.java`:
```java
package br.com.autopecas.conta.internal.infra.seguranca;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
@EnableConfigurationProperties(SegurancaProperties.class)
class ConfiguracaoDeSeguranca {

    static final String EMISSOR = "autopecas";
    private static final String ALGORITMO_DA_CHAVE = "HmacSHA256";

    @Bean
    Clock relogio() {
        return Clock.systemUTC();
    }

    @Bean
    JwtEncoder codificadorDeJwt(SegurancaProperties propriedades) {
        SecretKey chave = new SecretKeySpec(propriedades.jwtChave(), ALGORITMO_DA_CHAVE);
        return new NimbusJwtEncoder(new ImmutableSecret<>(chave));
    }
}
```

- [ ] **Step 5: Rodar tudo e ver passar**

Run: `mvn test`
Expected: `BUILD SUCCESS` e `Tests run: 35, Failures: 0, Errors: 0` (35 testes ao todo até aqui). O `SegurancaPropertiesTest` completo garante que um segredo curto **impede** a criação do *encoder*, ou seja, a aplicação não sobe.

- [ ] **Step 6: Commit**

```bash
cd ..
git add backend
git commit -m "feat(backend): emissão de JWT HS256 com jti e validade configurável" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
cd backend
```


---

### Task 5: Cadeia de segurança: JWT no cookie e `X-Auth-Id`

**Files:**
- Modify: `backend/pom.xml` (os dois starters), `backend/src/main/java/br/com/autopecas/conta/internal/infra/seguranca/ConfiguracaoDeSeguranca.java`, `backend/src/test/java/br/com/autopecas/TesteDeIntegracao.java`
- Create: em `backend/src/main/java/br/com/autopecas/`: `compartilhado/{ErroDeNegocio,Identidade,TratadorDeErros}.java`, `conta/internal/infra/seguranca/{ValidadorDeTokenRevogado,CookieDeSessao,CookieBearerTokenResolver,RespostaDeProblema,FiltroXAuthId}.java`
- Test: `backend/src/test/java/br/com/autopecas/ApoioDeTeste.java`, `backend/src/test/java/br/com/autopecas/conta/CadeiaDeSegurancaTest.java`

**Interfaces:**
- Consumes: `EmissorDeToken`, `CalculadoraDeAuthId`, `SegurancaProperties`, `TokenRevogadoRepository`, `UsuarioRepository`.
- Produces:
  - **Regras de acesso:** públicos são só `POST /api/v1/auth/registro`, `POST /api/v1/auth/login` e `/actuator/health`. Todo o resto exige o cookie `access_token` com JWT válido (assinatura HS256, emissor `autopecas`, validade, `jti` presente e **não revogado**) **e** o cabeçalho `X-Auth-Id` igual ao HMAC do `sub` do JWT. A única exceção é `GET /api/v1/me`. CSRF do Spring desligado, sessão sem estado, sem CORS.
  - **Respostas de falha em Problem Details, em português:** `401 "Não autenticado"` (sem token, token inválido, expirado, revogado, com outro segredo, emissor ou algoritmo) e `401 "Não foi possível confirmar o usuário da sessão"` (`X-Auth-Id` ausente ou que não confere). Erros de MVC também saem em Problem Details: `404 "Não encontrado"`, `405 "Método não permitido"`, `415 "Tipo de conteúdo não suportado"`, `400 "Requisição inválida"`, `400 "Corpo da requisição inválido"`, `400 "Dados inválidos"` (com `erros`), `500 "Erro interno do servidor"`, sem *stack trace*.
  - `ErroDeNegocio.requisicaoInvalida(String) | naoAutenticado | naoEncontrado | conflito`; `Identidade.usuarioId(Jwt): UUID`; `CookieDeSessao` (`NOME = "access_token"`, `criar(TokenEmitido): ResponseCookie` com `HttpOnly`, `SameSite=Lax`, `Path=/`, `Max-Age` = validade e `Secure` conforme `cookieSeguro`; `apagar(): ResponseCookie`).
  - Bean `PasswordEncoder` (BCrypt) e `JwtDecoder` com o validador de tokens revogados.
  - **Apoio de teste** `ApoioDeTeste` (`@TestComponent`): `emailUnico()`, `usuario()`, `usuario(String email)`, `vendedor(StatusVendedor)`, `jwtDe(Usuario)`, `cookieDe(Usuario)`, `authIdDe(Usuario)`, `notificacao(Usuario, TipoNotificacao, boolean lida, Instant criadaEm, String titulo)`, e a constante `SENHA`. `@TesteDeIntegracao` passa a importá-lo.

- [ ] **Step 1: Trocar a dependência do JWT pelos starters do Spring Security**

Os starters `spring-boot-starter-security` e `spring-boot-starter-security-oauth2-resource-server` já trazem o *jose*, que sai da lista.

Replace `backend/pom.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.1</version>
        <relativePath/>
    </parent>
    <groupId>br.com.autopecas</groupId>
    <artifactId>autopecas</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>autopecas</name>
    <description>Marketplace reverso de autopeças: API</description>

    <properties>
        <java.version>21</java.version>
        <spring-modulith.version>2.1.1</spring-modulith.version>
        <archunit.version>1.5.1</archunit.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security-oauth2-resource-server</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-liquibase</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.modulith</groupId>
            <artifactId>spring-modulith-starter-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-testcontainers</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-postgresql</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.modulith</groupId>
            <artifactId>spring-modulith-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.tngtech.archunit</groupId>
            <artifactId>archunit-junit5</artifactId>
            <version>${archunit.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.modulith</groupId>
                <artifactId>spring-modulith-bom</artifactId>
                <version>${spring-modulith.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Escrever os testes**

Create `backend/src/test/java/br/com/autopecas/ApoioDeTeste.java`:
```java
package br.com.autopecas;

import br.com.autopecas.conta.internal.application.CalculadoraDeAuthId;
import br.com.autopecas.conta.internal.application.EmissorDeToken;
import br.com.autopecas.conta.internal.domain.PerfilVendedor;
import br.com.autopecas.conta.internal.domain.StatusVendedor;
import br.com.autopecas.conta.internal.domain.Usuario;
import br.com.autopecas.conta.internal.infra.persistencia.PerfilVendedorRepository;
import br.com.autopecas.conta.internal.infra.persistencia.UsuarioRepository;
import br.com.autopecas.conta.internal.infra.seguranca.CookieDeSessao;
import br.com.autopecas.notificacao.internal.domain.Notificacao;
import br.com.autopecas.notificacao.internal.domain.TipoNotificacao;
import br.com.autopecas.notificacao.internal.infra.persistencia.NotificacaoRepository;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Cria usuários, sessões e notificações direto no banco, para os testes não dependerem dos endpoints. */
@TestComponent
public class ApoioDeTeste {

    public static final String SENHA = "senha-de-teste-123";

    // Custo baixo só nos testes: o login confere qualquer custo de BCrypt.
    private static final String HASH_DA_SENHA = new BCryptPasswordEncoder(4).encode(SENHA);

    private final UsuarioRepository usuarios;
    private final PerfilVendedorRepository perfis;
    private final NotificacaoRepository notificacoes;
    private final EmissorDeToken emissor;
    private final CalculadoraDeAuthId calculadora;

    ApoioDeTeste(
            UsuarioRepository usuarios,
            PerfilVendedorRepository perfis,
            NotificacaoRepository notificacoes,
            EmissorDeToken emissor,
            CalculadoraDeAuthId calculadora) {
        this.usuarios = usuarios;
        this.perfis = perfis;
        this.notificacoes = notificacoes;
        this.emissor = emissor;
        this.calculadora = calculadora;
    }

    public String emailUnico() {
        return "usuario-" + UUID.randomUUID() + "@example.com";
    }

    public Usuario usuario() {
        return usuarios.saveAndFlush(Usuario.novo("Usuário de Teste", emailUnico(), HASH_DA_SENHA, Instant.now()));
    }

    public Usuario usuario(String email) {
        return usuarios.saveAndFlush(Usuario.novo("Usuário de Teste", email, HASH_DA_SENHA, Instant.now()));
    }

    public Usuario vendedor(StatusVendedor status) {
        Usuario usuario = usuario();
        perfis.saveAndFlush(PerfilVendedor.de(usuario.getId(), status, Instant.now()));
        return usuario;
    }

    public String jwtDe(Usuario usuario) {
        return emissor.emitir(usuario.getId()).jwt();
    }

    public Cookie cookieDe(Usuario usuario) {
        return new Cookie(CookieDeSessao.NOME, jwtDe(usuario));
    }

    public String authIdDe(Usuario usuario) {
        return calculadora.calcular(usuario.getId());
    }

    public Notificacao notificacao(
            Usuario usuario, TipoNotificacao tipo, boolean lida, Instant criadaEm, String titulo) {
        Notificacao notificacao = Notificacao.nova(usuario.getId(), tipo, titulo, "Resumo de " + titulo, criadaEm);
        notificacao.definirLida(lida);
        return notificacoes.saveAndFlush(notificacao);
    }
}
```

Replace `backend/src/test/java/br/com/autopecas/TesteDeIntegracao.java`:
```java
package br.com.autopecas;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Sobe a aplicação inteira com um PostgreSQL real (Testcontainers) e o MockMvc. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, ApoioDeTeste.class})
public @interface TesteDeIntegracao {
}
```

Create `backend/src/test/java/br/com/autopecas/conta/CadeiaDeSegurancaTest.java`:
```java
package br.com.autopecas.conta;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.autopecas.ApoioDeTeste;
import br.com.autopecas.TesteDeIntegracao;
import br.com.autopecas.conta.internal.domain.Usuario;
import br.com.autopecas.conta.internal.infra.seguranca.CookieDeSessao;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Cookie com o JWT + cabeçalho X-Auth-Id: toda a cadeia de segurança, com uma rota protegida só de teste. */
@TesteDeIntegracao
@Import(CadeiaDeSegurancaTest.ControladorProtegido.class)
class CadeiaDeSegurancaTest {

    private static final String ROTA = "/api/v1/teste/protegido";
    private static final MediaType PROBLEMA = MediaType.valueOf("application/problem+json");

    @RestController
    static class ControladorProtegido {

        @GetMapping(ROTA)
        String ler(@AuthenticationPrincipal Jwt jwt) {
            return jwt.getSubject();
        }

        @PostMapping(ROTA)
        ResponseEntity<Void> escrever() {
            return ResponseEntity.noContent().build();
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApoioDeTeste apoio;

    @Autowired
    JdbcTemplate jdbc;

    @Value("${app.seguranca.jwt-segredo}")
    String jwtSegredo;

    Usuario usuario;

    @BeforeEach
    void criarUsuario() {
        usuario = apoio.usuario();
    }

    private MockHttpServletRequestBuilder autenticada(MockHttpServletRequestBuilder requisicao) {
        return requisicao.cookie(apoio.cookieDe(usuario)).header("X-Auth-Id", apoio.authIdDe(usuario));
    }

    private static String token(
            String segredoBase64, String emissor, String subject, Instant emitidoEm, Instant expiraEm, String jti) {
        var chave = new SecretKeySpec(Base64.getDecoder().decode(segredoBase64), "HmacSHA256");
        var codificador = new NimbusJwtEncoder(new ImmutableSecret<>(chave));
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(emissor)
                .subject(subject)
                .issuedAt(emitidoEm)
                .expiresAt(expiraEm);
        if (jti != null) {
            claims.id(jti);
        }
        return codificador
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
    }

    private String tokenValidoDoUsuario(String jti, Instant expiraEm) {
        return token(
                jwtSegredo, "autopecas", usuario.getId().toString(), Instant.now().minusSeconds(5), expiraEm, jti);
    }

    private Cookie cookie(String jwt) {
        return new Cookie(CookieDeSessao.NOME, jwt);
    }

    @Test
    void semCookieARespostaEUmProblemaDeNaoAutenticado() throws Exception {
        mockMvc.perform(get(ROTA))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEMA))
                .andExpect(jsonPath("$.title").value("Não autenticado"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void cookieValidoMaisOAuthIdCorretoDeixaPassar() throws Exception {
        mockMvc.perform(autenticada(get(ROTA)))
                .andExpect(status().isOk())
                .andExpect(content().string(usuario.getId().toString()));
    }

    @Test
    void cookieValidoSemOAuthIdENegado() throws Exception {
        mockMvc.perform(get(ROTA).cookie(apoio.cookieDe(usuario)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEMA))
                .andExpect(jsonPath("$.title").value("Não foi possível confirmar o usuário da sessão"));
    }

    @Test
    void cookieValidoComOAuthIdDeOutroUsuarioENegado() throws Exception {
        Usuario outro = apoio.usuario();

        mockMvc.perform(get(ROTA).cookie(apoio.cookieDe(usuario)).header("X-Auth-Id", apoio.authIdDe(outro)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Não foi possível confirmar o usuário da sessão"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "abc", "00000000-0000-0000-0000-000000000001"})
    void cookieValidoComOAuthIdInventadoENegado(String forjado) throws Exception {
        mockMvc.perform(get(ROTA).cookie(apoio.cookieDe(usuario)).header("X-Auth-Id", forjado))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void oAuthIdEmMaiusculasOuComEspacoNaoConfere() throws Exception {
        String hash = apoio.authIdDe(usuario);

        mockMvc.perform(get(ROTA).cookie(apoio.cookieDe(usuario)).header("X-Auth-Id", hash.toUpperCase()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(ROTA).cookie(apoio.cookieDe(usuario)).header("X-Auth-Id", hash + " "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void oAuthIdSemOCookieNaoAutentica() throws Exception {
        mockMvc.perform(get(ROTA).header("X-Auth-Id", apoio.authIdDe(usuario)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Não autenticado"));
    }

    @Test
    void tokenExpiradoENegadoMesmoComAssinaturaValida() throws Exception {
        String expirado = token(
                jwtSegredo,
                "autopecas",
                usuario.getId().toString(),
                Instant.now().minus(2, ChronoUnit.HOURS),
                Instant.now().minus(1, ChronoUnit.HOURS),
                UUID.randomUUID().toString());

        mockMvc.perform(get(ROTA).cookie(cookie(expirado)).header("X-Auth-Id", apoio.authIdDe(usuario)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Não autenticado"));
    }

    @Test
    void tokenAssinadoComOutroSegredoENegado() throws Exception {
        String outroSegredo = Base64.getEncoder().encodeToString(new byte[32]);
        String forjado = token(
                outroSegredo,
                "autopecas",
                usuario.getId().toString(),
                Instant.now(),
                Instant.now().plusSeconds(600),
                UUID.randomUUID().toString());

        mockMvc.perform(get(ROTA).cookie(cookie(forjado)).header("X-Auth-Id", apoio.authIdDe(usuario)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenDeOutroEmissorENegado() throws Exception {
        String deOutroEmissor = token(
                jwtSegredo,
                "outro-sistema",
                usuario.getId().toString(),
                Instant.now(),
                Instant.now().plusSeconds(600),
                UUID.randomUUID().toString());

        mockMvc.perform(get(ROTA).cookie(cookie(deOutroEmissor)).header("X-Auth-Id", apoio.authIdDe(usuario)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSemJtiENegado() throws Exception {
        String semJti = token(
                jwtSegredo,
                "autopecas",
                usuario.getId().toString(),
                Instant.now(),
                Instant.now().plusSeconds(600),
                null);

        mockMvc.perform(get(ROTA).cookie(cookie(semJti)).header("X-Auth-Id", apoio.authIdDe(usuario)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenRevogadoENegadoAteDentroDaValidade() throws Exception {
        UUID jti = UUID.randomUUID();
        Instant expiraEm = Instant.now().plusSeconds(600);
        jdbc.update("insert into token_revogado (jti, expira_em) values (?, ?)", jti, Timestamp.from(expiraEm));

        mockMvc.perform(get(ROTA)
                        .cookie(cookie(tokenValidoDoUsuario(jti.toString(), expiraEm)))
                        .header("X-Auth-Id", apoio.authIdDe(usuario)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenCujoSubNaoEUmUuidENegado() throws Exception {
        String subInvalido = token(
                jwtSegredo,
                "autopecas",
                "nao-e-um-uuid",
                Instant.now(),
                Instant.now().plusSeconds(600),
                UUID.randomUUID().toString());

        mockMvc.perform(get(ROTA).cookie(cookie(subInvalido)).header("X-Auth-Id", "qualquer"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Não foi possível confirmar o usuário da sessão"));
    }

    @Test
    void oCabecalhoAuthorizationBearerEIgnorado() throws Exception {
        mockMvc.perform(get(ROTA)
                        .header("Authorization", "Bearer " + apoio.jwtDe(usuario))
                        .header("X-Auth-Id", apoio.authIdDe(usuario)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Não autenticado"));
    }

    @Test
    void tokenSemAssinaturaComAlgNoneENegado() throws Exception {
        Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
        String cabecalho = base64.encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(UTF_8));
        String corpo = base64.encodeToString(("{\"iss\":\"autopecas\",\"sub\":\"" + usuario.getId()
                        + "\",\"exp\":" + Instant.now().plusSeconds(600).getEpochSecond()
                        + ",\"jti\":\"" + UUID.randomUUID() + "\"}")
                .getBytes(UTF_8));

        mockMvc.perform(get(ROTA)
                        .cookie(cookie(cabecalho + "." + corpo + "."))
                        .header("X-Auth-Id", apoio.authIdDe(usuario)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenAssinadoComOutroAlgoritmoENegadoMesmoComOSegredoCerto() throws Exception {
        Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
        String cabecalho = base64.encodeToString("{\"alg\":\"HS512\",\"typ\":\"JWT\"}".getBytes(UTF_8));
        String corpo = base64.encodeToString(("{\"iss\":\"autopecas\",\"sub\":\"" + usuario.getId()
                        + "\",\"exp\":" + Instant.now().plusSeconds(600).getEpochSecond()
                        + ",\"jti\":\"" + UUID.randomUUID() + "\"}")
                .getBytes(UTF_8));
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(jwtSegredo), "HmacSHA512"));
        String assinatura = base64.encodeToString(mac.doFinal((cabecalho + "." + corpo).getBytes(UTF_8)));

        mockMvc.perform(get(ROTA)
                        .cookie(cookie(cabecalho + "." + corpo + "." + assinatura))
                        .header("X-Auth-Id", apoio.authIdDe(usuario)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void oBackendNaoHabilitaCorsParaOutrasOrigens() throws Exception {
        // A defesa contra CSRF depende de outro site não conseguir enviar o X-Auth-Id (cabeçalho personalizado).
        mockMvc.perform(options(ROTA)
                        .header("Origin", "https://site-malicioso.example")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "x-auth-id"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Headers"));

        mockMvc.perform(autenticada(get(ROTA)).header("Origin", "https://site-malicioso.example"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void cookieComOutroNomeNaoAutentica() throws Exception {
        mockMvc.perform(get(ROTA)
                        .cookie(new Cookie("sessao", apoio.jwtDe(usuario)))
                        .header("X-Auth-Id", apoio.authIdDe(usuario)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void metodosQueAlteramEstadoTambemExigemOAuthId() throws Exception {
        mockMvc.perform(post(ROTA).cookie(apoio.cookieDe(usuario)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Não foi possível confirmar o usuário da sessão"));

        mockMvc.perform(autenticada(post(ROTA))).andExpect(status().isNoContent());
    }

    @Test
    void rotaInexistenteAutenticadaResponde404EmPortugues() throws Exception {
        mockMvc.perform(autenticada(get("/api/v1/nao-existe")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEMA))
                .andExpect(jsonPath("$.title").value("Não encontrado"));
    }

    @Test
    void rotaInexistenteSemAutenticacaoResponde401ENaoVazaQueElaNaoExiste() throws Exception {
        mockMvc.perform(get("/api/v1/nao-existe")).andExpect(status().isUnauthorized());
    }

    @Test
    void metodoNaoPermitidoResponde405EmPortugues() throws Exception {
        mockMvc.perform(autenticada(delete(ROTA)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.title").value("Método não permitido"));
    }

    @Test
    void aRespostaDeErroNaoVazaDetalhesInternos() throws Exception {
        mockMvc.perform(get(ROTA))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Exception"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("at br.com"))));
    }
}
```

- [ ] **Step 3: Rodar e ver falhar**

Run: `mvn -q test-compile`
Expected: FAIL com erro de compilação: `cannot find symbol` em `ApoioDeTeste` (`CookieDeSessao` ainda não existe).

- [ ] **Step 4: Implementar a cadeia de segurança e o tratamento de erros**

Pontos que não são óbvios e estão comentados no código:
- O `FiltroXAuthId` **não** é um `@Component`: o Boot o registraria também fora da cadeia de segurança, e ele rodaria duas vezes.
- O `CookieBearerTokenResolver` só lê o cookie (o cabeçalho `Authorization` é ignorado) e **não** lê nada nas rotas públicas de entrada, para um cookie velho ou inválido nunca impedir um novo login.
- O `TratadorDeErros` estende `ResponseEntityExceptionHandler`: os erros padrão do Spring MVC (404, 405, 415…) também saem em Problem Details, com o título traduzido.

Create `backend/src/main/java/br/com/autopecas/compartilhado/ErroDeNegocio.java`:
```java
package br.com.autopecas.compartilhado;

import org.springframework.http.HttpStatus;

/** Erro esperado da regra de negócio. Vira uma resposta Problem Details (RFC 9457). */
public class ErroDeNegocio extends RuntimeException {

    private final HttpStatus status;

    private ErroDeNegocio(HttpStatus status, String mensagem) {
        super(mensagem);
        this.status = status;
    }

    public static ErroDeNegocio requisicaoInvalida(String mensagem) {
        return new ErroDeNegocio(HttpStatus.BAD_REQUEST, mensagem);
    }

    public static ErroDeNegocio naoAutenticado(String mensagem) {
        return new ErroDeNegocio(HttpStatus.UNAUTHORIZED, mensagem);
    }

    public static ErroDeNegocio naoEncontrado(String mensagem) {
        return new ErroDeNegocio(HttpStatus.NOT_FOUND, mensagem);
    }

    public static ErroDeNegocio conflito(String mensagem) {
        return new ErroDeNegocio(HttpStatus.CONFLICT, mensagem);
    }

    public HttpStatus status() {
        return status;
    }
}
```

Create `backend/src/main/java/br/com/autopecas/compartilhado/Identidade.java`:
```java
package br.com.autopecas.compartilhado;

import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** Quem está autenticado na requisição. O filtro de segurança já garantiu que o "sub" é um UUID. */
public final class Identidade {

    private Identidade() {
    }

    public static UUID usuarioId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
```

Create `backend/src/main/java/br/com/autopecas/compartilhado/TratadorDeErros.java`:
```java
package br.com.autopecas.compartilhado;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Toda falha da API sai como Problem Details (RFC 9457), com o texto do título em português. */
@RestControllerAdvice
class TratadorDeErros extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(TratadorDeErros.class);

    @ExceptionHandler(ErroDeNegocio.class)
    ResponseEntity<ProblemDetail> negocio(ErroDeNegocio erro) {
        return ResponseEntity.status(erro.status()).body(problema(erro.status(), erro.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> inesperado(Exception erro) {
        LOG.error("Erro inesperado", erro);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(problema(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno do servidor"));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException erro, HttpHeaders cabecalhos, HttpStatusCode status, WebRequest requisicao) {
        ProblemDetail problema = problema(status, "Dados inválidos");
        Map<String, String> erros = new LinkedHashMap<>();
        erro.getBindingResult().getFieldErrors().forEach(e -> erros.putIfAbsent(e.getField(), e.getDefaultMessage()));
        problema.setProperty("erros", erros);
        return ResponseEntity.status(status).body(problema);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException erro, HttpHeaders cabecalhos, HttpStatusCode status, WebRequest requisicao) {
        return ResponseEntity.status(status).body(problema(status, "Corpo da requisição inválido"));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception erro, Object corpo, HttpHeaders cabecalhos, HttpStatusCode status, WebRequest requisicao) {
        ResponseEntity<Object> resposta = super.handleExceptionInternal(erro, corpo, cabecalhos, status, requisicao);
        if (resposta != null && resposta.getBody() instanceof ProblemDetail problema) {
            problema.setTitle(tituloPadrao(status));
        }
        return resposta;
    }

    private static ProblemDetail problema(HttpStatusCode status, String titulo) {
        ProblemDetail problema = ProblemDetail.forStatus(status);
        problema.setTitle(titulo);
        return problema;
    }

    private static String tituloPadrao(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "Requisição inválida";
            case 401 -> "Não autenticado";
            case 403 -> "Acesso negado";
            case 404 -> "Não encontrado";
            case 405 -> "Método não permitido";
            case 415 -> "Tipo de conteúdo não suportado";
            default -> "Erro ao processar a requisição";
        };
    }
}
```

Create `backend/src/main/java/br/com/autopecas/conta/internal/infra/seguranca/ValidadorDeTokenRevogado.java`:
```java
package br.com.autopecas.conta.internal.infra.seguranca;

import br.com.autopecas.conta.internal.infra.persistencia.TokenRevogadoRepository;
import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** Recusa o token de uma sessão que já fez logout, mesmo que ele ainda esteja dentro da validade. */
class ValidadorDeTokenRevogado implements OAuth2TokenValidator<Jwt> {

    private final TokenRevogadoRepository revogados;

    ValidadorDeTokenRevogado(TokenRevogadoRepository revogados) {
        this.revogados = revogados;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        UUID jti;
        try {
            jti = UUID.fromString(jwt.getId());
        } catch (IllegalArgumentException | NullPointerException erro) {
            return falha("Token sem identificador válido.");
        }
        if (revogados.existsById(jti)) {
            return falha("Token revogado.");
        }
        return OAuth2TokenValidatorResult.success();
    }

    private static OAuth2TokenValidatorResult falha(String descricao) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", descricao, null));
    }
}
```

Create `backend/src/main/java/br/com/autopecas/conta/internal/infra/seguranca/CookieDeSessao.java`:
```java
package br.com.autopecas.conta.internal.infra.seguranca;

import br.com.autopecas.conta.internal.application.TokenEmitido;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** O JWT vai num cookie HttpOnly: o JavaScript da página nunca lê o token. */
@Component
public class CookieDeSessao {

    public static final String NOME = "access_token";

    private final SegurancaProperties propriedades;

    CookieDeSessao(SegurancaProperties propriedades) {
        this.propriedades = propriedades;
    }

    public ResponseCookie criar(TokenEmitido token) {
        return base(token.jwt()).maxAge(propriedades.tokenValidade()).build();
    }

    public ResponseCookie apagar() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String valor) {
        return ResponseCookie.from(NOME, valor)
                .httpOnly(true)
                .secure(propriedades.cookieSeguro())
                .sameSite("Lax")
                .path("/");
    }
}
```

Create `backend/src/main/java/br/com/autopecas/conta/internal/infra/seguranca/CookieBearerTokenResolver.java`:
```java
package br.com.autopecas.conta.internal.infra.seguranca;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Set;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

/**
 * Lê o JWT só do cookie de sessão. O cabeçalho Authorization é ignorado de propósito.
 * Nas rotas públicas de entrada o cookie também é ignorado: um cookie antigo ou inválido
 * não pode impedir quem quer fazer login de novo.
 */
class CookieBearerTokenResolver implements BearerTokenResolver {

    private static final Set<String> ROTAS_PUBLICAS = Set.of("/api/v1/auth/login", "/api/v1/auth/registro");

    @Override
    public String resolve(HttpServletRequest requisicao) {
        if (ROTAS_PUBLICAS.contains(requisicao.getRequestURI())) {
            return null;
        }
        Cookie[] cookies = requisicao.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> CookieDeSessao.NOME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(valor -> valor != null && !valor.isBlank())
                .findFirst()
                .orElse(null);
    }
}
```

Create `backend/src/main/java/br/com/autopecas/conta/internal/infra/seguranca/RespostaDeProblema.java`:
```java
package br.com.autopecas.conta.internal.infra.seguranca;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Resposta Problem Details escrita à mão, para falhas que acontecem antes de chegar a um controller. */
final class RespostaDeProblema {

    private RespostaDeProblema() {
    }

    /** O título é sempre um texto fixo nosso, sem aspas, então não precisa de escape. */
    static void escrever(HttpServletResponse resposta, int status, String titulo) throws IOException {
        resposta.setStatus(status);
        resposta.setContentType("application/problem+json");
        resposta.setCharacterEncoding("UTF-8");
        resposta.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + titulo + "\",\"status\":" + status + "}");
    }
}
```

Create `backend/src/main/java/br/com/autopecas/conta/internal/infra/seguranca/FiltroXAuthId.java`:
```java
package br.com.autopecas.conta.internal.infra.seguranca;

import br.com.autopecas.conta.internal.application.CalculadoraDeAuthId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Confirma o usuário da sessão: o cabeçalho X-Auth-Id precisa ser o HMAC do "sub" do JWT.
 * Só o nosso frontend sabe o valor (ele vem de GET /api/v1/me), e um cabeçalho personalizado não sai de um
 * formulário de outro site, então este filtro também protege contra CSRF.
 * Não é um @Component de propósito: assim ele roda só dentro da cadeia de segurança, e não duas vezes.
 */
class FiltroXAuthId extends OncePerRequestFilter {

    static final String CABECALHO = "X-Auth-Id";

    private final CalculadoraDeAuthId calculadora;

    FiltroXAuthId(CalculadoraDeAuthId calculadora) {
        this.calculadora = calculadora;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao instanceof JwtAuthenticationToken token && exigeConfirmacao(requisicao)) {
            UUID usuarioId = comoUuid(token.getName());
            if (usuarioId == null || !calculadora.confere(usuarioId, requisicao.getHeader(CABECALHO))) {
                SecurityContextHolder.clearContext();
                RespostaDeProblema.escrever(resposta, 401, "Não foi possível confirmar o usuário da sessão");
                return;
            }
        }
        cadeia.doFilter(requisicao, resposta);
    }

    /** GET /api/v1/me é a única exceção: é por ele que o frontend descobre o valor do X-Auth-Id. */
    private static boolean exigeConfirmacao(HttpServletRequest requisicao) {
        boolean ehMe = "GET".equals(requisicao.getMethod()) && "/api/v1/me".equals(requisicao.getRequestURI());
        return !ehMe;
    }

    private static UUID comoUuid(String texto) {
        try {
            return UUID.fromString(texto);
        } catch (IllegalArgumentException | NullPointerException erro) {
            return null;
        }
    }
}
```

Replace `backend/src/main/java/br/com/autopecas/conta/internal/infra/seguranca/ConfiguracaoDeSeguranca.java`:
```java
package br.com.autopecas.conta.internal.infra.seguranca;

import br.com.autopecas.conta.internal.application.CalculadoraDeAuthId;
import br.com.autopecas.conta.internal.infra.persistencia.TokenRevogadoRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

@Configuration
@EnableConfigurationProperties(SegurancaProperties.class)
class ConfiguracaoDeSeguranca {

    static final String EMISSOR = "autopecas";
    private static final String ALGORITMO_DA_CHAVE = "HmacSHA256";

    @Bean
    PasswordEncoder codificadorDeSenha() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    Clock relogio() {
        return Clock.systemUTC();
    }

    @Bean
    JwtEncoder codificadorDeJwt(SegurancaProperties propriedades) {
        SecretKey chave = new SecretKeySpec(propriedades.jwtChave(), ALGORITMO_DA_CHAVE);
        return new NimbusJwtEncoder(new ImmutableSecret<>(chave));
    }

    @Bean
    JwtDecoder decodificadorDeJwt(SegurancaProperties propriedades, TokenRevogadoRepository revogados) {
        SecretKey chave = new SecretKeySpec(propriedades.jwtChave(), ALGORITMO_DA_CHAVE);
        NimbusJwtDecoder decodificador =
                NimbusJwtDecoder.withSecretKey(chave).macAlgorithm(MacAlgorithm.HS256).build();
        decodificador.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(EMISSOR), new ValidadorDeTokenRevogado(revogados)));
        return decodificador;
    }

    /**
     * CSRF do Spring fica desligado de propósito: o cookie é SameSite=Lax e toda requisição autenticada
     * (menos GET /api/v1/me) exige o cabeçalho X-Auth-Id, que outro site não consegue enviar.
     */
    @Bean
    SecurityFilterChain cadeiaDeSeguranca(HttpSecurity http, CalculadoraDeAuthId calculadora) throws Exception {
        AuthenticationEntryPoint naoAutenticado =
                (requisicao, resposta, erro) -> RespostaDeProblema.escrever(resposta, 401, "Não autenticado");
        AccessDeniedHandler acessoNegado =
                (requisicao, resposta, erro) -> RespostaDeProblema.escrever(resposta, 403, "Acesso negado");

        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(regras -> regras
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/registro", "/api/v1/auth/login")
                        .permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(recurso -> recurso
                        .bearerTokenResolver(new CookieBearerTokenResolver())
                        .authenticationEntryPoint(naoAutenticado)
                        .jwt(Customizer.withDefaults()))
                .exceptionHandling(erros -> erros.authenticationEntryPoint(naoAutenticado)
                        .accessDeniedHandler(acessoNegado))
                .addFilterAfter(new FiltroXAuthId(calculadora), BearerTokenAuthenticationFilter.class);
        return http.build();
    }
}
```

- [ ] **Step 5: Rodar tudo e ver passar**

Run: `mvn test`
Expected: `BUILD SUCCESS` e `Tests run: 61, Failures: 0, Errors: 0` (61 testes ao todo até aqui). São os 26 testes da cadeia de segurança, incluindo token `alg: none`, token HS512 com o segredo certo e a ausência de CORS.

- [ ] **Step 6: Commit**

```bash
cd ..
git add backend
git commit -m "feat(backend): cadeia de segurança com JWT em cookie HttpOnly, X-Auth-Id e revogação de token" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
cd backend
```


---

### Task 6: Cadastro e login

**Files:**
- Create: em `backend/src/main/java/br/com/autopecas/conta/internal/`: `application/{SessaoDoUsuario,ResultadoDeLogin,SessaoService,RegistroService,AutenticacaoService}.java`, `web/{RegistroRequest,RegistroResponse,LoginRequest,SessaoResponse,AuthController}.java`
- Test: `backend/src/test/java/br/com/autopecas/conta/{RegistroTest,LoginTest,LoginCookieSeguroTest}.java`

**Interfaces:**
- Consumes: `EmissorDeToken`, `CalculadoraDeAuthId`, `CookieDeSessao`, `UsuarioRepository`, `PerfilVendedorRepository`, `PasswordEncoder`, `ErroDeNegocio`, `ApoioDeTeste`.
- Produces:
  - `POST /api/v1/auth/registro` `{ nome, email, senha }` → `201 { id, nome, email }`. `400 "Dados inválidos"` com `erros.<campo>` (nome de 1 a 120 caracteres, e-mail válido de até 254, senha com **pelo menos 8** caracteres). `400 "A senha pode ter no máximo 72 bytes."` (**bytes** em UTF-8, o limite do BCrypt). `409 "Já existe uma conta com este e-mail."`, ignorando caixa e espaços, inclusive numa corrida entre duas requisições. Não abre sessão. Espaços nas pontas do nome e do e-mail são removidos **antes** da validação.
  - `POST /api/v1/auth/login` `{ email, senha }` → `200 { id, nome, email, vendedor, authId }` mais `Set-Cookie: access_token=<jwt>`. `401 "E-mail ou senha inválidos."` **idêntico** para e-mail inexistente e senha errada (e um hash falso é conferido sempre). Senha com mais de 72 bytes dá `401`, não `500`. Nenhum `Set-Cookie` na falha.
  - `SessaoDoUsuario(UUID id, String nome, String email, StatusVendedor vendedor, String authId)`; `SessaoService.montar(Usuario)` e `obter(UUID)` (usuário removido → `401 "Sessão inválida."`); `RegistroService.registrar(nome, email, senha): Usuario`; `AutenticacaoService.autenticar(email, senha): ResultadoDeLogin`.
  - Corpo de sessão (`SessaoResponse`): `vendedor` é `null` ou `{ "status": "PENDENTE|APROVADO|REPROVADO|SUSPENSO" }`.

- [ ] **Step 1: Escrever os testes**

Create `backend/src/test/java/br/com/autopecas/conta/RegistroTest.java`:
```java
package br.com.autopecas.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.autopecas.ApoioDeTeste;
import br.com.autopecas.TesteDeIntegracao;
import br.com.autopecas.conta.internal.domain.Usuario;
import br.com.autopecas.conta.internal.infra.persistencia.UsuarioRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@TesteDeIntegracao
class RegistroTest {

    private static final String ROTA = "/api/v1/auth/registro";
    private static final String SENHA_VALIDA = "senha-valida-1";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApoioDeTeste apoio;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    PasswordEncoder codificador;

    private static String json(String nome, String email, String senha) {
        return """
                {"nome": "%s", "email": "%s", "senha": "%s"}""".formatted(nome, email, senha);
    }

    private ResultActions registrar(String corpo) throws Exception {
        return mockMvc.perform(post(ROTA).contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    @Test
    void registraEDevolveOUsuarioSemDadosSecretos() throws Exception {
        String email = apoio.emailUnico();

        registrar(json("  Ana Souza ", email, SENHA_VALIDA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.nome").value("Ana Souza"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.senhaHash").doesNotExist())
                .andExpect(content().string(Matchers.not(Matchers.containsString(SENHA_VALIDA))));
    }

    @Test
    void guardaOEmailEmMinusculasEASenhaComBcrypt() throws Exception {
        String email = apoio.emailUnico();

        registrar(json("Ana", "  " + email.toUpperCase() + " ", SENHA_VALIDA)).andExpect(status().isCreated());

        Usuario salvo = usuarios.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(salvo.getEmail()).isEqualTo(email.toLowerCase());
        assertThat(salvo.getSenhaHash()).startsWith("$2").isNotEqualTo(SENHA_VALIDA);
        assertThat(codificador.matches(SENHA_VALIDA, salvo.getSenhaHash())).isTrue();
    }

    @Test
    void oRegistroNaoAbreSessao() throws Exception {
        registrar(json("Ana", apoio.emailUnico(), SENHA_VALIDA))
                .andExpect(status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .doesNotExist("Set-Cookie"));
    }

    @Test
    void emailJaCadastradoDa409() throws Exception {
        String email = apoio.emailUnico();
        registrar(json("Ana", email, SENHA_VALIDA)).andExpect(status().isCreated());

        registrar(json("Outra Ana", email, SENHA_VALIDA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Já existe uma conta com este e-mail."));
    }

    @Test
    void emailJaCadastradoIgnoraCaixaEEspacos() throws Exception {
        String email = apoio.emailUnico();
        registrar(json("Ana", email, SENHA_VALIDA)).andExpect(status().isCreated());

        registrar(json("Ana", " " + email.toUpperCase() + " ", SENHA_VALIDA)).andExpect(status().isConflict());
    }

    static Stream<Arguments> cadastrosInvalidos() {
        return Stream.of(
                Arguments.of("nome vazio", json("", "a@example.com", SENHA_VALIDA), "nome"),
                Arguments.of("nome só com espaços", json("   ", "a@example.com", SENHA_VALIDA), "nome"),
                Arguments.of("nome longo demais", json("x".repeat(121), "a@example.com", SENHA_VALIDA), "nome"),
                Arguments.of("e-mail vazio", json("Ana", "", SENHA_VALIDA), "email"),
                Arguments.of("e-mail sem arroba", json("Ana", "ana.example.com", SENHA_VALIDA), "email"),
                Arguments.of(
                        "e-mail longo demais", json("Ana", "a".repeat(250) + "@example.com", SENHA_VALIDA), "email"),
                Arguments.of("senha vazia", json("Ana", "a@example.com", ""), "senha"),
                Arguments.of("senha curta", json("Ana", "a@example.com", "1234567"), "senha"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cadastrosInvalidos")
    void dadosInvalidosDao400ComOCampoComErro(String descricao, String corpo, String campo) throws Exception {
        registrar(corpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Dados inválidos"))
                .andExpect(jsonPath("$.erros." + campo).isNotEmpty());
    }

    @Test
    void aSenhaDeExatamente72BytesEAceita() throws Exception {
        registrar(json("Ana", apoio.emailUnico(), "a".repeat(72))).andExpect(status().isCreated());
        registrar(json("Ana", apoio.emailUnico(), "é".repeat(36))).andExpect(status().isCreated());
    }

    @Test
    void aSenhaComMaisDe72BytesDa400EmVezDeErroInterno() throws Exception {
        registrar(json("Ana", apoio.emailUnico(), "a".repeat(73)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("A senha pode ter no máximo 72 bytes."));
        // 37 caracteres acentuados passam de 72 bytes em UTF-8, mesmo tendo menos de 72 caracteres.
        registrar(json("Ana", apoio.emailUnico(), "é".repeat(37)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("A senha pode ter no máximo 72 bytes."));
    }

    @Test
    void jsonMalformadoDa400() throws Exception {
        registrar("{\"nome\": ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Corpo da requisição inválido"));
    }

    @Test
    void semCorpoDa400() throws Exception {
        mockMvc.perform(post(ROTA).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Corpo da requisição inválido"));
    }

    @Test
    void semContentTypeJsonDa415() throws Exception {
        mockMvc.perform(post(ROTA).content(json("Ana", apoio.emailUnico(), SENHA_VALIDA)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.title").value("Tipo de conteúdo não suportado"));
    }

    @Test
    void duasRequisicoesSimultaneasComOMesmoEmailNuncaDao500() throws Exception {
        String email = apoio.emailUnico();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<Integer>> resultados = IntStream.range(0, 2)
                    .mapToObj(i -> pool.submit(() -> {
                        largada.await();
                        return registrar(json("Ana", email, SENHA_VALIDA))
                                .andReturn()
                                .getResponse()
                                .getStatus();
                    }))
                    .toList();
            largada.countDown();
            List<Integer> status = new ArrayList<>();
            for (Future<Integer> resultado : resultados) {
                status.add(resultado.get(30, TimeUnit.SECONDS));
            }

            assertThat(status).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdownNow();
        }
    }
}
```

Create `backend/src/test/java/br/com/autopecas/conta/LoginTest.java`:
```java
package br.com.autopecas.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.autopecas.ApoioDeTeste;
import br.com.autopecas.TesteDeIntegracao;
import br.com.autopecas.conta.internal.domain.StatusVendedor;
import br.com.autopecas.conta.internal.domain.Usuario;
import br.com.autopecas.conta.internal.infra.seguranca.CookieDeSessao;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@TesteDeIntegracao
class LoginTest {

    private static final String ROTA = "/api/v1/auth/login";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApoioDeTeste apoio;

    @Autowired
    JwtDecoder decodificador;

    private static String json(String email, String senha) {
        return """
                {"email": "%s", "senha": "%s"}""".formatted(email, senha);
    }

    private MockHttpServletRequestBuilder login(String email, String senha) {
        return post(ROTA).contentType(MediaType.APPLICATION_JSON).content(json(email, senha));
    }

    private static String setCookie(ResultActions resultado) {
        return resultado.andReturn().getResponse().getHeader("Set-Cookie");
    }

    @Test
    void loginCorretoDevolveASessaoComOAuthId() throws Exception {
        Usuario usuario = apoio.usuario();

        mockMvc.perform(login(usuario.getEmail(), ApoioDeTeste.SENHA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(usuario.getId().toString()))
                .andExpect(jsonPath("$.nome").value("Usuário de Teste"))
                .andExpect(jsonPath("$.email").value(usuario.getEmail()))
                .andExpect(jsonPath("$.vendedor").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.authId").value(apoio.authIdDe(usuario)))
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.senhaHash").doesNotExist());
    }

    @Test
    void oCookieDeSessaoEHttpOnlySameSiteLaxEValeUmaHora() throws Exception {
        Usuario usuario = apoio.usuario();

        String cabecalho = setCookie(mockMvc.perform(login(usuario.getEmail(), ApoioDeTeste.SENHA)));

        assertThat(cabecalho)
                .startsWith(CookieDeSessao.NOME + "=")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .contains("Path=/")
                .contains("Max-Age=3600")
                .doesNotContain("Secure");
    }

    @Test
    void oJwtDoCookieIdentificaOUsuario() throws Exception {
        Usuario usuario = apoio.usuario();

        MockHttpServletResponse resposta = mockMvc.perform(login(usuario.getEmail(), ApoioDeTeste.SENHA))
                .andReturn()
                .getResponse();

        Cookie cookie = resposta.getCookie(CookieDeSessao.NOME);
        assertThat(cookie).isNotNull();
        Jwt jwt = decodificador.decode(cookie.getValue());
        assertThat(jwt.getSubject()).isEqualTo(usuario.getId().toString());
        assertThat(jwt.getId()).isNotBlank();
    }

    @Test
    void cadaLoginGeraUmTokenDiferente() throws Exception {
        Usuario usuario = apoio.usuario();

        Cookie primeiro = mockMvc.perform(login(usuario.getEmail(), ApoioDeTeste.SENHA))
                .andReturn()
                .getResponse()
                .getCookie(CookieDeSessao.NOME);
        Cookie segundo = mockMvc.perform(login(usuario.getEmail(), ApoioDeTeste.SENHA))
                .andReturn()
                .getResponse()
                .getCookie(CookieDeSessao.NOME);

        assertThat(primeiro.getValue()).isNotEqualTo(segundo.getValue());
    }

    @Test
    void oLoginDoVendedorTrazOStatusDoPerfil() throws Exception {
        Usuario vendedor = apoio.vendedor(StatusVendedor.APROVADO);

        mockMvc.perform(login(vendedor.getEmail(), ApoioDeTeste.SENHA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendedor.status").value("APROVADO"));
    }

    @Test
    void oEmailNaoDependeDeCaixaNemDeEspacosNasPontas() throws Exception {
        Usuario usuario = apoio.usuario();

        mockMvc.perform(login("  " + usuario.getEmail().toUpperCase() + "  ", ApoioDeTeste.SENHA))
                .andExpect(status().isOk());
    }

    @Test
    void senhaErradaEEmailInexistenteDaoARespostaIdentica() throws Exception {
        Usuario usuario = apoio.usuario();

        String senhaErrada = mockMvc.perform(login(usuario.getEmail(), "senha-errada"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("E-mail ou senha inválidos."))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String emailInexistente = mockMvc.perform(login(apoio.emailUnico(), ApoioDeTeste.SENHA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("E-mail ou senha inválidos."))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(senhaErrada).isEqualTo(emailInexistente);
    }

    @Test
    void senhaComMaisDe72BytesDa401EmVezDeErroInterno() throws Exception {
        Usuario usuario = apoio.usuario();

        mockMvc.perform(login(usuario.getEmail(), "a".repeat(200)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("E-mail ou senha inválidos."));
    }

    @Test
    void camposEmBrancoDao400() throws Exception {
        mockMvc.perform(login("", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Dados inválidos"))
                .andExpect(jsonPath("$.erros.email").isNotEmpty())
                .andExpect(jsonPath("$.erros.senha").isNotEmpty());
    }

    @Test
    void umCookieAntigoOuInvalidoNaoImpedeOLogin() throws Exception {
        Usuario usuario = apoio.usuario();

        mockMvc.perform(login(usuario.getEmail(), ApoioDeTeste.SENHA).cookie(new Cookie(CookieDeSessao.NOME, "lixo")))
                .andExpect(status().isOk());
    }

    @Test
    void umCookieInvalidoTambemNaoImpedeOCadastro() throws Exception {
        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie(CookieDeSessao.NOME, "lixo"))
                        .content("""
                                {"nome": "Ana", "email": "%s", "senha": "senha-valida-1"}"""
                                .formatted(apoio.emailUnico())))
                .andExpect(status().isCreated());
    }
}
```

Create `backend/src/test/java/br/com/autopecas/conta/LoginCookieSeguroTest.java`:
```java
package br.com.autopecas.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.autopecas.ApoioDeTeste;
import br.com.autopecas.TesteDeIntegracao;
import br.com.autopecas.conta.internal.domain.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Em produção o cookie só trafega por HTTPS (app.seguranca.cookie-seguro=true, o padrão do application.properties). */
@TesteDeIntegracao
@TestPropertySource(properties = "app.seguranca.cookie-seguro=true")
class LoginCookieSeguroTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApoioDeTeste apoio;

    @Test
    void oCookieDeSessaoVemMarcadoComoSecure() throws Exception {
        Usuario usuario = apoio.usuario();

        String cabecalho = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "senha": "%s"}"""
                                .formatted(usuario.getEmail(), ApoioDeTeste.SENHA)))
                .andReturn()
                .getResponse()
                .getHeader("Set-Cookie");

        assertThat(cabecalho).contains("Secure").contains("HttpOnly").contains("SameSite=Lax");
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn -q test -Dtest=RegistroTest,LoginTest,LoginCookieSeguroTest`
Expected: FAIL: os testes de `RegistroTest`, `LoginTest` e `LoginCookieSeguroTest` falham com `Status expected:<201> but was:<404>` e similares, porque as rotas ainda não existem.

- [ ] **Step 3: Implementar o cadastro e o login**

O `AuthController` desta tarefa só tem registro e login. O logout entra na Tarefa 7, e o arquivo é substituído por completo lá.

Create `backend/src/main/java/br/com/autopecas/conta/internal/application/SessaoDoUsuario.java`:
```java
package br.com.autopecas.conta.internal.application;

import br.com.autopecas.conta.internal.domain.StatusVendedor;
import java.util.UUID;

/** vendedor é null para quem só tem o perfil de comprador. */
public record SessaoDoUsuario(UUID id, String nome, String email, StatusVendedor vendedor, String authId) {
}
```

Create `backend/src/main/java/br/com/autopecas/conta/internal/application/ResultadoDeLogin.java`:
```java
package br.com.autopecas.conta.internal.application;

public record ResultadoDeLogin(SessaoDoUsuario sessao, TokenEmitido token) {
}
```

Create `backend/src/main/java/br/com/autopecas/conta/internal/application/SessaoService.java`:
```java
package br.com.autopecas.conta.internal.application;

import br.com.autopecas.compartilhado.ErroDeNegocio;
import br.com.autopecas.conta.internal.domain.PerfilVendedor;
import br.com.autopecas.conta.internal.domain.StatusVendedor;
import br.com.autopecas.conta.internal.domain.Usuario;
import br.com.autopecas.conta.internal.infra.persistencia.PerfilVendedorRepository;
import br.com.autopecas.conta.internal.infra.persistencia.UsuarioRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessaoService {

    private final UsuarioRepository usuarios;
    private final PerfilVendedorRepository perfis;
    private final CalculadoraDeAuthId calculadora;

    SessaoService(UsuarioRepository usuarios, PerfilVendedorRepository perfis, CalculadoraDeAuthId calculadora) {
        this.usuarios = usuarios;
        this.perfis = perfis;
        this.calculadora = calculadora;
    }

    @Transactional(readOnly = true)
    public SessaoDoUsuario montar(Usuario usuario) {
        StatusVendedor vendedor = perfis.findById(usuario.getId()).map(PerfilVendedor::getStatus).orElse(null);
        return new SessaoDoUsuario(
                usuario.getId(), usuario.getNome(), usuario.getEmail(), vendedor, calculadora.calcular(usuario.getId()));
    }

    /** Um usuário removido não tem mais sessão, mesmo com um token ainda dentro da validade. */
    @Transactional(readOnly = true)
    public SessaoDoUsuario obter(UUID usuarioId) {
        Usuario usuario = usuarios.findById(usuarioId)
                .orElseThrow(() -> ErroDeNegocio.naoAutenticado("Sessão inválida."));
        return montar(usuario);
    }
}
```

Create `backend/src/main/java/br/com/autopecas/conta/internal/application/RegistroService.java`:
```java
package br.com.autopecas.conta.internal.application;

import static java.nio.charset.StandardCharsets.UTF_8;

import br.com.autopecas.compartilhado.ErroDeNegocio;
import br.com.autopecas.conta.internal.domain.Usuario;
import br.com.autopecas.conta.internal.infra.persistencia.UsuarioRepository;
import java.time.Clock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistroService {

    /** O BCrypt só lê os primeiros 72 bytes da senha. */
    static final int LIMITE_DA_SENHA_EM_BYTES = 72;

    private final UsuarioRepository usuarios;
    private final PasswordEncoder codificador;
    private final Clock relogio;

    RegistroService(UsuarioRepository usuarios, PasswordEncoder codificador, Clock relogio) {
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.relogio = relogio;
    }

    @Transactional
    public Usuario registrar(String nome, String email, String senha) {
        if (senha.getBytes(UTF_8).length > LIMITE_DA_SENHA_EM_BYTES) {
            throw ErroDeNegocio.requisicaoInvalida("A senha pode ter no máximo 72 bytes.");
        }
        if (usuarios.existsByEmailIgnoreCase(email.trim())) {
            throw ErroDeNegocio.conflito("Já existe uma conta com este e-mail.");
        }
        try {
            return usuarios.saveAndFlush(Usuario.novo(nome, email, codificador.encode(senha), relogio.instant()));
        } catch (DataIntegrityViolationException corrida) {
            // Duas requisições simultâneas com o mesmo e-mail: a segunda bate no índice único.
            throw ErroDeNegocio.conflito("Já existe uma conta com este e-mail.");
        }
    }
}
```

Create `backend/src/main/java/br/com/autopecas/conta/internal/application/AutenticacaoService.java`:
```java
package br.com.autopecas.conta.internal.application;

import static java.nio.charset.StandardCharsets.UTF_8;

import br.com.autopecas.compartilhado.ErroDeNegocio;
import br.com.autopecas.conta.internal.domain.Usuario;
import br.com.autopecas.conta.internal.infra.persistencia.UsuarioRepository;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutenticacaoService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder codificador;
    private final EmissorDeToken emissor;
    private final SessaoService sessoes;
    private final String hashFalso;

    AutenticacaoService(
            UsuarioRepository usuarios, PasswordEncoder codificador, EmissorDeToken emissor, SessaoService sessoes) {
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.emissor = emissor;
        this.sessoes = sessoes;
        this.hashFalso = codificador.encode("senha-que-nunca-sera-usada");
    }

    @Transactional(readOnly = true)
    public ResultadoDeLogin autenticar(String email, String senha) {
        Optional<Usuario> encontrado = usuarios.findByEmailIgnoreCase(email.trim());
        // Sempre confere um hash, mesmo sem usuário: o tempo de resposta não revela se o e-mail existe.
        String hash = encontrado.map(Usuario::getSenhaHash).orElse(hashFalso);
        boolean cabeNoBcrypt = senha.getBytes(UTF_8).length <= RegistroService.LIMITE_DA_SENHA_EM_BYTES;
        boolean confere = cabeNoBcrypt && codificador.matches(senha, hash);

        if (encontrado.isEmpty() || !confere) {
            throw ErroDeNegocio.naoAutenticado("E-mail ou senha inválidos.");
        }
        Usuario usuario = encontrado.get();
        return new ResultadoDeLogin(sessoes.montar(usuario), emissor.emitir(usuario.getId()));
    }
}
```

Create `backend/src/main/java/br/com/autopecas/conta/internal/web/RegistroRequest.java`:
```java
package br.com.autopecas.conta.internal.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record RegistroRequest(
        @NotBlank(message = "Informe o nome.") @Size(max = 120, message = "O nome pode ter no máximo 120 caracteres.")
                String nome,
        @NotBlank(message = "Informe o e-mail.")
                @Email(message = "Informe um e-mail válido.")
                @Size(max = 254, message = "O e-mail pode ter no máximo 254 caracteres.")
                String email,
        @NotBlank(message = "Informe a senha.") @Size(min = 8, message = "A senha precisa ter pelo menos 8 caracteres.")
                String senha) {

    /** Espaços nas pontas (o teclado do celular costuma deixar um) são removidos antes da validação. */
    RegistroRequest {
        nome = nome == null ? null : nome.trim();
        email = email == null ? null : email.trim();
    }
}
```

Create `backend/src/main/java/br/com/autopecas/conta/internal/web/RegistroResponse.java`:
```java
package br.com.autopecas.conta.internal.web;

import br.com.autopecas.conta.internal.domain.Usuario;

record RegistroResponse(String id, String nome, String email) {

    static RegistroResponse de(Usuario usuario) {
        return new RegistroResponse(usuario.getId().toString(), usuario.getNome(), usuario.getEmail());
    }
}
```

Create `backend/src/main/java/br/com/autopecas/conta/internal/web/LoginRequest.java`:
```java
package br.com.autopecas.conta.internal.web;

import jakarta.validation.constraints.NotBlank;

record LoginRequest(
        @NotBlank(message = "Informe o e-mail.") String email, @NotBlank(message = "Informe a senha.") String senha) {
}
```

Create `backend/src/main/java/br/com/autopecas/conta/internal/web/SessaoResponse.java`:
```java
package br.com.autopecas.conta.internal.web;

import br.com.autopecas.conta.internal.application.SessaoDoUsuario;

/** Contrato de GET /api/v1/me e do corpo do login. "vendedor" é null para quem só é comprador. */
record SessaoResponse(String id, String nome, String email, VendedorResponse vendedor, String authId) {

    record VendedorResponse(String status) {
    }

    static SessaoResponse de(SessaoDoUsuario sessao) {
        VendedorResponse vendedor =
                sessao.vendedor() == null ? null : new VendedorResponse(sessao.vendedor().name());
        return new SessaoResponse(sessao.id().toString(), sessao.nome(), sessao.email(), vendedor, sessao.authId());
    }
}
```

Create `backend/src/main/java/br/com/autopecas/conta/internal/web/AuthController.java`:
```java
package br.com.autopecas.conta.internal.web;

import br.com.autopecas.conta.internal.application.AutenticacaoService;
import br.com.autopecas.conta.internal.application.RegistroService;
import br.com.autopecas.conta.internal.application.ResultadoDeLogin;
import br.com.autopecas.conta.internal.domain.Usuario;
import br.com.autopecas.conta.internal.infra.seguranca.CookieDeSessao;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final RegistroService registro;
    private final AutenticacaoService autenticacao;
    private final CookieDeSessao cookies;

    AuthController(RegistroService registro, AutenticacaoService autenticacao, CookieDeSessao cookies) {
        this.registro = registro;
        this.autenticacao = autenticacao;
        this.cookies = cookies;
    }

    @PostMapping("/registro")
    ResponseEntity<RegistroResponse> registrar(@Valid @RequestBody RegistroRequest requisicao) {
        Usuario usuario = registro.registrar(requisicao.nome(), requisicao.email(), requisicao.senha());
        return ResponseEntity.status(HttpStatus.CREATED).body(RegistroResponse.de(usuario));
    }

    @PostMapping("/login")
    ResponseEntity<SessaoResponse> entrar(@Valid @RequestBody LoginRequest requisicao) {
        ResultadoDeLogin resultado = autenticacao.autenticar(requisicao.email(), requisicao.senha());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.criar(resultado.token()).toString())
                .body(SessaoResponse.de(resultado.sessao()));
    }
}
```

- [ ] **Step 4: Rodar tudo e ver passar**

Run: `mvn test`
Expected: `BUILD SUCCESS` e `Tests run: 92, Failures: 0, Errors: 0` (92 testes ao todo até aqui). O `LoginCookieSeguroTest` sobe um segundo contexto (com outro PostgreSQL) com `cookie-seguro=true`.

- [ ] **Step 5: Commit**

```bash
cd ..
git add backend
git commit -m "feat(backend): cadastro e login com BCrypt, cookie de sessão e authId" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
cd backend
```


---

### Task 7: `/me`, logout e limpeza de tokens revogados

**Files:**
- Create: em `backend/src/main/java/br/com/autopecas/conta/internal/`: `application/{LogoutService,LimpezaDeTokensRevogados}.java`, `web/MeController.java`
- Modify: `.../web/AuthController.java` (ganha o logout), `backend/src/main/java/br/com/autopecas/AutopecasApplication.java` (`@EnableScheduling`)
- Test: `backend/src/test/java/br/com/autopecas/conta/{MeTest,LogoutTest}.java`

**Interfaces:**
- Consumes: `SessaoService`, `TokenRevogadoRepository`, `Identidade`, `CookieDeSessao`.
- Produces:
  - `GET /api/v1/me` (só o cookie) → `200 { id, nome, email, vendedor, authId }`. É por ele que o frontend descobre o `authId`.
  - `POST /api/v1/auth/logout` (cookie + `X-Auth-Id`) → `204` mais `Set-Cookie` que apaga o cookie (`Max-Age=0`) e grava o `jti` em `token_revogado`. O mesmo token passa a dar `401`, mas **as outras sessões do mesmo usuário continuam válidas**. Sem o `X-Auth-Id` o logout **não acontece** (outro site não força o logout).
  - `LogoutService.encerrar(UUID jti, Instant expiraEm)` (idempotente) e `LimpezaDeTokensRevogados.removerExpirados(): int`, agendado a cada hora, que remove só os revogados já vencidos.

- [ ] **Step 1: Escrever os testes**

Create `backend/src/test/java/br/com/autopecas/conta/MeTest.java`:
```java
package br.com.autopecas.conta;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.autopecas.ApoioDeTeste;
import br.com.autopecas.TesteDeIntegracao;
import br.com.autopecas.conta.internal.domain.StatusVendedor;
import br.com.autopecas.conta.internal.domain.Usuario;
import br.com.autopecas.conta.internal.infra.persistencia.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@TesteDeIntegracao
class MeTest {

    private static final String ROTA = "/api/v1/me";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApoioDeTeste apoio;

    @Autowired
    UsuarioRepository usuarios;

    @Test
    void oCookieSozinhoBastaEDevolveOAuthIdParaOFrontend() throws Exception {
        Usuario usuario = apoio.usuario();

        mockMvc.perform(get(ROTA).cookie(apoio.cookieDe(usuario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(usuario.getId().toString()))
                .andExpect(jsonPath("$.nome").value(usuario.getNome()))
                .andExpect(jsonPath("$.email").value(usuario.getEmail()))
                .andExpect(jsonPath("$.vendedor").value(nullValue()))
                .andExpect(jsonPath("$.authId").value(apoio.authIdDe(usuario)))
                .andExpect(jsonPath("$.senhaHash").doesNotExist());
    }

    @ParameterizedTest
    @EnumSource(StatusVendedor.class)
    void oPerfilDeVendedorApareceComOStatus(StatusVendedor status) throws Exception {
        Usuario vendedor = apoio.vendedor(status);

        mockMvc.perform(get(ROTA).cookie(apoio.cookieDe(vendedor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendedor.status").value(status.name()));
    }

    @Test
    void semCookieENaoAutenticado() throws Exception {
        mockMvc.perform(get(ROTA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Não autenticado"));
    }

    @Test
    void umUsuarioRemovidoNaoTemMaisSessaoMesmoComTokenValido() throws Exception {
        Usuario usuario = apoio.usuario();
        var cookie = apoio.cookieDe(usuario);
        usuarios.deleteById(usuario.getId());
        usuarios.flush();

        mockMvc.perform(get(ROTA).cookie(cookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Sessão inválida."));
    }

    @Test
    void soOGetDeMeDispensaOAuthId() throws Exception {
        Usuario usuario = apoio.usuario();

        // Outros métodos na mesma rota continuam exigindo o X-Auth-Id (aqui cai em 401 antes do 405).
        mockMvc.perform(post(ROTA).cookie(apoio.cookieDe(usuario))).andExpect(status().isUnauthorized());
    }
}
```

Create `backend/src/test/java/br/com/autopecas/conta/LogoutTest.java`:
```java
package br.com.autopecas.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.autopecas.ApoioDeTeste;
import br.com.autopecas.TesteDeIntegracao;
import br.com.autopecas.conta.internal.application.LimpezaDeTokensRevogados;
import br.com.autopecas.conta.internal.domain.Usuario;
import br.com.autopecas.conta.internal.infra.seguranca.CookieDeSessao;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@TesteDeIntegracao
class LogoutTest {

    private static final String ROTA = "/api/v1/auth/logout";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApoioDeTeste apoio;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtDecoder decodificador;

    @Autowired
    LimpezaDeTokensRevogados limpeza;

    @Test
    void logoutApagaOCookieERevogaOToken() throws Exception {
        Usuario usuario = apoio.usuario();
        Cookie cookie = apoio.cookieDe(usuario);
        String jti = decodificador.decode(cookie.getValue()).getId();

        String setCookie = mockMvc.perform(post(ROTA).cookie(cookie).header("X-Auth-Id", apoio.authIdDe(usuario)))
                .andExpect(status().isNoContent())
                .andReturn()
                .getResponse()
                .getHeader("Set-Cookie");

        assertThat(setCookie).startsWith(CookieDeSessao.NOME + "=;").contains("Max-Age=0").contains("HttpOnly");
        assertThat(jdbc.queryForObject("select count(*) from token_revogado where jti = ?", Long.class, UUID.fromString(jti)))
                .isEqualTo(1L);
    }

    @Test
    void depoisDoLogoutOMesmoTokenNaoAbreMaisNada() throws Exception {
        Usuario usuario = apoio.usuario();
        Cookie cookie = apoio.cookieDe(usuario);
        mockMvc.perform(get("/api/v1/me").cookie(cookie)).andExpect(status().isOk());

        mockMvc.perform(post(ROTA).cookie(cookie).header("X-Auth-Id", apoio.authIdDe(usuario)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/me").cookie(cookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Não autenticado"));
    }

    @Test
    void logoutDeUmaSessaoNaoDerrubaAsOutrasDoMesmoUsuario() throws Exception {
        Usuario usuario = apoio.usuario();
        Cookie notebook = apoio.cookieDe(usuario);
        Cookie celular = apoio.cookieDe(usuario);

        mockMvc.perform(post(ROTA).cookie(notebook).header("X-Auth-Id", apoio.authIdDe(usuario)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/me").cookie(celular)).andExpect(status().isOk());
    }

    @Test
    void segundoLogoutComOMesmoTokenENegado() throws Exception {
        Usuario usuario = apoio.usuario();
        Cookie cookie = apoio.cookieDe(usuario);
        String authId = apoio.authIdDe(usuario);
        mockMvc.perform(post(ROTA).cookie(cookie).header("X-Auth-Id", authId)).andExpect(status().isNoContent());

        mockMvc.perform(post(ROTA).cookie(cookie).header("X-Auth-Id", authId)).andExpect(status().isUnauthorized());
    }

    @Test
    void semOAuthIdOLogoutNaoAcontece() throws Exception {
        Usuario usuario = apoio.usuario();
        Cookie cookie = apoio.cookieDe(usuario);

        // Um site malicioso não consegue forçar o logout: falta o cabeçalho que só o nosso frontend envia.
        mockMvc.perform(post(ROTA).cookie(cookie)).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/me").cookie(cookie)).andExpect(status().isOk());
    }

    @Test
    void semCookieOLogoutENegado() throws Exception {
        Usuario usuario = apoio.usuario();

        mockMvc.perform(post(ROTA).header("X-Auth-Id", apoio.authIdDe(usuario)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aLimpezaRemoveSoOsTokensRevogadosJaVencidos() {
        UUID vencido = UUID.randomUUID();
        UUID vigente = UUID.randomUUID();
        jdbc.update(
                "insert into token_revogado (jti, expira_em) values (?, ?)",
                vencido,
                Timestamp.from(Instant.now().minusSeconds(60)));
        jdbc.update(
                "insert into token_revogado (jti, expira_em) values (?, ?)",
                vigente,
                Timestamp.from(Instant.now().plusSeconds(3600)));

        int removidos = limpeza.removerExpirados();

        assertThat(removidos).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from token_revogado where jti = ?", Long.class, vencido))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from token_revogado where jti = ?", Long.class, vigente))
                .isEqualTo(1L);
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn -q test-compile`
Expected: FAIL com erro de compilação: `cannot find symbol` em `LogoutTest` (`LimpezaDeTokensRevogados` ainda não existe).

- [ ] **Step 3: Implementar `/me`, logout e a limpeza**

O `AuthController` é substituído pela versão completa (com `sair`), e a `AutopecasApplication` ganha o `@EnableScheduling`.

Create `backend/src/main/java/br/com/autopecas/conta/internal/application/LogoutService.java`:
```java
package br.com.autopecas.conta.internal.application;

import br.com.autopecas.conta.internal.domain.TokenRevogado;
import br.com.autopecas.conta.internal.infra.persistencia.TokenRevogadoRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LogoutService {

    private final TokenRevogadoRepository revogados;

    LogoutService(TokenRevogadoRepository revogados) {
        this.revogados = revogados;
    }

    @Transactional
    public void encerrar(UUID jti, Instant expiraEm) {
        if (!revogados.existsById(jti)) {
            revogados.save(new TokenRevogado(jti, expiraEm));
        }
    }
}
```

Create `backend/src/main/java/br/com/autopecas/conta/internal/application/LimpezaDeTokensRevogados.java`:
```java
package br.com.autopecas.conta.internal.application;

import br.com.autopecas.conta.internal.infra.persistencia.TokenRevogadoRepository;
import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Um token vencido é recusado de qualquer jeito, então não precisa continuar na lista de revogados. */
@Component
public class LimpezaDeTokensRevogados {

    private final TokenRevogadoRepository revogados;
    private final Clock relogio;

    LimpezaDeTokensRevogados(TokenRevogadoRepository revogados, Clock relogio) {
        this.revogados = revogados;
        this.relogio = relogio;
    }

    @Scheduled(initialDelayString = "PT5M", fixedDelayString = "PT1H")
    @Transactional
    public int removerExpirados() {
        return revogados.removerExpirados(relogio.instant());
    }
}
```

Create `backend/src/main/java/br/com/autopecas/conta/internal/web/MeController.java`:
```java
package br.com.autopecas.conta.internal.web;

import br.com.autopecas.compartilhado.Identidade;
import br.com.autopecas.conta.internal.application.SessaoService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MeController {

    private final SessaoService sessoes;

    MeController(SessaoService sessoes) {
        this.sessoes = sessoes;
    }

    /** Só o cookie autentica esta rota (sem X-Auth-Id): é por aqui que o frontend descobre o authId. */
    @GetMapping("/api/v1/me")
    SessaoResponse me(@AuthenticationPrincipal Jwt jwt) {
        return SessaoResponse.de(sessoes.obter(Identidade.usuarioId(jwt)));
    }
}
```

Replace `backend/src/main/java/br/com/autopecas/conta/internal/web/AuthController.java`:
```java
package br.com.autopecas.conta.internal.web;

import br.com.autopecas.conta.internal.application.AutenticacaoService;
import br.com.autopecas.conta.internal.application.LogoutService;
import br.com.autopecas.conta.internal.application.RegistroService;
import br.com.autopecas.conta.internal.application.ResultadoDeLogin;
import br.com.autopecas.conta.internal.domain.Usuario;
import br.com.autopecas.conta.internal.infra.seguranca.CookieDeSessao;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final RegistroService registro;
    private final AutenticacaoService autenticacao;
    private final LogoutService logout;
    private final CookieDeSessao cookies;

    AuthController(
            RegistroService registro, AutenticacaoService autenticacao, LogoutService logout, CookieDeSessao cookies) {
        this.registro = registro;
        this.autenticacao = autenticacao;
        this.logout = logout;
        this.cookies = cookies;
    }

    @PostMapping("/registro")
    ResponseEntity<RegistroResponse> registrar(@Valid @RequestBody RegistroRequest requisicao) {
        Usuario usuario = registro.registrar(requisicao.nome(), requisicao.email(), requisicao.senha());
        return ResponseEntity.status(HttpStatus.CREATED).body(RegistroResponse.de(usuario));
    }

    @PostMapping("/login")
    ResponseEntity<SessaoResponse> entrar(@Valid @RequestBody LoginRequest requisicao) {
        ResultadoDeLogin resultado = autenticacao.autenticar(requisicao.email(), requisicao.senha());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.criar(resultado.token()).toString())
                .body(SessaoResponse.de(resultado.sessao()));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> sair(@AuthenticationPrincipal Jwt jwt) {
        logout.encerrar(UUID.fromString(jwt.getId()), jwt.getExpiresAt());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.apagar().toString())
                .build();
    }
}
```

Replace `backend/src/main/java/br/com/autopecas/AutopecasApplication.java`:
```java
package br.com.autopecas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AutopecasApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutopecasApplication.class, args);
    }
}
```

- [ ] **Step 4: Rodar tudo e ver passar**

Run: `mvn test`
Expected: `BUILD SUCCESS` e `Tests run: 107, Failures: 0, Errors: 0` (107 testes ao todo até aqui). 

- [ ] **Step 5: Commit**

```bash
cd ..
git add backend
git commit -m "feat(backend): /me com authId, logout com revogação do token e limpeza agendada" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
cd backend
```


---

### Task 8: Notificações

**Files:**
- Create: em `backend/src/main/java/br/com/autopecas/notificacao/internal/`: `application/{PainelDeNotificacoes,NotificacaoService}.java`, `web/NotificacaoController.java`
- Test: `backend/src/test/java/br/com/autopecas/notificacao/NotificacaoTest.java`

**Interfaces:**
- Consumes: `NotificacaoRepository`, `Identidade`, `ErroDeNegocio`, `ApoioDeTeste`.
- Produces os contratos que o Plano 1 do frontend já consome:
  - `GET /api/v1/notificacoes?tipos=CHAT,PROPOSTA&limite=20` (cookie + `X-Auth-Id`) → `200 { itens: [{ id, tipo, titulo, resumo, criadaEm, lida }], naoLidas: { chat, outras, pecasAProcura } }`. Só as notificações **do usuário**, da mais recente para a mais antiga. `criadaEm` é um instante ISO 8601 em UTC (termina em `Z`, com até microssegundos). `tipos` opcional (sem ele, todos os tipos); `limite` de 1 a 100, padrão 20. **Os contadores valem para todos os tipos**, mesmo com o filtro (`outras` = `PROPOSTA` + `SISTEMA`). `400` para tipo desconhecido, limite fora do intervalo ou não numérico.
  - `PATCH /api/v1/notificacoes/{id}` `{ "lida": true|false }` → `204`, idempotente. `404 "Notificação não encontrada."` se não existe **ou é de outro usuário**, sem alterar nada. `400` para id que não é UUID, corpo ausente, `{}` (com `erros.lida`) ou `lida` que não é booleano.
  - `NotificacaoService.listar(UUID usuarioId, Collection<TipoNotificacao> tipos, int limite): PainelDeNotificacoes` e `definirLida(UUID usuarioId, UUID notificacaoId, boolean lida)`.

- [ ] **Step 1: Escrever os testes**

Create `backend/src/test/java/br/com/autopecas/notificacao/NotificacaoTest.java`:
```java
package br.com.autopecas.notificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.autopecas.ApoioDeTeste;
import br.com.autopecas.TesteDeIntegracao;
import br.com.autopecas.conta.internal.domain.Usuario;
import br.com.autopecas.notificacao.internal.domain.Notificacao;
import br.com.autopecas.notificacao.internal.domain.TipoNotificacao;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@TesteDeIntegracao
class NotificacaoTest {

    private static final String ROTA = "/api/v1/notificacoes";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApoioDeTeste apoio;

    Usuario usuario;

    @BeforeEach
    void criarUsuario() {
        usuario = apoio.usuario();
    }

    private MockHttpServletRequestBuilder autenticada(MockHttpServletRequestBuilder requisicao, Usuario de) {
        return requisicao.cookie(apoio.cookieDe(de)).header("X-Auth-Id", apoio.authIdDe(de));
    }

    private MockHttpServletRequestBuilder autenticada(MockHttpServletRequestBuilder requisicao) {
        return autenticada(requisicao, usuario);
    }

    private Notificacao criar(TipoNotificacao tipo, boolean lida, long segundosAtras, String titulo) {
        return apoio.notificacao(usuario, tipo, lida, Instant.now().minusSeconds(segundosAtras), titulo);
    }

    private static String lida(boolean valor) {
        return "{\"lida\": " + valor + "}";
    }

    @Test
    void usuarioSemNotificacoesRecebeListaVaziaEContadoresZerados() throws Exception {
        mockMvc.perform(autenticada(get(ROTA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(0)))
                .andExpect(jsonPath("$.naoLidas.chat").value(0))
                .andExpect(jsonPath("$.naoLidas.outras").value(0))
                .andExpect(jsonPath("$.naoLidas.pecasAProcura").value(0));
    }

    @Test
    void oContratoTemOsCamposQueOFrontendConsome() throws Exception {
        Notificacao criada = criar(TipoNotificacao.CHAT, false, 60, "Auto Peças Silva");

        String criadaEm = mockMvc.perform(autenticada(get(ROTA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].id").value(criada.getId().toString()))
                .andExpect(jsonPath("$.itens[0].tipo").value("CHAT"))
                .andExpect(jsonPath("$.itens[0].titulo").value("Auto Peças Silva"))
                .andExpect(jsonPath("$.itens[0].resumo").value("Resumo de Auto Peças Silva"))
                .andExpect(jsonPath("$.itens[0].lida").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // criadaEm é um instante ISO 8601 em UTC (termina em Z), como o frontend valida.
        assertThat(criadaEm).containsPattern("\"criadaEm\":\"\\d{4}-\\d{2}-\\d{2}T[\\d:.]+Z\"");
    }

    @Test
    void listaDaMaisRecenteParaAMaisAntiga() throws Exception {
        criar(TipoNotificacao.CHAT, false, 3000, "antiga");
        criar(TipoNotificacao.PROPOSTA, false, 10, "nova");
        criar(TipoNotificacao.SISTEMA, true, 500, "meio");

        mockMvc.perform(autenticada(get(ROTA)))
                .andExpect(jsonPath("$.itens[0].titulo").value("nova"))
                .andExpect(jsonPath("$.itens[1].titulo").value("meio"))
                .andExpect(jsonPath("$.itens[2].titulo").value("antiga"));
    }

    @Test
    void cadaUsuarioSoVeAsProprias() throws Exception {
        criar(TipoNotificacao.CHAT, false, 10, "minha");
        Usuario outro = apoio.usuario();
        apoio.notificacao(outro, TipoNotificacao.CHAT, false, Instant.now(), "do outro");

        mockMvc.perform(autenticada(get(ROTA)))
                .andExpect(jsonPath("$.itens", hasSize(1)))
                .andExpect(jsonPath("$.itens[0].titulo").value("minha"))
                .andExpect(jsonPath("$.naoLidas.chat").value(1));
    }

    @Test
    void filtraPorTipo() throws Exception {
        criar(TipoNotificacao.CHAT, false, 10, "chat");
        criar(TipoNotificacao.PROPOSTA, false, 20, "proposta");
        criar(TipoNotificacao.PECA_A_PROCURA, false, 30, "procura");

        mockMvc.perform(autenticada(get(ROTA).param("tipos", "CHAT,PROPOSTA")))
                .andExpect(jsonPath("$.itens", hasSize(2)))
                .andExpect(jsonPath("$.itens[0].titulo").value("chat"))
                .andExpect(jsonPath("$.itens[1].titulo").value("proposta"));
    }

    @Test
    void semOParametroTiposDevolveTodosOsTipos() throws Exception {
        criar(TipoNotificacao.CHAT, false, 10, "chat");
        criar(TipoNotificacao.PECA_A_PROCURA, false, 20, "procura");

        mockMvc.perform(autenticada(get(ROTA))).andExpect(jsonPath("$.itens", hasSize(2)));
    }

    @Test
    void oLimiteCortaAListaEOPadraoEVinte() throws Exception {
        for (int i = 0; i < 25; i++) {
            criar(TipoNotificacao.SISTEMA, true, 10L + i, "n" + i);
        }

        mockMvc.perform(autenticada(get(ROTA))).andExpect(jsonPath("$.itens", hasSize(20)));
        mockMvc.perform(autenticada(get(ROTA).param("limite", "3"))).andExpect(jsonPath("$.itens", hasSize(3)));
    }

    @Test
    void osContadoresValemParaTodosOsTiposMesmoComFiltro() throws Exception {
        criar(TipoNotificacao.CHAT, false, 10, "chat 1");
        criar(TipoNotificacao.CHAT, false, 20, "chat 2");
        criar(TipoNotificacao.CHAT, true, 30, "chat lido");
        criar(TipoNotificacao.PROPOSTA, false, 40, "proposta");
        criar(TipoNotificacao.SISTEMA, true, 50, "sistema lido");
        criar(TipoNotificacao.PECA_A_PROCURA, false, 60, "procura 1");
        criar(TipoNotificacao.PECA_A_PROCURA, false, 70, "procura 2");

        mockMvc.perform(autenticada(get(ROTA).param("tipos", "CHAT")))
                .andExpect(jsonPath("$.itens", hasSize(3)))
                .andExpect(jsonPath("$.naoLidas.chat").value(2))
                .andExpect(jsonPath("$.naoLidas.outras").value(1))
                .andExpect(jsonPath("$.naoLidas.pecasAProcura").value(2));
    }

    @Test
    void marcarComoLidaAtualizaAListaEOsContadores() throws Exception {
        Notificacao chat = criar(TipoNotificacao.CHAT, false, 10, "chat");
        criar(TipoNotificacao.CHAT, false, 20, "outro chat");

        mockMvc.perform(autenticada(patch(ROTA + "/" + chat.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lida(true))))
                .andExpect(status().isNoContent());

        mockMvc.perform(autenticada(get(ROTA)))
                .andExpect(jsonPath("$.itens[0].lida").value(true))
                .andExpect(jsonPath("$.naoLidas.chat").value(1));
    }

    @Test
    void marcarDuasVezesEIdempotenteEDaParaDesmarcar() throws Exception {
        Notificacao chat = criar(TipoNotificacao.CHAT, false, 10, "chat");
        String rota = ROTA + "/" + chat.getId();

        mockMvc.perform(autenticada(patch(rota).contentType(MediaType.APPLICATION_JSON).content(lida(true))))
                .andExpect(status().isNoContent());
        mockMvc.perform(autenticada(patch(rota).contentType(MediaType.APPLICATION_JSON).content(lida(true))))
                .andExpect(status().isNoContent());
        mockMvc.perform(autenticada(patch(rota).contentType(MediaType.APPLICATION_JSON).content(lida(false))))
                .andExpect(status().isNoContent());

        mockMvc.perform(autenticada(get(ROTA))).andExpect(jsonPath("$.naoLidas.chat").value(1));
    }

    @Test
    void notificacaoDeOutroUsuarioResponde404ENaoMudaNada() throws Exception {
        Usuario outro = apoio.usuario();
        Notificacao doOutro = apoio.notificacao(outro, TipoNotificacao.CHAT, false, Instant.now(), "do outro");

        mockMvc.perform(autenticada(patch(ROTA + "/" + doOutro.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lida(true))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Notificação não encontrada."));

        mockMvc.perform(autenticada(get(ROTA), outro)).andExpect(jsonPath("$.naoLidas.chat").value(1));
    }

    @Test
    void notificacaoInexistenteResponde404() throws Exception {
        mockMvc.perform(autenticada(patch(ROTA + "/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lida(true))))
                .andExpect(status().isNotFound());
    }

    @Test
    void idQueNaoEUuidDa400() throws Exception {
        mockMvc.perform(autenticada(patch(ROTA + "/nao-e-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lida(true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requisição inválida"));
    }

    @Test
    void corpoSemOCampoLidaDa400ComOCampoComErro() throws Exception {
        Notificacao chat = criar(TipoNotificacao.CHAT, false, 10, "chat");

        mockMvc.perform(autenticada(patch(ROTA + "/" + chat.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Dados inválidos"))
                .andExpect(jsonPath("$.erros.lida").isNotEmpty());
    }

    @Test
    void lidaQueNaoEBooleanoEAusenciaDeCorpoDao400() throws Exception {
        Notificacao chat = criar(TipoNotificacao.CHAT, false, 10, "chat");
        String rota = ROTA + "/" + chat.getId();

        mockMvc.perform(autenticada(patch(rota).contentType(MediaType.APPLICATION_JSON).content("{\"lida\": \"talvez\"}")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(autenticada(patch(rota).contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tipoDesconhecidoNaConsultaDa400() throws Exception {
        mockMvc.perform(autenticada(get(ROTA).param("tipos", "CHAT,EMAIL")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requisição inválida"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "101", "abc"})
    void limiteForaDoIntervaloDa400(String limite) throws Exception {
        mockMvc.perform(autenticada(get(ROTA).param("limite", limite))).andExpect(status().isBadRequest());
    }

    @Test
    void semAutenticacaoOuSemOAuthIdNadaAcontece() throws Exception {
        Notificacao chat = criar(TipoNotificacao.CHAT, false, 10, "chat");
        String rota = ROTA + "/" + chat.getId();

        mockMvc.perform(get(ROTA)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(ROTA).cookie(apoio.cookieDe(usuario))).andExpect(status().isUnauthorized());
        mockMvc.perform(patch(rota).contentType(MediaType.APPLICATION_JSON).content(lida(true)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch(rota)
                        .cookie(apoio.cookieDe(usuario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lida(true)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(autenticada(get(ROTA))).andExpect(jsonPath("$.naoLidas.chat").value(1));
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn -q test -Dtest=NotificacaoTest`
Expected: FAIL: os testes de `NotificacaoTest` falham com `Status expected:<200> but was:<404>` e similares, porque as rotas ainda não existem.

- [ ] **Step 3: Implementar as notificações**

Os contadores vêm de uma consulta agrupada por tipo, à parte da lista, para valerem para todos os tipos.

Create `backend/src/main/java/br/com/autopecas/notificacao/internal/application/PainelDeNotificacoes.java`:
```java
package br.com.autopecas.notificacao.internal.application;

import br.com.autopecas.notificacao.internal.domain.Notificacao;
import java.util.List;

/** Os contadores valem para todos os tipos, não só para os que vieram na lista. */
public record PainelDeNotificacoes(List<Notificacao> itens, ContadoresNaoLidas naoLidas) {

    public record ContadoresNaoLidas(long chat, long outras, long pecasAProcura) {
    }
}
```

Create `backend/src/main/java/br/com/autopecas/notificacao/internal/application/NotificacaoService.java`:
```java
package br.com.autopecas.notificacao.internal.application;

import br.com.autopecas.compartilhado.ErroDeNegocio;
import br.com.autopecas.notificacao.internal.application.PainelDeNotificacoes.ContadoresNaoLidas;
import br.com.autopecas.notificacao.internal.domain.Notificacao;
import br.com.autopecas.notificacao.internal.domain.TipoNotificacao;
import br.com.autopecas.notificacao.internal.infra.persistencia.NotificacaoRepository;
import br.com.autopecas.notificacao.internal.infra.persistencia.NotificacaoRepository.ContagemPorTipo;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificacaoService {

    private final NotificacaoRepository notificacoes;

    NotificacaoService(NotificacaoRepository notificacoes) {
        this.notificacoes = notificacoes;
    }

    @Transactional(readOnly = true)
    public PainelDeNotificacoes listar(UUID usuarioId, Collection<TipoNotificacao> tipos, int limite) {
        Collection<TipoNotificacao> pedidos = tipos == null || tipos.isEmpty() ? EnumSet.allOf(TipoNotificacao.class) : tipos;
        List<Notificacao> itens = notificacoes.findByUsuarioIdAndTipoInOrderByCriadaEmDesc(
                usuarioId, pedidos, PageRequest.of(0, limite));
        return new PainelDeNotificacoes(itens, contadores(usuarioId));
    }

    /** Uma notificação de outro usuário responde "não encontrada", para não revelar que ela existe. */
    @Transactional
    public void definirLida(UUID usuarioId, UUID notificacaoId, boolean lida) {
        Notificacao notificacao = notificacoes.findByIdAndUsuarioId(notificacaoId, usuarioId)
                .orElseThrow(() -> ErroDeNegocio.naoEncontrado("Notificação não encontrada."));
        notificacao.definirLida(lida);
    }

    private ContadoresNaoLidas contadores(UUID usuarioId) {
        long chat = 0;
        long outras = 0;
        long pecasAProcura = 0;
        for (ContagemPorTipo contagem : notificacoes.contarNaoLidasPorTipo(usuarioId)) {
            switch (contagem.getTipo()) {
                case CHAT -> chat += contagem.getTotal();
                case PECA_A_PROCURA -> pecasAProcura += contagem.getTotal();
                case PROPOSTA, SISTEMA -> outras += contagem.getTotal();
            }
        }
        return new ContadoresNaoLidas(chat, outras, pecasAProcura);
    }
}
```

Create `backend/src/main/java/br/com/autopecas/notificacao/internal/web/NotificacaoController.java`:
```java
package br.com.autopecas.notificacao.internal.web;

import br.com.autopecas.compartilhado.Identidade;
import br.com.autopecas.notificacao.internal.application.NotificacaoService;
import br.com.autopecas.notificacao.internal.application.PainelDeNotificacoes;
import br.com.autopecas.notificacao.internal.domain.Notificacao;
import br.com.autopecas.notificacao.internal.domain.TipoNotificacao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notificacoes")
class NotificacaoController {

    private final NotificacaoService servico;

    NotificacaoController(NotificacaoService servico) {
        this.servico = servico;
    }

    @GetMapping
    ListaNotificacoesResponse listar(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) List<TipoNotificacao> tipos,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limite) {
        return ListaNotificacoesResponse.de(servico.listar(Identidade.usuarioId(jwt), tipos, limite));
    }

    @PatchMapping("/{id}")
    ResponseEntity<Void> atualizar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody AtualizarNotificacaoRequest requisicao) {
        servico.definirLida(Identidade.usuarioId(jwt), id, requisicao.lida());
        return ResponseEntity.noContent().build();
    }

    record AtualizarNotificacaoRequest(@NotNull(message = "Informe o campo lida.") Boolean lida) {
    }

    record ListaNotificacoesResponse(List<NotificacaoResponse> itens, NaoLidasResponse naoLidas) {

        static ListaNotificacoesResponse de(PainelDeNotificacoes painel) {
            return new ListaNotificacoesResponse(
                    painel.itens().stream().map(NotificacaoResponse::de).toList(),
                    new NaoLidasResponse(
                            painel.naoLidas().chat(), painel.naoLidas().outras(), painel.naoLidas().pecasAProcura()));
        }
    }

    record NaoLidasResponse(long chat, long outras, long pecasAProcura) {
    }

    record NotificacaoResponse(String id, String tipo, String titulo, String resumo, Instant criadaEm, boolean lida) {

        static NotificacaoResponse de(Notificacao notificacao) {
            return new NotificacaoResponse(
                    notificacao.getId().toString(),
                    notificacao.getTipo().name(),
                    notificacao.getTitulo(),
                    notificacao.getResumo(),
                    notificacao.getCriadaEm(),
                    notificacao.isLida());
        }
    }
}
```

- [ ] **Step 4: Rodar tudo e ver passar**

Run: `mvn test`
Expected: `BUILD SUCCESS` e `Tests run: 128, Failures: 0, Errors: 0` (128 testes ao todo até aqui). 

- [ ] **Step 5: Commit**

```bash
cd ..
git add backend
git commit -m "feat(backend): listar e marcar notificações como lidas, com contadores por tipo" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
cd backend
```


---

### Task 9: Seed de desenvolvimento, execução local e documentação

**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/900-seed-dev.sql`, `backend/README.md`
- Test: `backend/src/test/java/br/com/autopecas/SeedDeDesenvolvimentoTest.java`

**Interfaces:**
- Produces:
  - Changelog `900-seed-dev` com `context:dev`: 3 usuários (`vynicyus@example.com` comprador e vendedor **aprovado**, `ana@example.com` só comprador, `pedro@example.com` vendedor **em análise**, todos com a senha `senha-dev-123`) e 8 notificações para o primeiro (3 de chat, 1 de proposta, 1 de sistema, 3 de peças à procura), iguais às do mock do frontend.
  - `backend/README.md` com como rodar, os usuários da seed, a configuração e a estrutura.

- [ ] **Step 1: Escrever os testes**

Create `backend/src/test/java/br/com/autopecas/SeedDeDesenvolvimentoTest.java`:
```java
package br.com.autopecas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Com o contexto "dev" do Liquibase, o banco vem com os usuários e as notificações que o frontend usa no mock. */
@TesteDeIntegracao
@TestPropertySource(properties = "spring.liquibase.contexts=dev")
class SeedDeDesenvolvimentoTest {

    private static final String SENHA_DE_DESENVOLVIMENTO = "senha-dev-123";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    private Cookie entrar(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "senha": "%s"}"""
                                .formatted(email, SENHA_DE_DESENVOLVIMENTO)))
                .andReturn()
                .getResponse()
                .getCookie("access_token");
    }

    @Test
    void aSeedFoiAplicadaNoContextoDev() {
        List<String> aplicados =
                jdbc.queryForList("select id from databasechangelog order by orderexecuted", String.class);

        assertThat(aplicados).contains("900-seed-dev");
    }

    @Test
    void osTresUsuariosDeDesenvolvimentoConseguemEntrarEVeemOProprioPerfil() throws Exception {
        mockMvc.perform(get("/api/v1/me").cookie(entrar("vynicyus@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Vynicyus Silva"))
                .andExpect(jsonPath("$.vendedor.status").value("APROVADO"));
        mockMvc.perform(get("/api/v1/me").cookie(entrar("ana@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendedor").value(nullValue()));
        mockMvc.perform(get("/api/v1/me").cookie(entrar("pedro@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendedor.status").value("PENDENTE"));
    }

    @Test
    void asNotificacoesDoVendedorBatemComOMockDoFrontend() throws Exception {
        Cookie cookie = entrar("vynicyus@example.com");
        String authId = mockMvc.perform(get("/api/v1/me").cookie(cookie))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"authId\":\"([0-9a-f]+)\".*", "$1");

        mockMvc.perform(get("/api/v1/notificacoes")
                        .param("tipos", "CHAT,PROPOSTA,SISTEMA")
                        .param("limite", "20")
                        .cookie(cookie)
                        .header("X-Auth-Id", authId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(5)))
                .andExpect(jsonPath("$.itens[0].titulo").value("Auto Peças Silva"))
                .andExpect(jsonPath("$.naoLidas.chat").value(2))
                .andExpect(jsonPath("$.naoLidas.outras").value(1))
                .andExpect(jsonPath("$.naoLidas.pecasAProcura").value(2));
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn -q test -Dtest=SeedDeDesenvolvimentoTest`
Expected: FAIL: `SeedDeDesenvolvimentoTest` falha porque o changelog `900-seed-dev` não existe (o login dos usuários da seed dá `401`).

- [ ] **Step 3: Criar a seed e o README**

A seed só roda no contexto `dev`. O `InicializacaoTest` (contexto `teste`) continua provando que ela não vai a lugar nenhum além do desenvolvimento.

Create `backend/src/main/resources/db/changelog/changes/900-seed-dev.sql`:
```sql
--liquibase formatted sql

--changeset autopecas:900-seed-dev context:dev
-- Dados só de desenvolvimento (contexto "dev"): nunca rodam em produção nem nos testes.
-- Senha de todos os usuários: senha-dev-123
INSERT INTO usuario (id, email, senha_hash, nome) VALUES
    ('00000000-0000-0000-0000-000000000001', 'vynicyus@example.com', '$2a$10$JTgh2PgLgjDPB2ccXfY0buW3.uU8Uz9acfaKoB3JVSySGEoLm8v9e', 'Vynicyus Silva'),
    ('00000000-0000-0000-0000-000000000002', 'ana@example.com',       '$2a$10$JTgh2PgLgjDPB2ccXfY0buW3.uU8Uz9acfaKoB3JVSySGEoLm8v9e', 'Ana Souza'),
    ('00000000-0000-0000-0000-000000000003', 'pedro@example.com',     '$2a$10$JTgh2PgLgjDPB2ccXfY0buW3.uU8Uz9acfaKoB3JVSySGEoLm8v9e', 'Pedro Lima');

INSERT INTO perfil_vendedor (usuario_id, status) VALUES
    ('00000000-0000-0000-0000-000000000001', 'APROVADO'),
    ('00000000-0000-0000-0000-000000000003', 'PENDENTE');

INSERT INTO notificacao (id, usuario_id, tipo, titulo, resumo, criada_em, lida) VALUES
    ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'CHAT',           'Auto Peças Silva',                 'Tenho o farol esquerdo do Gol G6, posso mandar foto?', now() - interval '4 minutes',  FALSE),
    ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'CHAT',           'Desmanche Rota 116',               'Consigo fazer R$ 180 se você retirar hoje.',           now() - interval '45 minutes', FALSE),
    ('10000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', 'PROPOSTA',       'Nova proposta: Retrovisor direito', 'Moto Center ofereceu R$ 89,90.',                       now() - interval '3 hours',    FALSE),
    ('10000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000001', 'SISTEMA',        'Seu pedido expira amanhã',          'Farol esquerdo · Gol 2014',                            now() - interval '30 hours',   TRUE),
    ('10000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000001', 'CHAT',           'Pneus & Cia',                      'Ainda precisa do amortecedor?',                        now() - interval '10 days',    TRUE),
    ('10000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000001', 'PECA_A_PROCURA', 'Procuram: Farol esquerdo',         'Gol 1.0 2014 · Recife/PE',                             now() - interval '8 minutes',  FALSE),
    ('10000000-0000-0000-0000-000000000007', '00000000-0000-0000-0000-000000000001', 'PECA_A_PROCURA', 'Procuram: Amortecedor dianteiro',  'Uno 2012 · Olinda/PE',                                 now() - interval '90 minutes', FALSE),
    ('10000000-0000-0000-0000-000000000008', '00000000-0000-0000-0000-000000000001', 'PECA_A_PROCURA', 'Procuram: Bateria 60Ah',           'HB20 2016 · Recife/PE',                                now() - interval '5 hours',    TRUE);
--rollback DELETE FROM usuario WHERE id IN ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000003');
```

Create `backend/README.md`:
````markdown
# Backend — Marketplace de Autopeças

Spring Boot 4 (Java 21), Spring Security (JWT em cookie HttpOnly + `X-Auth-Id`), PostgreSQL 18, Liquibase (changelogs em SQL). Desenvolvido com TDD.

## Pré-requisitos

- JDK 21 e Maven 3.9 (`java -version`, `mvn -v`)
- Docker em execução (o banco de desenvolvimento e os testes de integração usam PostgreSQL de verdade)

## Rodar em desenvolvimento

```bash
docker compose up -d --wait                    # PostgreSQL 18 em localhost:5432 (na raiz do repositório)
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

O perfil `dev` aponta para o banco do `docker compose`, usa o contexto `dev` do Liquibase (que carrega a **seed**) e traz segredos de desenvolvimento. A API sobe em `http://localhost:8080`.

Usuários da seed (senha de todos: `senha-dev-123`):

| E-mail | Perfil |
|---|---|
| `vynicyus@example.com` | comprador e vendedor **aprovado**, com 8 notificações |
| `ana@example.com` | só comprador |
| `pedro@example.com` | comprador e vendedor **em análise** |

Exemplo de sessão completa (no Git Bash; no PowerShell use `curl.exe`):

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

## Banco de dados

Os changelogs ficam em `src/main/resources/db/changelog/changes/`, um arquivo `NNN-descricao.sql` por mudança, no formato `--liquibase formatted sql`, com `--rollback`. Para uma nova mudança, crie o próximo número. O arquivo mestre `db.changelog-master.yaml` inclui a pasta inteira, em ordem alfabética. O Hibernate só confere o esquema (`ddl-auto=validate`), quem cria as tabelas é o Liquibase.

## Estrutura

```
br.com.autopecas
├─ compartilhado/   # ErroDeNegocio, TratadorDeErros (Problem Details), Identidade
├─ conta/           # usuários, perfil de vendedor, sessão (JWT, X-Auth-Id)
└─ notificacao/     # notificações do usuário
```

Dentro de cada módulo, `internal/{domain,application,web,infra}`. Um módulo só usa a API pública de outro (pacote raiz do módulo), e o `domain` não depende do Spring. Ambas as regras são verificadas por testes.

Especificação: `docs/superpowers/specs/2026-09-29-backend-autenticacao-design.md`.
````

- [ ] **Step 4: Rodar tudo e ver passar**

Run: `mvn test`
Expected: `BUILD SUCCESS` e `Tests run: 131, Failures: 0, Errors: 0` (131 testes ao todo até aqui). O `SeedDeDesenvolvimentoTest` sobe um contexto próprio, com o contexto `dev` do Liquibase.

- [ ] **Step 5: Conferir a API rodando de verdade**

Este passo confirma o caminho de desenvolvimento **sem** Testcontainers: o PostgreSQL do `docker compose` e o perfil `dev`. A partir da pasta `IdeiaDeTCC`:

```bash
docker compose up -d --wait
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```
Deixe rodando (ou use outro terminal para os `curl` abaixo). Expected no log: `Started AutopecasApplication` e o Liquibase aplicando os 5 changelogs, o último deles `900-seed-dev`. No Git Bash (no PowerShell, use `curl.exe`), a partir de `IdeiaDeTCC/backend`:

```bash
curl -s http://localhost:8080/actuator/health
curl -s -i -c cookies.txt -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"email":"vynicyus@example.com","senha":"senha-dev-123"}'
curl -s -b cookies.txt http://localhost:8080/api/v1/me
```
Expected, em ordem:
1. `{"groups":["liveness","readiness"],"status":"UP"}`.
2. `HTTP/1.1 200`, um `Set-Cookie: access_token=...; Path=/; Max-Age=3600; ...; HttpOnly; SameSite=Lax` (sem `Secure`, porque o perfil `dev` roda em HTTP) e o JSON da sessão com `"vendedor":{"status":"APROVADO"}` e um `authId` de 64 caracteres.
3. O mesmo JSON da sessão, agora só com o cookie (o `/me` dispensa o `X-Auth-Id`).

Copie o `authId` e continue (troque `<authId>` pelo valor):

```bash
curl -s -o /dev/null -w "%{http_code}\n" -b cookies.txt http://localhost:8080/api/v1/notificacoes
curl -s -b cookies.txt -H "X-Auth-Id: <authId>" "http://localhost:8080/api/v1/notificacoes?tipos=CHAT,PROPOSTA,SISTEMA&limite=20"
curl -s -o /dev/null -w "%{http_code}\n" -b cookies.txt -H "X-Auth-Id: <authId>" -H 'Content-Type: application/json' -X PATCH "http://localhost:8080/api/v1/notificacoes/10000000-0000-0000-0000-000000000001" -d '{"lida":true}'
curl -s -o /dev/null -w "%{http_code}\n" -b cookies.txt -c cookies.txt -H "X-Auth-Id: <authId>" -X POST http://localhost:8080/api/v1/auth/logout
curl -s -o /dev/null -w "%{http_code}\n" -b cookies.txt http://localhost:8080/api/v1/me
```
Expected, em ordem:
1. `401` (sem o `X-Auth-Id` nada passa).
2. 5 itens (sem `PECA_A_PROCURA`), com `"naoLidas":{"chat":2,"outras":1,"pecasAProcura":2}` e o primeiro item "Auto Peças Silva".
3. `204`.
4. `204`.
5. `401` (o token foi revogado; o cookie já foi apagado).

Encerre a API (`Ctrl+C`) e o banco: `docker compose down` (a partir de `IdeiaDeTCC`; acrescente `-v` para apagar também os dados). Apague o `cookies.txt`.

- [ ] **Step 6: Commit**

```bash
cd ..
git add backend
git commit -m "feat(backend): seed de desenvolvimento, README e verificação com o PostgreSQL do compose" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
cd backend
```


---

## Auto-revisão (feita ao escrever o plano)

- **Cobertura da spec do backend:** B2 a B5 (JWT, cookie, `X-Auth-Id`, HMAC) → Tarefas 3, 4, 5, 6 e 7. B6 (Liquibase em SQL) → Tarefas 1, 2 e 9. B7 (escopo: esqueleto, autenticação, `/me`, notificações) → todas. Cada linha do modelo de ameaças (seção 3 da spec) tem o teste nomeado e vive nesta suíte.
- **O plano foi executado inteiro num projeto limpo antes de ser entregue:** para cada tarefa, os testes escritos primeiro **falharam** sem a implementação (por compilação ou por execução) e **passaram** depois dela. Os números de testes de cada tarefa vêm dessa execução.
- **Consistência de nomes:** `SegurancaProperties`, `CalculadoraDeAuthId`, `EmissorDeToken`, `TokenEmitido`, `CookieDeSessao`, `Identidade`, `ErroDeNegocio`, `ApoioDeTeste`, `TesteDeIntegracao`, `SessaoDoUsuario`, `ResultadoDeLogin`, `PainelDeNotificacoes` têm a mesma grafia em todas as tarefas. Os arquivos que mudam de uma tarefa para outra (`pom.xml`, `ConfiguracaoDeSeguranca`, `AuthController`, `InicializacaoTest`, `TesteDeIntegracao`, os `application*.properties`, `AutopecasApplication`) são sempre substituídos **por completo**.
- **Sem marcadores pendentes:** todo passo de código tem o código.

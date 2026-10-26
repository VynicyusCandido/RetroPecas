# Frontend, Plano 1 de 3: esqueleto e navbar — Plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Nota (2026-09-30):** o login passa a ser **somente pelo Google** ([regras](../../login-com-google-regras-de-seguranca.md)). Este plano **não muda** (navbar, sessão por `/me`, `X-Auth-Id`, sino), mas o passo opcional "login pelo console com senha", na última tarefa, **deixa de existir**. A tela `/entrar`, a guarda de rota e o aceite dos Termos formam o **Plano 1A** (ver o [design das telas](../specs/2026-09-29-frontend-telas-design.md), seção 7). O esquema de `/me` deve aceitar qualquer e-mail que o Google devolva (relaxar `z.email()`).

**Goal:** Criar o frontend Next.js com a estrutura definida pelo usuário e entregar a **barra de navegação** dos dois perfis (comprador e vendedor): alternar de perfil, campo "Peças à procura" do vendedor, sino de notificações com chat e link para as configurações. Desenvolvido com TDD.

**Architecture:** Componentes e páginas dentro de `app/`. Conexão com o backend em `api/` e regras de negócio em `services/`, **fora** de `app/`. Hooks do React ficam em `app/hooks/`. A dependência vai só de `app/` → `services/` → `api/`, garantida por um teste de arquitetura. O backend ainda não existe: os contratos são validados com Zod e simulados com MSW nos testes e com um servidor de mock em desenvolvimento.

**Tech Stack:** Next.js 16 (App Router), React 19, TypeScript, Tailwind CSS 4, shadcn/ui (base Radix, preset Nova), lucide-react, TanStack Query 5, Zod 4, Vitest 5, Testing Library, MSW 2, vitest-axe. Node 24.

**Specs:**
- [Design das telas](../specs/2026-09-29-frontend-telas-design.md) (seções 2, 3 e 6)
- [Spec do produto](../specs/2026-09-29-marketplace-autopecas-design.md) (AUT-03, AUT-05, AUT-06, NOT-01, LST-04, RNF-USA-01, RNF-USA-04, RNF-MAN-02)
- [Stack e TDD](../specs/2026-09-29-stack-e-tdd-design.md)

## Global Constraints

- Estrutura do frontend (decisão do usuário): **componentes e páginas dentro de `app/`; `api/` (conexão) e `services/` (regra de negócio) fora de `app/`.** Componentes e hooks falam com `services/`, nunca direto com `api/`. `services/` não importa React, Next nem TanStack.
- "Interface em pt-BR, **mobile-first** e responsiva" (RNF-USA-01).
- "WCAG 2.1 AA nos fluxos principais" (RNF-USA-04). Testes com axe em cada tela nova. O contraste é conferido depois, no navegador real, porque o jsdom não calcula estilos.
- "Todo usuário é comprador" (AUT-03). "O vendedor só recebe pedidos e publica catálogo depois de **aprovado por um administrador**" (AUT-05).
- O contador do sino soma **chat + outras** notificações e **não inclui "peças à procura"**, que têm campo próprio na navbar. Contador acima de 99 aparece como "99+".
- Erros da API no formato Problem Details (RFC 9457). A mensagem técnica do servidor **nunca** vai para a tela.
- Sessão por **JWT em cookie HttpOnly** (o navegador o envia sozinho): toda chamada usa `credentials: 'include'`. Toda chamada autenticada, menos `GET /api/v1/me`, também envia o cabeçalho **`X-Auth-Id`**, cujo valor vem de `/me` e fica **só em memória** (nunca em `localStorage`). Sem CSRF do Spring: o cabeçalho cumpre esse papel. O navegador chama `/api/v1/*` no **mesmo site**, via `rewrites` do Next.js. Ver a [spec do backend](../specs/2026-09-29-backend-autenticacao-design.md).
- TDD: nenhum código de produção sem um teste falhando antes. Testes importam `describe`, `it` e `expect` de `vitest` (sem globais).
- Versões: usar a última estável na criação. O `@types/node` precisa ser `^24` (o Vitest 5 exige 22 ou 24).

## Review Focus

Falhas que a spec implica mas nenhuma tarefa "óbvia" exercita. Cada linha tem o teste na tarefa dona.

1. **Sessão expirada (401)**, tanto na carga da página quanto depois, em uma nova consulta com dados antigos em cache: tela própria, sem navbar e **sem** "Tentar novamente". *(Tarefas 7 e 9)*
2. **Acesso direto a `/vendedor/**` sem ser vendedor aprovado** (comprador, em análise, reprovado ou suspenso): volta para `/comprador` e o conteúdo do vendedor **nunca** aparece, nem por um instante. *(Tarefa 9)*
3. **Contadores:** 0 não mostra emblema, mais de 99 mostra "99+", singular e plural no nome acessível, e "peças à procura" **nunca** entram no sino, nem se o servidor as enviar junto. *(Tarefas 6, 7 e 8)*
4. **Falha nas notificações não derruba a página:** o painel mostra o erro com "Tentar novamente", a navbar continua funcionando, e uma falha de atualização não apaga as notificações já exibidas. *(Tarefa 8)*
5. **Clicar em notificação já lida não dispara `PATCH`**, e um `401` por `X-Auth-Id` ausente ou que não confere vira erro de API com a mensagem do servidor, sem quebrar. O valor do `X-Auth-Id` nunca é guardado no servidor do Next.js, onde seria compartilhado entre usuários. *(Tarefas 3, 4 e 8)*

## Fora do escopo deste plano

Backend, login e logout, o conteúdo dos dashboards (Planos 2 e 3), a tela de chat e a edição dos dados da conta. As páginas `/comprador`, `/vendedor` e `/vendedor/pecas-a-procura` existem só como páginas provisórias, para a navbar ter para onde levar.

## Contratos consumidos

| Endpoint | Resposta |
|---|---|
| `GET /api/v1/me` | `{ "id": "u-1", "nome": "Vynicyus Silva", "email": "v@example.com", "vendedor": { "status": "APROVADO" }, "authId": "3795…394b" }`. `vendedor` é `null` ou ausente para quem não tem perfil de vendedor. `status`: `PENDENTE \| APROVADO \| REPROVADO \| SUSPENSO`. `authId` são 64 caracteres hexadecimais minúsculos: é o valor do cabeçalho `X-Auth-Id`. Só o cookie autentica esta rota |
| `GET /api/v1/notificacoes?tipos=CHAT,PROPOSTA,SISTEMA&limite=20` | `{ "itens": [{ "id": "n-1", "tipo": "CHAT", "titulo": "Auto Peças Silva", "resumo": "Tenho o farol…", "criadaEm": "2026-09-29T11:55:00Z", "lida": false }], "naoLidas": { "chat": 2, "outras": 1, "pecasAProcura": 2 } }`. `tipo`: `CHAT \| PROPOSTA \| PECA_A_PROCURA \| SISTEMA`. Os contadores valem para todos os tipos |
| `PATCH /api/v1/notificacoes/{id}` | Corpo `{ "lida": true }`, resposta `204` |

## Estrutura de arquivos

```
IdeiaDeTCC/
└─ frontend/
   ├─ app/
   │  ├─ layout.tsx                       # pt-BR, título, Providers
   │  ├─ page.tsx                         # redireciona para /comprador
   │  ├─ providers.tsx                    # QueryClientProvider
   │  ├─ (app)/                           # rotas com navbar
   │  │  ├─ layout.tsx                    # <AppShell>
   │  │  ├─ comprador/page.tsx            # provisória
   │  │  ├─ vendedor/page.tsx             # provisória
   │  │  ├─ vendedor/pecas-a-procura/page.tsx   # provisória
   │  │  └─ configuracoes/page.tsx
   │  ├─ hooks/
   │  │  ├─ useAgora.ts · useSessao.ts · useNotificacoes.ts
   │  └─ components/
   │     ├─ ui/                           # shadcn: button, card, badge, skeleton, alert, sheet, tabs
   │     ├─ navegacao/
   │     │  ├─ AppShell.tsx · Navbar.tsx · LinkNavegacao.tsx · ContadorBadge.tsx
   │     │  ├─ MenuNotificacoes.tsx · ListaNotificacoes.tsx · ItemNotificacao.tsx
   │     │  └─ EstadosDaSessao.tsx
   │     ├─ configuracoes/ConfiguracoesDaConta.tsx
   │     └─ EmConstrucao.tsx
   ├─ api/
   │  ├─ cliente.ts · sessao.ts · notificacoes.ts
   ├─ services/
   │  ├─ sessao.ts · navegacao.ts · notificacoes.ts · formatacao.ts · erros.ts
   ├─ mocks/
   │  ├─ servidor.ts                      # MSW (testes)
   │  └─ api-mock.mjs                     # servidor de mock (desenvolvimento)
   ├─ tests/                              # espelha as pastas de código
   ├─ vitest.config.mts · vitest.setup.ts · vitest.d.ts
   └─ next.config.ts · components.json
```

---

### Task 1: Esqueleto do frontend e cadeia de ferramentas

**Files:**
- Create: repositório Git em `IdeiaDeTCC/`, `frontend/` (gerado), `frontend/vitest.config.mts`, `frontend/vitest.setup.ts`, `frontend/vitest.d.ts`, `frontend/mocks/servidor.ts`, `frontend/tests/axe.ts`, `frontend/tests/toolchain.test.tsx`
- Modify: `frontend/package.json`, `frontend/components.json`, `frontend/app/components/ui/sheet.tsx`

**Interfaces:**
- Produces: `@/mocks/servidor` exporta `servidor` (instância `setupServer()` do MSW). `@/tests/axe` exporta `axe(container)` com `color-contrast` e `region` desligados. Scripts npm: `test`, `test:watch`, `typecheck`, `lint`, `build`.

- [ ] **Step 1: Criar o repositório Git dentro de `IdeiaDeTCC` e versionar a documentação**

A raiz atual do Git é a pasta pai `ProjectCode`, que tem vários outros projetos. Este projeto passa a ter o próprio repositório. Este passo roda a partir de `C:\Users\vynic\ProjectCode\IdeiaDeTCC`. Os passos seguintes indicam quando trocar de pasta.

```bash
git init -b main
git rev-parse --show-toplevel
git add docs
git commit -m "docs: spec, stack, diagramas, design das telas e planos" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```
Expected: `git rev-parse --show-toplevel` imprime o caminho de `IdeiaDeTCC` (e não `ProjectCode`). O commit é criado.

- [ ] **Step 2: Gerar o projeto Next.js**

```bash
npx --yes create-next-app@latest frontend --ts --tailwind --eslint --app --no-src-dir --import-alias "@/*" --use-npm --disable-git --yes
```
Expected: `Success! Created frontend`. O comando gera também `AGENTS.md` e `CLAUDE.md` dentro de `frontend/`. O `next dev` os recria, então **mantenha-os e versione junto**.

- [ ] **Step 3: Instalar as dependências**

```bash
cd frontend
npm install "@tanstack/react-query" zod
npm install -D "@types/node@^24"
npm install -D vitest @vitejs/plugin-react jsdom @testing-library/react @testing-library/dom @testing-library/user-event @testing-library/jest-dom msw vitest-axe
```
Expected: sem `ERESOLVE`. (O erro clássico é o `@types/node@^20` gerado pelo Next conflitar com o Vitest 5. A segunda linha já resolve.) O npm pode escolher MSW 2.x, e isso é o esperado.

- [ ] **Step 4: Configurar o shadcn/ui com os componentes dentro de `app/components`**

```bash
npx --yes shadcn@latest init -y -t next -b radix -p nova --no-monorepo
node -e "const fs=require('fs');const c=JSON.parse(fs.readFileSync('components.json','utf8'));c.aliases.components='@/app/components';c.aliases.ui='@/app/components/ui';fs.writeFileSync('components.json',JSON.stringify(c,null,2)+'\n')"
node -e "require('fs').rmSync('components',{recursive:true,force:true})"
npx --yes shadcn@latest add button card badge skeleton alert sheet tabs -y
```
Expected: o `init` cria `components/ui/button.tsx` e `lib/utils.ts`. O segundo comando muda os aliases, o terceiro apaga a pasta `components/` da raiz, e o `add` cria **sete arquivos em `app/components/ui/`**: `button`, `card`, `badge`, `skeleton`, `alert`, `sheet` e `tabs` (todos `.tsx`). Os componentes importam `cn` do pacote `cn`, e isso é o comportamento atual do shadcn, não um erro. O `lucide-react` (ícones) já vem instalado pelo `init`.
Conferir: `ls app/components/ui` lista os sete arquivos, e `components/` não existe na raiz de `frontend/`.

- [ ] **Step 5: Escrever o teste de fumaça da cadeia de ferramentas (ainda sem configuração)**

Create `frontend/tests/axe.ts`:
```ts
import { configureAxe } from 'vitest-axe'

// O jsdom não calcula estilos de verdade: contraste é conferido depois, no navegador real.
// A regra "region" cobra landmarks de página inteira, o que não se aplica a componentes isolados.
export const axe = configureAxe({
  rules: {
    'color-contrast': { enabled: false },
    region: { enabled: false },
  },
})
```

Create `frontend/mocks/servidor.ts`:
```ts
import { setupServer } from 'msw/node'

export const servidor = setupServer()
```

Create `frontend/tests/toolchain.test.tsx`:
```tsx
import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { servidor } from '@/mocks/servidor'
import { axe } from '@/tests/axe'
import { Button } from '@/app/components/ui/button'
import { Card, CardTitle } from '@/app/components/ui/card'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from '@/app/components/ui/sheet'

describe('cadeia de ferramentas', () => {
  it('MSW responde a fetch com credentials include', async () => {
    servidor.use(
      http.get('http://localhost:8080/api/v1/x', () => HttpResponse.json({ ok: true })),
    )
    const resposta = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/api/v1/x`, {
      credentials: 'include',
    })
    expect(await resposta.json()).toEqual({ ok: true })
  })

  it('renderiza componentes do shadcn, reage a clique e passa no axe', async () => {
    let cliques = 0
    const { container } = render(
      <Card>
        <CardTitle>Oi</CardTitle>
        <Button onClick={() => { cliques += 1 }}>Clique</Button>
      </Card>,
    )
    await userEvent.click(screen.getByRole('button', { name: 'Clique' }))
    expect(cliques).toBe(1)
    expect(screen.getByText('Oi')).toBeInTheDocument()
    expect(await axe(container)).toHaveNoViolations()
  })

  it('abre e fecha o painel lateral (Sheet) com o botão de fechar em português', async () => {
    render(
      <Sheet>
        <SheetTrigger asChild>
          <Button>Abrir</Button>
        </SheetTrigger>
        <SheetContent>
          <SheetHeader>
            <SheetTitle>Painel</SheetTitle>
            <SheetDescription>Descrição do painel</SheetDescription>
          </SheetHeader>
        </SheetContent>
      </Sheet>,
    )

    await userEvent.click(screen.getByRole('button', { name: 'Abrir' }))
    expect(await screen.findByRole('dialog', { name: 'Painel' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Fechar' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
```

- [ ] **Step 6: Rodar e ver falhar**

Run: `npx vitest run`
Expected: FAIL. Sem a configuração, o alias `@/` não resolve (`Failed to resolve import "@/mocks/servidor"`) e não há ambiente de DOM.

- [ ] **Step 7: Adicionar a configuração do Vitest e ver o botão em inglês falhar**

Create `frontend/vitest.config.mts`:
```ts
import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  resolve: { tsconfigPaths: true },
  test: {
    environment: 'jsdom',
    globals: false,
    setupFiles: ['./vitest.setup.ts'],
    css: false,
    env: { NEXT_PUBLIC_API_URL: 'http://localhost:8080' },
  },
})
```

Create `frontend/vitest.setup.ts`:
```ts
import '@testing-library/jest-dom/vitest'
import { afterAll, afterEach, beforeAll, expect } from 'vitest'
import { cleanup } from '@testing-library/react'
import * as axeMatchers from 'vitest-axe/matchers'
import { servidor } from './mocks/servidor'

expect.extend(axeMatchers)

beforeAll(() => servidor.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  cleanup()
  servidor.resetHandlers()
})
afterAll(() => servidor.close())
```

Create `frontend/vitest.d.ts`:
```ts
import type { AxeMatchers } from 'vitest-axe/matchers'

declare module 'vitest' {
  // eslint-disable-next-line @typescript-eslint/no-empty-object-type
  interface Assertion extends AxeMatchers {}
  // eslint-disable-next-line @typescript-eslint/no-empty-object-type
  interface AsymmetricMatchersContaining extends AxeMatchers {}
}
```

Run: `npx vitest run`
Expected: os dois primeiros testes passam e o terceiro **falha** procurando o botão `Fechar`, porque o shadcn gera o texto em inglês (`Close`). É o defeito que o teste existe para pegar.

- [ ] **Step 8: Traduzir o botão de fechar do Sheet e rodar tudo**

```bash
node -e "const fs=require('fs');const f='app/components/ui/sheet.tsx';const t=fs.readFileSync(f,'utf8');if(!t.includes('>Close<'))throw new Error('texto Close nao encontrado em sheet.tsx');fs.writeFileSync(f,t.replaceAll('>Close<','>Fechar<'))"
npm pkg set scripts.test="vitest run" scripts.test:watch="vitest" scripts.typecheck="tsc --noEmit"
npm test
npm run typecheck
npm run lint
npm run build
```
Expected: `npm test` mostra `3 passed`. `typecheck` e `lint` terminam sem erro. `build` termina com as rotas `/` e `/_not-found`.

- [ ] **Step 9: Commit**

```bash
cd ..
git add frontend
git commit -m "chore(frontend): esqueleto Next.js, shadcn em app/components e cadeia de testes" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
cd frontend
```

---

### Task 2: Teste de arquitetura das pastas

**Files:**
- Create: `frontend/tests/arquitetura.test.ts`

**Interfaces:**
- Consumes: pastas `app/`, `app/components/ui/`, `api/`, `services/` (as duas últimas ainda não existem; a regra as ignora até lá).
- Produces: regras verificadas em todo `npm test`: (1) `app/` não importa `@/api`; (2) `app/components/ui` não importa `@/services` nem `@/api`; (3) `services/` não importa `app`, React, Next nem TanStack; (4) `api/` não importa `app`, `services`, React, Next nem TanStack; (5) nenhum import relativo que sobe pastas (`../`) em `app`, `api` e `services`.

- [ ] **Step 1: Escrever o teste**

Create `frontend/tests/arquitetura.test.ts`:
```ts
import { readdirSync, readFileSync, type Dirent } from 'node:fs'
import { join, sep } from 'node:path'
import { describe, expect, it } from 'vitest'

const RAIZ = process.cwd()
const EXTENSOES = /\.(ts|tsx|mts|mjs)$/
const IMPORT =
  /(?:import|export)\s[^'"]*?from\s*['"]([^'"]+)['"]|import\s*['"]([^'"]+)['"]|import\(\s*['"]([^'"]+)['"]\s*\)/g

function entradasDe(pasta: string): Dirent[] {
  try {
    return readdirSync(join(RAIZ, pasta), { withFileTypes: true })
  } catch {
    return []
  }
}

function arquivosDe(pasta: string): string[] {
  return entradasDe(pasta).flatMap((entrada) => {
    const caminho = join(pasta, entrada.name)
    if (entrada.isDirectory()) return arquivosDe(caminho)
    return EXTENSOES.test(entrada.name) ? [caminho] : []
  })
}

function importsDe(arquivo: string): string[] {
  const codigo = readFileSync(join(RAIZ, arquivo), 'utf8')
  return [...codigo.matchAll(IMPORT)]
    .map((m) => m[1] ?? m[2] ?? m[3])
    .filter((especificador): especificador is string => Boolean(especificador))
}

type Regra = { descricao: string; pasta: string; proibidos: RegExp }

const REGRAS: Regra[] = [
  {
    descricao: 'app/ fala com services/, nunca direto com api/',
    pasta: 'app',
    proibidos: /^@\/api(\/|$)/,
  },
  {
    descricao: 'app/components/ui (shadcn) não conhece regra de negócio nem api',
    pasta: 'app/components/ui',
    proibidos: /^@\/(services|api)(\/|$)/,
  },
  {
    descricao: 'services/ não depende de app/, React, Next nem TanStack',
    pasta: 'services',
    proibidos: /^(@\/app(\/|$)|react(-dom)?(\/|$)|next(\/|$)|@tanstack\/)/,
  },
  {
    descricao: 'api/ não depende de app/, services/, React, Next nem TanStack',
    pasta: 'api',
    proibidos: /^(@\/(app|services)(\/|$)|react(-dom)?(\/|$)|next(\/|$)|@tanstack\/)/,
  },
  {
    descricao: 'app/ usa o alias @/ em vez de imports relativos que sobem pastas',
    pasta: 'app',
    proibidos: /^\.\.(\/|$)/,
  },
  {
    descricao: 'services/ usa o alias @/ em vez de imports relativos que sobem pastas',
    pasta: 'services',
    proibidos: /^\.\.(\/|$)/,
  },
  {
    descricao: 'api/ usa o alias @/ em vez de imports relativos que sobem pastas',
    pasta: 'api',
    proibidos: /^\.\.(\/|$)/,
  },
]

const PASTAS_QUE_DEVEM_EXISTIR = ['app', 'app/components/ui']

describe('arquitetura do frontend', () => {
  it.each(PASTAS_QUE_DEVEM_EXISTIR)('a suíte enxerga arquivos em %s', (pasta) => {
    expect(arquivosDe(pasta).length).toBeGreaterThan(0)
  })

  it.each(REGRAS)('$descricao', ({ pasta, proibidos }) => {
    const violacoes = arquivosDe(pasta).flatMap((arquivo) =>
      importsDe(arquivo)
        .filter((especificador) => proibidos.test(especificador))
        .map((especificador) => `${arquivo.split(sep).join('/')} importa "${especificador}"`),
    )
    expect(violacoes).toEqual([])
  })
})
```

- [ ] **Step 2: Rodar e ver passar no esqueleto**

Run: `npm test -- tests/arquitetura.test.ts`
Expected: PASS (o esqueleto ainda não viola nada).

- [ ] **Step 3: Provar que o teste realmente pega uma violação**

Create `frontend/app/violacao-temporaria.ts`:
```ts
import { obterSessao } from '@/api/sessao'

export const usado = obterSessao
```
Run: `npm test -- tests/arquitetura.test.ts`
Expected: FAIL na regra `app/ fala com services/, nunca direto com api/`, com a mensagem `app/violacao-temporaria.ts importa "@/api/sessao"`.

- [ ] **Step 4: Remover a violação e ver passar**

Run: `node -e "require('fs').rmSync('app/violacao-temporaria.ts')"` e depois `npm test -- tests/arquitetura.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
cd ..
git add frontend/tests/arquitetura.test.ts
git commit -m "test(frontend): teste de arquitetura das pastas app, api e services" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
cd frontend
```

---

### Task 3: Cliente HTTP (`api/cliente.ts`)

**Files:**
- Create: `frontend/api/cliente.ts`
- Test: `frontend/tests/api/cliente.test.ts`

**Interfaces:**
- Consumes: `servidor` de `@/mocks/servidor`.
- Produces:
  - `type TipoErroApi = 'rede' | 'http' | 'contrato'`
  - `class ApiError extends Error { readonly tipo: TipoErroApi; readonly status: number; constructor(tipo: TipoErroApi, status: number, mensagem: string) }` com `name === 'ApiError'`
  - `type MetodoHttp = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'` e `type OpcoesDeRequisicao = { metodo?: MetodoHttp; corpo?: unknown }`
  - `requisitar<T>(caminho: string, schema: ZodType<T>, opcoes?: OpcoesDeRequisicao): Promise<T>`: chama, valida o JSON de resposta com o schema. Base da URL: `process.env.NEXT_PUBLIC_API_URL ?? ''`.
  - `enviar(caminho: string, opcoes: OpcoesDeRequisicao): Promise<void>`: chama e ignora o corpo da resposta (por exemplo `204`).
  - `definirAuthId(valor: string): void` e `limparAuthId(): void`: guardam o valor do cabeçalho `X-Auth-Id` **só em memória e só no navegador**. No servidor do Next.js, `definirAuthId` é ignorado, porque lá a variável seria compartilhada entre usuários.
  - Toda chamada usa `credentials: 'include'` e `Accept: application/json`. Com `corpo`, envia `Content-Type: application/json`. Com o `authId` definido, envia `X-Auth-Id` em **toda** chamada, inclusive `GET`.

- [ ] **Step 1: Escrever os testes**

Create `frontend/tests/api/cliente.test.ts`:
```ts
import { afterEach, describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { z } from 'zod'
import { servidor } from '@/mocks/servidor'
import { ApiError, definirAuthId, enviar, limparAuthId, requisitar } from '@/api/cliente'

const URL_TESTE = 'http://localhost:8080/api/v1/teste'
const Schema = z.object({ ok: z.boolean() })
const AUTH_ID = 'a1b2c3d4'.repeat(8)

afterEach(() => {
  limparAuthId()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('requisitar (GET)', () => {
  it('devolve o corpo validado quando a resposta é 200', async () => {
    servidor.use(http.get(URL_TESTE, () => HttpResponse.json({ ok: true })))

    await expect(requisitar('/api/v1/teste', Schema)).resolves.toEqual({ ok: true })
  })

  it('envia o cookie de sessão (credentials include) e pede JSON', async () => {
    const espiao = vi.spyOn(globalThis, 'fetch').mockResolvedValue(Response.json({ ok: true }))

    await requisitar('/api/v1/teste', Schema)

    expect(espiao).toHaveBeenCalledWith(
      URL_TESTE,
      expect.objectContaining({
        method: 'GET',
        credentials: 'include',
        headers: { Accept: 'application/json' },
      }),
    )
  })

  it('lança ApiError do tipo http com o título do Problem Details', async () => {
    servidor.use(
      http.get(URL_TESTE, () =>
        HttpResponse.json({ title: 'Erro interno do servidor' }, { status: 500 }),
      ),
    )

    const chamada = requisitar('/api/v1/teste', Schema)

    await expect(chamada).rejects.toBeInstanceOf(ApiError)
    await expect(chamada).rejects.toMatchObject({
      name: 'ApiError',
      tipo: 'http',
      status: 500,
      message: 'Erro interno do servidor',
    })
  })

  it('usa mensagem genérica quando o corpo do erro não é JSON', async () => {
    servidor.use(http.get(URL_TESTE, () => new HttpResponse('bad gateway', { status: 502 })))

    await expect(requisitar('/api/v1/teste', Schema)).rejects.toMatchObject({
      tipo: 'http',
      status: 502,
      message: 'Erro 502 ao chamar o servidor.',
    })
  })

  it('preserva o status 401 para a tela decidir o que fazer', async () => {
    servidor.use(
      http.get(URL_TESTE, () => HttpResponse.json({ title: 'Não autenticado' }, { status: 401 })),
    )

    await expect(requisitar('/api/v1/teste', Schema)).rejects.toMatchObject({
      tipo: 'http',
      status: 401,
    })
  })

  it('lança ApiError do tipo rede quando não consegue conectar', async () => {
    servidor.use(http.get(URL_TESTE, () => HttpResponse.error()))

    await expect(requisitar('/api/v1/teste', Schema)).rejects.toMatchObject({
      tipo: 'rede',
      status: 0,
      message: 'Não foi possível conectar ao servidor.',
    })
  })

  it('lança ApiError do tipo contrato quando o corpo 200 não é JSON', async () => {
    servidor.use(
      http.get(
        URL_TESTE,
        () => new HttpResponse('{nao-e-json', { headers: { 'Content-Type': 'application/json' } }),
      ),
    )

    await expect(requisitar('/api/v1/teste', Schema)).rejects.toMatchObject({
      tipo: 'contrato',
      status: 200,
    })
  })

  it('lança ApiError do tipo contrato quando o JSON não segue o schema', async () => {
    servidor.use(http.get(URL_TESTE, () => HttpResponse.json({ ok: 'talvez' })))

    await expect(requisitar('/api/v1/teste', Schema)).rejects.toMatchObject({
      tipo: 'contrato',
      status: 200,
    })
  })
})

describe('X-Auth-Id', () => {
  it('sem authId definido, não envia o cabeçalho', async () => {
    const capturado: { valor?: string | null } = {}
    servidor.use(
      http.get(URL_TESTE, ({ request }) => {
        capturado.valor = request.headers.get('x-auth-id')
        return HttpResponse.json({ ok: true })
      }),
    )

    await requisitar('/api/v1/teste', Schema)

    expect(capturado.valor).toBeNull()
  })

  it('com authId definido, envia o cabeçalho em toda chamada, inclusive GET', async () => {
    definirAuthId(AUTH_ID)
    const capturado: { valor?: string | null } = {}
    servidor.use(
      http.get(URL_TESTE, ({ request }) => {
        capturado.valor = request.headers.get('x-auth-id')
        return HttpResponse.json({ ok: true })
      }),
    )

    await requisitar('/api/v1/teste', Schema)

    expect(capturado.valor).toBe(AUTH_ID)
  })

  it('também envia o cabeçalho em PATCH', async () => {
    definirAuthId(AUTH_ID)
    const capturado: { valor?: string | null } = {}
    servidor.use(
      http.patch(URL_TESTE, ({ request }) => {
        capturado.valor = request.headers.get('x-auth-id')
        return new HttpResponse(null, { status: 204 })
      }),
    )

    await enviar('/api/v1/teste', { metodo: 'PATCH', corpo: { lida: true } })

    expect(capturado.valor).toBe(AUTH_ID)
  })

  it('limparAuthId volta a não enviar o cabeçalho', async () => {
    definirAuthId(AUTH_ID)
    limparAuthId()
    const capturado: { valor?: string | null } = {}
    servidor.use(
      http.get(URL_TESTE, ({ request }) => {
        capturado.valor = request.headers.get('x-auth-id')
        return HttpResponse.json({ ok: true })
      }),
    )

    await requisitar('/api/v1/teste', Schema)

    expect(capturado.valor).toBeNull()
  })

  it('definirAuthId é ignorado fora do navegador: no servidor o valor seria compartilhado entre usuários', async () => {
    vi.stubGlobal('window', undefined)
    definirAuthId(AUTH_ID)
    vi.unstubAllGlobals()
    const capturado: { valor?: string | null } = {}
    servidor.use(
      http.get(URL_TESTE, ({ request }) => {
        capturado.valor = request.headers.get('x-auth-id')
        return HttpResponse.json({ ok: true })
      }),
    )

    await requisitar('/api/v1/teste', Schema)

    expect(capturado.valor).toBeNull()
  })

  it('um 401 por X-Auth-Id que não confere vira ApiError http com o título do servidor', async () => {
    definirAuthId('0'.repeat(64))
    servidor.use(
      http.get(URL_TESTE, () =>
        HttpResponse.json(
          { title: 'Não foi possível confirmar o usuário da sessão' },
          { status: 401 },
        ),
      ),
    )

    await expect(requisitar('/api/v1/teste', Schema)).rejects.toMatchObject({
      tipo: 'http',
      status: 401,
      message: 'Não foi possível confirmar o usuário da sessão',
    })
  })
})

describe('mutações', () => {
  it('PATCH envia o corpo em JSON com o Content-Type correto', async () => {
    let recebido: { tipo: string | null; corpo: unknown } | null = null
    servidor.use(
      http.patch(URL_TESTE, async ({ request }) => {
        recebido = { tipo: request.headers.get('content-type'), corpo: await request.json() }
        return new HttpResponse(null, { status: 204 })
      }),
    )

    await enviar('/api/v1/teste', { metodo: 'PATCH', corpo: { lida: true } })

    expect(recebido).toEqual({ tipo: 'application/json', corpo: { lida: true } })
  })

  it('requisitar aceita POST com corpo e valida a resposta', async () => {
    servidor.use(
      http.post(URL_TESTE, async ({ request }) =>
        HttpResponse.json({ ok: (await request.json()) !== null }),
      ),
    )

    await expect(
      requisitar('/api/v1/teste', Schema, { metodo: 'POST', corpo: { a: 1 } }),
    ).resolves.toEqual({ ok: true })
  })

  it('enviar resolve em uma resposta 204, sem corpo', async () => {
    servidor.use(http.patch(URL_TESTE, () => new HttpResponse(null, { status: 204 })))

    await expect(enviar('/api/v1/teste', { metodo: 'PATCH', corpo: {} })).resolves.toBeUndefined()
  })

  it('enviar lança ApiError http com a mensagem do servidor num 403', async () => {
    servidor.use(
      http.patch(URL_TESTE, () => HttpResponse.json({ title: 'Acesso negado' }, { status: 403 })),
    )

    await expect(enviar('/api/v1/teste', { metodo: 'PATCH', corpo: {} })).rejects.toMatchObject({
      tipo: 'http',
      status: 403,
      message: 'Acesso negado',
    })
  })

  it('enviar lança ApiError do tipo rede quando não consegue conectar', async () => {
    servidor.use(http.patch(URL_TESTE, () => HttpResponse.error()))

    await expect(enviar('/api/v1/teste', { metodo: 'PATCH', corpo: {} })).rejects.toMatchObject({
      tipo: 'rede',
      status: 0,
    })
  })
})
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `npm test -- tests/api/cliente.test.ts`
Expected: FAIL com `Failed to resolve import "@/api/cliente"`.

- [ ] **Step 3: Implementar**

Create `frontend/api/cliente.ts`:
```ts
import type { ZodType } from 'zod'

export type TipoErroApi = 'rede' | 'http' | 'contrato'

export class ApiError extends Error {
  readonly tipo: TipoErroApi
  readonly status: number

  constructor(tipo: TipoErroApi, status: number, mensagem: string) {
    super(mensagem)
    this.name = 'ApiError'
    this.tipo = tipo
    this.status = status
  }
}

export type MetodoHttp = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
export type OpcoesDeRequisicao = { metodo?: MetodoHttp; corpo?: unknown }

function urlBase(): string {
  return process.env.NEXT_PUBLIC_API_URL ?? ''
}

// Valor do cabeçalho X-Auth-Id: o backend o devolve em GET /api/v1/me e o confere em toda chamada autenticada.
// Vive só na memória do navegador. No servidor do Next.js essa variável seria compartilhada entre
// todos os usuários, então lá ela nunca é preenchida.
let authId: string | null = null

export function definirAuthId(valor: string): void {
  if (typeof window === 'undefined') return
  authId = valor
}

export function limparAuthId(): void {
  authId = null
}

async function tituloDoProblema(resposta: Response): Promise<string | null> {
  try {
    const corpo: unknown = await resposta.json()
    if (typeof corpo === 'object' && corpo !== null && 'title' in corpo) {
      return typeof corpo.title === 'string' ? corpo.title : null
    }
  } catch {
    // corpo ausente ou que não é JSON
  }
  return null
}

async function chamar(caminho: string, { metodo = 'GET', corpo }: OpcoesDeRequisicao): Promise<Response> {
  const cabecalhos: Record<string, string> = { Accept: 'application/json' }
  if (corpo !== undefined) cabecalhos['Content-Type'] = 'application/json'
  if (authId !== null) cabecalhos['X-Auth-Id'] = authId

  let resposta: Response
  try {
    resposta = await fetch(`${urlBase()}${caminho}`, {
      method: metodo,
      credentials: 'include',
      headers: cabecalhos,
      body: corpo === undefined ? undefined : JSON.stringify(corpo),
    })
  } catch {
    throw new ApiError('rede', 0, 'Não foi possível conectar ao servidor.')
  }

  if (!resposta.ok) {
    const titulo = await tituloDoProblema(resposta)
    throw new ApiError('http', resposta.status, titulo ?? `Erro ${resposta.status} ao chamar o servidor.`)
  }
  return resposta
}

export async function requisitar<T>(
  caminho: string,
  schema: ZodType<T>,
  opcoes: OpcoesDeRequisicao = {},
): Promise<T> {
  const resposta = await chamar(caminho, opcoes)

  let corpo: unknown
  try {
    corpo = await resposta.json()
  } catch {
    throw new ApiError('contrato', resposta.status, 'A resposta do servidor não é JSON válido.')
  }

  const resultado = schema.safeParse(corpo)
  if (!resultado.success) {
    throw new ApiError('contrato', resposta.status, 'A resposta do servidor não segue o contrato esperado.')
  }
  return resultado.data
}

export async function enviar(caminho: string, opcoes: OpcoesDeRequisicao): Promise<void> {
  await chamar(caminho, opcoes)
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `npm test -- tests/api/cliente.test.ts && npm run typecheck && npm test -- tests/arquitetura.test.ts`
Expected: todos passam. (O teste de arquitetura agora enxerga `api/` e confirma que ele não importa `app`, `services`, React nem Next.)

- [ ] **Step 5: Commit**

```bash
cd ..
git add frontend/api frontend/tests/api
git commit -m "feat(frontend): cliente HTTP com ApiError, mutações, X-Auth-Id e validação Zod" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
cd frontend
```

---

### Task 4: Sessão (`api/sessao.ts` e `services/sessao.ts`)

**Files:**
- Create: `frontend/api/sessao.ts`, `frontend/services/sessao.ts`, `frontend/tests/builders.ts`
- Test: `frontend/tests/api/sessao.test.ts`, `frontend/tests/services/sessao.test.ts`

**Interfaces:**
- Consumes: `requisitar` de `@/api/cliente`.
- Produces:
  - `@/api/sessao`: `STATUS_VENDEDOR` (`['PENDENTE','APROVADO','REPROVADO','SUSPENSO'] as const`), `SessaoDtoSchema`, `type SessaoDto`, `obterSessao(): Promise<SessaoDto>` → `GET /api/v1/me` (a resposta inclui `authId`: 64 caracteres hexadecimais minúsculos)
  - `@/services/sessao`:
    - `type StatusVendedor`, `type Sessao = { id: string; nome: string; email: string; vendedor: { status: StatusVendedor } | null }`
    - `type Modo = 'COMPRADOR' | 'VENDEDOR'`, `type SituacaoDoVendedor = 'NAO_E_VENDEDOR' | 'EM_ANALISE' | 'ATIVO' | 'REPROVADO' | 'SUSPENSO'`
    - `paraSessao(dto: SessaoDto): Sessao`, `situacaoDoVendedor(sessao: Sessao): SituacaoDoVendedor`, `podeVender(sessao: Sessao): boolean` (só `ATIVO`)
    - `modoDaRota(pathname: string): Modo` (`/vendedor` e `/vendedor/...` são `VENDEDOR`; `/vendedores` **não**)
    - `acessoPermitido(sessao: Sessao, pathname: string): boolean` (rotas de vendedor exigem `podeVender`)
    - `carregarSessao(): Promise<Sessao>`: busca a sessão, **entrega o `authId` ao cliente HTTP** (`definirAuthId`) e devolve a `Sessao`, que não guarda o `authId`
  - Helper de teste `umaSessaoDto(sobrescritas?: Partial<SessaoDto>): SessaoDto` em `@/tests/builders`, com `id: 'u-1'`, `nome: 'Vynicyus Silva'`, `email: 'vynicyus@example.com'`, `vendedor: null` e `authId: AUTH_ID_DE_TESTE` (constante exportada, `'0123456789abcdef'.repeat(4)`).

- [ ] **Step 1: Escrever o builder e os testes**

Create `frontend/tests/builders.ts`:
```ts
import type { SessaoDto } from '@/api/sessao'

export const AUTH_ID_DE_TESTE = '0123456789abcdef'.repeat(4)

export function umaSessaoDto(sobrescritas: Partial<SessaoDto> = {}): SessaoDto {
  return {
    id: 'u-1',
    nome: 'Vynicyus Silva',
    email: 'vynicyus@example.com',
    vendedor: null,
    authId: AUTH_ID_DE_TESTE,
    ...sobrescritas,
  }
}
```

Create `frontend/tests/api/sessao.test.ts`:
```ts
import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { servidor } from '@/mocks/servidor'
import { umaSessaoDto } from '@/tests/builders'
import { obterSessao } from '@/api/sessao'

const URL_SESSAO = 'http://localhost:8080/api/v1/me'

function responderCom(corpo: unknown, status = 200) {
  servidor.use(
    http.get(URL_SESSAO, () => HttpResponse.json(corpo as Record<string, unknown>, { status })),
  )
}

describe('obterSessao', () => {
  it('devolve a sessão de quem só é comprador', async () => {
    const dto = umaSessaoDto({ vendedor: null })
    responderCom(dto)

    await expect(obterSessao()).resolves.toEqual(dto)
  })

  it('devolve a sessão de vendedor com o status', async () => {
    const dto = umaSessaoDto({ vendedor: { status: 'APROVADO' } })
    responderCom(dto)

    await expect(obterSessao()).resolves.toEqual(dto)
  })

  it('aceita o campo vendedor ausente (o backend pode omitir null)', async () => {
    const bruto: Record<string, unknown> = { ...umaSessaoDto() }
    delete bruto.vendedor
    responderCom(bruto)

    const sessao = await obterSessao()

    expect(sessao.vendedor ?? null).toBeNull()
  })

  it.each([
    ['status de vendedor desconhecido', { vendedor: { status: 'TALVEZ' } }],
    ['e-mail inválido', { email: 'nao-e-um-email' }],
    ['nome vazio', { nome: '' }],
    ['id vazio', { id: '' }],
    ['authId ausente', { authId: undefined }],
    ['authId curto demais', { authId: 'abc' }],
    ['authId que não é hexadecimal minúsculo', { authId: 'Z'.repeat(64) }],
  ])('rejeita como erro de contrato: %s', async (_nome, alteracao) => {
    responderCom({ ...umaSessaoDto(), ...alteracao })

    await expect(obterSessao()).rejects.toMatchObject({ tipo: 'contrato' })
  })

  it('propaga o 401 como ApiError http', async () => {
    responderCom({ title: 'Não autenticado' }, 401)

    await expect(obterSessao()).rejects.toMatchObject({ tipo: 'http', status: 401 })
  })
})
```

Create `frontend/tests/services/sessao.test.ts`:
```ts
import { afterEach, describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { z } from 'zod'
import { limparAuthId, requisitar } from '@/api/cliente'
import { servidor } from '@/mocks/servidor'
import { umaSessaoDto } from '@/tests/builders'
import {
  acessoPermitido,
  carregarSessao,
  modoDaRota,
  paraSessao,
  podeVender,
  situacaoDoVendedor,
  type StatusVendedor,
} from '@/services/sessao'

function sessaoCom(status: StatusVendedor | null) {
  return paraSessao(umaSessaoDto({ vendedor: status === null ? null : { status } }))
}

describe('paraSessao', () => {
  it('copia os dados básicos e mantém o perfil de vendedor', () => {
    const sessao = paraSessao(umaSessaoDto({ vendedor: { status: 'APROVADO' } }))

    expect(sessao).toEqual({
      id: 'u-1',
      nome: 'Vynicyus Silva',
      email: 'vynicyus@example.com',
      vendedor: { status: 'APROVADO' },
    })
  })

  it('vendedor ausente vira null', () => {
    expect(paraSessao({ ...umaSessaoDto(), vendedor: undefined }).vendedor).toBeNull()
  })
})

describe('situacaoDoVendedor e podeVender', () => {
  it.each([
    [null, 'NAO_E_VENDEDOR', false],
    ['PENDENTE', 'EM_ANALISE', false],
    ['APROVADO', 'ATIVO', true],
    ['REPROVADO', 'REPROVADO', false],
    ['SUSPENSO', 'SUSPENSO', false],
  ] as const)('%s → %s (pode vender: %s)', (status, situacao, pode) => {
    const sessao = sessaoCom(status)

    expect(situacaoDoVendedor(sessao)).toBe(situacao)
    expect(podeVender(sessao)).toBe(pode)
  })
})

describe('modoDaRota', () => {
  it.each([
    ['/comprador', 'COMPRADOR'],
    ['/vendedor', 'VENDEDOR'],
    ['/vendedor/pecas-a-procura', 'VENDEDOR'],
    ['/vendedores', 'COMPRADOR'],
    ['/vendedor-x', 'COMPRADOR'],
    ['/configuracoes', 'COMPRADOR'],
    ['/', 'COMPRADOR'],
  ] as const)('%s → %s', (pathname, modo) => {
    expect(modoDaRota(pathname)).toBe(modo)
  })
})

describe('acessoPermitido', () => {
  it.each([null, 'PENDENTE', 'REPROVADO', 'SUSPENSO'] as const)(
    'quem é %s NÃO acessa rotas de vendedor',
    (status) => {
      const sessao = sessaoCom(status)

      expect(acessoPermitido(sessao, '/vendedor')).toBe(false)
      expect(acessoPermitido(sessao, '/vendedor/pecas-a-procura')).toBe(false)
    },
  )

  it('vendedor aprovado acessa as rotas de vendedor', () => {
    const sessao = sessaoCom('APROVADO')

    expect(acessoPermitido(sessao, '/vendedor')).toBe(true)
    expect(acessoPermitido(sessao, '/vendedor/pecas-a-procura')).toBe(true)
  })

  it.each([null, 'PENDENTE', 'APROVADO'] as const)(
    'todo mundo acessa as rotas de comprador e as configurações (%s)',
    (status) => {
      const sessao = sessaoCom(status)

      expect(acessoPermitido(sessao, '/comprador')).toBe(true)
      expect(acessoPermitido(sessao, '/configuracoes')).toBe(true)
    },
  )
})

describe('carregarSessao', () => {
  afterEach(() => {
    limparAuthId()
  })

  it('busca e converte a sessão', async () => {
    servidor.use(
      http.get('http://localhost:8080/api/v1/me', () =>
        HttpResponse.json(umaSessaoDto({ vendedor: { status: 'PENDENTE' } })),
      ),
    )

    await expect(carregarSessao()).resolves.toMatchObject({
      nome: 'Vynicyus Silva',
      vendedor: { status: 'PENDENTE' },
    })
  })

  it('entrega o authId ao cliente HTTP, que passa a enviá-lo em toda chamada', async () => {
    const capturado: { valor?: string | null } = {}
    servidor.use(
      http.get('http://localhost:8080/api/v1/me', () =>
        HttpResponse.json(umaSessaoDto({ authId: 'f'.repeat(64) })),
      ),
      http.get('http://localhost:8080/api/v1/outra', ({ request }) => {
        capturado.valor = request.headers.get('x-auth-id')
        return HttpResponse.json({ ok: true })
      }),
    )

    await carregarSessao()
    await requisitar('/api/v1/outra', z.object({ ok: z.boolean() }))

    expect(capturado.valor).toBe('f'.repeat(64))
  })

  it('propaga o erro da API', async () => {
    servidor.use(
      http.get('http://localhost:8080/api/v1/me', () =>
        HttpResponse.json({ title: 'Não autenticado' }, { status: 401 }),
      ),
    )

    await expect(carregarSessao()).rejects.toMatchObject({ tipo: 'http', status: 401 })
  })
})
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `npm test -- tests/api/sessao.test.ts tests/services/sessao.test.ts`
Expected: FAIL com `Failed to resolve import "@/api/sessao"`.

- [ ] **Step 3: Implementar**

Create `frontend/api/sessao.ts`:
```ts
import { z } from 'zod'
import { requisitar } from '@/api/cliente'

export const STATUS_VENDEDOR = ['PENDENTE', 'APROVADO', 'REPROVADO', 'SUSPENSO'] as const

export const SessaoDtoSchema = z.object({
  id: z.string().min(1),
  nome: z.string().min(1),
  email: z.email(),
  // Valor do cabeçalho X-Auth-Id: HMAC-SHA256 do id do usuário, em hexadecimal minúsculo.
  authId: z.string().regex(/^[0-9a-f]{64}$/),
  // null ou ausente: o usuário só tem o perfil de comprador.
  vendedor: z.object({ status: z.enum(STATUS_VENDEDOR) }).nullish(),
})

export type SessaoDto = z.infer<typeof SessaoDtoSchema>

export function obterSessao(): Promise<SessaoDto> {
  return requisitar('/api/v1/me', SessaoDtoSchema)
}
```

Create `frontend/services/sessao.ts`:
```ts
import { definirAuthId } from '@/api/cliente'
import { obterSessao, type SessaoDto } from '@/api/sessao'

export type StatusVendedor = NonNullable<SessaoDto['vendedor']>['status']

export type Sessao = {
  id: string
  nome: string
  email: string
  vendedor: { status: StatusVendedor } | null
}

export type Modo = 'COMPRADOR' | 'VENDEDOR'
export type SituacaoDoVendedor = 'NAO_E_VENDEDOR' | 'EM_ANALISE' | 'ATIVO' | 'REPROVADO' | 'SUSPENSO'

export function paraSessao(dto: SessaoDto): Sessao {
  return {
    id: dto.id,
    nome: dto.nome,
    email: dto.email,
    vendedor: dto.vendedor ? { status: dto.vendedor.status } : null,
  }
}

export function situacaoDoVendedor(sessao: Sessao): SituacaoDoVendedor {
  if (sessao.vendedor === null) return 'NAO_E_VENDEDOR'
  switch (sessao.vendedor.status) {
    case 'PENDENTE':
      return 'EM_ANALISE'
    case 'APROVADO':
      return 'ATIVO'
    case 'REPROVADO':
      return 'REPROVADO'
    case 'SUSPENSO':
      return 'SUSPENSO'
  }
}

// AUT-05: o vendedor só age na plataforma depois de aprovado.
export function podeVender(sessao: Sessao): boolean {
  return situacaoDoVendedor(sessao) === 'ATIVO'
}

const RAIZ_DO_VENDEDOR = '/vendedor'

export function modoDaRota(pathname: string): Modo {
  const ehDoVendedor = pathname === RAIZ_DO_VENDEDOR || pathname.startsWith(`${RAIZ_DO_VENDEDOR}/`)
  return ehDoVendedor ? 'VENDEDOR' : 'COMPRADOR'
}

export function acessoPermitido(sessao: Sessao, pathname: string): boolean {
  return modoDaRota(pathname) === 'COMPRADOR' || podeVender(sessao)
}

export async function carregarSessao(): Promise<Sessao> {
  const dto = await obterSessao()
  // A partir daqui toda chamada autenticada leva o X-Auth-Id que o backend acabou de devolver.
  definirAuthId(dto.authId)
  return paraSessao(dto)
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `npm test -- tests/api/sessao.test.ts tests/services/sessao.test.ts && npm run typecheck && npm test -- tests/arquitetura.test.ts`
Expected: todos passam.

- [ ] **Step 5: Commit**

```bash
cd ..
git add frontend/api/sessao.ts frontend/services/sessao.ts frontend/tests/builders.ts frontend/tests/api/sessao.test.ts frontend/tests/services/sessao.test.ts
git commit -m "feat(frontend): sessão do usuário, situação de vendedor e regra de acesso por rota" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
cd frontend
```

---

### Task 5: Navegação por perfil (`services/navegacao.ts`)

**Files:**
- Create: `frontend/services/navegacao.ts`
- Test: `frontend/tests/services/navegacao.test.ts`

**Interfaces:**
- Consumes: `Sessao`, `podeVender`, `situacaoDoVendedor` de `@/services/sessao`; `umaSessaoDto` de `@/tests/builders`.
- Produces:
  - `type IdNavegacao = 'comprar' | 'vender' | 'pecas-a-procura' | 'configuracoes'`
  - `type ItemNavegacao = { id: IdNavegacao; rotulo: string; href: string; ativo: boolean; desabilitado: boolean }`
  - `type Navegacao = { perfis: ItemNavegacao[]; pecasAProcura: ItemNavegacao | null; configuracoes: ItemNavegacao }`
  - `montarNavegacao(sessao: Sessao, pathname: string): Navegacao`
    - `perfis`: sempre "Comprar" (`/comprador`). "Vender" (`/vendedor`) só se há perfil de vendedor: habilitado se aprovado, "Vender (em análise)" desabilitado se pendente, "Vender (indisponível)" desabilitado se reprovado ou suspenso.
    - `pecasAProcura` (`/vendedor/pecas-a-procura`, rótulo "Peças à procura"): só para vendedor aprovado, senão `null`.
    - `configuracoes` (`/configuracoes`, rótulo "Configurações"): sempre.
    - `ativo`: só o item cuja rota é a **mais específica** que casa com o pathname (`/vendedor/pecas-a-procura` não acende também "Vender").

- [ ] **Step 1: Escrever os testes**

Create `frontend/tests/services/navegacao.test.ts`:
```ts
import { describe, expect, it } from 'vitest'
import { umaSessaoDto } from '@/tests/builders'
import { montarNavegacao } from '@/services/navegacao'
import { paraSessao, type StatusVendedor } from '@/services/sessao'

function sessaoCom(status: StatusVendedor | null) {
  return paraSessao(umaSessaoDto({ vendedor: status === null ? null : { status } }))
}

describe('montarNavegacao: itens de perfil', () => {
  it('quem só é comprador vê apenas "Comprar"', () => {
    const { perfis, pecasAProcura } = montarNavegacao(sessaoCom(null), '/comprador')

    expect(perfis.map((p) => p.rotulo)).toEqual(['Comprar'])
    expect(pecasAProcura).toBeNull()
  })

  it('vendedor aprovado vê "Comprar" e "Vender" habilitados, e o campo Peças à procura', () => {
    const { perfis, pecasAProcura } = montarNavegacao(sessaoCom('APROVADO'), '/comprador')

    expect(perfis).toEqual([
      { id: 'comprar', rotulo: 'Comprar', href: '/comprador', ativo: true, desabilitado: false },
      { id: 'vender', rotulo: 'Vender', href: '/vendedor', ativo: false, desabilitado: false },
    ])
    expect(pecasAProcura).toEqual({
      id: 'pecas-a-procura',
      rotulo: 'Peças à procura',
      href: '/vendedor/pecas-a-procura',
      ativo: false,
      desabilitado: false,
    })
  })

  it('vendedor em análise vê "Vender (em análise)" desabilitado e não vê Peças à procura', () => {
    const { perfis, pecasAProcura } = montarNavegacao(sessaoCom('PENDENTE'), '/comprador')

    expect(perfis[1]).toMatchObject({ rotulo: 'Vender (em análise)', desabilitado: true })
    expect(pecasAProcura).toBeNull()
  })

  it.each(['REPROVADO', 'SUSPENSO'] as const)(
    'vendedor %s vê "Vender (indisponível)" desabilitado e não vê Peças à procura',
    (status) => {
      const { perfis, pecasAProcura } = montarNavegacao(sessaoCom(status), '/comprador')

      expect(perfis[1]).toMatchObject({ rotulo: 'Vender (indisponível)', desabilitado: true })
      expect(pecasAProcura).toBeNull()
    },
  )

  it('Configurações aparece para todos', () => {
    expect(montarNavegacao(sessaoCom(null), '/comprador').configuracoes).toEqual({
      id: 'configuracoes',
      rotulo: 'Configurações',
      href: '/configuracoes',
      ativo: false,
      desabilitado: false,
    })
  })
})

describe('montarNavegacao: item ativo', () => {
  const aprovado = sessaoCom('APROVADO')

  function ativos(pathname: string): string[] {
    const n = montarNavegacao(aprovado, pathname)
    return [...n.perfis, n.pecasAProcura, n.configuracoes].flatMap((item) =>
      item !== null && item.ativo ? [item.id] : [],
    )
  }

  it.each([
    ['/comprador', ['comprar']],
    ['/vendedor', ['vender']],
    ['/vendedor/pecas-a-procura', ['pecas-a-procura']],
    ['/configuracoes', ['configuracoes']],
    ['/', []],
    ['/vendedores', []],
  ])('%s → %j', (pathname, esperado) => {
    expect(ativos(pathname)).toEqual(esperado)
  })
})
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `npm test -- tests/services/navegacao.test.ts`
Expected: FAIL com `Failed to resolve import "@/services/navegacao"`.

- [ ] **Step 3: Implementar**

Create `frontend/services/navegacao.ts`:
```ts
import { podeVender, situacaoDoVendedor, type Sessao } from '@/services/sessao'

export type IdNavegacao = 'comprar' | 'vender' | 'pecas-a-procura' | 'configuracoes'

export type ItemNavegacao = {
  id: IdNavegacao
  rotulo: string
  href: string
  ativo: boolean
  desabilitado: boolean
}

export type Navegacao = {
  perfis: ItemNavegacao[]
  pecasAProcura: ItemNavegacao | null
  configuracoes: ItemNavegacao
}

const ROTAS: Record<IdNavegacao, string> = {
  comprar: '/comprador',
  vender: '/vendedor',
  'pecas-a-procura': '/vendedor/pecas-a-procura',
  configuracoes: '/configuracoes',
}

// Vence a rota mais específica: /vendedor/pecas-a-procura não acende também "Vender".
function idAtivo(pathname: string): IdNavegacao | null {
  const candidatos = (Object.entries(ROTAS) as [IdNavegacao, string][])
    .filter(([, rota]) => pathname === rota || pathname.startsWith(`${rota}/`))
    .sort((a, b) => b[1].length - a[1].length)
  return candidatos[0]?.[0] ?? null
}

export function montarNavegacao(sessao: Sessao, pathname: string): Navegacao {
  const ativo = idAtivo(pathname)
  const item = (id: IdNavegacao, rotulo: string, desabilitado = false): ItemNavegacao => ({
    id,
    rotulo,
    href: ROTAS[id],
    ativo: ativo === id,
    desabilitado,
  })

  const perfis = [item('comprar', 'Comprar')]
  switch (situacaoDoVendedor(sessao)) {
    case 'ATIVO':
      perfis.push(item('vender', 'Vender'))
      break
    case 'EM_ANALISE':
      perfis.push(item('vender', 'Vender (em análise)', true))
      break
    case 'REPROVADO':
    case 'SUSPENSO':
      perfis.push(item('vender', 'Vender (indisponível)', true))
      break
    case 'NAO_E_VENDEDOR':
      break
  }

  return {
    perfis,
    pecasAProcura: podeVender(sessao) ? item('pecas-a-procura', 'Peças à procura') : null,
    configuracoes: item('configuracoes', 'Configurações'),
  }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `npm test -- tests/services/navegacao.test.ts && npm run typecheck && npm run lint && npm test -- tests/arquitetura.test.ts`
Expected: todos passam.

- [ ] **Step 5: Commit**

```bash
cd ..
git add frontend/services/navegacao.ts frontend/tests/services/navegacao.test.ts
git commit -m "feat(frontend): regra de navegação por perfil e item ativo" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
cd frontend
```

---

### Task 6: Notificações (`api/notificacoes.ts` e `services/notificacoes.ts`)

**Files:**
- Create: `frontend/api/notificacoes.ts`, `frontend/services/notificacoes.ts`
- Modify: `frontend/tests/builders.ts` (acrescenta dois builders)
- Test: `frontend/tests/api/notificacoes.test.ts`, `frontend/tests/services/notificacoes.test.ts`

**Interfaces:**
- Consumes: `requisitar`, `enviar` de `@/api/cliente`.
- Produces:
  - `@/api/notificacoes`: `TIPOS_NOTIFICACAO` (`['CHAT','PROPOSTA','PECA_A_PROCURA','SISTEMA'] as const`), `type TipoNotificacao`, `NotificacaoDtoSchema`, `ListaNotificacoesDtoSchema`, `type NotificacaoDto`, `type ListaNotificacoesDto = { itens: NotificacaoDto[]; naoLidas: { chat: number; outras: number; pecasAProcura: number } }`, `type ParametrosDeNotificacoes = { tipos: readonly TipoNotificacao[]; limite: number }`, `listarNotificacoes(parametros): Promise<ListaNotificacoesDto>` (`GET /api/v1/notificacoes?tipos=…&limite=…`), `marcarNotificacaoComoLida(id: string): Promise<void>` (`PATCH /api/v1/notificacoes/{id}` com `{ lida: true }`)
  - `@/services/notificacoes`:
    - `type Notificacao = { id: string; tipo: TipoNotificacao; titulo: string; resumo: string; criadaEm: Date; lida: boolean }`
    - `type ContadoresNaoLidas = { chat: number; outras: number; pecasAProcura: number }`, `type PainelNotificacoes = { itens: Notificacao[]; naoLidas: ContadoresNaoLidas }`
    - `TIPOS_DO_SINO: readonly TipoNotificacao[]` = `['CHAT','PROPOSTA','SISTEMA']`
    - `type AbaNotificacoes = 'TODAS' | 'CHAT'`
    - `paraNotificacao(dto): Notificacao`, `filtrarPorAba(itens: readonly Notificacao[], aba: AbaNotificacoes): Notificacao[]`, `totalNoSino(c: ContadoresNaoLidas): number` (chat + outras, **sem** peças à procura)
    - `carregarNotificacoes(): Promise<PainelNotificacoes>`: pede `TIPOS_DO_SINO` com limite 20, **descarta** `PECA_A_PROCURA` se o servidor mandar, ordena da mais recente para a mais antiga
    - `marcarComoLida(id: string): Promise<void>`
  - Builders de teste: `umaNotificacaoDto(sobrescritas?)` (`id: 'n-1'`, `tipo: 'CHAT'`, `titulo: 'Auto Peças Silva'`, `resumo: 'Tenho o farol, posso mandar foto?'`, `criadaEm: '2026-09-29T11:55:00Z'`, `lida: false`) e `umaListaDeNotificacoesDto(sobrescritas?)` (`itens: []`, contadores `0`).

- [ ] **Step 1: Acrescentar os builders e escrever os testes**

Modify `frontend/tests/builders.ts`: substitua o conteúdo por
```ts
import type { ListaNotificacoesDto, NotificacaoDto } from '@/api/notificacoes'
import type { SessaoDto } from '@/api/sessao'

export const AUTH_ID_DE_TESTE = '0123456789abcdef'.repeat(4)

export function umaSessaoDto(sobrescritas: Partial<SessaoDto> = {}): SessaoDto {
  return {
    id: 'u-1',
    nome: 'Vynicyus Silva',
    email: 'vynicyus@example.com',
    vendedor: null,
    authId: AUTH_ID_DE_TESTE,
    ...sobrescritas,
  }
}

export function umaNotificacaoDto(sobrescritas: Partial<NotificacaoDto> = {}): NotificacaoDto {
  return {
    id: 'n-1',
    tipo: 'CHAT',
    titulo: 'Auto Peças Silva',
    resumo: 'Tenho o farol, posso mandar foto?',
    criadaEm: '2026-09-29T11:55:00Z',
    lida: false,
    ...sobrescritas,
  }
}

export function umaListaDeNotificacoesDto(
  sobrescritas: Partial<ListaNotificacoesDto> = {},
): ListaNotificacoesDto {
  return {
    itens: [],
    naoLidas: { chat: 0, outras: 0, pecasAProcura: 0 },
    ...sobrescritas,
  }
}
```

Create `frontend/tests/api/notificacoes.test.ts`:
```ts
import { afterEach, describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { servidor } from '@/mocks/servidor'
import { umaListaDeNotificacoesDto, umaNotificacaoDto } from '@/tests/builders'
import { definirAuthId, limparAuthId } from '@/api/cliente'
import { listarNotificacoes, marcarNotificacaoComoLida } from '@/api/notificacoes'

const URL_NOTIFICACOES = 'http://localhost:8080/api/v1/notificacoes'
const PARAMETROS = { tipos: ['CHAT', 'PROPOSTA', 'SISTEMA'], limite: 20 } as const

function responderCom(corpo: unknown) {
  servidor.use(
    http.get(URL_NOTIFICACOES, () => HttpResponse.json(corpo as Record<string, unknown>)),
  )
}

describe('listarNotificacoes', () => {
  it('devolve a lista validada', async () => {
    const lista = umaListaDeNotificacoesDto({
      itens: [umaNotificacaoDto({ id: 'n-9', tipo: 'PROPOSTA', resumo: '' })],
      naoLidas: { chat: 2, outras: 1, pecasAProcura: 5 },
    })
    responderCom(lista)

    await expect(listarNotificacoes(PARAMETROS)).resolves.toEqual(lista)
  })

  it('envia os tipos e o limite na consulta', async () => {
    const capturado: { consulta?: URLSearchParams } = {}
    servidor.use(
      http.get(URL_NOTIFICACOES, ({ request }) => {
        capturado.consulta = new URL(request.url).searchParams
        return HttpResponse.json(umaListaDeNotificacoesDto())
      }),
    )

    await listarNotificacoes(PARAMETROS)

    expect(capturado.consulta?.get('tipos')).toBe('CHAT,PROPOSTA,SISTEMA')
    expect(capturado.consulta?.get('limite')).toBe('20')
  })

  it.each([
    ['tipo desconhecido', { itens: [{ ...umaNotificacaoDto(), tipo: 'EMAIL' }] }],
    ['título vazio', { itens: [{ ...umaNotificacaoDto(), titulo: '' }] }],
    ['data fora do padrão ISO', { itens: [{ ...umaNotificacaoDto(), criadaEm: 'ontem' }] }],
    ['lida que não é booleano', { itens: [{ ...umaNotificacaoDto(), lida: 'sim' }] }],
    ['contador negativo', { naoLidas: { chat: -1, outras: 0, pecasAProcura: 0 } }],
    ['contador com casas decimais', { naoLidas: { chat: 1.5, outras: 0, pecasAProcura: 0 } }],
  ])('rejeita como erro de contrato: %s', async (_nome, alteracao) => {
    responderCom({ ...umaListaDeNotificacoesDto(), ...alteracao })

    await expect(listarNotificacoes(PARAMETROS)).rejects.toMatchObject({ tipo: 'contrato' })
  })

  it('rejeita resposta sem os contadores', async () => {
    responderCom({ itens: [] })

    await expect(listarNotificacoes(PARAMETROS)).rejects.toMatchObject({ tipo: 'contrato' })
  })
})

describe('marcarNotificacaoComoLida', () => {
  afterEach(() => {
    limparAuthId()
  })

  it('faz PATCH com { lida: true } e o X-Auth-Id', async () => {
    definirAuthId('a1b2c3d4'.repeat(8))
    let recebido: { caminho: string; corpo: unknown; authId: string | null } | null = null
    servidor.use(
      http.patch(`${URL_NOTIFICACOES}/:id`, async ({ request }) => {
        recebido = {
          caminho: new URL(request.url).pathname,
          corpo: await request.json(),
          authId: request.headers.get('x-auth-id'),
        }
        return new HttpResponse(null, { status: 204 })
      }),
    )

    await marcarNotificacaoComoLida('n-1')

    expect(recebido).toEqual({
      caminho: '/api/v1/notificacoes/n-1',
      corpo: { lida: true },
      authId: 'a1b2c3d4'.repeat(8),
    })
  })

  it('codifica o id no caminho', async () => {
    let caminho = ''
    servidor.use(
      http.patch(`${URL_NOTIFICACOES}/:id`, ({ request }) => {
        caminho = new URL(request.url).pathname
        return new HttpResponse(null, { status: 204 })
      }),
    )

    await marcarNotificacaoComoLida('a b/c')

    expect(caminho).toBe('/api/v1/notificacoes/a%20b%2Fc')
  })

  it('propaga o 401 do X-Auth-Id que não confere como ApiError com a mensagem do servidor', async () => {
    servidor.use(
      http.patch(`${URL_NOTIFICACOES}/:id`, () =>
        HttpResponse.json(
          { title: 'Não foi possível confirmar o usuário da sessão' },
          { status: 401 },
        ),
      ),
    )

    await expect(marcarNotificacaoComoLida('n-1')).rejects.toMatchObject({
      tipo: 'http',
      status: 401,
      message: 'Não foi possível confirmar o usuário da sessão',
    })
  })
})
```

Create `frontend/tests/services/notificacoes.test.ts`:
```ts
import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { servidor } from '@/mocks/servidor'
import { umaListaDeNotificacoesDto, umaNotificacaoDto } from '@/tests/builders'
import {
  carregarNotificacoes,
  filtrarPorAba,
  marcarComoLida,
  paraNotificacao,
  totalNoSino,
  type Notificacao,
} from '@/services/notificacoes'

const URL_NOTIFICACOES = 'http://localhost:8080/api/v1/notificacoes'

function umaNotificacao(sobrescritas: Partial<Notificacao> = {}): Notificacao {
  return { ...paraNotificacao(umaNotificacaoDto()), ...sobrescritas }
}

describe('paraNotificacao', () => {
  it('converte a data para Date e mantém os demais campos', () => {
    const n = paraNotificacao(umaNotificacaoDto({ criadaEm: '2026-09-29T11:55:00Z', lida: true }))

    expect(n.criadaEm).toEqual(new Date('2026-09-29T11:55:00Z'))
    expect(n).toMatchObject({ id: 'n-1', tipo: 'CHAT', lida: true })
  })
})

describe('filtrarPorAba', () => {
  const itens = [
    umaNotificacao({ id: 'a', tipo: 'CHAT' }),
    umaNotificacao({ id: 'b', tipo: 'PROPOSTA' }),
    umaNotificacao({ id: 'c', tipo: 'SISTEMA' }),
    umaNotificacao({ id: 'd', tipo: 'CHAT' }),
  ]

  it('TODAS devolve tudo, em cópia', () => {
    const resultado = filtrarPorAba(itens, 'TODAS')

    expect(resultado.map((n) => n.id)).toEqual(['a', 'b', 'c', 'd'])
    expect(resultado).not.toBe(itens)
  })

  it('CHAT devolve só as mensagens de chat', () => {
    expect(filtrarPorAba(itens, 'CHAT').map((n) => n.id)).toEqual(['a', 'd'])
  })

  it('devolve vazio quando nada combina', () => {
    expect(filtrarPorAba([umaNotificacao({ tipo: 'SISTEMA' })], 'CHAT')).toEqual([])
  })
})

describe('totalNoSino', () => {
  it('soma chat e outras, e NÃO inclui peças à procura', () => {
    expect(totalNoSino({ chat: 2, outras: 1, pecasAProcura: 50 })).toBe(3)
  })

  it('é zero quando não há nada não lido', () => {
    expect(totalNoSino({ chat: 0, outras: 0, pecasAProcura: 7 })).toBe(0)
  })
})

describe('carregarNotificacoes', () => {
  it('pede só os tipos do sino, com limite 20', async () => {
    const capturado: { consulta?: URLSearchParams } = {}
    servidor.use(
      http.get(URL_NOTIFICACOES, ({ request }) => {
        capturado.consulta = new URL(request.url).searchParams
        return HttpResponse.json(umaListaDeNotificacoesDto())
      }),
    )

    await carregarNotificacoes()

    expect(capturado.consulta?.get('tipos')).toBe('CHAT,PROPOSTA,SISTEMA')
    expect(capturado.consulta?.get('limite')).toBe('20')
  })

  it('ordena da mais recente para a mais antiga e repassa os contadores', async () => {
    servidor.use(
      http.get(URL_NOTIFICACOES, () =>
        HttpResponse.json(
          umaListaDeNotificacoesDto({
            itens: [
              umaNotificacaoDto({ id: 'antiga', criadaEm: '2026-09-01T00:00:00Z' }),
              umaNotificacaoDto({ id: 'nova', criadaEm: '2026-09-29T00:00:00Z' }),
            ],
            naoLidas: { chat: 2, outras: 1, pecasAProcura: 4 },
          }),
        ),
      ),
    )

    const painel = await carregarNotificacoes()

    expect(painel.itens.map((n) => n.id)).toEqual(['nova', 'antiga'])
    expect(painel.itens[0]?.criadaEm).toBeInstanceOf(Date)
    expect(painel.naoLidas).toEqual({ chat: 2, outras: 1, pecasAProcura: 4 })
  })

  it('descarta peças à procura, mesmo que o servidor as envie junto', async () => {
    servidor.use(
      http.get(URL_NOTIFICACOES, () =>
        HttpResponse.json(
          umaListaDeNotificacoesDto({
            itens: [
              umaNotificacaoDto({ id: 'chat', tipo: 'CHAT' }),
              umaNotificacaoDto({ id: 'procura', tipo: 'PECA_A_PROCURA' }),
            ],
          }),
        ),
      ),
    )

    const painel = await carregarNotificacoes()

    expect(painel.itens.map((n) => n.id)).toEqual(['chat'])
  })

  it('propaga o erro da API', async () => {
    servidor.use(
      http.get(URL_NOTIFICACOES, () => HttpResponse.json({ title: 'Erro interno' }, { status: 500 })),
    )

    await expect(carregarNotificacoes()).rejects.toMatchObject({ tipo: 'http', status: 500 })
  })
})

describe('marcarComoLida', () => {
  it('faz o PATCH da notificação', async () => {
    let id: string | readonly string[] | undefined
    servidor.use(
      http.patch(`${URL_NOTIFICACOES}/:id`, ({ params }) => {
        id = params.id
        return new HttpResponse(null, { status: 204 })
      }),
    )

    await expect(marcarComoLida('n-7')).resolves.toBeUndefined()
    expect(id).toBe('n-7')
  })
})
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `npm test -- tests/api/notificacoes.test.ts tests/services/notificacoes.test.ts`
Expected: FAIL com `Failed to resolve import "@/api/notificacoes"`.

- [ ] **Step 3: Implementar**

Create `frontend/api/notificacoes.ts`:
```ts
import { z } from 'zod'
import { enviar, requisitar } from '@/api/cliente'

export const TIPOS_NOTIFICACAO = ['CHAT', 'PROPOSTA', 'PECA_A_PROCURA', 'SISTEMA'] as const
export type TipoNotificacao = (typeof TIPOS_NOTIFICACAO)[number]

export const NotificacaoDtoSchema = z.object({
  id: z.string().min(1),
  tipo: z.enum(TIPOS_NOTIFICACAO),
  titulo: z.string().min(1),
  resumo: z.string(),
  criadaEm: z.iso.datetime(),
  lida: z.boolean(),
})

const ContadorSchema = z.number().int().nonnegative()

export const ListaNotificacoesDtoSchema = z.object({
  itens: z.array(NotificacaoDtoSchema),
  // Os contadores valem para todos os tipos, não só para os pedidos em "tipos".
  naoLidas: z.object({
    chat: ContadorSchema,
    outras: ContadorSchema,
    pecasAProcura: ContadorSchema,
  }),
})

export type NotificacaoDto = z.infer<typeof NotificacaoDtoSchema>
export type ListaNotificacoesDto = z.infer<typeof ListaNotificacoesDtoSchema>

export type ParametrosDeNotificacoes = {
  tipos: readonly TipoNotificacao[]
  limite: number
}

export function listarNotificacoes({ tipos, limite }: ParametrosDeNotificacoes): Promise<ListaNotificacoesDto> {
  const consulta = new URLSearchParams({ tipos: tipos.join(','), limite: String(limite) })
  return requisitar(`/api/v1/notificacoes?${consulta.toString()}`, ListaNotificacoesDtoSchema)
}

export function marcarNotificacaoComoLida(id: string): Promise<void> {
  return enviar(`/api/v1/notificacoes/${encodeURIComponent(id)}`, {
    metodo: 'PATCH',
    corpo: { lida: true },
  })
}
```

Create `frontend/services/notificacoes.ts`:
```ts
import {
  listarNotificacoes,
  marcarNotificacaoComoLida,
  type NotificacaoDto,
  type TipoNotificacao,
} from '@/api/notificacoes'

export type Notificacao = {
  id: string
  tipo: TipoNotificacao
  titulo: string
  resumo: string
  criadaEm: Date
  lida: boolean
}

export type ContadoresNaoLidas = { chat: number; outras: number; pecasAProcura: number }
export type PainelNotificacoes = { itens: Notificacao[]; naoLidas: ContadoresNaoLidas }
export type AbaNotificacoes = 'TODAS' | 'CHAT'

// O sino mostra tudo, menos "peças à procura": elas têm um campo próprio na barra de navegação.
export const TIPOS_DO_SINO: readonly TipoNotificacao[] = ['CHAT', 'PROPOSTA', 'SISTEMA']
const LIMITE_DO_SINO = 20

export function paraNotificacao(dto: NotificacaoDto): Notificacao {
  return {
    id: dto.id,
    tipo: dto.tipo,
    titulo: dto.titulo,
    resumo: dto.resumo,
    criadaEm: new Date(dto.criadaEm),
    lida: dto.lida,
  }
}

export function filtrarPorAba(itens: readonly Notificacao[], aba: AbaNotificacoes): Notificacao[] {
  if (aba === 'TODAS') return [...itens]
  return itens.filter((notificacao) => notificacao.tipo === 'CHAT')
}

export function totalNoSino(contadores: ContadoresNaoLidas): number {
  return contadores.chat + contadores.outras
}

export async function carregarNotificacoes(): Promise<PainelNotificacoes> {
  const { itens, naoLidas } = await listarNotificacoes({ tipos: TIPOS_DO_SINO, limite: LIMITE_DO_SINO })
  const doSino = itens
    .filter((dto) => TIPOS_DO_SINO.includes(dto.tipo))
    .map(paraNotificacao)
    .sort((a, b) => b.criadaEm.getTime() - a.criadaEm.getTime())
  return { itens: doSino, naoLidas }
}

export function marcarComoLida(id: string): Promise<void> {
  return marcarNotificacaoComoLida(id)
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `npm test -- tests/api/notificacoes.test.ts tests/services/notificacoes.test.ts && npm run typecheck && npm run lint && npm test -- tests/arquitetura.test.ts`
Expected: todos passam.

- [ ] **Step 5: Commit**

```bash
cd ..
git add frontend/api/notificacoes.ts frontend/services/notificacoes.ts frontend/tests
git commit -m "feat(frontend): notificações (contrato, sino sem peças à procura e marcar como lida)" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
cd frontend
```

---

### Task 7: Formatação e mensagens de erro (`services/formatacao.ts`, `services/erros.ts`)

**Files:**
- Create: `frontend/services/formatacao.ts`, `frontend/services/erros.ts`
- Test: `frontend/tests/services/formatacao.test.ts`, `frontend/tests/services/erros.test.ts`

**Interfaces:**
- Consumes: `SituacaoDoVendedor` de `@/services/sessao`; `ApiError` de `@/api/cliente`.
- Produces:
  - `rotuloDoContador(quantidade: number): string` → `''` (0 ou menos), `'7'`, `'99'`, `'99+'`
  - `nomeComContador(nome: string, quantidade: number): string` → `'Notificações'`, `'Notificações, 1 não lida'`, `'Notificações, 3 não lidas'`, `'Notificações, 99+ não lidas'`
  - `tempoRelativo(data: Date, agora: Date): string` → `'agora'`, `'há 5 min'`, `'há 3 h'`, `'ontem'`, `'há 3 dias'`, `'22/09/2026'` (7 dias ou mais, fuso America/Sao_Paulo), `''` para data inválida
  - `rotuloDaSituacaoDoVendedor(situacao: SituacaoDoVendedor): string`
  - `type MensagemDeErro = { texto: string; podeTentarDeNovo: boolean }`, `mensagemDeErro(erro: unknown, recurso: string): MensagemDeErro`, `sessaoExpirou(erro: unknown): boolean` (ApiError `http` 401)

- [ ] **Step 1: Escrever os testes**

Create `frontend/tests/services/formatacao.test.ts`:
```ts
import { describe, expect, it } from 'vitest'
import {
  nomeComContador,
  rotuloDaSituacaoDoVendedor,
  rotuloDoContador,
  tempoRelativo,
} from '@/services/formatacao'

const AGORA = new Date('2026-09-29T12:00:00Z')
const MINUTO = 60_000
const HORA = 60 * MINUTO
const DIA = 24 * HORA

function antes(ms: number): Date {
  return new Date(AGORA.getTime() - ms)
}

describe('rotuloDoContador', () => {
  it.each([
    [0, ''],
    [-3, ''],
    [1, '1'],
    [7, '7'],
    [99, '99'],
    [100, '99+'],
    [12345, '99+'],
  ])('%i → "%s"', (quantidade, esperado) => {
    expect(rotuloDoContador(quantidade)).toBe(esperado)
  })
})

describe('nomeComContador', () => {
  it.each([
    [0, 'Notificações'],
    [1, 'Notificações, 1 não lida'],
    [3, 'Notificações, 3 não lidas'],
    [99, 'Notificações, 99 não lidas'],
    [100, 'Notificações, 99+ não lidas'],
  ])('%i → "%s"', (quantidade, esperado) => {
    expect(nomeComContador('Notificações', quantidade)).toBe(esperado)
  })
})

describe('tempoRelativo', () => {
  it.each([
    [30 * 1000, 'agora'],
    [MINUTO, 'há 1 min'],
    [5 * MINUTO, 'há 5 min'],
    [59 * MINUTO, 'há 59 min'],
    [60 * MINUTO, 'há 1 h'],
    [23 * HORA + 59 * MINUTO, 'há 23 h'],
    [24 * HORA, 'ontem'],
    [47 * HORA, 'ontem'],
    [2 * DIA, 'há 2 dias'],
    [6 * DIA + 23 * HORA, 'há 6 dias'],
    [7 * DIA, '22/09/2026'],
    [40 * DIA, '20/08/2026'],
  ])('%i ms atrás → "%s"', (ms, esperado) => {
    expect(tempoRelativo(antes(ms), AGORA)).toBe(esperado)
  })

  it('data no futuro (relógio adiantado) vira "agora"', () => {
    expect(tempoRelativo(new Date(AGORA.getTime() + 10 * MINUTO), AGORA)).toBe('agora')
  })

  it('data inválida devolve texto vazio, nunca "NaN"', () => {
    expect(tempoRelativo(new Date('invalida'), AGORA)).toBe('')
  })
})

describe('rotuloDaSituacaoDoVendedor', () => {
  it.each([
    ['NAO_E_VENDEDOR', 'Você ainda não é vendedor'],
    ['EM_ANALISE', 'Em análise'],
    ['ATIVO', 'Ativo'],
    ['REPROVADO', 'Reprovado'],
    ['SUSPENSO', 'Suspenso'],
  ] as const)('%s → "%s"', (situacao, esperado) => {
    expect(rotuloDaSituacaoDoVendedor(situacao)).toBe(esperado)
  })
})
```

Create `frontend/tests/services/erros.test.ts`:
```ts
import { describe, expect, it } from 'vitest'
import { ApiError } from '@/api/cliente'
import { mensagemDeErro, sessaoExpirou } from '@/services/erros'

describe('sessaoExpirou', () => {
  it('só é verdadeiro para ApiError http 401', () => {
    expect(sessaoExpirou(new ApiError('http', 401, 'x'))).toBe(true)
    expect(sessaoExpirou(new ApiError('http', 403, 'x'))).toBe(false)
    expect(sessaoExpirou(new ApiError('http', 500, 'x'))).toBe(false)
    expect(sessaoExpirou(new ApiError('rede', 0, 'x'))).toBe(false)
    expect(sessaoExpirou(new Error('x'))).toBe(false)
    expect(sessaoExpirou(undefined)).toBe(false)
  })
})

describe('mensagemDeErro', () => {
  it('401: sessão expirada e não adianta tentar de novo', () => {
    expect(mensagemDeErro(new ApiError('http', 401, 'Não autenticado'), 'suas notificações')).toEqual({
      texto: 'Sua sessão expirou. Entre novamente para continuar.',
      podeTentarDeNovo: false,
    })
  })

  it('rede: pede para verificar a conexão e permite tentar de novo', () => {
    expect(mensagemDeErro(new ApiError('rede', 0, 'x'), 'sua conta')).toEqual({
      texto: 'Não foi possível conectar ao servidor. Verifique sua conexão.',
      podeTentarDeNovo: true,
    })
  })

  it('contrato: resposta inesperada, permite tentar de novo', () => {
    expect(mensagemDeErro(new ApiError('contrato', 200, 'x'), 'sua conta')).toEqual({
      texto: 'O servidor respondeu de um jeito inesperado. Tente novamente em instantes.',
      podeTentarDeNovo: true,
    })
  })

  it.each([
    ['500 do servidor', new ApiError('http', 500, 'Erro interno')],
    ['403 do servidor', new ApiError('http', 403, 'Acesso negado')],
    ['Error comum', new Error('falhou')],
    ['valor desconhecido', undefined],
  ])('%s: mensagem genérica com o recurso, e permite tentar de novo', (_nome, erro) => {
    expect(mensagemDeErro(erro, 'suas notificações')).toEqual({
      texto: 'Não foi possível carregar suas notificações agora.',
      podeTentarDeNovo: true,
    })
  })

  it('nunca expõe a mensagem técnica do servidor ao usuário', () => {
    const { texto } = mensagemDeErro(
      new ApiError('http', 500, 'NullPointerException em NotificacaoService'),
      'suas notificações',
    )

    expect(texto).not.toContain('NullPointerException')
  })
})
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `npm test -- tests/services/formatacao.test.ts tests/services/erros.test.ts`
Expected: FAIL com `Failed to resolve import "@/services/formatacao"`.

- [ ] **Step 3: Implementar**

Create `frontend/services/formatacao.ts`:
```ts
import type { SituacaoDoVendedor } from '@/services/sessao'

export function rotuloDoContador(quantidade: number): string {
  if (quantidade <= 0) return ''
  return quantidade > 99 ? '99+' : String(quantidade)
}

export function nomeComContador(nome: string, quantidade: number): string {
  const rotulo = rotuloDoContador(quantidade)
  if (rotulo === '') return nome
  return `${nome}, ${rotulo} ${quantidade === 1 ? 'não lida' : 'não lidas'}`
}

const FORMATO_DATA = new Intl.DateTimeFormat('pt-BR', { timeZone: 'America/Sao_Paulo' })
const UM_MINUTO_EM_MS = 60_000

export function tempoRelativo(data: Date, agora: Date): string {
  const diferenca = agora.getTime() - data.getTime()
  if (Number.isNaN(diferenca)) return ''

  const minutos = Math.floor(diferenca / UM_MINUTO_EM_MS)
  if (minutos < 1) return 'agora'
  if (minutos < 60) return `há ${minutos} min`

  const horas = Math.floor(minutos / 60)
  if (horas < 24) return `há ${horas} h`

  const dias = Math.floor(horas / 24)
  if (dias === 1) return 'ontem'
  if (dias < 7) return `há ${dias} dias`
  return FORMATO_DATA.format(data)
}

const ROTULO_DA_SITUACAO: Record<SituacaoDoVendedor, string> = {
  NAO_E_VENDEDOR: 'Você ainda não é vendedor',
  EM_ANALISE: 'Em análise',
  ATIVO: 'Ativo',
  REPROVADO: 'Reprovado',
  SUSPENSO: 'Suspenso',
}

export function rotuloDaSituacaoDoVendedor(situacao: SituacaoDoVendedor): string {
  return ROTULO_DA_SITUACAO[situacao]
}
```

Create `frontend/services/erros.ts`:
```ts
import { ApiError } from '@/api/cliente'

export type MensagemDeErro = { texto: string; podeTentarDeNovo: boolean }

export function sessaoExpirou(erro: unknown): boolean {
  return erro instanceof ApiError && erro.tipo === 'http' && erro.status === 401
}

// A mensagem técnica do servidor nunca vai para a tela: o usuário vê só o texto abaixo.
export function mensagemDeErro(erro: unknown, recurso: string): MensagemDeErro {
  if (sessaoExpirou(erro)) {
    return { texto: 'Sua sessão expirou. Entre novamente para continuar.', podeTentarDeNovo: false }
  }
  if (erro instanceof ApiError && erro.tipo === 'rede') {
    return {
      texto: 'Não foi possível conectar ao servidor. Verifique sua conexão.',
      podeTentarDeNovo: true,
    }
  }
  if (erro instanceof ApiError && erro.tipo === 'contrato') {
    return {
      texto: 'O servidor respondeu de um jeito inesperado. Tente novamente em instantes.',
      podeTentarDeNovo: true,
    }
  }
  return { texto: `Não foi possível carregar ${recurso} agora.`, podeTentarDeNovo: true }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `npm test -- tests/services && npm run typecheck && npm run lint && npm test -- tests/arquitetura.test.ts`
Expected: todos passam. O teste de datas depende do fuso `America/Sao_Paulo` do `Intl`. O Node 24 tem ICU completo, então funciona em qualquer máquina.

- [ ] **Step 5: Commit**

```bash
cd ..
git add frontend/services/formatacao.ts frontend/services/erros.ts frontend/tests/services/formatacao.test.ts frontend/tests/services/erros.test.ts
git commit -m "feat(frontend): formatação em pt-BR (contador, tempo relativo) e mensagens de erro" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
cd frontend
```

---

### Task 8: Hooks e sino de notificações

**Files:**
- Create: `frontend/app/providers.tsx`, `frontend/app/hooks/useAgora.ts`, `frontend/app/hooks/useSessao.ts`, `frontend/app/hooks/useNotificacoes.ts`, `frontend/app/components/navegacao/ContadorBadge.tsx`, `ItemNotificacao.tsx`, `ListaNotificacoes.tsx`, `MenuNotificacoes.tsx` (os quatro em `frontend/app/components/navegacao/`), `frontend/tests/api-fake.ts`
- Test: `frontend/tests/app/hooks/useAgora.test.tsx`, `frontend/tests/app/components/navegacao/sino.test.tsx`

**Interfaces:**
- Consumes: `carregarSessao` de `@/services/sessao`; `carregarNotificacoes`, `marcarComoLida`, `filtrarPorAba`, `totalNoSino`, `type Notificacao` de `@/services/notificacoes`; `nomeComContador`, `rotuloDoContador`, `tempoRelativo` de `@/services/formatacao`; `mensagemDeErro` de `@/services/erros`; componentes `Sheet*`, `Tabs*`, `Button`, `Badge`, `Alert*`, `Skeleton` do shadcn; builders `umaNotificacaoDto`, `umaSessaoDto`.
- Produces:
  - `Providers({ children })` (client), com `QueryClient` sem retentativas automáticas
  - `useAgora(intervaloEmMs = 60_000): Date`: relógio que avança sozinho
  - `useSessao()`: `useQuery` de `['sessao']`, `staleTime` de 5 min
  - `useNotificacoes(habilitado = true)`: `useQuery` de `['notificacoes']` que consulta a cada 30 s; `useMarcarComoLida()`: `useMutation` que marca e refaz a consulta
  - `ContadorBadge({ quantidade })`: emblema `aria-hidden` com `rotuloDoContador`; não renderiza nada se 0
  - `ItemNotificacao({ notificacao, agora, aoAbrir })`: `<li>` com um `<button>`; texto "Não lida" para leitor de tela quando não lida
  - `ListaNotificacoes({ itens, agora, aoAbrir, mensagemVazia })`: `<ul aria-label="Lista de notificações">` ou a mensagem vazia
  - `MenuNotificacoes({ agora?: Date })`: botão do sino (nome `nomeComContador('Notificações', totalNoSino)`) que abre um `Sheet` com abas **Todas** e **Chat (n)**
  - Helpers de teste em `@/tests/api-fake`: `URL_SESSAO`, `URL_NOTIFICACOES`, `fingirSessao(sessao?)`, `fingirNotificacoes(lista?)`

- [ ] **Step 1: Escrever o helper de API falsa e os testes**

Create `frontend/tests/api-fake.ts`:
```ts
import { http, HttpResponse } from 'msw'
import type { ListaNotificacoesDto } from '@/api/notificacoes'
import type { SessaoDto } from '@/api/sessao'
import { servidor } from '@/mocks/servidor'
import { umaListaDeNotificacoesDto, umaSessaoDto } from '@/tests/builders'

export const URL_SESSAO = 'http://localhost:8080/api/v1/me'
export const URL_NOTIFICACOES = 'http://localhost:8080/api/v1/notificacoes'

export function fingirSessao(sessao: SessaoDto = umaSessaoDto()) {
  servidor.use(http.get(URL_SESSAO, () => HttpResponse.json(sessao)))
}

export function fingirNotificacoes(lista: ListaNotificacoesDto = umaListaDeNotificacoesDto()) {
  servidor.use(http.get(URL_NOTIFICACOES, () => HttpResponse.json(lista)))
}
```

Create `frontend/tests/app/hooks/useAgora.test.tsx`:
```tsx
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { useAgora } from '@/app/hooks/useAgora'

describe('useAgora', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-09-29T12:00:00Z'))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('começa na hora atual e avança a cada intervalo', () => {
    const { result } = renderHook(() => useAgora(60_000))

    expect(result.current).toEqual(new Date('2026-09-29T12:00:00Z'))

    act(() => {
      vi.advanceTimersByTime(60_000)
    })

    expect(result.current).toEqual(new Date('2026-09-29T12:01:00Z'))
  })

  it('para de atualizar ao desmontar (não deixa timer pendente)', () => {
    const { unmount } = renderHook(() => useAgora(60_000))

    unmount()

    expect(vi.getTimerCount()).toBe(0)
  })
})
```

Create `frontend/tests/app/components/navegacao/sino.test.tsx`:
```tsx
import { describe, expect, it } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import type { NotificacaoDto } from '@/api/notificacoes'
import { servidor } from '@/mocks/servidor'
import { axe } from '@/tests/axe'
import { URL_NOTIFICACOES } from '@/tests/api-fake'
import { umaNotificacaoDto } from '@/tests/builders'
import { Providers } from '@/app/providers'
import { MenuNotificacoes } from '@/app/components/navegacao/MenuNotificacoes'

const AGORA = new Date('2026-09-29T12:00:00Z')

const CHAT_RECENTE = umaNotificacaoDto({
  id: 'n-chat-1', tipo: 'CHAT', titulo: 'Auto Peças Silva',
  resumo: 'Tenho o farol, posso mandar foto?', criadaEm: '2026-09-29T11:55:00Z', lida: false,
})
const CHAT_ANTIGO = umaNotificacaoDto({
  id: 'n-chat-2', tipo: 'CHAT', titulo: 'Desmanche Rota 116',
  resumo: 'Consigo fazer R$ 180 se retirar hoje.', criadaEm: '2026-09-29T11:15:00Z', lida: false,
})
const PROPOSTA = umaNotificacaoDto({
  id: 'n-prop', tipo: 'PROPOSTA', titulo: 'Nova proposta: Retrovisor',
  resumo: 'Moto Center ofereceu R$ 89,90.', criadaEm: '2026-09-29T09:00:00Z', lida: false,
})
const SISTEMA_LIDA = umaNotificacaoDto({
  id: 'n-sis', tipo: 'SISTEMA', titulo: 'Seu pedido expira amanhã',
  resumo: 'Farol esquerdo · Gol 2014', criadaEm: '2026-09-28T06:00:00Z', lida: true,
})
const TODAS = [CHAT_ANTIGO, SISTEMA_LIDA, CHAT_RECENTE, PROPOSTA]

// Servidor com estado: o PATCH marca a notificação como lida e o GET seguinte reflete isso.
function servirNotificacoes(itens: NotificacaoDto[], pecasAProcura = 0) {
  const estado = itens.map((item) => ({ ...item }))
  const patches: string[] = []
  servidor.use(
    http.get(URL_NOTIFICACOES, () =>
      HttpResponse.json({
        itens: estado,
        naoLidas: {
          chat: estado.filter((i) => i.tipo === 'CHAT' && !i.lida).length,
          outras: estado.filter((i) => i.tipo !== 'CHAT' && i.tipo !== 'PECA_A_PROCURA' && !i.lida).length,
          pecasAProcura,
        },
      }),
    ),
    http.patch(`${URL_NOTIFICACOES}/:id`, ({ params }) => {
      patches.push(String(params.id))
      const alvo = estado.find((i) => i.id === params.id)
      if (alvo) alvo.lida = true
      return new HttpResponse(null, { status: 204 })
    }),
  )
  return { patches }
}

function renderizar() {
  return render(
    <Providers>
      <MenuNotificacoes agora={AGORA} />
    </Providers>,
  )
}

// Por padrão espera o "Carregando…" sumir, para os testes já encontrarem os itens (ou o erro).
async function abrirPainel(nome: string | RegExp = /^Notificações/, esperarDados = true) {
  await userEvent.click(await screen.findByRole('button', { name: nome }))
  const painel = await screen.findByRole('dialog', { name: 'Notificações' })
  if (esperarDados) {
    await waitFor(() => {
      expect(within(painel).queryByRole('status')).not.toBeInTheDocument()
    })
  }
  return painel
}

describe('sino de notificações: contador', () => {
  it('soma chat e outras e NÃO conta peças à procura', async () => {
    servirNotificacoes(TODAS, 5)

    renderizar()

    expect(
      await screen.findByRole('button', { name: 'Notificações, 3 não lidas' }),
    ).toBeInTheDocument()
  })

  it('sem nada não lido, o nome do botão é só "Notificações"', async () => {
    servirNotificacoes([SISTEMA_LIDA])

    renderizar()

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Notificações' })).toBeInTheDocument()
    })
  })

  it('mais de 99 não lidas aparece como 99+', async () => {
    servidor.use(
      http.get(URL_NOTIFICACOES, () =>
        HttpResponse.json({ itens: [], naoLidas: { chat: 120, outras: 0, pecasAProcura: 0 } }),
      ),
    )

    renderizar()

    expect(
      await screen.findByRole('button', { name: 'Notificações, 99+ não lidas' }),
    ).toBeInTheDocument()
  })
})

describe('sino de notificações: painel', () => {
  it('lista da mais recente para a mais antiga, com o tempo relativo', async () => {
    servirNotificacoes(TODAS)
    renderizar()

    const painel = await abrirPainel()

    const itens = within(painel).getAllByRole('listitem')
    expect(itens.map((li) => li.textContent)).toEqual([
      expect.stringContaining('Auto Peças Silva'),
      expect.stringContaining('Desmanche Rota 116'),
      expect.stringContaining('Nova proposta: Retrovisor'),
      expect.stringContaining('Seu pedido expira amanhã'),
    ])
    expect(within(painel).getByText('há 5 min')).toBeInTheDocument()
    expect(within(painel).getByText('há 45 min')).toBeInTheDocument()
    expect(within(painel).getByText('há 3 h')).toBeInTheDocument()
    expect(within(painel).getByText('ontem')).toBeInTheDocument()
  })

  it('a aba Chat mostra a contagem e filtra só as mensagens de chat', async () => {
    servirNotificacoes(TODAS)
    renderizar()
    const painel = await abrirPainel()

    await userEvent.click(within(painel).getByRole('tab', { name: 'Chat (2)' }))

    const itens = within(painel).getAllByRole('listitem')
    expect(itens).toHaveLength(2)
    expect(within(painel).queryByText('Nova proposta: Retrovisor')).not.toBeInTheDocument()
  })

  it('clicar em uma notificação não lida faz o PATCH e o contador cai', async () => {
    const { patches } = servirNotificacoes(TODAS)
    renderizar()
    const painel = await abrirPainel()

    await userEvent.click(within(painel).getByRole('button', { name: /Auto Peças Silva/ }))

    expect(
      await screen.findByRole('button', { name: 'Notificações, 2 não lidas', hidden: true }),
    ).toBeInTheDocument()
    expect(patches).toEqual(['n-chat-1'])
    await waitFor(() => {
      expect(
        within(painel).getByRole('button', { name: /Auto Peças Silva/ }),
      ).not.toHaveTextContent('Não lida')
    })
  })

  it('clicar em uma notificação já lida NÃO dispara o PATCH', async () => {
    const { patches } = servirNotificacoes(TODAS)
    renderizar()
    const painel = await abrirPainel()

    await userEvent.click(within(painel).getByRole('button', { name: /Seu pedido expira amanhã/ }))

    expect(patches).toEqual([])
  })

  it('notificação não lida é anunciada como "Não lida"', async () => {
    servirNotificacoes([CHAT_RECENTE, SISTEMA_LIDA])
    renderizar()
    const painel = await abrirPainel()

    expect(within(painel).getByRole('button', { name: /Auto Peças Silva/ })).toHaveTextContent('Não lida')
    expect(within(painel).getByRole('button', { name: /Seu pedido expira amanhã/ })).not.toHaveTextContent(
      'Não lida',
    )
  })

  it('peças à procura enviadas pelo servidor NÃO aparecem no painel', async () => {
    servirNotificacoes([
      CHAT_RECENTE,
      umaNotificacaoDto({ id: 'n-procura', tipo: 'PECA_A_PROCURA', titulo: 'Procuram: Farol esquerdo' }),
    ])
    renderizar()
    const painel = await abrirPainel()

    expect(within(painel).getByText('Auto Peças Silva')).toBeInTheDocument()
    expect(within(painel).queryByText('Procuram: Farol esquerdo')).not.toBeInTheDocument()
  })

  it('sem notificações mostra a mensagem vazia, e a aba Chat mostra a dela', async () => {
    servirNotificacoes([])
    renderizar()
    const painel = await abrirPainel()

    expect(within(painel).getByText('Nenhuma notificação.')).toBeInTheDocument()
    await userEvent.click(within(painel).getByRole('tab', { name: 'Chat' }))
    expect(within(painel).getByText('Nenhuma mensagem de chat.')).toBeInTheDocument()
  })

  it('enquanto carrega, o painel avisa para leitores de tela', async () => {
    servidor.use(
      http.get(URL_NOTIFICACOES, async () => {
        await delay('infinite')
        return HttpResponse.json({})
      }),
    )
    renderizar()

    const painel = await abrirPainel('Notificações', false)

    expect(within(painel).getByRole('status')).toHaveTextContent('Carregando notificações…')
  })

  it('não tem violações de acessibilidade com o painel aberto', async () => {
    servirNotificacoes(TODAS)
    const { baseElement } = renderizar()
    await abrirPainel()

    expect(await axe(baseElement)).toHaveNoViolations()
  })
})

describe('sino de notificações: falhas', () => {
  it('erro 500: o painel mostra o alerta, e "Tentar novamente" recarrega', async () => {
    servidor.use(
      http.get(URL_NOTIFICACOES, () => HttpResponse.json({ title: 'Erro interno' }, { status: 500 })),
    )
    renderizar()
    const painel = await abrirPainel('Notificações')

    expect(await within(painel).findByRole('alert')).toHaveTextContent(
      'Não foi possível carregar suas notificações agora.',
    )

    servirNotificacoes([CHAT_RECENTE])
    await userEvent.click(within(painel).getByRole('button', { name: 'Tentar novamente' }))

    expect(await within(painel).findByText('Auto Peças Silva')).toBeInTheDocument()
    expect(within(painel).queryByRole('alert')).not.toBeInTheDocument()
  })

  it('erro de rede: alerta com botão de tentar de novo', async () => {
    servidor.use(http.get(URL_NOTIFICACOES, () => HttpResponse.error()))
    renderizar()
    const painel = await abrirPainel('Notificações')

    expect(await within(painel).findByRole('alert')).toHaveTextContent(
      'Não foi possível conectar ao servidor.',
    )
    expect(within(painel).getByRole('button', { name: 'Tentar novamente' })).toBeInTheDocument()
  })

  it('sessão expirada (401): mensagem própria e sem botão de tentar de novo', async () => {
    servidor.use(
      http.get(URL_NOTIFICACOES, () => HttpResponse.json({ title: 'Não autenticado' }, { status: 401 })),
    )
    renderizar()
    const painel = await abrirPainel('Notificações')

    expect(await within(painel).findByRole('alert')).toHaveTextContent('Sua sessão expirou.')
    expect(within(painel).queryByRole('button', { name: 'Tentar novamente' })).not.toBeInTheDocument()
  })

  it('resposta fora do contrato vira erro, sem NaN nem Invalid Date', async () => {
    servidor.use(
      http.get(URL_NOTIFICACOES, () =>
        HttpResponse.json({
          itens: [{ ...CHAT_RECENTE, criadaEm: 'ontem' }],
          naoLidas: { chat: 1, outras: 0, pecasAProcura: 0 },
        }),
      ),
    )
    renderizar()
    const painel = await abrirPainel('Notificações')

    expect(await within(painel).findByRole('alert')).toHaveTextContent(
      'O servidor respondeu de um jeito inesperado.',
    )
    expect(document.body).not.toHaveTextContent('NaN')
    expect(document.body).not.toHaveTextContent('Invalid Date')
  })
})
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `npm test -- tests/app/hooks tests/app/components/navegacao/sino.test.tsx`
Expected: FAIL com `Failed to resolve import "@/app/hooks/useAgora"` (e `@/app/providers`).

- [ ] **Step 3: Implementar o provider e os hooks**

Create `frontend/app/providers.tsx`:
```tsx
'use client'

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'

export function Providers({ children }: { children: ReactNode }) {
  // Sem retentativas automáticas: o usuário decide com o botão "Tentar novamente".
  const [cliente] = useState(
    () => new QueryClient({ defaultOptions: { queries: { retry: false } } }),
  )
  return <QueryClientProvider client={cliente}>{children}</QueryClientProvider>
}
```

Create `frontend/app/hooks/useAgora.ts`:
```ts
import { useEffect, useState } from 'react'

// Relógio que avança sozinho, para o "há 5 min" não ficar parado numa tela aberta.
export function useAgora(intervaloEmMs = 60_000): Date {
  const [agora, setAgora] = useState(() => new Date())

  useEffect(() => {
    const id = setInterval(() => setAgora(new Date()), intervaloEmMs)
    return () => clearInterval(id)
  }, [intervaloEmMs])

  return agora
}
```

Create `frontend/app/hooks/useSessao.ts`:
```ts
import { useQuery } from '@tanstack/react-query'
import { carregarSessao } from '@/services/sessao'

const CINCO_MINUTOS = 5 * 60_000

export function useSessao() {
  return useQuery({ queryKey: ['sessao'], queryFn: carregarSessao, staleTime: CINCO_MINUTOS })
}
```

Create `frontend/app/hooks/useNotificacoes.ts`:
```ts
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { carregarNotificacoes, marcarComoLida } from '@/services/notificacoes'

const CHAVE = ['notificacoes'] as const
const TRINTA_SEGUNDOS = 30_000

export function useNotificacoes(habilitado = true) {
  return useQuery({
    queryKey: CHAVE,
    queryFn: carregarNotificacoes,
    refetchInterval: TRINTA_SEGUNDOS,
    enabled: habilitado,
  })
}

export function useMarcarComoLida() {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: marcarComoLida,
    onSuccess: () => cliente.invalidateQueries({ queryKey: CHAVE }),
  })
}
```

- [ ] **Step 4: Implementar os componentes do sino**

Create `frontend/app/components/navegacao/ContadorBadge.tsx`:
```tsx
import { Badge } from '@/app/components/ui/badge'
import { rotuloDoContador } from '@/services/formatacao'

// O nome acessível do controle que contém o emblema já traz a contagem (nomeComContador).
export function ContadorBadge({ quantidade }: { quantidade: number }) {
  const rotulo = rotuloDoContador(quantidade)
  if (rotulo === '') return null

  return (
    <Badge aria-hidden="true" className="ml-1 h-5 min-w-5 justify-center px-1">
      {rotulo}
    </Badge>
  )
}
```

Create `frontend/app/components/navegacao/ItemNotificacao.tsx`:
```tsx
import { Bell, MessageSquare } from 'lucide-react'
import { tempoRelativo } from '@/services/formatacao'
import type { Notificacao } from '@/services/notificacoes'

type Props = {
  notificacao: Notificacao
  agora: Date
  aoAbrir: (notificacao: Notificacao) => void
}

export function ItemNotificacao({ notificacao, agora, aoAbrir }: Props) {
  const Icone = notificacao.tipo === 'CHAT' ? MessageSquare : Bell

  return (
    <li>
      <button
        type="button"
        onClick={() => aoAbrir(notificacao)}
        className="flex w-full items-start gap-3 rounded-md p-3 text-left hover:bg-muted focus-visible:outline-2 focus-visible:outline-ring"
      >
        <Icone aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
        <span className="flex-1 space-y-0.5">
          <span className="block text-sm font-medium">{notificacao.titulo}</span>
          {notificacao.resumo !== '' && (
            <span className="block text-sm text-muted-foreground">{notificacao.resumo}</span>
          )}
          <span className="block text-xs text-muted-foreground">
            {tempoRelativo(notificacao.criadaEm, agora)}
          </span>
        </span>
        {!notificacao.lida && (
          <>
            <span aria-hidden="true" className="mt-1.5 size-2 shrink-0 rounded-full bg-primary" />
            <span className="sr-only">Não lida</span>
          </>
        )}
      </button>
    </li>
  )
}
```

Create `frontend/app/components/navegacao/ListaNotificacoes.tsx`:
```tsx
import { ItemNotificacao } from '@/app/components/navegacao/ItemNotificacao'
import type { Notificacao } from '@/services/notificacoes'

type Props = {
  itens: Notificacao[]
  agora: Date
  aoAbrir: (notificacao: Notificacao) => void
  mensagemVazia: string
}

export function ListaNotificacoes({ itens, agora, aoAbrir, mensagemVazia }: Props) {
  if (itens.length === 0) {
    return <p className="p-3 text-sm text-muted-foreground">{mensagemVazia}</p>
  }

  return (
    <ul aria-label="Lista de notificações" className="divide-y">
      {itens.map((notificacao) => (
        <ItemNotificacao key={notificacao.id} notificacao={notificacao} agora={agora} aoAbrir={aoAbrir} />
      ))}
    </ul>
  )
}
```

Create `frontend/app/components/navegacao/MenuNotificacoes.tsx`:
```tsx
'use client'

import { Bell } from 'lucide-react'
import { ContadorBadge } from '@/app/components/navegacao/ContadorBadge'
import { ListaNotificacoes } from '@/app/components/navegacao/ListaNotificacoes'
import { Alert, AlertDescription } from '@/app/components/ui/alert'
import { Button } from '@/app/components/ui/button'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from '@/app/components/ui/sheet'
import { Skeleton } from '@/app/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/app/components/ui/tabs'
import { useAgora } from '@/app/hooks/useAgora'
import { useMarcarComoLida, useNotificacoes } from '@/app/hooks/useNotificacoes'
import { mensagemDeErro } from '@/services/erros'
import { nomeComContador, rotuloDoContador } from '@/services/formatacao'
import { filtrarPorAba, totalNoSino, type Notificacao } from '@/services/notificacoes'

export function MenuNotificacoes({ agora }: { agora?: Date }) {
  const consulta = useNotificacoes()
  const marcarComoLida = useMarcarComoLida()
  const relogio = useAgora()
  const referencia = agora ?? relogio

  const painel = consulta.data
  const totalDoSino = painel ? totalNoSino(painel.naoLidas) : 0
  const chatNaoLidas = painel?.naoLidas.chat ?? 0

  function abrir(notificacao: Notificacao) {
    if (!notificacao.lida) marcarComoLida.mutate(notificacao.id)
  }

  function corpo() {
    // Dados já exibidos continuam na tela mesmo que uma atualização falhe.
    if (painel) {
      return (
        <Tabs defaultValue="TODAS">
          <TabsList>
            <TabsTrigger value="TODAS">Todas</TabsTrigger>
            <TabsTrigger value="CHAT">
              {chatNaoLidas > 0 ? `Chat (${rotuloDoContador(chatNaoLidas)})` : 'Chat'}
            </TabsTrigger>
          </TabsList>
          <TabsContent value="TODAS">
            <ListaNotificacoes
              itens={filtrarPorAba(painel.itens, 'TODAS')}
              agora={referencia}
              aoAbrir={abrir}
              mensagemVazia="Nenhuma notificação."
            />
          </TabsContent>
          <TabsContent value="CHAT">
            <ListaNotificacoes
              itens={filtrarPorAba(painel.itens, 'CHAT')}
              agora={referencia}
              aoAbrir={abrir}
              mensagemVazia="Nenhuma mensagem de chat."
            />
          </TabsContent>
        </Tabs>
      )
    }

    if (consulta.isError) {
      const { texto, podeTentarDeNovo } = mensagemDeErro(consulta.error, 'suas notificações')
      return (
        <div className="space-y-3">
          <Alert variant="destructive">
            <AlertDescription>{texto}</AlertDescription>
          </Alert>
          {podeTentarDeNovo && (
            <Button
              type="button"
              variant="outline"
              disabled={consulta.isFetching}
              onClick={() => void consulta.refetch()}
            >
              {consulta.isFetching ? 'Tentando novamente…' : 'Tentar novamente'}
            </Button>
          )}
        </div>
      )
    }

    return (
      <div role="status" aria-busy="true">
        <span className="sr-only">Carregando notificações…</span>
        <div aria-hidden="true" className="space-y-2">
          <Skeleton className="h-14" />
          <Skeleton className="h-14" />
          <Skeleton className="h-14" />
        </div>
      </div>
    )
  }

  return (
    <Sheet>
      <SheetTrigger asChild>
        <Button
          type="button"
          variant="ghost"
          size="icon"
          aria-label={nomeComContador('Notificações', totalDoSino)}
        >
          <Bell aria-hidden="true" />
          <ContadorBadge quantidade={totalDoSino} />
        </Button>
      </SheetTrigger>
      <SheetContent>
        <SheetHeader>
          <SheetTitle>Notificações</SheetTitle>
          <SheetDescription>Mensagens do chat e avisos recentes.</SheetDescription>
        </SheetHeader>
        <div className="px-4 pb-4">{corpo()}</div>
      </SheetContent>
    </Sheet>
  )
}
```

- [ ] **Step 5: Rodar e ver passar**

Run: `npm test -- tests/app/hooks tests/app/components/navegacao/sino.test.tsx && npm run typecheck && npm run lint && npm test -- tests/arquitetura.test.ts`
Expected: todos passam. Pontos de atenção se algo falhar:
- O botão do sino fica `aria-hidden` enquanto o painel está aberto, por isso os testes usam `hidden: true` ao procurá-lo depois de abrir.
- Se o `lint` reclamar de `corpo()` como função dentro do componente, é aceitável. Se preferir, extraia para um componente `CorpoDoPainel` no mesmo arquivo, sem mudar o comportamento.

- [ ] **Step 6: Commit**

```bash
cd ..
git add frontend/app frontend/tests
git commit -m "feat(frontend): sino de notificações com chat, contador e marcar como lida" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
cd frontend
```

---

### Task 9: Navbar, AppShell e estados da sessão

**Files:**
- Create: `frontend/app/components/navegacao/LinkNavegacao.tsx`, `Navbar.tsx`, `EstadosDaSessao.tsx`, `AppShell.tsx` (todos em `frontend/app/components/navegacao/`)
- Test: `frontend/tests/app/components/navegacao/navbar.test.tsx`, `frontend/tests/app/components/navegacao/app-shell.test.tsx`

**Interfaces:**
- Consumes: `montarNavegacao`, `type ItemNavegacao` de `@/services/navegacao`; `acessoPermitido`, `type Sessao`, `paraSessao` de `@/services/sessao`; `nomeComContador` de `@/services/formatacao`; `mensagemDeErro`, `sessaoExpirou` de `@/services/erros`; hooks `useSessao`, `useNotificacoes`; `MenuNotificacoes`, `ContadorBadge`; `usePathname`, `useRouter` de `next/navigation`; `fingirSessao`, `fingirNotificacoes`, `URL_SESSAO`, `URL_NOTIFICACOES` de `@/tests/api-fake`.
- Produces:
  - `LinkNavegacao({ item, icone?, quantidade? }: { item: ItemNavegacao; icone?: ReactNode; quantidade?: number })`: `<Link>` com `aria-current="page"` se ativo e `aria-label={nomeComContador(item.rotulo, quantidade)}`. Se `desabilitado`, renderiza `<span role="link" aria-disabled="true">` sem `href`.
  - `Navbar({ sessao, pathname, agora? }: { sessao: Sessao; pathname: string; agora?: Date })`: `<header>` com `<nav aria-label="Principal">`: marca "Autopeças" (`/`), perfis, campo **Peças à procura** (com contador, só vendedor aprovado), sino e Configurações.
  - `CarregandoSessao()` (`role="status"`, texto "Carregando…") e `ErroDeSessao({ erro, aoTentarDeNovo, carregando? })` (`<main>` com `role="alert"`; botão "Tentar novamente" só se a mensagem permitir)
  - `AppShell({ children })` (client): busca a sessão. Mostra carregando, erro (também no **401 com dados em cache**), ou a `Navbar` e `<main>{children}</main>`. Se a rota é de vendedor e o usuário não é aprovado: `router.replace('/comprador')` e **não renderiza** os filhos.

- [ ] **Step 1: Escrever os testes**

Create `frontend/tests/app/components/navegacao/navbar.test.tsx`:
```tsx
import { describe, expect, it } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { servidor } from '@/mocks/servidor'
import { axe } from '@/tests/axe'
import { fingirNotificacoes, URL_NOTIFICACOES } from '@/tests/api-fake'
import { umaListaDeNotificacoesDto, umaSessaoDto } from '@/tests/builders'
import { Providers } from '@/app/providers'
import { Navbar } from '@/app/components/navegacao/Navbar'
import { paraSessao, type StatusVendedor } from '@/services/sessao'

const AGORA = new Date('2026-09-29T12:00:00Z')

function sessaoCom(status: StatusVendedor | null) {
  return paraSessao(umaSessaoDto({ vendedor: status === null ? null : { status } }))
}

function renderizar(status: StatusVendedor | null, pathname: string) {
  return render(
    <Providers>
      <Navbar sessao={sessaoCom(status)} pathname={pathname} agora={AGORA} />
    </Providers>,
  )
}

function navegacao() {
  return screen.getByRole('navigation', { name: 'Principal' })
}

describe('Navbar', () => {
  it('quem só é comprador vê marca, Comprar, sino e Configurações, e nada de vendedor', async () => {
    fingirNotificacoes()

    renderizar(null, '/comprador')

    const nav = navegacao()
    expect(within(nav).getByRole('link', { name: 'Autopeças' })).toHaveAttribute('href', '/')
    expect(within(nav).getByRole('link', { name: 'Comprar' })).toHaveAttribute('href', '/comprador')
    expect(within(nav).getByRole('button', { name: 'Notificações' })).toBeInTheDocument()
    expect(within(nav).getByRole('link', { name: 'Configurações' })).toHaveAttribute('href', '/configuracoes')
    expect(within(nav).queryByRole('link', { name: /Vender/ })).not.toBeInTheDocument()
    expect(within(nav).queryByRole('link', { name: /Peças à procura/ })).not.toBeInTheDocument()
  })

  it('vendedor aprovado vê Vender e o campo Peças à procura com o contador', async () => {
    fingirNotificacoes(
      umaListaDeNotificacoesDto({ naoLidas: { chat: 0, outras: 0, pecasAProcura: 2 } }),
    )

    renderizar('APROVADO', '/vendedor')

    const nav = navegacao()
    const vender = within(nav).getByRole('link', { name: 'Vender' })
    expect(vender).toHaveAttribute('href', '/vendedor')
    expect(vender).toHaveAttribute('aria-current', 'page')
    expect(within(nav).getByRole('link', { name: 'Comprar' })).not.toHaveAttribute('aria-current')
    const procura = await within(nav).findByRole('link', { name: 'Peças à procura, 2 não lidas' })
    expect(procura).toHaveAttribute('href', '/vendedor/pecas-a-procura')
  })

  it('o contador de peças à procura NÃO entra no contador do sino', async () => {
    fingirNotificacoes(
      umaListaDeNotificacoesDto({ naoLidas: { chat: 1, outras: 0, pecasAProcura: 9 } }),
    )

    renderizar('APROVADO', '/comprador')

    const nav = navegacao()
    expect(await within(nav).findByRole('button', { name: 'Notificações, 1 não lida' })).toBeInTheDocument()
    expect(await within(nav).findByRole('link', { name: 'Peças à procura, 9 não lidas' })).toBeInTheDocument()
  })

  it('sem peças à procura não lidas, o campo aparece sem contador', async () => {
    fingirNotificacoes()

    renderizar('APROVADO', '/comprador')

    expect(within(navegacao()).getByRole('link', { name: 'Peças à procura' })).toBeInTheDocument()
  })

  it('na página de peças à procura só esse campo fica ativo (não acende também Vender)', async () => {
    fingirNotificacoes()

    renderizar('APROVADO', '/vendedor/pecas-a-procura')

    const nav = navegacao()
    expect(within(nav).getByRole('link', { name: 'Peças à procura' })).toHaveAttribute('aria-current', 'page')
    expect(within(nav).getByRole('link', { name: 'Vender' })).not.toHaveAttribute('aria-current')
  })

  it('vendedor em análise vê "Vender (em análise)" desabilitado e sem o campo Peças à procura', async () => {
    fingirNotificacoes()

    renderizar('PENDENTE', '/comprador')

    const nav = navegacao()
    const vender = within(nav).getByRole('link', { name: 'Vender (em análise)' })
    expect(vender).toHaveAttribute('aria-disabled', 'true')
    expect(vender).not.toHaveAttribute('href')
    expect(within(nav).queryByRole('link', { name: /Peças à procura/ })).not.toBeInTheDocument()
  })

  it.each(['REPROVADO', 'SUSPENSO'] as const)('vendedor %s vê "Vender (indisponível)" desabilitado', async (status) => {
    fingirNotificacoes()

    renderizar(status, '/comprador')

    expect(
      within(navegacao()).getByRole('link', { name: 'Vender (indisponível)' }),
    ).toHaveAttribute('aria-disabled', 'true')
  })

  it('a navbar continua inteira quando as notificações falham', async () => {
    servidor.use(
      http.get(URL_NOTIFICACOES, () => HttpResponse.json({ title: 'Erro interno' }, { status: 500 })),
    )

    renderizar('APROVADO', '/comprador')

    const nav = navegacao()
    expect(within(nav).getByRole('link', { name: 'Comprar' })).toBeInTheDocument()
    expect(within(nav).getByRole('link', { name: 'Peças à procura' })).toBeInTheDocument()
    expect(within(nav).getByRole('button', { name: 'Notificações' })).toBeInTheDocument()
    expect(within(nav).getByRole('link', { name: 'Configurações' })).toBeInTheDocument()
  })

  it('não tem violações de acessibilidade', async () => {
    fingirNotificacoes()
    const { container } = renderizar('APROVADO', '/vendedor')

    expect(await axe(container)).toHaveNoViolations()
  })
})
```

Create `frontend/tests/app/components/navegacao/app-shell.test.tsx`:
```tsx
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { servidor } from '@/mocks/servidor'
import { axe } from '@/tests/axe'
import { fingirNotificacoes, fingirSessao, URL_SESSAO } from '@/tests/api-fake'
import { umaSessaoDto } from '@/tests/builders'
import { Providers } from '@/app/providers'
import { AppShell } from '@/app/components/navegacao/AppShell'

const rota = vi.hoisted(() => ({ pathname: '/comprador', replace: vi.fn() }))

vi.mock('next/navigation', () => {
  const roteador = { replace: rota.replace, push: vi.fn() }
  return { usePathname: () => rota.pathname, useRouter: () => roteador }
})

function renderizar() {
  return render(
    <Providers>
      <AppShell>
        <p>Conteúdo da página</p>
      </AppShell>
    </Providers>,
  )
}

beforeEach(() => {
  rota.pathname = '/comprador'
  rota.replace.mockClear()
})

describe('AppShell: carregamento e conteúdo', () => {
  it('mostra "Carregando…" e depois a navbar com o conteúdo dentro de <main>', async () => {
    fingirSessao()
    fingirNotificacoes()

    renderizar()

    expect(screen.getByRole('status')).toHaveTextContent('Carregando…')
    expect(await screen.findByRole('navigation', { name: 'Principal' })).toBeInTheDocument()
    expect(within(screen.getByRole('main')).getByText('Conteúdo da página')).toBeInTheDocument()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('marca o item da rota atual como ativo', async () => {
    fingirSessao()
    fingirNotificacoes()

    renderizar()

    expect(await screen.findByRole('link', { name: 'Comprar' })).toHaveAttribute('aria-current', 'page')
  })

  it('as configurações são acessíveis a quem só é comprador', async () => {
    rota.pathname = '/configuracoes'
    fingirSessao(umaSessaoDto({ vendedor: null }))
    fingirNotificacoes()

    renderizar()

    expect(await screen.findByText('Conteúdo da página')).toBeInTheDocument()
    expect(rota.replace).not.toHaveBeenCalled()
  })

  it('não tem violações de acessibilidade', async () => {
    fingirSessao(umaSessaoDto({ vendedor: { status: 'APROVADO' } }))
    fingirNotificacoes()
    const { container } = renderizar()
    await screen.findByText('Conteúdo da página')

    expect(await axe(container)).toHaveNoViolations()
  })
})

describe('AppShell: acesso às rotas de vendedor', () => {
  it('vendedor aprovado vê o conteúdo e não é redirecionado', async () => {
    rota.pathname = '/vendedor'
    fingirSessao(umaSessaoDto({ vendedor: { status: 'APROVADO' } }))
    fingirNotificacoes()

    renderizar()

    expect(await screen.findByText('Conteúdo da página')).toBeInTheDocument()
    expect(rota.replace).not.toHaveBeenCalled()
  })

  it.each([
    ['comprador', null],
    ['vendedor em análise', { status: 'PENDENTE' }],
    ['vendedor reprovado', { status: 'REPROVADO' }],
    ['vendedor suspenso', { status: 'SUSPENSO' }],
  ] as const)('%s em /vendedor/pecas-a-procura volta para /comprador, sem mostrar o conteúdo', async (_nome, vendedor) => {
    rota.pathname = '/vendedor/pecas-a-procura'
    fingirSessao(umaSessaoDto({ vendedor }))
    fingirNotificacoes()

    renderizar()

    await waitFor(() => {
      expect(rota.replace).toHaveBeenCalledWith('/comprador')
    })
    expect(screen.queryByText('Conteúdo da página')).not.toBeInTheDocument()
    expect(screen.queryByRole('navigation', { name: 'Principal' })).not.toBeInTheDocument()
  })
})

describe('AppShell: falhas da sessão', () => {
  it('sessão expirada (401): mensagem própria, sem navbar, sem conteúdo e sem tentar de novo', async () => {
    servidor.use(
      http.get(URL_SESSAO, () => HttpResponse.json({ title: 'Não autenticado' }, { status: 401 })),
    )

    renderizar()

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Sua sessão expirou. Entre novamente para continuar.',
    )
    expect(screen.queryByRole('button', { name: /Tentar/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('navigation', { name: 'Principal' })).not.toBeInTheDocument()
    expect(screen.queryByText('Conteúdo da página')).not.toBeInTheDocument()
  })

  it('erro 500: alerta e "Tentar novamente" que carrega a página', async () => {
    let chamadas = 0
    servidor.use(
      http.get(URL_SESSAO, () => {
        chamadas += 1
        return chamadas === 1
          ? HttpResponse.json({ title: 'Erro interno' }, { status: 500 })
          : HttpResponse.json(umaSessaoDto())
      }),
    )
    fingirNotificacoes()
    renderizar()

    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível carregar sua conta agora.')
    await userEvent.click(screen.getByRole('button', { name: 'Tentar novamente' }))

    expect(await screen.findByText('Conteúdo da página')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('erro de rede: alerta com botão de tentar de novo', async () => {
    servidor.use(http.get(URL_SESSAO, () => HttpResponse.error()))

    renderizar()

    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível conectar ao servidor.')
    expect(screen.getByRole('button', { name: 'Tentar novamente' })).toBeInTheDocument()
  })

  it('resposta fora do contrato vira erro, sem NaN nem undefined na tela', async () => {
    servidor.use(http.get(URL_SESSAO, () => HttpResponse.json({ nome: 'Sem id' })))

    renderizar()

    expect(await screen.findByRole('alert')).toHaveTextContent('O servidor respondeu de um jeito inesperado.')
    expect(document.body).not.toHaveTextContent('undefined')
    expect(document.body).not.toHaveTextContent('NaN')
  })
})
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `npm test -- tests/app/components/navegacao/navbar.test.tsx tests/app/components/navegacao/app-shell.test.tsx`
Expected: FAIL com `Failed to resolve import "@/app/components/navegacao/Navbar"` (e `AppShell`).

- [ ] **Step 3: Implementar `LinkNavegacao` e `Navbar`**

Create `frontend/app/components/navegacao/LinkNavegacao.tsx`:
```tsx
import Link from 'next/link'
import type { ReactNode } from 'react'
import { ContadorBadge } from '@/app/components/navegacao/ContadorBadge'
import { nomeComContador } from '@/services/formatacao'
import type { ItemNavegacao } from '@/services/navegacao'

type Props = {
  item: ItemNavegacao
  icone?: ReactNode
  quantidade?: number
}

const BASE = 'inline-flex h-9 items-center gap-1 rounded-md px-3 text-sm font-medium'

export function LinkNavegacao({ item, icone, quantidade = 0 }: Props) {
  const nome = nomeComContador(item.rotulo, quantidade)
  // Com ícone, o rótulo some em telas pequenas. O nome acessível continua completo.
  const rotulo = icone ? <span className="hidden sm:inline">{item.rotulo}</span> : item.rotulo

  if (item.desabilitado) {
    return (
      <span role="link" aria-disabled="true" aria-label={nome} className={`${BASE} cursor-not-allowed text-muted-foreground`}>
        {icone}
        {rotulo}
      </span>
    )
  }

  return (
    <Link
      href={item.href}
      aria-label={nome}
      aria-current={item.ativo ? 'page' : undefined}
      className={`${BASE} hover:bg-muted ${item.ativo ? 'bg-muted' : ''}`}
    >
      {icone}
      {rotulo}
      <ContadorBadge quantidade={quantidade} />
    </Link>
  )
}
```

Create `frontend/app/components/navegacao/Navbar.tsx`:
```tsx
'use client'

import { PackageSearch, Settings } from 'lucide-react'
import Link from 'next/link'
import { LinkNavegacao } from '@/app/components/navegacao/LinkNavegacao'
import { MenuNotificacoes } from '@/app/components/navegacao/MenuNotificacoes'
import { useNotificacoes } from '@/app/hooks/useNotificacoes'
import { montarNavegacao } from '@/services/navegacao'
import type { Sessao } from '@/services/sessao'

type Props = {
  sessao: Sessao
  pathname: string
  agora?: Date
}

export function Navbar({ sessao, pathname, agora }: Props) {
  const navegacao = montarNavegacao(sessao, pathname)
  const notificacoes = useNotificacoes()
  const pecasAProcuraNaoLidas = notificacoes.data?.naoLidas.pecasAProcura ?? 0

  return (
    <header className="border-b bg-background">
      <nav aria-label="Principal" className="mx-auto flex w-full max-w-6xl flex-wrap items-center gap-2 p-3">
        <Link href="/" className="mr-2 font-semibold">
          Autopeças
        </Link>
        <ul className="flex items-center gap-1">
          {navegacao.perfis.map((item) => (
            <li key={item.id}>
              <LinkNavegacao item={item} />
            </li>
          ))}
        </ul>
        <ul className="ml-auto flex items-center gap-1">
          {navegacao.pecasAProcura && (
            <li>
              <LinkNavegacao
                item={navegacao.pecasAProcura}
                icone={<PackageSearch aria-hidden="true" className="size-4" />}
                quantidade={pecasAProcuraNaoLidas}
              />
            </li>
          )}
          <li>
            <MenuNotificacoes agora={agora} />
          </li>
          <li>
            <LinkNavegacao
              item={navegacao.configuracoes}
              icone={<Settings aria-hidden="true" className="size-4" />}
            />
          </li>
        </ul>
      </nav>
    </header>
  )
}
```

- [ ] **Step 4: Implementar os estados da sessão e o `AppShell`**

Create `frontend/app/components/navegacao/EstadosDaSessao.tsx`:
```tsx
import { Alert, AlertDescription, AlertTitle } from '@/app/components/ui/alert'
import { Button } from '@/app/components/ui/button'
import { Skeleton } from '@/app/components/ui/skeleton'
import { mensagemDeErro } from '@/services/erros'

export function CarregandoSessao() {
  return (
    <div role="status" aria-busy="true" className="mx-auto w-full max-w-6xl space-y-4 p-4">
      <span className="sr-only">Carregando…</span>
      <div aria-hidden="true" className="space-y-4">
        <Skeleton className="h-10" />
        <Skeleton className="h-40" />
      </div>
    </div>
  )
}

type PropsDeErro = {
  erro: unknown
  aoTentarDeNovo: () => void
  carregando?: boolean
}

export function ErroDeSessao({ erro, aoTentarDeNovo, carregando = false }: PropsDeErro) {
  const { texto, podeTentarDeNovo } = mensagemDeErro(erro, 'sua conta')

  return (
    <main className="mx-auto w-full max-w-md flex-1 space-y-3 p-4 pt-16">
      <Alert variant="destructive">
        <AlertTitle>Não foi possível abrir sua conta</AlertTitle>
        <AlertDescription>{texto}</AlertDescription>
      </Alert>
      {podeTentarDeNovo && (
        <Button type="button" variant="outline" disabled={carregando} onClick={aoTentarDeNovo}>
          {carregando ? 'Tentando novamente…' : 'Tentar novamente'}
        </Button>
      )}
    </main>
  )
}
```

Create `frontend/app/components/navegacao/AppShell.tsx`:
```tsx
'use client'

import { usePathname, useRouter } from 'next/navigation'
import { useEffect, type ReactNode } from 'react'
import { CarregandoSessao, ErroDeSessao } from '@/app/components/navegacao/EstadosDaSessao'
import { Navbar } from '@/app/components/navegacao/Navbar'
import { useSessao } from '@/app/hooks/useSessao'
import { sessaoExpirou } from '@/services/erros'
import { acessoPermitido } from '@/services/sessao'

export function AppShell({ children }: { children: ReactNode }) {
  const sessao = useSessao()
  const pathname = usePathname()
  const roteador = useRouter()

  const bloqueado = sessao.data !== undefined && !acessoPermitido(sessao.data, pathname)

  useEffect(() => {
    if (bloqueado) roteador.replace('/comprador')
  }, [bloqueado, roteador])

  if (sessao.isPending) return <CarregandoSessao />

  // Sessão expirada aparece mesmo que ainda haja dados antigos em cache.
  if (sessao.isError && (sessao.data === undefined || sessaoExpirou(sessao.error))) {
    return (
      <ErroDeSessao
        erro={sessao.error}
        aoTentarDeNovo={() => void sessao.refetch()}
        carregando={sessao.isFetching}
      />
    )
  }

  // Enquanto redireciona, o conteúdo do vendedor nunca é renderizado.
  if (sessao.data === undefined || bloqueado) return <CarregandoSessao />

  return (
    <>
      <Navbar sessao={sessao.data} pathname={pathname} />
      <main className="mx-auto w-full max-w-6xl flex-1 p-4 md:p-8">{children}</main>
    </>
  )
}
```

- [ ] **Step 5: Rodar e ver passar**

Run: `npm test -- tests/app/components/navegacao && npm run typecheck && npm run lint && npm test -- tests/arquitetura.test.ts`
Expected: todos passam. Se o `lint` reclamar do teste que usa `sessao.data` em condição de `useEffect`, mantenha a regra ligada e ajuste o código, sem desligá-la.

- [ ] **Step 6: Commit**

```bash
cd ..
git add frontend/app frontend/tests
git commit -m "feat(frontend): navbar por perfil, campo Peças à procura e AppShell com guarda de rota" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
cd frontend
```

---

### Task 10: Páginas, layout raiz e proxy da API

**Files:**
- Create: `frontend/app/components/EmConstrucao.tsx`, `frontend/app/components/configuracoes/ConfiguracoesDaConta.tsx`, `frontend/app/(app)/layout.tsx`, `frontend/app/(app)/comprador/page.tsx`, `frontend/app/(app)/vendedor/page.tsx`, `frontend/app/(app)/vendedor/pecas-a-procura/page.tsx`, `frontend/app/(app)/configuracoes/page.tsx`
- Modify: `frontend/app/layout.tsx`, `frontend/app/page.tsx`, `frontend/next.config.ts`
- Test: `frontend/tests/app/components/paginas.test.tsx`

**Interfaces:**
- Consumes: `AppShell`, `Providers`, `useSessao`, `situacaoDoVendedor`, `rotuloDaSituacaoDoVendedor`, `Card`, `fingirSessao`.
- Produces:
  - `EmConstrucao({ titulo, descricao })`: `<section>` com `<h1>` e o texto
  - `ConfiguracoesDaConta()` (client): `<h1>Configurações</h1>` e uma lista com Nome, E-mail, Perfil de comprador ("Ativo") e Perfil de vendedor (rótulo da situação). Não renderiza nada enquanto a sessão não chegou (o `AppShell` já mostra o estado).
  - Rotas: `/` (redireciona para `/comprador`), `/comprador`, `/vendedor`, `/vendedor/pecas-a-procura`, `/configuracoes`, as quatro últimas dentro de `app/(app)/`, com a navbar.
  - `next.config.ts`: `rewrites` de `/api/v1/:path*` para `${API_URL ?? 'http://localhost:8080'}/api/v1/:path*`.

- [ ] **Step 1: Escrever os testes**

Create `frontend/tests/app/components/paginas.test.tsx`:
```tsx
import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { axe } from '@/tests/axe'
import { fingirSessao } from '@/tests/api-fake'
import { umaSessaoDto } from '@/tests/builders'
import { Providers } from '@/app/providers'
import { EmConstrucao } from '@/app/components/EmConstrucao'
import { ConfiguracoesDaConta } from '@/app/components/configuracoes/ConfiguracoesDaConta'
import PaginaComprador, { metadata as metadataComprador } from '@/app/(app)/comprador/page'
import PaginaVendedor, { metadata as metadataVendedor } from '@/app/(app)/vendedor/page'
import PaginaPecasAProcura, { metadata as metadataProcura } from '@/app/(app)/vendedor/pecas-a-procura/page'
import PaginaConfiguracoes, { metadata as metadataConfiguracoes } from '@/app/(app)/configuracoes/page'

function valorDoCampo(rotulo: string): string | null {
  return screen.getByText(rotulo).closest('div')?.querySelector('dd')?.textContent ?? null
}

describe('EmConstrucao', () => {
  it('mostra o título como h1 e a descrição', () => {
    render(<EmConstrucao titulo="Dashboard do comprador" descricao="Chega no próximo plano." />)

    expect(screen.getByRole('heading', { level: 1, name: 'Dashboard do comprador' })).toBeInTheDocument()
    expect(screen.getByText('Chega no próximo plano.')).toBeInTheDocument()
  })
})

describe('páginas provisórias', () => {
  it.each([
    ['comprador', PaginaComprador, metadataComprador, 'Dashboard do comprador', 'Comprar'],
    ['vendedor', PaginaVendedor, metadataVendedor, 'Dashboard do vendedor', 'Vender'],
    ['peças à procura', PaginaPecasAProcura, metadataProcura, 'Peças à procura', 'Peças à procura'],
  ] as const)('%s tem título de página e h1', (_nome, Pagina, metadata, titulo, tituloDaAba) => {
    render(<Pagina />)

    expect(screen.getByRole('heading', { level: 1, name: titulo })).toBeInTheDocument()
    expect(metadata.title).toBe(tituloDaAba)
  })
})

describe('ConfiguracoesDaConta', () => {
  it('mostra nome, e-mail e perfil de comprador', async () => {
    fingirSessao(umaSessaoDto({ nome: 'Ana Souza', email: 'ana@example.com' }))

    render(<Providers><ConfiguracoesDaConta /></Providers>)

    expect(await screen.findByRole('heading', { level: 1, name: 'Configurações' })).toBeInTheDocument()
    expect(valorDoCampo('Nome')).toBe('Ana Souza')
    expect(valorDoCampo('E-mail')).toBe('ana@example.com')
    expect(valorDoCampo('Perfil de comprador')).toBe('Ativo')
  })

  it.each([
    [null, 'Você ainda não é vendedor'],
    [{ status: 'PENDENTE' }, 'Em análise'],
    [{ status: 'APROVADO' }, 'Ativo'],
    [{ status: 'REPROVADO' }, 'Reprovado'],
    [{ status: 'SUSPENSO' }, 'Suspenso'],
  ] as const)('perfil de vendedor %j aparece como "%s"', async (vendedor, esperado) => {
    fingirSessao(umaSessaoDto({ vendedor }))

    render(<Providers><ConfiguracoesDaConta /></Providers>)

    await screen.findByRole('heading', { level: 1, name: 'Configurações' })
    expect(valorDoCampo('Perfil de vendedor')).toBe(esperado)
  })

  it('não renderiza nada enquanto a sessão não chegou', () => {
    fingirSessao()

    const { container } = render(<Providers><ConfiguracoesDaConta /></Providers>)

    expect(container).toBeEmptyDOMElement()
  })

  it('a página de configurações tem título de aba', () => {
    expect(metadataConfiguracoes.title).toBe('Configurações')
    expect(PaginaConfiguracoes).toBeTypeOf('function')
  })

  it('não tem violações de acessibilidade', async () => {
    fingirSessao(umaSessaoDto({ vendedor: { status: 'APROVADO' } }))
    const { container } = render(<Providers><ConfiguracoesDaConta /></Providers>)
    await screen.findByRole('heading', { level: 1, name: 'Configurações' })

    expect(await axe(container)).toHaveNoViolations()
  })
})
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `npm test -- tests/app/components/paginas.test.tsx`
Expected: FAIL com `Failed to resolve import "@/app/components/EmConstrucao"`.

- [ ] **Step 3: Implementar os componentes e as páginas**

Create `frontend/app/components/EmConstrucao.tsx`:
```tsx
export function EmConstrucao({ titulo, descricao }: { titulo: string; descricao: string }) {
  return (
    <section className="space-y-2">
      <h1 className="text-2xl font-semibold">{titulo}</h1>
      <p className="text-sm text-muted-foreground">{descricao}</p>
    </section>
  )
}
```

Create `frontend/app/components/configuracoes/ConfiguracoesDaConta.tsx`:
```tsx
'use client'

import { Card, CardContent } from '@/app/components/ui/card'
import { useSessao } from '@/app/hooks/useSessao'
import { rotuloDaSituacaoDoVendedor } from '@/services/formatacao'
import { situacaoDoVendedor } from '@/services/sessao'

function Campo({ rotulo, valor }: { rotulo: string; valor: string }) {
  return (
    <div>
      <dt className="text-sm text-muted-foreground">{rotulo}</dt>
      <dd className="font-medium">{valor}</dd>
    </div>
  )
}

export function ConfiguracoesDaConta() {
  const sessao = useSessao()
  // O AppShell já mostra carregando e erro. Aqui só se desenha a conta quando ela existe.
  if (sessao.data === undefined) return null

  const { nome, email } = sessao.data

  return (
    <section className="space-y-6">
      <header className="space-y-1">
        <h1 className="text-2xl font-semibold">Configurações</h1>
        <p className="text-sm text-muted-foreground">Dados da sua conta.</p>
      </header>
      <Card>
        <CardContent>
          <dl className="grid gap-4 sm:grid-cols-2">
            <Campo rotulo="Nome" valor={nome} />
            <Campo rotulo="E-mail" valor={email} />
            <Campo rotulo="Perfil de comprador" valor="Ativo" />
            <Campo
              rotulo="Perfil de vendedor"
              valor={rotuloDaSituacaoDoVendedor(situacaoDoVendedor(sessao.data))}
            />
          </dl>
        </CardContent>
      </Card>
    </section>
  )
}
```

Create `frontend/app/(app)/layout.tsx`:
```tsx
import type { ReactNode } from 'react'
import { AppShell } from '@/app/components/navegacao/AppShell'

export default function LayoutDoApp({ children }: { children: ReactNode }) {
  return <AppShell>{children}</AppShell>
}
```

Create `frontend/app/(app)/comprador/page.tsx`:
```tsx
import type { Metadata } from 'next'
import { EmConstrucao } from '@/app/components/EmConstrucao'

export const metadata: Metadata = { title: 'Comprar' }

export default function PaginaComprador() {
  return (
    <EmConstrucao
      titulo="Dashboard do comprador"
      descricao="A pesquisa de peças, os filtros e o lançamento de pedidos chegam no próximo plano."
    />
  )
}
```

Create `frontend/app/(app)/vendedor/page.tsx`:
```tsx
import type { Metadata } from 'next'
import { EmConstrucao } from '@/app/components/EmConstrucao'

export const metadata: Metadata = { title: 'Vender' }

export default function PaginaVendedor() {
  return (
    <EmConstrucao
      titulo="Dashboard do vendedor"
      descricao="O catálogo, o cadastro de peças e o filtro de estoque chegam em um plano seguinte."
    />
  )
}
```

Create `frontend/app/(app)/vendedor/pecas-a-procura/page.tsx`:
```tsx
import type { Metadata } from 'next'
import { EmConstrucao } from '@/app/components/EmConstrucao'

export const metadata: Metadata = { title: 'Peças à procura' }

export default function PaginaPecasAProcura() {
  return (
    <EmConstrucao
      titulo="Peças à procura"
      descricao="As cotações recebidas dos compradores vão aparecer aqui."
    />
  )
}
```

Create `frontend/app/(app)/configuracoes/page.tsx`:
```tsx
import type { Metadata } from 'next'
import { ConfiguracoesDaConta } from '@/app/components/configuracoes/ConfiguracoesDaConta'

export const metadata: Metadata = { title: 'Configurações' }

export default function PaginaConfiguracoes() {
  return <ConfiguracoesDaConta />
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `npm test -- tests/app && npm run typecheck && npm test -- tests/arquitetura.test.ts`
Expected: todos passam.

- [ ] **Step 5: Ligar layout raiz, home e proxy da API (configuração, sem teste unitário)**

Modify `frontend/app/layout.tsx`: substitua o arquivo por (baseado no layout gerado, com as fontes Geist):
```tsx
import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";
import { Providers } from "@/app/providers";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: {
    default: "Marketplace de Autopeças",
    template: "%s | Marketplace de Autopeças",
  },
  description: "Peça a autopeça que você precisa e receba propostas de vendedores.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="pt-BR"
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
    >
      <body className="min-h-full flex flex-col">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
```

Modify `frontend/app/page.tsx`: substitua todo o conteúdo por:
```tsx
import { redirect } from 'next/navigation'

export default function Home() {
  redirect('/comprador')
}
```

Modify `frontend/next.config.ts`: substitua por:
```ts
import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // O navegador chama /api/v1/* no mesmo site (cookie de sessão sem CORS).
  // O Next repassa ao backend. API_URL é lida no servidor.
  async rewrites() {
    return [
      {
        source: "/api/v1/:path*",
        destination: `${process.env.API_URL ?? "http://localhost:8080"}/api/v1/:path*`,
      },
    ];
  },
};

export default nextConfig;
```

- [ ] **Step 6: Verificar tudo**

Run: `npm test && npm run typecheck && npm run lint && npm run build`
Expected: todos passam. O `build` lista as rotas `/` (redireciona), `/comprador`, `/vendedor`, `/vendedor/pecas-a-procura`, `/configuracoes` e `/_not-found`.

- [ ] **Step 7: Commit**

```bash
cd ..
git add frontend
git commit -m "feat(frontend): páginas com navbar, configurações da conta e proxy da API" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
cd frontend
```

---

### Task 11: Servidor de mock, verificação no navegador e documentação

**Files:**
- Create: `frontend/mocks/api-mock.mjs`
- Modify: `frontend/package.json` (script `mock-api`), `frontend/README.md`, `docs/superpowers/specs/2026-09-29-stack-e-tdd-design.md`

**Interfaces:**
- Consumes: os contratos da seção "Contratos consumidos".
- Produces: `npm run mock-api [-- --cenario=vendedor|comprador|vendedor-em-analise|sessao-expirada|erro|notificacoes-com-erro]` sobe um servidor em `:8080` (ou `PORT`). Padrão `vendedor`. As notificações têm estado em memória: o `PATCH` marca como lida e os contadores refletem isso. O backend real substitui este script sem mudar nada no frontend.

- [ ] **Step 1: Criar o servidor de mock**

Create `frontend/mocks/api-mock.mjs`:
```js
import { createServer } from 'node:http'

// Servidor de mock só para desenvolvimento. Segue os contratos de /api/v1/me e /api/v1/notificacoes.
// Como o backend real, exige o cabeçalho X-Auth-Id (o valor vem de /api/v1/me) em tudo, menos em GET /api/v1/me.
// Uso: npm run mock-api -- --cenario=comprador
// Cenários: vendedor (padrão), comprador, vendedor-em-analise, sessao-expirada, erro, notificacoes-com-erro

const PORTA = Number(process.env.PORT ?? 8080)
const CENARIOS = [
  'vendedor',
  'comprador',
  'vendedor-em-analise',
  'sessao-expirada',
  'erro',
  'notificacoes-com-erro',
]
const argumento = process.argv.find((a) => a.startsWith('--cenario='))
const cenario = argumento ? argumento.split('=')[1] : 'vendedor'

if (!CENARIOS.includes(cenario)) {
  console.error(`Cenário desconhecido: "${cenario}". Use um de: ${CENARIOS.join(', ')}`)
  process.exit(1)
}

// Um valor fixo de 64 caracteres hexadecimais. O mock só compara igualdade.
const AUTH_ID = '0123456789abcdef'.repeat(4)
const usuario = { id: 'u-1', nome: 'Vynicyus Silva', email: 'vynicyus@example.com', authId: AUTH_ID }
const sessoes = {
  vendedor: { ...usuario, vendedor: { status: 'APROVADO' } },
  comprador: { ...usuario, vendedor: null },
  'vendedor-em-analise': { ...usuario, vendedor: { status: 'PENDENTE' } },
}

const MINUTO = 60 * 1000
const atras = (minutos) => new Date(Date.now() - minutos * MINUTO).toISOString()

const notificacoes = [
  { id: 'n-1', tipo: 'CHAT', titulo: 'Auto Peças Silva', resumo: 'Tenho o farol esquerdo do Gol G6, posso mandar foto?', criadaEm: atras(4), lida: false },
  { id: 'n-2', tipo: 'CHAT', titulo: 'Desmanche Rota 116', resumo: 'Consigo fazer R$ 180 se você retirar hoje.', criadaEm: atras(45), lida: false },
  { id: 'n-3', tipo: 'PROPOSTA', titulo: 'Nova proposta: Retrovisor direito', resumo: 'Moto Center ofereceu R$ 89,90.', criadaEm: atras(180), lida: false },
  { id: 'n-4', tipo: 'SISTEMA', titulo: 'Seu pedido expira amanhã', resumo: 'Farol esquerdo · Gol 2014', criadaEm: atras(60 * 30), lida: true },
  { id: 'n-5', tipo: 'CHAT', titulo: 'Pneus & Cia', resumo: 'Ainda precisa do amortecedor?', criadaEm: atras(60 * 24 * 10), lida: true },
  { id: 'n-6', tipo: 'PECA_A_PROCURA', titulo: 'Procuram: Farol esquerdo', resumo: 'Gol 1.0 2014 · Recife/PE', criadaEm: atras(8), lida: false },
  { id: 'n-7', tipo: 'PECA_A_PROCURA', titulo: 'Procuram: Amortecedor dianteiro', resumo: 'Uno 2012 · Olinda/PE', criadaEm: atras(90), lida: false },
  { id: 'n-8', tipo: 'PECA_A_PROCURA', titulo: 'Procuram: Bateria 60Ah', resumo: 'HB20 2016 · Recife/PE', criadaEm: atras(60 * 5), lida: true },
]

function contadores() {
  const naoLidas = notificacoes.filter((n) => !n.lida)
  return {
    chat: naoLidas.filter((n) => n.tipo === 'CHAT').length,
    outras: naoLidas.filter((n) => n.tipo === 'PROPOSTA' || n.tipo === 'SISTEMA').length,
    pecasAProcura: naoLidas.filter((n) => n.tipo === 'PECA_A_PROCURA').length,
  }
}

function responder(res, status, corpo, tipo = 'application/json') {
  res.writeHead(status, { 'Content-Type': `${tipo}; charset=utf-8` })
  res.end(corpo === undefined ? undefined : JSON.stringify(corpo))
}

const problema = (res, status, title) =>
  responder(res, status, { title, status }, 'application/problem+json')

async function lerJson(req) {
  const partes = []
  for await (const parte of req) partes.push(parte)
  return partes.length === 0 ? null : JSON.parse(Buffer.concat(partes).toString('utf8'))
}

createServer(async (req, res) => {
  const url = new URL(req.url ?? '/', 'http://localhost')
  console.log(`${req.method} ${url.pathname}${url.search} [${cenario}]`)

  if (cenario === 'sessao-expirada') return problema(res, 401, 'Não autenticado')

  if (req.method === 'GET' && url.pathname === '/api/v1/me') {
    if (cenario === 'erro') return problema(res, 500, 'Erro interno do servidor')
    return responder(res, 200, sessoes[cenario] ?? sessoes.vendedor)
  }

  if (req.headers['x-auth-id'] !== AUTH_ID) {
    return problema(res, 401, 'Não foi possível confirmar o usuário da sessão')
  }

  if (url.pathname === '/api/v1/notificacoes' && req.method === 'GET') {
    if (cenario === 'notificacoes-com-erro') return problema(res, 500, 'Erro interno do servidor')
    const tipos = url.searchParams.get('tipos')?.split(',')
    const limite = Number(url.searchParams.get('limite') ?? 20)
    const itens = notificacoes
      .filter((n) => !tipos || tipos.includes(n.tipo))
      .sort((a, b) => b.criadaEm.localeCompare(a.criadaEm))
      .slice(0, limite)
    return responder(res, 200, { itens, naoLidas: contadores() })
  }

  const patch = url.pathname.match(/^\/api\/v1\/notificacoes\/([^/]+)$/)
  if (patch && req.method === 'PATCH') {
    const alvo = notificacoes.find((n) => n.id === decodeURIComponent(patch[1]))
    if (!alvo) return problema(res, 404, 'Notificação não encontrada')
    const corpo = await lerJson(req)
    alvo.lida = corpo?.lida === true
    return responder(res, 204)
  }

  return problema(res, 404, 'Não encontrado')
}).listen(PORTA, () => {
  console.log(`API de mock em http://localhost:${PORTA} (cenário: ${cenario})`)
})
```

- [ ] **Step 2: Adicionar o script e conferir o servidor**

```bash
npm pkg set scripts.mock-api="node mocks/api-mock.mjs"
npm run lint
```
Expected: `lint` sem erros. Depois, em um segundo terminal (ou em segundo plano), suba o mock e consulte:
```bash
npm run mock-api
```
e em outro terminal:
```bash
curl -s http://localhost:8080/api/v1/me
curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8080/api/v1/notificacoes"
curl -s -H "X-Auth-Id: 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef" "http://localhost:8080/api/v1/notificacoes?tipos=CHAT,PROPOSTA,SISTEMA&limite=20"
```
Expected: o primeiro devolve o usuário com `"vendedor":{"status":"APROVADO"}` e o `authId`. O segundo imprime `401` (sem o cabeçalho, como o backend real). O terceiro devolve 5 itens (sem `PECA_A_PROCURA`) e `"naoLidas":{"chat":2,"outras":1,"pecasAProcura":2}`. Pare o mock em seguida.

- [ ] **Step 3: Verificar a navbar no navegador (mock + Next em desenvolvimento)**

Suba `npm run mock-api` e, em outro processo, `npm run dev`. Abra `http://localhost:3000/` no navegador.
Expected, conferindo cada item:
1. A raiz `/` redireciona para `/comprador`, com título de aba "Comprar | Marketplace de Autopeças".
2. A navbar mostra: marca **Autopeças**, **Comprar** (destacado), **Vender**, **Peças à procura** com o emblema **2**, o **sino** com o emblema **3** e **Configurações**. O sino **não** soma as 2 peças à procura.
3. Clicar no sino abre o painel lateral com as abas **Todas** e **Chat (2)**. A lista mostra 5 notificações, da mais recente ("há 4 min") à mais antiga (uma data), e as não lidas têm um ponto. A aba Chat mostra só as 3 mensagens de chat. O botão de fechar e o Escape fecham o painel.
4. Clicar em "Auto Peças Silva" tira o ponto e o emblema do sino cai para **2**. Clicar em uma já lida não muda nada. No terminal do mock aparece **um** `PATCH` por clique em não lida e nenhum em já lida.
5. **Vender** leva a `/vendedor` ("Dashboard do vendedor") e fica destacado. **Peças à procura** leva a `/vendedor/pecas-a-procura` e só ele fica destacado (Vender não). **Configurações** leva a `/configuracoes` e mostra Nome, E-mail, "Perfil de comprador: Ativo" e "Perfil de vendedor: Ativo".
6. Em largura de celular (~375 px), a navbar não gera rolagem horizontal e os itens da direita mostram só o ícone.
7. `curl -s http://localhost:3000/api/v1/me` devolve o mesmo JSON do mock, o que confirma o `rewrite`.

Depois, repita com cada cenário (reinicie o mock com `npm run mock-api -- --cenario=<nome>` e recarregue a página):
- `comprador` → sem **Vender** e sem **Peças à procura**. Abrir `/vendedor` na barra de endereço volta para `/comprador` **sem mostrar** o conteúdo do vendedor. Em Configurações: "Você ainda não é vendedor".
- `vendedor-em-analise` → **Vender (em análise)** aparece apagado e não clicável, sem Peças à procura. `/vendedor` volta para `/comprador`.
- `sessao-expirada` → página com "Sua sessão expirou. Entre novamente para continuar.", sem navbar e sem botão de tentar de novo.
- `erro` → alerta "Não foi possível carregar sua conta agora." com **Tentar novamente**.
- `notificacoes-com-erro` → a navbar aparece normalmente. O sino abre com o alerta de erro e **Tentar novamente**.

Se algum item divergir, corrija o código com um teste que reproduza o defeito antes.

**Contra o backend real (opcional, depois do plano do backend):** suba o backend (`API_URL` padrão `http://localhost:8080`) e o `npm run dev`. Como ainda não há tela de login, entre pelo console do navegador, em `http://localhost:3000`: `fetch('/api/v1/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email: 'vynicyus@example.com', senha: 'senha-dev-123' }) })`. O `Set-Cookie` passa pelo proxy do Next.js. Depois recarregue a página: a navbar deve aparecer com os dados reais.

- [ ] **Step 4: Documentar o frontend**

Replace `frontend/README.md` com:
```markdown
# Frontend — Marketplace de Autopeças

Next.js (App Router), TypeScript, Tailwind CSS e shadcn/ui. Desenvolvido com TDD.

## Estrutura

| Pasta | Papel |
|---|---|
| `app/` | Páginas, componentes e hooks. As páginas dentro de `app/(app)/` têm a barra de navegação |
| `app/components/ui/` | Componentes do shadcn/ui |
| `app/components/navegacao/` | Navbar, sino de notificações e `AppShell` |
| `app/hooks/` | Hooks do React (TanStack Query) que chamam `services/` |
| `api/` | Conexão com o backend (fetch, erros, `X-Auth-Id`, contratos validados com Zod) |
| `services/` | **Regras de negócio**, sem React nem Next |
| `mocks/` | MSW (testes) e servidor de mock (desenvolvimento) |
| `tests/` | Testes, espelhando as pastas de código |

Regra de dependência: `app/` → `services/` → `api/`. Componentes e hooks nunca chamam `api/` direto. O teste `tests/arquitetura.test.ts` garante isso.

## Comandos

```bash
npm run dev          # Next.js em http://localhost:3000
npm run mock-api     # API de mock em http://localhost:8080
npm test             # Vitest
npm run test:watch
npm run typecheck
npm run lint
npm run build
```

Cenários do mock: `npm run mock-api -- --cenario=<nome>`, com `vendedor` (padrão), `comprador`, `vendedor-em-analise`, `sessao-expirada`, `erro` e `notificacoes-com-erro`.

## Backend

O navegador chama `/api/v1/*` no mesmo site, e o Next.js repassa ao backend (`rewrites` em `next.config.ts`). A URL do backend vem de `API_URL` (padrão `http://localhost:8080`). Em desenvolvimento sem backend, use `npm run mock-api`.

Contratos consumidos hoje: `GET /api/v1/me`, `GET /api/v1/notificacoes` e `PATCH /api/v1/notificacoes/{id}` (ver `api/`). Toda chamada autenticada, menos `GET /api/v1/me`, envia o cabeçalho `X-Auth-Id`, cujo valor vem de `/me` (ver `api/cliente.ts`). O JWT vai no cookie `access_token`, que o navegador envia sozinho.
```

Modify `docs/superpowers/specs/2026-09-29-stack-e-tdd-design.md`: substitua o parágrafo que começa com `**Frontend:**` (na seção 3.1) por:
```
**Frontend (decisão do usuário):** componentes e páginas dentro de `app/` (`app/components/…`, páginas em `app/(app)/…`, hooks do React em `app/hooks/`); a conexão com o backend em `api/` e as regras de negócio em `services/`, **fora** de `app/`. A dependência vai só de `app/` para `services/` e de `services/` para `api/`, verificada por um teste de arquitetura. Isso substitui a organização por `features/` proposta antes. Até o backend publicar o OpenAPI, o contrato é escrito à mão com Zod em `api/`. Depois, o cliente passa a ser gerado. Telas e divisão em planos: [design das telas](2026-09-29-frontend-telas-design.md).
```

- [ ] **Step 5: Verificação final e commit**

```bash
npm test && npm run typecheck && npm run lint && npm run build
cd ..
git add frontend docs
git commit -m "feat(frontend): servidor de mock, README e estrutura documentada" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
git status --short
```
Expected: todos os comandos passam. `git status --short` sai vazio (nada sem commit).

---

## Auto-revisão (feita ao escrever o plano)

- **Cobertura do pedido do usuário (navbar):** configurações do usuário → Tarefa 10 (`/configuracoes` + link). Campo de notificação incluindo chat → Tarefa 8 (sino com abas Todas e Chat). Campo específico de peças à procura do vendedor → Tarefas 5 e 9 (item próprio na navbar, com contador, só vendedor aprovado). Navbar para os dois perfis → Tarefas 5 e 9. Os dashboards de comprador e vendedor ficam nos Planos 2 e 3, como decidido.
- **Cobertura da spec:** AUT-03 e AUT-05 → Tarefas 4, 5 e 9 (perfis, aprovação e guarda de rota). NOT-01 → Tarefa 8. LST-04 (entrada de "Cotações recebidas") → campo Peças à procura, com o conteúdo no Plano 3. AUT-06 → só a leitura em `/configuracoes` (editar e excluir ficam para depois). RNF-USA-01 → textos em pt-BR e ícones em tela pequena, conferidos na Tarefa 11. RNF-USA-04 → axe nas telas (Tarefas 8, 9 e 10). RNF-MAN-02 → toda a suíte.
- **Estrutura pedida:** componentes e páginas em `app/`, `api/` e `services/` fora, regra de negócio em `services/` → Tarefas 2 (teste de arquitetura), 3 a 7 e 8 a 10.
- **Consistência de nomes:** `Sessao`, `SessaoDto`, `StatusVendedor`, `SituacaoDoVendedor`, `Modo`, `ItemNavegacao`, `Navegacao`, `Notificacao`, `NotificacaoDto`, `ContadoresNaoLidas`, `PainelNotificacoes`, `TIPOS_DO_SINO`, `ApiError.tipo`, `requisitar`, `enviar`, `carregarSessao`, `carregarNotificacoes`, `marcarComoLida`, `mensagemDeErro(erro, recurso)`, `sessaoExpirou`, `nomeComContador`, `rotuloDoContador`, `tempoRelativo`, `umaSessaoDto`, `umaNotificacaoDto`, `umaListaDeNotificacoesDto`, `fingirSessao`, `fingirNotificacoes`, `Providers`, `AppShell` e `Navbar` têm a mesma grafia em todas as tarefas.
- **Sem marcadores pendentes:** todo passo de código tem o código. Os passos de configuração (Tarefa 1, passos 4 e 8; Tarefa 10, passo 5) dão os comandos e os arquivos completos.

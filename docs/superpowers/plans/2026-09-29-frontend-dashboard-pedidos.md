> **SUBSTITUÍDO em 2026-09-29. Não execute este plano.** O dashboard passou a ser pesquisa e filtros (comprador) e catálogo (vendedor), com navbar para os dois perfis, e o trabalho foi dividido em três planos (ver [design das telas](../specs/2026-09-29-frontend-telas-design.md)). Este documento fica como **referência**: o esqueleto, o teste de arquitetura, o cliente HTTP e as regras de "Meus pedidos" (tarefas 4 a 9) serão reaproveitados nos Planos 1 e 2.

# Frontend: esqueleto e dashboard "Meus pedidos" — Plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Criar o frontend Next.js do marketplace com a estrutura definida pelo usuário e entregar o primeiro dashboard, o **"Meus pedidos" do comprador** (PED-06), desenvolvido com TDD.

**Architecture:** Componentes e a página do dashboard ficam dentro de `app/`. A conexão com o backend (`api/`) e as regras de negócio (`services/`) ficam **fora** de `app/`. A dependência só vai num sentido: `app/` → `services/` → `api/`, e um teste de arquitetura garante isso. O backend ainda não existe: o contrato de `GET /api/v1/pedidos` é validado com Zod e simulado com MSW nos testes e com um servidor de mock em desenvolvimento.

**Tech Stack:** Next.js 16 (App Router), React 19, TypeScript, Tailwind CSS 4, shadcn/ui (base Radix, preset Nova), TanStack Query 5, Zod 4, Vitest 5, Testing Library, MSW 2, vitest-axe. Node 24.

**Specs:**
- [Spec do produto](../specs/2026-09-29-marketplace-autopecas-design.md) (PED-06, RNF-USA-01, RNF-USA-04, RNF-MAN-02)
- [Stack e TDD](../specs/2026-09-29-stack-e-tdd-design.md)

## Global Constraints

- Estrutura do frontend (decisão do usuário): **componentes e a página do dashboard dentro de `app/`; `api/` (conexão) e `services/` (regra de negócio) fora de `app/`.** Componentes falam com `services/`, nunca direto com `api/`.
- "Painel 'Meus pedidos' com status e contadores de conversas e propostas" (PED-06).
- "Validade padrão de 7 dias, com renovação pelo comprador" (PED-04). Dados de exemplo usam 7 dias.
- Estados do pedido: "Aberto → Em negociação (primeira resposta) → Concluído · Cancelado · Expirado". "Concluído" tem resultado *Vendido* ou *Sem compra* (05a_estados_pedido).
- "Interface em pt-BR, **mobile-first** e responsiva" (RNF-USA-01).
- "WCAG 2.1 AA nos fluxos principais" (RNF-USA-04). Testes automáticos com axe em cada tela. O contraste é conferido depois, no navegador real, porque o jsdom não calcula estilos.
- Erros da API no formato Problem Details (RFC 9457): o campo `title` vira a mensagem técnica.
- Sessão por cookie HttpOnly: toda chamada usa `credentials: 'include'`. O navegador chama `/api/v1/*` no **mesmo site**, via `rewrites` do Next.js.
- Valores monetários trafegam em **centavos (inteiro)**, nunca em ponto flutuante.
- TDD: nenhum código de produção sem um teste falhando antes. Testes importam `describe`, `it` e `expect` de `vitest` (sem globais).
- Versões: usar a última estável na criação (Next 16.x, React 19, Tailwind 4, Vitest 5). O `@types/node` precisa ser `^24` (o Vitest 5 exige 22 ou 24).

## Review Focus

Entradas e falhas que a spec implica mas nenhuma tarefa "óbvia" exercita. Cada linha tem o teste na tarefa dona.

1. **API fora do ar, erro 5xx ou rede caída:** a página mostra um alerta e o botão "Tentar novamente", sem quebrar. O botão recarrega e, se der certo, o alerta some. *(Tarefas 3 e 9)*
2. **Sessão expirada (401):** mensagem própria e **sem** botão de tentar de novo, porque repetir não adianta. *(Tarefas 6 e 9)*
3. **Resposta fora do contrato** (status desconhecido, data em formato errado, número negativo, campo faltando): vira erro de contrato, e a tela nunca mostra `NaN` nem `Invalid Date`. *(Tarefas 4, 5 e 9)*
4. **Lista vazia** e **filtro sem resultado** são dois estados diferentes, com mensagens diferentes. *(Tarefas 7 e 9)*
5. **Pedido sem propostas × proposta de R$ 0,00:** `0` não pode virar "Sem propostas ainda" (armadilha de valor falso). O valor aparece em reais com centavos e com o espaço não separável do `Intl` (U+00A0). Singular e plural (1 conversa, 0 propostas). *(Tarefa 6)*

Também exercitado: pedido ainda "Aberto" cuja validade já passou (job atrasado) aparece como "Expirado" *(Tarefa 5)*.

## Fora do escopo deste plano

Backend (Spring Boot, PostgreSQL, Docker Compose), login, criar pedido, ações do pedido (cancelar, renovar, reabrir) e o dashboard do vendedor. O backend terá plano próprio e implementará o contrato descrito abaixo.

## Contrato consumido: `GET /api/v1/pedidos`

```json
{
  "itens": [
    {
      "id": "p-1",
      "veiculo": { "tipo": "CARRO", "marca": "Volkswagen", "modelo": "Gol", "versao": "1.0 Flex 12V 5p", "ano": 2014 },
      "descricao": "Farol esquerdo",
      "status": "ABERTO",
      "resultado": null,
      "criadoEm": "2026-09-28T12:00:00Z",
      "expiraEm": "2026-10-05T12:00:00Z",
      "totalConversas": 3,
      "totalPropostas": 0,
      "menorPropostaEmCentavos": null
    }
  ]
}
```

`tipo`: `CARRO | MOTO | CAMINHAO`. `status`: `ABERTO | EM_NEGOCIACAO | CONCLUIDO | CANCELADO | EXPIRADO`. `resultado`: `VENDIDO | SEM_COMPRA | null` (só quando `CONCLUIDO`). `resultado` e `menorPropostaEmCentavos` podem vir `null` ou ausentes. Datas em ISO 8601 UTC (`...Z`).

## Estrutura de arquivos

```
IdeiaDeTCC/
└─ frontend/
   ├─ app/
   │  ├─ layout.tsx                  # (modifica) pt-BR, título, Providers
   │  ├─ page.tsx                    # (substitui) redireciona para /dashboard
   │  ├─ providers.tsx               # QueryClientProvider
   │  ├─ dashboard/page.tsx          # monta a página com os componentes
   │  └─ components/
   │     ├─ ui/                      # shadcn: button, card, badge, skeleton, alert
   │     └─ dashboard/
   │        ├─ CabecalhoDashboard.tsx
   │        ├─ DashboardPedidos.tsx  # container: consulta + estados
   │        ├─ ResumoPedidos.tsx
   │        ├─ FiltroStatus.tsx
   │        ├─ ListaPedidos.tsx
   │        ├─ PedidoItem.tsx
   │        ├─ StatusBadge.tsx
   │        ├─ EstadoVazio.tsx
   │        ├─ EstadoErro.tsx
   │        └─ CarregandoPedidos.tsx
   ├─ api/                           # conexão com o backend
   │  ├─ cliente.ts                  # fetch + ApiError
   │  └─ pedidos.ts                  # schema Zod + listarPedidos
   ├─ services/                      # regra de negócio
   │  ├─ pedidos.ts                  # domínio: resumo, filtro, ordem, validade
   │  ├─ formatacao.ts               # moeda, rótulos e textos
   │  └─ erros.ts                    # mensagem e política de repetição por erro
   ├─ mocks/
   │  ├─ servidor.ts                 # MSW (testes)
   │  └─ api-mock.mjs                # servidor de mock (desenvolvimento)
   ├─ tests/                         # espelha as pastas de código
   ├─ vitest.config.mts · vitest.setup.ts · vitest.d.ts
   └─ next.config.ts · components.json
```

---

### Task 1: Esqueleto do frontend e cadeia de ferramentas

**Files:**
- Create: repositório Git em `IdeiaDeTCC/`, `frontend/` (gerado), `frontend/vitest.config.mts`, `frontend/vitest.setup.ts`, `frontend/vitest.d.ts`, `frontend/mocks/servidor.ts`, `frontend/tests/axe.ts`, `frontend/tests/toolchain.test.tsx`
- Modify: `frontend/package.json`, `frontend/components.json`

**Interfaces:**
- Produces: `@/mocks/servidor` exporta `servidor` (instância `setupServer()` do MSW). `@/tests/axe` exporta `axe(container)` com `color-contrast` e `region` desligados. Scripts npm: `test`, `test:watch`, `typecheck`, `lint`, `build`.

- [ ] **Step 1: Criar o repositório Git dentro de `IdeiaDeTCC` e versionar a documentação**

A raiz atual do Git é a pasta pai `ProjectCode`, que tem vários outros projetos. Este projeto passa a ter o próprio repositório. A partir daqui, **todos os comandos rodam a partir de `C:\Users\vynic\ProjectCode\IdeiaDeTCC`**, salvo indicação.

```bash
git init -b main
git rev-parse --show-toplevel
git add docs
git commit -m "docs: spec, stack, diagramas e plano do frontend" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
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
npx --yes shadcn@latest add button card badge skeleton alert -y
```
Expected: o `init` cria `components/ui/button.tsx` e `lib/utils.ts`. O segundo comando muda os aliases, o terceiro apaga a pasta `components/` da raiz, e o `add` cria **cinco arquivos em `app/components/ui/`**: `button.tsx`, `card.tsx`, `badge.tsx`, `skeleton.tsx` e `alert.tsx`. Os componentes importam `cn` do pacote `cn`, e isso é o comportamento atual do shadcn, não um erro.
Conferir: `ls app/components/ui` lista os cinco arquivos, e `components/` não existe na raiz de `frontend/`.

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
})
```

- [ ] **Step 6: Rodar e ver falhar**

Run: `npx vitest run`
Expected: FAIL. Sem a configuração, o alias `@/` não resolve (`Failed to resolve import "@/mocks/servidor"`) e não há ambiente de DOM.

- [ ] **Step 7: Adicionar a configuração do Vitest**

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

- [ ] **Step 8: Adicionar os scripts e rodar tudo**

```bash
npm pkg set scripts.test="vitest run" scripts.test:watch="vitest" scripts.typecheck="tsc --noEmit"
npm test
npm run typecheck
npm run lint
npm run build
```
Expected: `npm test` mostra `2 passed`. `typecheck` e `lint` terminam sem erro. `build` termina com as rotas `/` e `/_not-found`.

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
import { listarPedidos } from '@/api/pedidos'

export const usado = listarPedidos
```
Run: `npm test -- tests/arquitetura.test.ts`
Expected: FAIL na regra `app/ fala com services/, nunca direto com api/`, com a mensagem `app/violacao-temporaria.ts importa "@/api/pedidos"`.

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
  - `requisitar<T>(caminho: string, schema: ZodType<T>): Promise<T>` faz `GET`, com `credentials: 'include'` e `Accept: application/json`. Base da URL: `process.env.NEXT_PUBLIC_API_URL ?? ''`.

- [ ] **Step 1: Escrever os testes**

Create `frontend/tests/api/cliente.test.ts`:
```ts
import { afterEach, describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { z } from 'zod'
import { servidor } from '@/mocks/servidor'
import { ApiError, requisitar } from '@/api/cliente'

const URL_TESTE = 'http://localhost:8080/api/v1/teste'
const Schema = z.object({ ok: z.boolean() })

describe('requisitar', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

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

function urlBase(): string {
  return process.env.NEXT_PUBLIC_API_URL ?? ''
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

export async function requisitar<T>(caminho: string, schema: ZodType<T>): Promise<T> {
  let resposta: Response
  try {
    resposta = await fetch(`${urlBase()}${caminho}`, {
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
  } catch {
    throw new ApiError('rede', 0, 'Não foi possível conectar ao servidor.')
  }

  if (!resposta.ok) {
    const titulo = await tituloDoProblema(resposta)
    throw new ApiError('http', resposta.status, titulo ?? `Erro ${resposta.status} ao chamar o servidor.`)
  }

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
```

- [ ] **Step 4: Rodar e ver passar**

Run: `npm test -- tests/api/cliente.test.ts && npm run typecheck && npm test -- tests/arquitetura.test.ts`
Expected: todos passam. (O teste de arquitetura agora enxerga `api/` e confirma que ele não importa `app`, `services`, React nem Next.)

- [ ] **Step 5: Commit**

```bash
cd ..
git add frontend/api frontend/tests/api
git commit -m "feat(frontend): cliente HTTP com ApiError, credentials include e validação Zod" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
cd frontend
```

---

### Task 4: Contrato de pedidos (`api/pedidos.ts`)

**Files:**
- Create: `frontend/api/pedidos.ts`, `frontend/tests/builders.ts`
- Test: `frontend/tests/api/pedidos.test.ts`

**Interfaces:**
- Consumes: `requisitar` de `@/api/cliente`.
- Produces:
  - `TIPOS_VEICULO`, `STATUS_PEDIDO`, `RESULTADOS_PEDIDO` (tuplas `as const`)
  - `PedidoDtoSchema`, `ListaPedidosDtoSchema` (Zod)
  - `type PedidoDto`, `type ListaPedidosDto = { itens: PedidoDto[] }`
  - `listarPedidos(): Promise<ListaPedidosDto>` → `GET /api/v1/pedidos`
  - Helper de teste `umPedidoDto(sobrescritas?: Partial<PedidoDto>): PedidoDto` em `@/tests/builders`, com `id: 'pedido-1'`, `ABERTO`, `criadoEm: '2026-09-29T12:00:00Z'`, `expiraEm: '2026-10-06T12:00:00Z'`, contadores 0 e `null` nos opcionais.

- [ ] **Step 1: Escrever o builder e os testes**

Create `frontend/tests/builders.ts`:
```ts
import type { PedidoDto } from '@/api/pedidos'

export function umPedidoDto(sobrescritas: Partial<PedidoDto> = {}): PedidoDto {
  return {
    id: 'pedido-1',
    veiculo: { tipo: 'CARRO', marca: 'Volkswagen', modelo: 'Gol', versao: '1.0 Flex 12V 5p', ano: 2014 },
    descricao: 'Farol esquerdo',
    status: 'ABERTO',
    resultado: null,
    criadoEm: '2026-09-29T12:00:00Z',
    expiraEm: '2026-10-06T12:00:00Z',
    totalConversas: 0,
    totalPropostas: 0,
    menorPropostaEmCentavos: null,
    ...sobrescritas,
  }
}
```

Create `frontend/tests/api/pedidos.test.ts`:
```ts
import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { servidor } from '@/mocks/servidor'
import { umPedidoDto } from '@/tests/builders'
import { listarPedidos } from '@/api/pedidos'

const URL_PEDIDOS = 'http://localhost:8080/api/v1/pedidos'

function responderCom(corpo: unknown) {
  servidor.use(http.get(URL_PEDIDOS, () => HttpResponse.json(corpo as Record<string, unknown>)))
}

describe('listarPedidos', () => {
  it('devolve a lista validada', async () => {
    const dto = umPedidoDto({
      id: 'p-9',
      status: 'EM_NEGOCIACAO',
      totalConversas: 2,
      totalPropostas: 1,
      menorPropostaEmCentavos: 8990,
    })
    responderCom({ itens: [dto] })

    await expect(listarPedidos()).resolves.toEqual({ itens: [dto] })
  })

  it('aceita lista vazia', async () => {
    responderCom({ itens: [] })

    await expect(listarPedidos()).resolves.toEqual({ itens: [] })
  })

  it('aceita resultado e menor proposta ausentes (o backend pode omitir null)', async () => {
    const bruto: Record<string, unknown> = { ...umPedidoDto() }
    delete bruto.resultado
    delete bruto.menorPropostaEmCentavos
    responderCom({ itens: [bruto] })

    const { itens } = await listarPedidos()

    expect(itens).toHaveLength(1)
    expect(itens[0]?.id).toBe('pedido-1')
  })

  it.each([
    ['status desconhecido', { status: 'DESCONHECIDO' }],
    ['tipo de veículo desconhecido', { veiculo: { ...umPedidoDto().veiculo, tipo: 'BICICLETA' } }],
    ['data fora do padrão ISO', { expiraEm: '06/10/2026' }],
    ['data inexistente', { expiraEm: 'ontem' }],
    ['contador negativo', { totalPropostas: -1 }],
    ['valor com casas decimais em vez de centavos inteiros', { menorPropostaEmCentavos: 89.9 }],
    ['descrição vazia', { descricao: '' }],
  ])('rejeita como erro de contrato: %s', async (_nome, alteracao) => {
    responderCom({ itens: [{ ...umPedidoDto(), ...alteracao }] })

    await expect(listarPedidos()).rejects.toMatchObject({ tipo: 'contrato' })
  })

  it('rejeita corpo sem a chave itens', async () => {
    responderCom({})

    await expect(listarPedidos()).rejects.toMatchObject({ tipo: 'contrato' })
  })
})
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `npm test -- tests/api/pedidos.test.ts`
Expected: FAIL com `Failed to resolve import "@/api/pedidos"`.

- [ ] **Step 3: Implementar**

Create `frontend/api/pedidos.ts`:
```ts
import { z } from 'zod'
import { requisitar } from '@/api/cliente'

export const TIPOS_VEICULO = ['CARRO', 'MOTO', 'CAMINHAO'] as const
export const STATUS_PEDIDO = ['ABERTO', 'EM_NEGOCIACAO', 'CONCLUIDO', 'CANCELADO', 'EXPIRADO'] as const
export const RESULTADOS_PEDIDO = ['VENDIDO', 'SEM_COMPRA'] as const

const VeiculoDtoSchema = z.object({
  tipo: z.enum(TIPOS_VEICULO),
  marca: z.string().min(1),
  modelo: z.string().min(1),
  versao: z.string().min(1),
  ano: z.number().int().min(1900).max(2100),
})

export const PedidoDtoSchema = z.object({
  id: z.string().min(1),
  veiculo: VeiculoDtoSchema,
  descricao: z.string().min(1),
  status: z.enum(STATUS_PEDIDO),
  // O backend pode enviar null ou omitir estes dois campos.
  resultado: z.enum(RESULTADOS_PEDIDO).nullish(),
  criadoEm: z.iso.datetime(),
  expiraEm: z.iso.datetime(),
  totalConversas: z.number().int().nonnegative(),
  totalPropostas: z.number().int().nonnegative(),
  menorPropostaEmCentavos: z.number().int().nonnegative().nullish(),
})

export const ListaPedidosDtoSchema = z.object({ itens: z.array(PedidoDtoSchema) })

export type PedidoDto = z.infer<typeof PedidoDtoSchema>
export type ListaPedidosDto = z.infer<typeof ListaPedidosDtoSchema>

export function listarPedidos(): Promise<ListaPedidosDto> {
  return requisitar('/api/v1/pedidos', ListaPedidosDtoSchema)
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `npm test -- tests/api && npm run typecheck && npm test -- tests/arquitetura.test.ts`
Expected: todos passam.

- [ ] **Step 5: Commit**

```bash
cd ..
git add frontend/api/pedidos.ts frontend/tests/builders.ts frontend/tests/api/pedidos.test.ts
git commit -m "feat(frontend): contrato GET /api/v1/pedidos validado com Zod" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
cd frontend
```

---

### Task 5: Regras de negócio dos pedidos (`services/pedidos.ts`)

**Files:**
- Create: `frontend/services/pedidos.ts`
- Test: `frontend/tests/services/pedidos.test.ts`
- Modify: `frontend/tests/arquitetura.test.ts` (só a lista `PASTAS_QUE_DEVEM_EXISTIR`)

**Interfaces:**
- Consumes: `listarPedidos`, `PedidoDto` de `@/api/pedidos`; `umPedidoDto` de `@/tests/builders`.
- Produces (todos em `@/services/pedidos`):
  - `type TipoVeiculo`, `type StatusPedido`, `type ResultadoPedido`
  - `type Pedido = { id: string; veiculo: PedidoDto['veiculo']; descricao: string; status: StatusPedido; resultado: ResultadoPedido | null; criadoEm: Date; expiraEm: Date; totalConversas: number; totalPropostas: number; menorPropostaEmCentavos: number | null }`
  - `paraPedido(dto: PedidoDto): Pedido`
  - `FILTROS_PEDIDOS = ['TODOS','ABERTOS','EM_NEGOCIACAO','CONCLUIDOS','ENCERRADOS'] as const` e `type FiltroPedidos`
  - `filtrarPedidos(pedidos: readonly Pedido[], filtro: FiltroPedidos): Pedido[]` (`ENCERRADOS` = cancelados + expirados)
  - `ordenarPorMaisRecente(pedidos: readonly Pedido[]): Pedido[]` (não altera a entrada)
  - `type ResumoDosPedidos = { porFiltro: Record<FiltroPedidos, number>; conversasEmAndamento: number; propostasEmAndamento: number }` (os dois totais contam só pedidos `ABERTO` e `EM_NEGOCIACAO`)
  - `resumirPedidos(pedidos: readonly Pedido[]): ResumoDosPedidos`
  - `type SituacaoValidade = { tipo: 'nao-se-aplica' } | { tipo: 'expirado' } | { tipo: 'menos-de-um-dia' } | { tipo: 'em-dias'; dias: number }`
  - `situacaoDeValidade(pedido: Pedido, agora: Date): SituacaoValidade`
  - `carregarPedidos(): Promise<Pedido[]>` (busca, converte e ordena)

- [ ] **Step 1: Escrever os testes**

Create `frontend/tests/services/pedidos.test.ts`:
```ts
import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { servidor } from '@/mocks/servidor'
import { umPedidoDto } from '@/tests/builders'
import {
  carregarPedidos,
  filtrarPedidos,
  ordenarPorMaisRecente,
  paraPedido,
  resumirPedidos,
  situacaoDeValidade,
  type Pedido,
} from '@/services/pedidos'

const AGORA = new Date('2026-09-29T12:00:00Z')
const HORA = 60 * 60 * 1000

function umPedido(sobrescritas: Partial<Pedido> = {}): Pedido {
  return { ...paraPedido(umPedidoDto()), ...sobrescritas }
}

function expirandoEm(horas: number): Date {
  return new Date(AGORA.getTime() + horas * HORA)
}

describe('paraPedido', () => {
  it('converte as datas para Date', () => {
    const pedido = paraPedido(
      umPedidoDto({ criadoEm: '2026-09-29T12:00:00Z', expiraEm: '2026-10-06T12:00:00Z' }),
    )

    expect(pedido.criadoEm).toEqual(new Date('2026-09-29T12:00:00Z'))
    expect(pedido.expiraEm).toEqual(new Date('2026-10-06T12:00:00Z'))
  })

  it('transforma resultado e menor proposta ausentes em null', () => {
    const pedido = paraPedido({
      ...umPedidoDto(),
      resultado: undefined,
      menorPropostaEmCentavos: undefined,
    })

    expect(pedido.resultado).toBeNull()
    expect(pedido.menorPropostaEmCentavos).toBeNull()
  })

  it('preserva o valor 0 da menor proposta (não vira null)', () => {
    expect(paraPedido(umPedidoDto({ menorPropostaEmCentavos: 0 })).menorPropostaEmCentavos).toBe(0)
  })
})

describe('ordenarPorMaisRecente', () => {
  it('põe o pedido criado por último primeiro, sem alterar a lista original', () => {
    const antigo = umPedido({ id: 'antigo', criadoEm: new Date('2026-09-01T00:00:00Z') })
    const novo = umPedido({ id: 'novo', criadoEm: new Date('2026-09-20T00:00:00Z') })
    const meio = umPedido({ id: 'meio', criadoEm: new Date('2026-09-10T00:00:00Z') })
    const original = [antigo, novo, meio]

    const ordenados = ordenarPorMaisRecente(original)

    expect(ordenados.map((p) => p.id)).toEqual(['novo', 'meio', 'antigo'])
    expect(original.map((p) => p.id)).toEqual(['antigo', 'novo', 'meio'])
  })
})

describe('filtrarPedidos', () => {
  const pedidos = [
    umPedido({ id: 'a', status: 'ABERTO' }),
    umPedido({ id: 'n', status: 'EM_NEGOCIACAO' }),
    umPedido({ id: 'c', status: 'CONCLUIDO', resultado: 'VENDIDO' }),
    umPedido({ id: 'x', status: 'CANCELADO' }),
    umPedido({ id: 'e', status: 'EXPIRADO' }),
  ]
  const ids = (lista: Pedido[]) => lista.map((p) => p.id)

  it('TODOS devolve uma cópia com tudo', () => {
    const resultado = filtrarPedidos(pedidos, 'TODOS')

    expect(ids(resultado)).toEqual(['a', 'n', 'c', 'x', 'e'])
    expect(resultado).not.toBe(pedidos)
  })

  it.each([
    ['ABERTOS', ['a']],
    ['EM_NEGOCIACAO', ['n']],
    ['CONCLUIDOS', ['c']],
    ['ENCERRADOS', ['x', 'e']],
  ] as const)('%s devolve só os pedidos do grupo', (filtro, esperados) => {
    expect(ids(filtrarPedidos(pedidos, filtro))).toEqual(esperados)
  })

  it('devolve lista vazia quando nenhum pedido combina', () => {
    expect(filtrarPedidos([umPedido({ status: 'ABERTO' })], 'CONCLUIDOS')).toEqual([])
  })
})

describe('resumirPedidos', () => {
  it('conta por grupo e soma conversas e propostas só dos pedidos em andamento', () => {
    const resumo = resumirPedidos([
      umPedido({ status: 'ABERTO', totalConversas: 3, totalPropostas: 0 }),
      umPedido({ status: 'EM_NEGOCIACAO', totalConversas: 2, totalPropostas: 3 }),
      umPedido({ status: 'CONCLUIDO', resultado: 'VENDIDO', totalConversas: 4, totalPropostas: 5 }),
      umPedido({ status: 'CANCELADO', totalConversas: 1, totalPropostas: 1 }),
      umPedido({ status: 'EXPIRADO', totalConversas: 6, totalPropostas: 2 }),
    ])

    expect(resumo.porFiltro).toEqual({
      TODOS: 5,
      ABERTOS: 1,
      EM_NEGOCIACAO: 1,
      CONCLUIDOS: 1,
      ENCERRADOS: 2,
    })
    expect(resumo.conversasEmAndamento).toBe(5)
    expect(resumo.propostasEmAndamento).toBe(3)
  })

  it('devolve tudo zerado para lista vazia', () => {
    expect(resumirPedidos([])).toEqual({
      porFiltro: { TODOS: 0, ABERTOS: 0, EM_NEGOCIACAO: 0, CONCLUIDOS: 0, ENCERRADOS: 0 },
      conversasEmAndamento: 0,
      propostasEmAndamento: 0,
    })
  })
})

describe('situacaoDeValidade', () => {
  it('pedido com status Expirado é expirado', () => {
    expect(situacaoDeValidade(umPedido({ status: 'EXPIRADO' }), AGORA)).toEqual({ tipo: 'expirado' })
  })

  it.each(['CONCLUIDO', 'CANCELADO'] as const)('%s não tem validade a mostrar', (status) => {
    expect(situacaoDeValidade(umPedido({ status }), AGORA)).toEqual({ tipo: 'nao-se-aplica' })
  })

  it('conta dias inteiros restantes (7 dias exatos)', () => {
    const pedido = umPedido({ status: 'ABERTO', expiraEm: expirandoEm(7 * 24) })

    expect(situacaoDeValidade(pedido, AGORA)).toEqual({ tipo: 'em-dias', dias: 7 })
  })

  it('arredonda para baixo (25 horas são 1 dia)', () => {
    const pedido = umPedido({ status: 'EM_NEGOCIACAO', expiraEm: expirandoEm(25) })

    expect(situacaoDeValidade(pedido, AGORA)).toEqual({ tipo: 'em-dias', dias: 1 })
  })

  it('menos de 24 horas restantes é menos de um dia', () => {
    const pedido = umPedido({ status: 'ABERTO', expiraEm: expirandoEm(23) })

    expect(situacaoDeValidade(pedido, AGORA)).toEqual({ tipo: 'menos-de-um-dia' })
  })

  it('pedido ainda Aberto cuja validade já passou é tratado como expirado (job atrasado)', () => {
    const pedido = umPedido({ status: 'ABERTO', expiraEm: expirandoEm(-2) })

    expect(situacaoDeValidade(pedido, AGORA)).toEqual({ tipo: 'expirado' })
  })

  it('no exato instante da validade já é expirado', () => {
    const pedido = umPedido({ status: 'ABERTO', expiraEm: new Date(AGORA) })

    expect(situacaoDeValidade(pedido, AGORA)).toEqual({ tipo: 'expirado' })
  })

  it('data inválida não vira NaN: a validade simplesmente não se aplica', () => {
    const pedido = umPedido({ status: 'ABERTO', expiraEm: new Date('invalida') })

    expect(situacaoDeValidade(pedido, AGORA)).toEqual({ tipo: 'nao-se-aplica' })
  })
})

describe('carregarPedidos', () => {
  const URL_PEDIDOS = 'http://localhost:8080/api/v1/pedidos'

  it('busca, converte e ordena do mais recente para o mais antigo', async () => {
    servidor.use(
      http.get(URL_PEDIDOS, () =>
        HttpResponse.json({
          itens: [
            umPedidoDto({ id: 'antigo', criadoEm: '2026-09-01T00:00:00Z' }),
            umPedidoDto({ id: 'novo', criadoEm: '2026-09-20T00:00:00Z' }),
          ],
        }),
      ),
    )

    const pedidos = await carregarPedidos()

    expect(pedidos.map((p) => p.id)).toEqual(['novo', 'antigo'])
    expect(pedidos[0]?.criadoEm).toBeInstanceOf(Date)
  })

  it('propaga o erro da API', async () => {
    servidor.use(
      http.get(URL_PEDIDOS, () => HttpResponse.json({ title: 'Erro interno' }, { status: 500 })),
    )

    await expect(carregarPedidos()).rejects.toMatchObject({ tipo: 'http', status: 500 })
  })
})
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `npm test -- tests/services/pedidos.test.ts`
Expected: FAIL com `Failed to resolve import "@/services/pedidos"`.

- [ ] **Step 3: Implementar**

Create `frontend/services/pedidos.ts`:
```ts
import { listarPedidos, type PedidoDto } from '@/api/pedidos'

export type TipoVeiculo = PedidoDto['veiculo']['tipo']
export type StatusPedido = PedidoDto['status']
export type ResultadoPedido = NonNullable<PedidoDto['resultado']>

export type Pedido = {
  id: string
  veiculo: PedidoDto['veiculo']
  descricao: string
  status: StatusPedido
  resultado: ResultadoPedido | null
  criadoEm: Date
  expiraEm: Date
  totalConversas: number
  totalPropostas: number
  menorPropostaEmCentavos: number | null
}

export function paraPedido(dto: PedidoDto): Pedido {
  return {
    id: dto.id,
    veiculo: dto.veiculo,
    descricao: dto.descricao,
    status: dto.status,
    resultado: dto.resultado ?? null,
    criadoEm: new Date(dto.criadoEm),
    expiraEm: new Date(dto.expiraEm),
    totalConversas: dto.totalConversas,
    totalPropostas: dto.totalPropostas,
    menorPropostaEmCentavos: dto.menorPropostaEmCentavos ?? null,
  }
}

export const FILTROS_PEDIDOS = ['TODOS', 'ABERTOS', 'EM_NEGOCIACAO', 'CONCLUIDOS', 'ENCERRADOS'] as const
export type FiltroPedidos = (typeof FILTROS_PEDIDOS)[number]

const STATUS_DO_FILTRO: Record<Exclude<FiltroPedidos, 'TODOS'>, readonly StatusPedido[]> = {
  ABERTOS: ['ABERTO'],
  EM_NEGOCIACAO: ['EM_NEGOCIACAO'],
  CONCLUIDOS: ['CONCLUIDO'],
  ENCERRADOS: ['CANCELADO', 'EXPIRADO'],
}

// Só estes status têm conversas e propostas ainda em andamento. Nos demais elas viram somente leitura.
const STATUS_EM_ANDAMENTO: readonly StatusPedido[] = ['ABERTO', 'EM_NEGOCIACAO']

export function filtrarPedidos(pedidos: readonly Pedido[], filtro: FiltroPedidos): Pedido[] {
  if (filtro === 'TODOS') return [...pedidos]
  const aceitos = STATUS_DO_FILTRO[filtro]
  return pedidos.filter((pedido) => aceitos.includes(pedido.status))
}

export function ordenarPorMaisRecente(pedidos: readonly Pedido[]): Pedido[] {
  return [...pedidos].sort((a, b) => b.criadoEm.getTime() - a.criadoEm.getTime())
}

export type ResumoDosPedidos = {
  porFiltro: Record<FiltroPedidos, number>
  conversasEmAndamento: number
  propostasEmAndamento: number
}

export function resumirPedidos(pedidos: readonly Pedido[]): ResumoDosPedidos {
  const emAndamento = pedidos.filter((pedido) => STATUS_EM_ANDAMENTO.includes(pedido.status))
  const porFiltro = Object.fromEntries(
    FILTROS_PEDIDOS.map((filtro) => [filtro, filtrarPedidos(pedidos, filtro).length]),
  ) as Record<FiltroPedidos, number>

  return {
    porFiltro,
    conversasEmAndamento: emAndamento.reduce((soma, pedido) => soma + pedido.totalConversas, 0),
    propostasEmAndamento: emAndamento.reduce((soma, pedido) => soma + pedido.totalPropostas, 0),
  }
}

export type SituacaoValidade =
  | { tipo: 'nao-se-aplica' }
  | { tipo: 'expirado' }
  | { tipo: 'menos-de-um-dia' }
  | { tipo: 'em-dias'; dias: number }

const UM_DIA_EM_MS = 24 * 60 * 60 * 1000

export function situacaoDeValidade(pedido: Pedido, agora: Date): SituacaoValidade {
  if (pedido.status === 'EXPIRADO') return { tipo: 'expirado' }
  if (!STATUS_EM_ANDAMENTO.includes(pedido.status)) return { tipo: 'nao-se-aplica' }

  const restante = pedido.expiraEm.getTime() - agora.getTime()
  if (Number.isNaN(restante)) return { tipo: 'nao-se-aplica' }
  if (restante <= 0) return { tipo: 'expirado' }
  if (restante < UM_DIA_EM_MS) return { tipo: 'menos-de-um-dia' }
  return { tipo: 'em-dias', dias: Math.floor(restante / UM_DIA_EM_MS) }
}

export async function carregarPedidos(): Promise<Pedido[]> {
  const { itens } = await listarPedidos()
  return ordenarPorMaisRecente(itens.map(paraPedido))
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `npm test -- tests/services/pedidos.test.ts && npm run typecheck`
Expected: todos passam.

- [ ] **Step 5: Fazer o teste de arquitetura exigir as duas pastas novas**

Modify `frontend/tests/arquitetura.test.ts`: troque a linha
```ts
const PASTAS_QUE_DEVEM_EXISTIR = ['app', 'app/components/ui']
```
por
```ts
const PASTAS_QUE_DEVEM_EXISTIR = ['app', 'app/components/ui', 'api', 'services']
```
Run: `npm test -- tests/arquitetura.test.ts`
Expected: PASS. A regra 3 agora vale de verdade: `services/` não importa `app`, React, Next nem TanStack.

- [ ] **Step 6: Commit**

```bash
cd ..
git add frontend/services frontend/tests/services frontend/tests/arquitetura.test.ts
git commit -m "feat(frontend): regras de negócio dos pedidos (resumo, filtro, ordem e validade)" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
cd frontend
```

---

### Task 6: Formatação e mensagens de erro (`services/formatacao.ts`, `services/erros.ts`)

**Files:**
- Create: `frontend/services/formatacao.ts`, `frontend/services/erros.ts`
- Test: `frontend/tests/services/formatacao.test.ts`, `frontend/tests/services/erros.test.ts`

**Interfaces:**
- Consumes: `Pedido`, `SituacaoValidade`, `FiltroPedidos`, `TipoVeiculo` de `@/services/pedidos`; `ApiError` de `@/api/cliente`.
- Produces:
  - `formatarMoeda(centavos: number): string` → `"R$\u00a01.234,50"`
  - `rotuloDoStatus(pedido: Pick<Pedido, 'status' | 'resultado'>): string`
  - `rotuloDoTipoVeiculo(tipo: TipoVeiculo): string`
  - `textoDoVeiculo(veiculo: Pedido['veiculo']): string` → `"Volkswagen Gol 2014"`
  - `pluralizar(quantidade: number, singular: string, plural: string): string` → `"1 conversa"`, `"0 conversas"`
  - `textoDeAtividade(pedido: Pick<Pedido, 'totalConversas' | 'totalPropostas'>): string` → `"3 conversas · 2 propostas"`
  - `textoDaMenorProposta(centavos: number | null): string`
  - `textoDaValidade(situacao: SituacaoValidade): string | null`
  - `ROTULO_FILTRO: Record<FiltroPedidos, string>`
  - `type MensagemDeErro = { texto: string; podeTentarDeNovo: boolean }` e `mensagemDeErro(erro: unknown): MensagemDeErro`

- [ ] **Step 1: Escrever os testes**

Create `frontend/tests/services/formatacao.test.ts`:
```ts
import { describe, expect, it } from 'vitest'
import {
  ROTULO_FILTRO,
  formatarMoeda,
  pluralizar,
  rotuloDoStatus,
  rotuloDoTipoVeiculo,
  textoDaMenorProposta,
  textoDaValidade,
  textoDeAtividade,
  textoDoVeiculo,
} from '@/services/formatacao'

// O Intl usa espaço não separável (U+00A0) entre "R$" e o valor.
const NBSP = '\u00a0'

describe('formatarMoeda', () => {
  it.each([
    [123450, `R$${NBSP}1.234,50`],
    [0, `R$${NBSP}0,00`],
    [5, `R$${NBSP}0,05`],
    [1234567890, `R$${NBSP}12.345.678,90`],
  ])('%i centavos viram %s', (centavos, esperado) => {
    expect(formatarMoeda(centavos)).toBe(esperado)
  })
})

describe('rotuloDoStatus', () => {
  it.each([
    [{ status: 'ABERTO', resultado: null }, 'Aberto'],
    [{ status: 'EM_NEGOCIACAO', resultado: null }, 'Em negociação'],
    [{ status: 'CONCLUIDO', resultado: 'VENDIDO' }, 'Vendido'],
    [{ status: 'CONCLUIDO', resultado: 'SEM_COMPRA' }, 'Concluído sem compra'],
    [{ status: 'CONCLUIDO', resultado: null }, 'Concluído'],
    [{ status: 'CANCELADO', resultado: null }, 'Cancelado'],
    [{ status: 'EXPIRADO', resultado: null }, 'Expirado'],
  ] as const)('%j → %s', (pedido, esperado) => {
    expect(rotuloDoStatus(pedido)).toBe(esperado)
  })
})

describe('rotuloDoTipoVeiculo e textoDoVeiculo', () => {
  it('traduz o tipo de veículo', () => {
    expect(rotuloDoTipoVeiculo('CARRO')).toBe('Carro')
    expect(rotuloDoTipoVeiculo('MOTO')).toBe('Moto')
    expect(rotuloDoTipoVeiculo('CAMINHAO')).toBe('Caminhão')
  })

  it('junta marca, modelo e ano', () => {
    const veiculo = { tipo: 'CARRO', marca: 'Volkswagen', modelo: 'Gol', versao: '1.0', ano: 2014 } as const

    expect(textoDoVeiculo(veiculo)).toBe('Volkswagen Gol 2014')
  })
})

describe('pluralizar e textoDeAtividade', () => {
  it.each([
    [0, '0 conversas'],
    [1, '1 conversa'],
    [2, '2 conversas'],
  ])('%i → %s', (quantidade, esperado) => {
    expect(pluralizar(quantidade, 'conversa', 'conversas')).toBe(esperado)
  })

  it.each([
    [{ totalConversas: 0, totalPropostas: 0 }, '0 conversas · 0 propostas'],
    [{ totalConversas: 1, totalPropostas: 1 }, '1 conversa · 1 proposta'],
    [{ totalConversas: 3, totalPropostas: 2 }, '3 conversas · 2 propostas'],
  ])('%j → %s', (pedido, esperado) => {
    expect(textoDeAtividade(pedido)).toBe(esperado)
  })
})

describe('textoDaMenorProposta', () => {
  it('sem propostas', () => {
    expect(textoDaMenorProposta(null)).toBe('Sem propostas ainda')
  })

  it('mostra o valor em reais', () => {
    expect(textoDaMenorProposta(123450)).toBe(`Menor proposta: R$${NBSP}1.234,50`)
  })

  it('proposta de R$ 0,00 NÃO é "sem propostas" (zero é um valor)', () => {
    expect(textoDaMenorProposta(0)).toBe(`Menor proposta: R$${NBSP}0,00`)
  })
})

describe('textoDaValidade', () => {
  it.each([
    [{ tipo: 'nao-se-aplica' }, null],
    [{ tipo: 'expirado' }, 'Expirado'],
    [{ tipo: 'menos-de-um-dia' }, 'Expira em menos de 1 dia'],
    [{ tipo: 'em-dias', dias: 1 }, 'Expira em 1 dia'],
    [{ tipo: 'em-dias', dias: 7 }, 'Expira em 7 dias'],
  ] as const)('%j → %s', (situacao, esperado) => {
    expect(textoDaValidade(situacao)).toBe(esperado)
  })
})

describe('ROTULO_FILTRO', () => {
  it('tem um rótulo para cada filtro', () => {
    expect(ROTULO_FILTRO).toEqual({
      TODOS: 'Todos',
      ABERTOS: 'Abertos',
      EM_NEGOCIACAO: 'Em negociação',
      CONCLUIDOS: 'Concluídos',
      ENCERRADOS: 'Encerrados',
    })
  })
})
```

Create `frontend/tests/services/erros.test.ts`:
```ts
import { describe, expect, it } from 'vitest'
import { ApiError } from '@/api/cliente'
import { mensagemDeErro } from '@/services/erros'

describe('mensagemDeErro', () => {
  it('401: sessão expirada e não adianta tentar de novo', () => {
    expect(mensagemDeErro(new ApiError('http', 401, 'Não autenticado'))).toEqual({
      texto: 'Sua sessão expirou. Entre novamente para ver seus pedidos.',
      podeTentarDeNovo: false,
    })
  })

  it('rede: pede para verificar a conexão e permite tentar de novo', () => {
    expect(mensagemDeErro(new ApiError('rede', 0, 'x'))).toEqual({
      texto: 'Não foi possível conectar ao servidor. Verifique sua conexão.',
      podeTentarDeNovo: true,
    })
  })

  it('contrato: resposta inesperada, permite tentar de novo', () => {
    expect(mensagemDeErro(new ApiError('contrato', 200, 'x'))).toEqual({
      texto: 'O servidor respondeu de um jeito inesperado. Tente novamente em instantes.',
      podeTentarDeNovo: true,
    })
  })

  it.each([
    ['500 do servidor', new ApiError('http', 500, 'Erro interno')],
    ['Error comum', new Error('falhou')],
    ['valor desconhecido', undefined],
  ])('%s: mensagem genérica e permite tentar de novo', (_nome, erro) => {
    expect(mensagemDeErro(erro)).toEqual({
      texto: 'Não foi possível carregar seus pedidos agora.',
      podeTentarDeNovo: true,
    })
  })

  it('nunca expõe a mensagem técnica do servidor ao usuário', () => {
    const { texto } = mensagemDeErro(new ApiError('http', 500, 'NullPointerException em PedidoService'))

    expect(texto).not.toContain('NullPointerException')
  })
})
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `npm test -- tests/services/formatacao.test.ts tests/services/erros.test.ts`
Expected: FAIL com `Failed to resolve import "@/services/formatacao"` (e `@/services/erros`).

- [ ] **Step 3: Implementar**

Create `frontend/services/formatacao.ts`:
```ts
import type { FiltroPedidos, Pedido, SituacaoValidade, TipoVeiculo } from '@/services/pedidos'

const MOEDA = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

export function formatarMoeda(centavos: number): string {
  return MOEDA.format(centavos / 100)
}

export function rotuloDoStatus(pedido: Pick<Pedido, 'status' | 'resultado'>): string {
  switch (pedido.status) {
    case 'ABERTO':
      return 'Aberto'
    case 'EM_NEGOCIACAO':
      return 'Em negociação'
    case 'CANCELADO':
      return 'Cancelado'
    case 'EXPIRADO':
      return 'Expirado'
    case 'CONCLUIDO':
      if (pedido.resultado === 'VENDIDO') return 'Vendido'
      if (pedido.resultado === 'SEM_COMPRA') return 'Concluído sem compra'
      return 'Concluído'
  }
}

const ROTULO_TIPO_VEICULO: Record<TipoVeiculo, string> = {
  CARRO: 'Carro',
  MOTO: 'Moto',
  CAMINHAO: 'Caminhão',
}

export function rotuloDoTipoVeiculo(tipo: TipoVeiculo): string {
  return ROTULO_TIPO_VEICULO[tipo]
}

export function textoDoVeiculo(veiculo: Pedido['veiculo']): string {
  return `${veiculo.marca} ${veiculo.modelo} ${veiculo.ano}`
}

export function pluralizar(quantidade: number, singular: string, plural: string): string {
  return `${quantidade} ${quantidade === 1 ? singular : plural}`
}

export function textoDeAtividade(pedido: Pick<Pedido, 'totalConversas' | 'totalPropostas'>): string {
  return `${pluralizar(pedido.totalConversas, 'conversa', 'conversas')} · ${pluralizar(pedido.totalPropostas, 'proposta', 'propostas')}`
}

export function textoDaMenorProposta(centavos: number | null): string {
  // Atenção: 0 é um valor válido. Por isso a comparação é com null, não com "falsy".
  return centavos === null ? 'Sem propostas ainda' : `Menor proposta: ${formatarMoeda(centavos)}`
}

export function textoDaValidade(situacao: SituacaoValidade): string | null {
  switch (situacao.tipo) {
    case 'nao-se-aplica':
      return null
    case 'expirado':
      return 'Expirado'
    case 'menos-de-um-dia':
      return 'Expira em menos de 1 dia'
    case 'em-dias':
      return situacao.dias === 1 ? 'Expira em 1 dia' : `Expira em ${situacao.dias} dias`
  }
}

export const ROTULO_FILTRO: Record<FiltroPedidos, string> = {
  TODOS: 'Todos',
  ABERTOS: 'Abertos',
  EM_NEGOCIACAO: 'Em negociação',
  CONCLUIDOS: 'Concluídos',
  ENCERRADOS: 'Encerrados',
}
```

Create `frontend/services/erros.ts`:
```ts
import { ApiError } from '@/api/cliente'

export type MensagemDeErro = { texto: string; podeTentarDeNovo: boolean }

const GENERICA: MensagemDeErro = {
  texto: 'Não foi possível carregar seus pedidos agora.',
  podeTentarDeNovo: true,
}

// A mensagem técnica do servidor nunca vai para a tela: o usuário vê só o texto abaixo.
export function mensagemDeErro(erro: unknown): MensagemDeErro {
  if (erro instanceof ApiError) {
    if (erro.tipo === 'http' && erro.status === 401) {
      return {
        texto: 'Sua sessão expirou. Entre novamente para ver seus pedidos.',
        podeTentarDeNovo: false,
      }
    }
    if (erro.tipo === 'rede') {
      return {
        texto: 'Não foi possível conectar ao servidor. Verifique sua conexão.',
        podeTentarDeNovo: true,
      }
    }
    if (erro.tipo === 'contrato') {
      return {
        texto: 'O servidor respondeu de um jeito inesperado. Tente novamente em instantes.',
        podeTentarDeNovo: true,
      }
    }
  }
  return GENERICA
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `npm test -- tests/services && npm run typecheck && npm test -- tests/arquitetura.test.ts`
Expected: todos passam. `services/erros.ts` importa `@/api/cliente`, e isso é permitido. `formatacao.ts` importa só tipos de `@/services/pedidos`.

- [ ] **Step 5: Commit**

```bash
cd ..
git add frontend/services frontend/tests/services
git commit -m "feat(frontend): formatação em pt-BR e mensagens de erro no service" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
cd frontend
```

---

### Task 7: Componentes da lista (`StatusBadge`, `PedidoItem`, `ListaPedidos`)

**Files:**
- Create: `frontend/app/components/dashboard/StatusBadge.tsx`, `frontend/app/components/dashboard/PedidoItem.tsx`, `frontend/app/components/dashboard/ListaPedidos.tsx`
- Test: `frontend/tests/app/components/dashboard/lista.test.tsx`

**Interfaces:**
- Consumes: `Badge`, `Card*` de `@/app/components/ui/*`; `Pedido`, `situacaoDeValidade` de `@/services/pedidos`; funções de `@/services/formatacao`; `umPedidoDto`, `paraPedido`.
- Produces:
  - `StatusBadge({ pedido }: { pedido: Pick<Pedido, 'status' | 'resultado'> })`
  - `PedidoItem({ pedido, agora }: { pedido: Pedido; agora: Date })` renderiza um `<li>`
  - `ListaPedidos({ pedidos, agora }: { pedidos: Pedido[]; agora: Date })` renderiza `<ul aria-label="Lista de pedidos">` ou, se vazia, o texto `Nenhum pedido neste filtro.`

- [ ] **Step 1: Escrever os testes**

Create `frontend/tests/app/components/dashboard/lista.test.tsx`:
```tsx
import { describe, expect, it } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import { axe } from '@/tests/axe'
import { umPedidoDto } from '@/tests/builders'
import { paraPedido, type Pedido } from '@/services/pedidos'
import { StatusBadge } from '@/app/components/dashboard/StatusBadge'
import { PedidoItem } from '@/app/components/dashboard/PedidoItem'
import { ListaPedidos } from '@/app/components/dashboard/ListaPedidos'

const AGORA = new Date('2026-09-29T12:00:00Z')
const NBSP = '\u00a0'

function umPedido(sobrescritas: Parameters<typeof umPedidoDto>[0] = {}): Pedido {
  return paraPedido(umPedidoDto(sobrescritas))
}

describe('StatusBadge', () => {
  it.each([
    [{ status: 'ABERTO', resultado: null }, 'Aberto'],
    [{ status: 'CONCLUIDO', resultado: 'VENDIDO' }, 'Vendido'],
    [{ status: 'CONCLUIDO', resultado: 'SEM_COMPRA' }, 'Concluído sem compra'],
  ] as const)('%j mostra "%s"', (pedido, rotulo) => {
    render(<StatusBadge pedido={pedido} />)

    expect(screen.getByText(rotulo)).toBeInTheDocument()
  })
})

describe('PedidoItem', () => {
  it('mostra peça, veículo, atividade, menor proposta, validade e status', () => {
    const pedido = umPedido({
      descricao: 'Retrovisor direito',
      veiculo: { tipo: 'MOTO', marca: 'Honda', modelo: 'CG 160 Titan', versao: 'Flex', ano: 2020 },
      status: 'EM_NEGOCIACAO',
      totalConversas: 4,
      totalPropostas: 2,
      menorPropostaEmCentavos: 8990,
      expiraEm: '2026-10-02T12:00:00Z',
    })

    render(<ul><PedidoItem pedido={pedido} agora={AGORA} /></ul>)

    const item = screen.getByRole('listitem')
    expect(within(item).getByText('Retrovisor direito')).toBeInTheDocument()
    expect(within(item).getByText('Moto · Honda CG 160 Titan 2020 · Flex')).toBeInTheDocument()
    expect(within(item).getByText('4 conversas · 2 propostas')).toBeInTheDocument()
    expect(within(item).getByText(`Menor proposta: R$${NBSP}89,90`)).toBeInTheDocument()
    expect(within(item).getByText('Expira em 3 dias')).toBeInTheDocument()
    expect(within(item).getByText('Em negociação')).toBeInTheDocument()
  })

  it('pedido sem propostas mostra "Sem propostas ainda"', () => {
    render(<ul><PedidoItem pedido={umPedido()} agora={AGORA} /></ul>)

    expect(screen.getByText('Sem propostas ainda')).toBeInTheDocument()
  })

  it('pedido concluído não mostra validade', () => {
    const pedido = umPedido({ status: 'CONCLUIDO', resultado: 'VENDIDO', menorPropostaEmCentavos: 21000 })

    render(<ul><PedidoItem pedido={pedido} agora={AGORA} /></ul>)

    expect(screen.queryByText(/Expira/)).not.toBeInTheDocument()
    expect(screen.queryByText('Expirado')).not.toBeInTheDocument()
    expect(screen.getByText('Vendido')).toBeInTheDocument()
  })

  it('pedido Aberto com validade vencida aparece como Expirado', () => {
    const pedido = umPedido({ status: 'ABERTO', expiraEm: '2026-09-28T12:00:00Z' })

    render(<ul><PedidoItem pedido={pedido} agora={AGORA} /></ul>)

    expect(screen.getByText('Expirado')).toBeInTheDocument()
  })
})

describe('ListaPedidos', () => {
  it('renderiza um item por pedido, na ordem recebida', () => {
    const pedidos = [
      umPedido({ id: 'a', descricao: 'Farol esquerdo' }),
      umPedido({ id: 'b', descricao: 'Amortecedor dianteiro' }),
    ]

    render(<ListaPedidos pedidos={pedidos} agora={AGORA} />)

    const itens = screen.getAllByRole('listitem')
    expect(itens).toHaveLength(2)
    expect(itens[0]).toHaveTextContent('Farol esquerdo')
    expect(itens[1]).toHaveTextContent('Amortecedor dianteiro')
  })

  it('lista vazia mostra a mensagem do filtro, sem <ul>', () => {
    render(<ListaPedidos pedidos={[]} agora={AGORA} />)

    expect(screen.getByText('Nenhum pedido neste filtro.')).toBeInTheDocument()
    expect(screen.queryByRole('list')).not.toBeInTheDocument()
  })

  it('não tem violações de acessibilidade', async () => {
    const { container } = render(
      <ListaPedidos
        pedidos={[umPedido({ status: 'EM_NEGOCIACAO', totalConversas: 2, totalPropostas: 1, menorPropostaEmCentavos: 5000 })]}
        agora={AGORA}
      />,
    )

    expect(await axe(container)).toHaveNoViolations()
  })
})
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `npm test -- tests/app/components/dashboard/lista.test.tsx`
Expected: FAIL com `Failed to resolve import "@/app/components/dashboard/StatusBadge"`.

- [ ] **Step 3: Implementar**

Create `frontend/app/components/dashboard/StatusBadge.tsx`:
```tsx
import { Badge } from '@/app/components/ui/badge'
import { rotuloDoStatus } from '@/services/formatacao'
import type { Pedido, StatusPedido } from '@/services/pedidos'

const VARIANTE: Record<StatusPedido, 'default' | 'secondary' | 'outline'> = {
  ABERTO: 'outline',
  EM_NEGOCIACAO: 'default',
  CONCLUIDO: 'secondary',
  CANCELADO: 'outline',
  EXPIRADO: 'outline',
}

export function StatusBadge({ pedido }: { pedido: Pick<Pedido, 'status' | 'resultado'> }) {
  return <Badge variant={VARIANTE[pedido.status]}>{rotuloDoStatus(pedido)}</Badge>
}
```

Create `frontend/app/components/dashboard/PedidoItem.tsx`:
```tsx
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/app/components/ui/card'
import { StatusBadge } from '@/app/components/dashboard/StatusBadge'
import {
  rotuloDoTipoVeiculo,
  textoDaMenorProposta,
  textoDaValidade,
  textoDeAtividade,
  textoDoVeiculo,
} from '@/services/formatacao'
import { situacaoDeValidade, type Pedido } from '@/services/pedidos'

export function PedidoItem({ pedido, agora }: { pedido: Pedido; agora: Date }) {
  const validade = textoDaValidade(situacaoDeValidade(pedido, agora))
  const { veiculo } = pedido

  return (
    <li>
      <Card>
        <CardHeader>
          <CardTitle>{pedido.descricao}</CardTitle>
          <CardDescription>
            {`${rotuloDoTipoVeiculo(veiculo.tipo)} · ${textoDoVeiculo(veiculo)} · ${veiculo.versao}`}
          </CardDescription>
          <CardAction>
            <StatusBadge pedido={pedido} />
          </CardAction>
        </CardHeader>
        <CardContent className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-muted-foreground">
          <span>{textoDeAtividade(pedido)}</span>
          <span>{textoDaMenorProposta(pedido.menorPropostaEmCentavos)}</span>
          {validade !== null && <span>{validade}</span>}
        </CardContent>
      </Card>
    </li>
  )
}
```

Create `frontend/app/components/dashboard/ListaPedidos.tsx`:
```tsx
import { PedidoItem } from '@/app/components/dashboard/PedidoItem'
import type { Pedido } from '@/services/pedidos'

export function ListaPedidos({ pedidos, agora }: { pedidos: Pedido[]; agora: Date }) {
  if (pedidos.length === 0) {
    return <p className="text-sm text-muted-foreground">Nenhum pedido neste filtro.</p>
  }

  return (
    <ul aria-label="Lista de pedidos" className="grid gap-3">
      {pedidos.map((pedido) => (
        <PedidoItem key={pedido.id} pedido={pedido} agora={agora} />
      ))}
    </ul>
  )
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `npm test -- tests/app/components/dashboard/lista.test.tsx && npm run typecheck && npm run lint && npm test -- tests/arquitetura.test.ts`
Expected: todos passam. A regra de arquitetura confirma que `app/` importa `@/services`, e não `@/api`.

- [ ] **Step 5: Commit**

```bash
cd ..
git add frontend/app/components/dashboard frontend/tests/app
git commit -m "feat(frontend): componentes da lista de pedidos (StatusBadge, PedidoItem, ListaPedidos)" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
cd frontend
```

---

### Task 8: Componentes de resumo, filtro e estados

**Files:**
- Create: `frontend/app/components/dashboard/ResumoPedidos.tsx`, `FiltroStatus.tsx`, `EstadoVazio.tsx`, `EstadoErro.tsx`, `CarregandoPedidos.tsx`, `CabecalhoDashboard.tsx` (todos em `frontend/app/components/dashboard/`)
- Test: `frontend/tests/app/components/dashboard/painel.test.tsx`

**Interfaces:**
- Consumes: `ResumoDosPedidos`, `FiltroPedidos`, `FILTROS_PEDIDOS` de `@/services/pedidos`; `ROTULO_FILTRO` de `@/services/formatacao`; `mensagemDeErro` de `@/services/erros`; `Alert*`, `Button`, `Card*`, `Skeleton` do shadcn.
- Produces:
  - `ResumoPedidos({ resumo }: { resumo: ResumoDosPedidos })` renderiza `<section aria-label="Resumo dos pedidos">` com quatro indicadores (`<dl>` com `<dt>` rótulo e `<dd>` valor): **Abertos**, **Em negociação**, **Conversas em andamento**, **Propostas em andamento**
  - `FiltroStatus({ valor, contagens, aoMudar })` renderiza `<div role="group" aria-label="Filtrar pedidos por status">` com um `<button aria-pressed>` por filtro, com nome `"<Rótulo> (<n>)"`
  - `EstadoVazio()` mostra `Você ainda não tem pedidos.`
  - `EstadoErro({ erro, aoTentarDeNovo, carregando? })` renderiza `role="alert"` com o texto de `mensagemDeErro`. O botão `Tentar novamente` só aparece se `podeTentarDeNovo`. Com `carregando`, o botão fica desabilitado e diz `Tentando novamente…`
  - `CarregandoPedidos()` renderiza `role="status"` com o texto acessível `Carregando pedidos…`
  - `CabecalhoDashboard()` renderiza `<header>` com `<h1>Meus pedidos</h1>`

- [ ] **Step 1: Escrever os testes**

Create `frontend/tests/app/components/dashboard/painel.test.tsx`:
```tsx
import { describe, expect, it, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from '@/tests/axe'
import { ApiError } from '@/api/cliente'
import type { ResumoDosPedidos } from '@/services/pedidos'
import { ResumoPedidos } from '@/app/components/dashboard/ResumoPedidos'
import { FiltroStatus } from '@/app/components/dashboard/FiltroStatus'
import { EstadoVazio } from '@/app/components/dashboard/EstadoVazio'
import { EstadoErro } from '@/app/components/dashboard/EstadoErro'
import { CarregandoPedidos } from '@/app/components/dashboard/CarregandoPedidos'
import { CabecalhoDashboard } from '@/app/components/dashboard/CabecalhoDashboard'

const RESUMO: ResumoDosPedidos = {
  porFiltro: { TODOS: 9, ABERTOS: 3, EM_NEGOCIACAO: 2, CONCLUIDOS: 1, ENCERRADOS: 3 },
  conversasEmAndamento: 11,
  propostasEmAndamento: 4,
}

function valorDoIndicador(rotulo: string): string | null {
  const resumo = screen.getByRole('region', { name: 'Resumo dos pedidos' })
  return within(resumo).getByText(rotulo).closest('dl')?.querySelector('dd')?.textContent ?? null
}

describe('ResumoPedidos', () => {
  it('mostra os quatro indicadores com seus valores', () => {
    render(<ResumoPedidos resumo={RESUMO} />)

    expect(screen.getByRole('region', { name: 'Resumo dos pedidos' })).toBeInTheDocument()
    expect(valorDoIndicador('Abertos')).toBe('3')
    expect(valorDoIndicador('Em negociação')).toBe('2')
    expect(valorDoIndicador('Conversas em andamento')).toBe('11')
    expect(valorDoIndicador('Propostas em andamento')).toBe('4')
  })

  it('não tem violações de acessibilidade', async () => {
    const { container } = render(<ResumoPedidos resumo={RESUMO} />)

    expect(await axe(container)).toHaveNoViolations()
  })
})

describe('FiltroStatus', () => {
  it('mostra um botão por filtro, com a contagem, marcando o selecionado', () => {
    render(<FiltroStatus valor="ABERTOS" contagens={RESUMO.porFiltro} aoMudar={() => {}} />)

    const grupo = screen.getByRole('group', { name: 'Filtrar pedidos por status' })
    const botoes = within(grupo).getAllByRole('button')
    expect(botoes.map((b) => b.textContent)).toEqual([
      'Todos (9)',
      'Abertos (3)',
      'Em negociação (2)',
      'Concluídos (1)',
      'Encerrados (3)',
    ])
    expect(screen.getByRole('button', { name: 'Abertos (3)' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'Todos (9)' })).toHaveAttribute('aria-pressed', 'false')
  })

  it('chama aoMudar com o filtro clicado', async () => {
    const aoMudar = vi.fn()
    render(<FiltroStatus valor="TODOS" contagens={RESUMO.porFiltro} aoMudar={aoMudar} />)

    await userEvent.click(screen.getByRole('button', { name: 'Encerrados (3)' }))

    expect(aoMudar).toHaveBeenCalledExactlyOnceWith('ENCERRADOS')
  })

  it('não tem violações de acessibilidade', async () => {
    const { container } = render(<FiltroStatus valor="TODOS" contagens={RESUMO.porFiltro} aoMudar={() => {}} />)

    expect(await axe(container)).toHaveNoViolations()
  })
})

describe('EstadoVazio', () => {
  it('explica que ainda não há pedidos', () => {
    render(<EstadoVazio />)

    expect(screen.getByText('Você ainda não tem pedidos.')).toBeInTheDocument()
  })
})

describe('EstadoErro', () => {
  it('erro de rede: alerta com botão que chama aoTentarDeNovo', async () => {
    const aoTentarDeNovo = vi.fn()
    render(<EstadoErro erro={new ApiError('rede', 0, 'x')} aoTentarDeNovo={aoTentarDeNovo} />)

    expect(screen.getByRole('alert')).toHaveTextContent(
      'Não foi possível conectar ao servidor. Verifique sua conexão.',
    )
    await userEvent.click(screen.getByRole('button', { name: 'Tentar novamente' }))
    expect(aoTentarDeNovo).toHaveBeenCalledOnce()
  })

  it('sessão expirada (401): mostra a mensagem e NÃO oferece tentar de novo', () => {
    render(<EstadoErro erro={new ApiError('http', 401, 'Não autenticado')} aoTentarDeNovo={() => {}} />)

    expect(screen.getByRole('alert')).toHaveTextContent('Sua sessão expirou.')
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('durante a nova tentativa o botão fica desabilitado e avisa', () => {
    render(<EstadoErro erro={new ApiError('rede', 0, 'x')} aoTentarDeNovo={() => {}} carregando />)

    expect(screen.getByRole('button', { name: 'Tentando novamente…' })).toBeDisabled()
  })

  it('não tem violações de acessibilidade', async () => {
    const { container } = render(<EstadoErro erro={new ApiError('rede', 0, 'x')} aoTentarDeNovo={() => {}} />)

    expect(await axe(container)).toHaveNoViolations()
  })
})

describe('CarregandoPedidos', () => {
  it('anuncia o carregamento para leitores de tela', () => {
    render(<CarregandoPedidos />)

    expect(screen.getByRole('status')).toHaveTextContent('Carregando pedidos…')
  })
})

describe('CabecalhoDashboard', () => {
  it('tem o título da página como h1', () => {
    render(<CabecalhoDashboard />)

    expect(screen.getByRole('heading', { level: 1, name: 'Meus pedidos' })).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `npm test -- tests/app/components/dashboard/painel.test.tsx`
Expected: FAIL com `Failed to resolve import "@/app/components/dashboard/ResumoPedidos"`.

- [ ] **Step 3: Implementar**

Create `frontend/app/components/dashboard/ResumoPedidos.tsx`:
```tsx
import { Card, CardContent } from '@/app/components/ui/card'
import type { ResumoDosPedidos } from '@/services/pedidos'

function Indicador({ rotulo, valor }: { rotulo: string; valor: number }) {
  return (
    <Card>
      <CardContent>
        <dl>
          <dt className="text-sm text-muted-foreground">{rotulo}</dt>
          <dd className="text-2xl font-semibold">{valor}</dd>
        </dl>
      </CardContent>
    </Card>
  )
}

export function ResumoPedidos({ resumo }: { resumo: ResumoDosPedidos }) {
  return (
    <section aria-label="Resumo dos pedidos" className="grid grid-cols-2 gap-3 md:grid-cols-4">
      <Indicador rotulo="Abertos" valor={resumo.porFiltro.ABERTOS} />
      <Indicador rotulo="Em negociação" valor={resumo.porFiltro.EM_NEGOCIACAO} />
      <Indicador rotulo="Conversas em andamento" valor={resumo.conversasEmAndamento} />
      <Indicador rotulo="Propostas em andamento" valor={resumo.propostasEmAndamento} />
    </section>
  )
}
```

Create `frontend/app/components/dashboard/FiltroStatus.tsx`:
```tsx
import { Button } from '@/app/components/ui/button'
import { ROTULO_FILTRO } from '@/services/formatacao'
import { FILTROS_PEDIDOS, type FiltroPedidos } from '@/services/pedidos'

type Props = {
  valor: FiltroPedidos
  contagens: Record<FiltroPedidos, number>
  aoMudar: (filtro: FiltroPedidos) => void
}

export function FiltroStatus({ valor, contagens, aoMudar }: Props) {
  return (
    <div role="group" aria-label="Filtrar pedidos por status" className="flex flex-wrap gap-2">
      {FILTROS_PEDIDOS.map((filtro) => (
        <Button
          key={filtro}
          type="button"
          size="sm"
          variant={filtro === valor ? 'default' : 'outline'}
          aria-pressed={filtro === valor}
          onClick={() => aoMudar(filtro)}
        >
          {`${ROTULO_FILTRO[filtro]} (${contagens[filtro]})`}
        </Button>
      ))}
    </div>
  )
}
```

Create `frontend/app/components/dashboard/EstadoVazio.tsx`:
```tsx
import { Card, CardContent } from '@/app/components/ui/card'

export function EstadoVazio() {
  return (
    <Card>
      <CardContent className="space-y-1 py-8 text-center">
        <p className="font-medium">Você ainda não tem pedidos.</p>
        <p className="text-sm text-muted-foreground">Quando você pedir uma peça, ela aparece aqui.</p>
      </CardContent>
    </Card>
  )
}
```

Create `frontend/app/components/dashboard/EstadoErro.tsx`:
```tsx
import { Alert, AlertDescription, AlertTitle } from '@/app/components/ui/alert'
import { Button } from '@/app/components/ui/button'
import { mensagemDeErro } from '@/services/erros'

type Props = {
  erro: unknown
  aoTentarDeNovo: () => void
  carregando?: boolean
}

export function EstadoErro({ erro, aoTentarDeNovo, carregando = false }: Props) {
  const { texto, podeTentarDeNovo } = mensagemDeErro(erro)

  return (
    <div className="space-y-3">
      <Alert variant="destructive">
        <AlertTitle>Não foi possível carregar seus pedidos</AlertTitle>
        <AlertDescription>{texto}</AlertDescription>
      </Alert>
      {podeTentarDeNovo && (
        <Button type="button" variant="outline" disabled={carregando} onClick={aoTentarDeNovo}>
          {carregando ? 'Tentando novamente…' : 'Tentar novamente'}
        </Button>
      )}
    </div>
  )
}
```

Create `frontend/app/components/dashboard/CarregandoPedidos.tsx`:
```tsx
import { Skeleton } from '@/app/components/ui/skeleton'

export function CarregandoPedidos() {
  return (
    <div role="status" aria-busy="true" className="space-y-3">
      <span className="sr-only">Carregando pedidos…</span>
      <div aria-hidden="true" className="space-y-3">
        <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
          <Skeleton className="h-20" />
          <Skeleton className="h-20" />
          <Skeleton className="h-20" />
          <Skeleton className="h-20" />
        </div>
        <Skeleton className="h-24" />
        <Skeleton className="h-24" />
      </div>
    </div>
  )
}
```

Create `frontend/app/components/dashboard/CabecalhoDashboard.tsx`:
```tsx
export function CabecalhoDashboard() {
  return (
    <header className="space-y-1">
      <h1 className="text-2xl font-semibold">Meus pedidos</h1>
      <p className="text-sm text-muted-foreground">
        Acompanhe as peças que você pediu, as conversas e as propostas recebidas.
      </p>
    </header>
  )
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `npm test -- tests/app/components/dashboard && npm run typecheck && npm run lint && npm test -- tests/arquitetura.test.ts`
Expected: todos passam. Se o `lint` reclamar de algum arquivo, corrija no arquivo do componente. Não desligue a regra.

- [ ] **Step 5: Commit**

```bash
cd ..
git add frontend/app/components/dashboard frontend/tests/app
git commit -m "feat(frontend): componentes de resumo, filtro, cabeçalho e estados do dashboard" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
cd frontend
```

---

### Task 9: Container do dashboard, página, layout e proxy da API

**Files:**
- Create: `frontend/app/providers.tsx`, `frontend/app/components/dashboard/DashboardPedidos.tsx`, `frontend/app/dashboard/page.tsx`
- Modify: `frontend/app/layout.tsx`, `frontend/app/page.tsx`, `frontend/next.config.ts`
- Test: `frontend/tests/app/components/dashboard/DashboardPedidos.test.tsx`, `frontend/tests/app/dashboard/page.test.tsx`

**Interfaces:**
- Consumes: todos os componentes das Tarefas 7 e 8; `carregarPedidos`, `filtrarPedidos`, `resumirPedidos`, `type FiltroPedidos` de `@/services/pedidos`.
- Produces:
  - `Providers({ children })` (client) com um `QueryClient` sem retentativas automáticas (`retry: false`)
  - `DashboardPedidos({ agora }: { agora?: Date })` (client): consulta `['pedidos']` com `carregarPedidos`, mostra carregando, erro, vazio ou resumo + filtro + lista
  - `app/dashboard/page.tsx`: página `/dashboard` com `<main>` = `CabecalhoDashboard` + `DashboardPedidos`, e `metadata.title = 'Meus pedidos'`
  - `next.config.ts`: `rewrites` de `/api/v1/:path*` para `${API_URL ?? 'http://localhost:8080'}/api/v1/:path*`

- [ ] **Step 1: Escrever os testes**

Create `frontend/tests/app/components/dashboard/DashboardPedidos.test.tsx`:
```tsx
import { describe, expect, it } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { servidor } from '@/mocks/servidor'
import { axe } from '@/tests/axe'
import { umPedidoDto } from '@/tests/builders'
import { Providers } from '@/app/providers'
import { DashboardPedidos } from '@/app/components/dashboard/DashboardPedidos'

const URL_PEDIDOS = 'http://localhost:8080/api/v1/pedidos'
const AGORA = new Date('2026-09-29T12:00:00Z')

const ABERTO = umPedidoDto({
  id: 'a', descricao: 'Farol esquerdo', status: 'ABERTO',
  criadoEm: '2026-09-28T12:00:00Z', expiraEm: '2026-10-05T12:00:00Z',
  totalConversas: 3, totalPropostas: 0,
})
const NEGOCIANDO = umPedidoDto({
  id: 'n', descricao: 'Retrovisor direito', status: 'EM_NEGOCIACAO',
  criadoEm: '2026-09-27T12:00:00Z', expiraEm: '2026-09-30T00:00:00Z',
  totalConversas: 4, totalPropostas: 2, menorPropostaEmCentavos: 8990,
})
const CANCELADO = umPedidoDto({
  id: 'c', descricao: 'Para-choque traseiro', status: 'CANCELADO',
  criadoEm: '2026-09-25T12:00:00Z', expiraEm: '2026-10-02T12:00:00Z', totalConversas: 1,
})
const VENDIDO = umPedidoDto({
  id: 'v', descricao: 'Amortecedor dianteiro', status: 'CONCLUIDO', resultado: 'VENDIDO',
  criadoEm: '2026-09-20T12:00:00Z', expiraEm: '2026-09-27T12:00:00Z',
  totalConversas: 5, totalPropostas: 3, menorPropostaEmCentavos: 21000,
})

function responderCom(itens: unknown[]) {
  servidor.use(http.get(URL_PEDIDOS, () => HttpResponse.json({ itens })))
}

function renderizar() {
  return render(
    <Providers>
      <DashboardPedidos agora={AGORA} />
    </Providers>,
  )
}

function valorDoIndicador(rotulo: string): string | null {
  const resumo = screen.getByRole('region', { name: 'Resumo dos pedidos' })
  return within(resumo).getByText(rotulo).closest('dl')?.querySelector('dd')?.textContent ?? null
}

describe('DashboardPedidos', () => {
  it('mostra carregando e depois os pedidos, do mais recente para o mais antigo', async () => {
    responderCom([VENDIDO, CANCELADO, ABERTO, NEGOCIANDO])

    renderizar()

    expect(screen.getByRole('status')).toHaveTextContent('Carregando pedidos…')
    await screen.findByText('Farol esquerdo')
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    const itens = screen.getAllByRole('listitem')
    expect(itens[0]).toHaveTextContent('Farol esquerdo')
    expect(itens[1]).toHaveTextContent('Retrovisor direito')
    expect(itens[2]).toHaveTextContent('Para-choque traseiro')
    expect(itens[3]).toHaveTextContent('Amortecedor dianteiro')
  })

  it('mostra o resumo: conversas e propostas contam só pedidos em andamento', async () => {
    responderCom([ABERTO, NEGOCIANDO, CANCELADO, VENDIDO])

    renderizar()

    await screen.findByText('Farol esquerdo')
    expect(valorDoIndicador('Abertos')).toBe('1')
    expect(valorDoIndicador('Em negociação')).toBe('1')
    expect(valorDoIndicador('Conversas em andamento')).toBe('7')
    expect(valorDoIndicador('Propostas em andamento')).toBe('2')
  })

  it('mostra a validade calculada com a data de referência', async () => {
    responderCom([ABERTO, NEGOCIANDO])

    renderizar()

    await screen.findByText('Farol esquerdo')
    expect(screen.getByText('Expira em 6 dias')).toBeInTheDocument()
    expect(screen.getByText('Expira em menos de 1 dia')).toBeInTheDocument()
  })

  it('o filtro mostra só os pedidos do grupo escolhido', async () => {
    responderCom([ABERTO, NEGOCIANDO, CANCELADO, VENDIDO])
    renderizar()
    await screen.findByText('Farol esquerdo')

    await userEvent.click(screen.getByRole('button', { name: 'Encerrados (1)' }))

    expect(screen.getByRole('button', { name: 'Encerrados (1)' })).toHaveAttribute('aria-pressed', 'true')
    const lista = screen.getByRole('list', { name: 'Lista de pedidos' })
    expect(within(lista).getAllByRole('listitem')).toHaveLength(1)
    expect(within(lista).getByText('Para-choque traseiro')).toBeInTheDocument()
  })

  it('filtro sem resultado mostra a mensagem do filtro, não a de lista vazia', async () => {
    responderCom([ABERTO])
    renderizar()
    await screen.findByText('Farol esquerdo')

    await userEvent.click(screen.getByRole('button', { name: 'Concluídos (0)' }))

    expect(screen.getByText('Nenhum pedido neste filtro.')).toBeInTheDocument()
    expect(screen.queryByText('Você ainda não tem pedidos.')).not.toBeInTheDocument()
    expect(screen.getByRole('group', { name: 'Filtrar pedidos por status' })).toBeInTheDocument()
  })

  it('lista vazia mostra o estado vazio, sem resumo nem filtro', async () => {
    responderCom([])

    renderizar()

    expect(await screen.findByText('Você ainda não tem pedidos.')).toBeInTheDocument()
    expect(screen.queryByRole('group', { name: 'Filtrar pedidos por status' })).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Resumo dos pedidos' })).not.toBeInTheDocument()
  })

  it('erro 500: mostra o alerta e, ao tentar de novo com sucesso, mostra os pedidos', async () => {
    let chamadas = 0
    servidor.use(
      http.get(URL_PEDIDOS, () => {
        chamadas += 1
        return chamadas === 1
          ? HttpResponse.json({ title: 'Erro interno do servidor' }, { status: 500 })
          : HttpResponse.json({ itens: [ABERTO] })
      }),
    )
    renderizar()

    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível carregar seus pedidos agora.')
    await userEvent.click(screen.getByRole('button', { name: 'Tentar novamente' }))

    expect(await screen.findByText('Farol esquerdo')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('erro de rede: mostra o alerta com botão de tentar de novo', async () => {
    servidor.use(http.get(URL_PEDIDOS, () => HttpResponse.error()))

    renderizar()

    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível conectar ao servidor.')
    expect(screen.getByRole('button', { name: 'Tentar novamente' })).toBeInTheDocument()
  })

  it('sessão expirada (401): mensagem própria e sem botão de tentar de novo', async () => {
    servidor.use(
      http.get(URL_PEDIDOS, () => HttpResponse.json({ title: 'Não autenticado' }, { status: 401 })),
    )

    renderizar()

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Sua sessão expirou. Entre novamente para ver seus pedidos.',
    )
    expect(screen.queryByRole('button', { name: /tentar/i })).not.toBeInTheDocument()
  })

  it('resposta fora do contrato vira erro e nunca mostra NaN nem Invalid Date', async () => {
    responderCom([{ ...ABERTO, expiraEm: 'ontem' }])

    renderizar()

    expect(await screen.findByRole('alert')).toHaveTextContent('O servidor respondeu de um jeito inesperado.')
    expect(document.body).not.toHaveTextContent('NaN')
    expect(document.body).not.toHaveTextContent('Invalid Date')
  })

  it('não tem violações de acessibilidade com dados na tela', async () => {
    responderCom([ABERTO, NEGOCIANDO, CANCELADO, VENDIDO])
    const { container } = renderizar()
    await screen.findByText('Farol esquerdo')

    expect(await axe(container)).toHaveNoViolations()
  })
})
```

Create `frontend/tests/app/dashboard/page.test.tsx`:
```tsx
import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { servidor } from '@/mocks/servidor'
import { Providers } from '@/app/providers'
import PaginaDashboard, { metadata } from '@/app/dashboard/page'

describe('página /dashboard', () => {
  it('monta o cabeçalho e o painel dentro de <main>', async () => {
    servidor.use(
      http.get('http://localhost:8080/api/v1/pedidos', () => HttpResponse.json({ itens: [] })),
    )

    render(
      <Providers>
        <PaginaDashboard />
      </Providers>,
    )

    expect(screen.getByRole('main')).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 1, name: 'Meus pedidos' })).toBeInTheDocument()
    expect(await screen.findByText('Você ainda não tem pedidos.')).toBeInTheDocument()
  })

  it('define o título da aba', () => {
    expect(metadata.title).toBe('Meus pedidos')
  })
})
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `npm test -- tests/app/components/dashboard/DashboardPedidos.test.tsx tests/app/dashboard/page.test.tsx`
Expected: FAIL com `Failed to resolve import "@/app/providers"`.

- [ ] **Step 3: Implementar o provider, o container e a página**

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

Create `frontend/app/components/dashboard/DashboardPedidos.tsx`:
```tsx
'use client'

import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { CarregandoPedidos } from '@/app/components/dashboard/CarregandoPedidos'
import { EstadoErro } from '@/app/components/dashboard/EstadoErro'
import { EstadoVazio } from '@/app/components/dashboard/EstadoVazio'
import { FiltroStatus } from '@/app/components/dashboard/FiltroStatus'
import { ListaPedidos } from '@/app/components/dashboard/ListaPedidos'
import { ResumoPedidos } from '@/app/components/dashboard/ResumoPedidos'
import {
  carregarPedidos,
  filtrarPedidos,
  resumirPedidos,
  type FiltroPedidos,
} from '@/services/pedidos'

export function DashboardPedidos({ agora }: { agora?: Date }) {
  const consulta = useQuery({ queryKey: ['pedidos'], queryFn: carregarPedidos })
  const [filtro, setFiltro] = useState<FiltroPedidos>('TODOS')

  if (consulta.isPending) return <CarregandoPedidos />

  if (consulta.isError) {
    return (
      <EstadoErro
        erro={consulta.error}
        aoTentarDeNovo={() => void consulta.refetch()}
        carregando={consulta.isFetching}
      />
    )
  }

  const pedidos = consulta.data
  if (pedidos.length === 0) return <EstadoVazio />

  const resumo = resumirPedidos(pedidos)

  return (
    <div className="space-y-6">
      <ResumoPedidos resumo={resumo} />
      <FiltroStatus valor={filtro} contagens={resumo.porFiltro} aoMudar={setFiltro} />
      <ListaPedidos pedidos={filtrarPedidos(pedidos, filtro)} agora={agora ?? new Date()} />
    </div>
  )
}
```

Create `frontend/app/dashboard/page.tsx`:
```tsx
import type { Metadata } from 'next'
import { CabecalhoDashboard } from '@/app/components/dashboard/CabecalhoDashboard'
import { DashboardPedidos } from '@/app/components/dashboard/DashboardPedidos'

export const metadata: Metadata = { title: 'Meus pedidos' }

export default function PaginaDashboard() {
  return (
    <main className="mx-auto w-full max-w-4xl flex-1 space-y-6 p-4 md:p-8">
      <CabecalhoDashboard />
      <DashboardPedidos />
    </main>
  )
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `npm test -- tests/app && npm run typecheck && npm test -- tests/arquitetura.test.ts`
Expected: todos passam.

- [ ] **Step 5: Ligar layout, home e proxy da API (configuração, sem teste unitário)**

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
  redirect('/dashboard')
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
Expected: todos passam. O `build` lista as rotas `/` (redireciona), `/dashboard` e `/_not-found`.

- [ ] **Step 7: Commit**

```bash
cd ..
git add frontend
git commit -m "feat(frontend): dashboard Meus pedidos com consulta, filtro, estados e proxy da API" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
cd frontend
```

---

### Task 10: Servidor de mock, verificação no navegador e documentação

**Files:**
- Create: `frontend/mocks/api-mock.mjs`
- Modify: `frontend/package.json` (script `mock-api`), `frontend/README.md`, `docs/superpowers/specs/2026-09-29-stack-e-tdd-design.md`

**Interfaces:**
- Consumes: o contrato `GET /api/v1/pedidos` (seção "Contrato consumido").
- Produces: `npm run mock-api [-- --cenario=normal|vazio|erro|sessao-expirada|fora-do-contrato]` sobe um servidor em `:8080` (ou `PORT`). O backend real substitui este script sem mudar nada no frontend.

- [ ] **Step 1: Criar o servidor de mock**

Create `frontend/mocks/api-mock.mjs`:
```js
import { createServer } from 'node:http'

// Servidor de mock só para desenvolvimento. Segue o contrato de GET /api/v1/pedidos.
// Uso: npm run mock-api -- --cenario=vazio
// Cenários: normal, vazio, erro, sessao-expirada, fora-do-contrato

const PORTA = Number(process.env.PORT ?? 8080)
const CENARIOS = ['normal', 'vazio', 'erro', 'sessao-expirada', 'fora-do-contrato']
const argumento = process.argv.find((a) => a.startsWith('--cenario='))
const cenario = argumento ? argumento.split('=')[1] : 'normal'

if (!CENARIOS.includes(cenario)) {
  console.error(`Cenário desconhecido: "${cenario}". Use um de: ${CENARIOS.join(', ')}`)
  process.exit(1)
}

const DIA = 24 * 60 * 60 * 1000
const daqui = (dias) => new Date(Date.now() + dias * DIA).toISOString()

const pedidos = [
  {
    id: 'p-1',
    veiculo: { tipo: 'CARRO', marca: 'Volkswagen', modelo: 'Gol', versao: '1.0 Flex 12V 5p', ano: 2014 },
    descricao: 'Farol esquerdo',
    status: 'ABERTO', resultado: null,
    criadoEm: daqui(-1), expiraEm: daqui(6.5),
    totalConversas: 3, totalPropostas: 0, menorPropostaEmCentavos: null,
  },
  {
    id: 'p-2',
    veiculo: { tipo: 'MOTO', marca: 'Honda', modelo: 'CG 160 Titan', versao: 'Flex', ano: 2020 },
    descricao: 'Retrovisor direito',
    status: 'EM_NEGOCIACAO', resultado: null,
    criadoEm: daqui(-3), expiraEm: daqui(0.5),
    totalConversas: 4, totalPropostas: 2, menorPropostaEmCentavos: 8990,
  },
  {
    id: 'p-3',
    veiculo: { tipo: 'CAMINHAO', marca: 'Mercedes-Benz', modelo: 'Actros 2651', versao: '6x4', ano: 2019 },
    descricao: 'Filtro de ar do motor',
    status: 'EM_NEGOCIACAO', resultado: null,
    criadoEm: daqui(-5), expiraEm: daqui(2.5),
    totalConversas: 2, totalPropostas: 1, menorPropostaEmCentavos: 145000,
  },
  {
    id: 'p-4',
    veiculo: { tipo: 'CARRO', marca: 'Fiat', modelo: 'Uno', versao: 'Way 1.0 Flex', ano: 2012 },
    descricao: 'Amortecedor dianteiro',
    status: 'CONCLUIDO', resultado: 'VENDIDO',
    criadoEm: daqui(-10), expiraEm: daqui(-3),
    totalConversas: 5, totalPropostas: 3, menorPropostaEmCentavos: 21000,
  },
  {
    id: 'p-5',
    veiculo: { tipo: 'CARRO', marca: 'Chevrolet', modelo: 'Onix', versao: '1.0 LT', ano: 2018 },
    descricao: 'Para-choque traseiro',
    status: 'CANCELADO', resultado: null,
    criadoEm: daqui(-8), expiraEm: daqui(-1),
    totalConversas: 1, totalPropostas: 0, menorPropostaEmCentavos: null,
  },
  {
    id: 'p-6',
    veiculo: { tipo: 'CARRO', marca: 'Hyundai', modelo: 'HB20', versao: '1.0 Comfort', ano: 2016 },
    descricao: 'Bateria 60Ah',
    status: 'EXPIRADO', resultado: null,
    criadoEm: daqui(-12), expiraEm: daqui(-5),
    totalConversas: 2, totalPropostas: 1, menorPropostaEmCentavos: 0,
  },
  {
    id: 'p-7',
    veiculo: { tipo: 'MOTO', marca: 'Yamaha', modelo: 'Fazer 250', versao: 'ABS', ano: 2021 },
    descricao: 'Kit de transmissão',
    status: 'CONCLUIDO', resultado: 'SEM_COMPRA',
    criadoEm: daqui(-15), expiraEm: daqui(-8),
    totalConversas: 3, totalPropostas: 2, menorPropostaEmCentavos: 38000,
  },
]

function responder(res, status, corpo, tipo = 'application/json') {
  res.writeHead(status, { 'Content-Type': `${tipo}; charset=utf-8` })
  res.end(JSON.stringify(corpo))
}

createServer((req, res) => {
  const { pathname } = new URL(req.url ?? '/', 'http://localhost')
  console.log(`${req.method} ${pathname} [${cenario}]`)

  if (req.method === 'GET' && pathname === '/api/v1/pedidos') {
    switch (cenario) {
      case 'vazio':
        return responder(res, 200, { itens: [] })
      case 'erro':
        return responder(res, 500, { title: 'Erro interno do servidor', status: 500 }, 'application/problem+json')
      case 'sessao-expirada':
        return responder(res, 401, { title: 'Não autenticado', status: 401 }, 'application/problem+json')
      case 'fora-do-contrato':
        return responder(res, 200, { itens: [{ id: 'p-x', status: 'DESCONHECIDO' }] })
      default:
        return responder(res, 200, { itens: pedidos })
    }
  }

  responder(res, 404, { title: 'Não encontrado', status: 404 }, 'application/problem+json')
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
curl -s http://localhost:8080/api/v1/pedidos
```
Expected: JSON com `"itens"` contendo 7 pedidos. Pare o mock em seguida.

- [ ] **Step 3: Verificar o dashboard no navegador (mock + Next em desenvolvimento)**

Suba `npm run mock-api` e, em outro processo, `npm run dev`. Abra `http://localhost:3000/` no navegador.
Expected, conferindo cada item:
1. A raiz `/` redireciona para `/dashboard`, com título de aba "Meus pedidos | Marketplace de Autopeças".
2. Aparecem quatro indicadores (Abertos 1, Em negociação 2, Conversas em andamento 9, Propostas em andamento 3), cinco botões de filtro e sete pedidos, do mais recente (Farol esquerdo) ao mais antigo.
3. "Retrovisor direito" mostra `Expira em menos de 1 dia`; "Farol esquerdo" mostra `Expira em 6 dias`; "Bateria 60Ah" mostra `Menor proposta: R$ 0,00` (e **não** "Sem propostas ainda"); "Farol esquerdo" mostra `Sem propostas ainda`.
4. O clique em "Encerrados" mostra só os pedidos Cancelado e Expirado, e o botão fica destacado.
5. Em largura de celular (~375 px) os indicadores ficam em 2 colunas, sem rolagem horizontal.
6. `curl -s http://localhost:3000/api/v1/pedidos` devolve o mesmo JSON do mock, o que confirma o `rewrite`.

Depois, repita com cada cenário (reinicie o mock com `npm run mock-api -- --cenario=<nome>` e recarregue a página):
- `vazio` → "Você ainda não tem pedidos.", sem filtro nem resumo.
- `erro` → alerta com "Tentar novamente".
- `sessao-expirada` → alerta "Sua sessão expirou..." **sem** botão.
- `fora-do-contrato` → alerta "O servidor respondeu de um jeito inesperado...".

Se algum item divergir, corrija o código com um teste que reproduza o defeito antes.

- [ ] **Step 4: Documentar o frontend**

Replace `frontend/README.md` com:
```markdown
# Frontend — Marketplace de Autopeças

Next.js (App Router), TypeScript, Tailwind CSS e shadcn/ui. Desenvolvido com TDD.

## Estrutura

| Pasta | Papel |
|---|---|
| `app/` | Páginas e componentes. `app/dashboard/page.tsx` monta a página com os componentes de `app/components/` |
| `app/components/ui/` | Componentes do shadcn/ui |
| `app/components/dashboard/` | Componentes do dashboard "Meus pedidos" |
| `api/` | Conexão com o backend (fetch, erros, contrato validado com Zod) |
| `services/` | **Regras de negócio**, sem React nem Next |
| `mocks/` | MSW (testes) e servidor de mock (desenvolvimento) |
| `tests/` | Testes, espelhando as pastas de código |

Regra de dependência: `app/` → `services/` → `api/`. Componentes nunca chamam `api/` direto. O teste `tests/arquitetura.test.ts` garante isso.

## Comandos

```bash
npm run dev          # Next.js em http://localhost:3000
npm run mock-api     # API de mock em http://localhost:8080 (--cenario=vazio|erro|sessao-expirada|fora-do-contrato)
npm test             # Vitest
npm run test:watch
npm run typecheck
npm run lint
npm run build
```

## Backend

O navegador chama `/api/v1/*` no mesmo site, e o Next.js repassa ao backend (`rewrites` em `next.config.ts`). A URL do backend vem de `API_URL` (padrão `http://localhost:8080`). Em desenvolvimento sem backend, use `npm run mock-api`.

Contrato consumido hoje: `GET /api/v1/pedidos` (ver `api/pedidos.ts`). Valores em centavos, datas ISO 8601 UTC.
```

Modify `docs/superpowers/specs/2026-09-29-stack-e-tdd-design.md`: substitua o parágrafo
```
**Frontend:** organizado por funcionalidade (`features/pedido`, `features/chat`, `features/catalogo`), e não por tipo de arquivo. As rotas do App Router ficam finas e só compõem as funcionalidades.
```
por
```
**Frontend (decisão do usuário):** componentes e páginas dentro de `app/` (`app/components/…`, `app/dashboard/page.tsx`); a conexão com o backend em `api/` e as regras de negócio em `services/`, **fora** de `app/`. A dependência vai só de `app/` para `services/` e de `services/` para `api/`, verificada por um teste de arquitetura. Isso substitui a organização por `features/` proposta antes. Até o backend publicar o OpenAPI, o contrato é escrito à mão com Zod em `api/`. Depois, o cliente passa a ser gerado.
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

- **Cobertura da spec:** PED-06 (painel com status e contadores de conversas e propostas) → Tarefas 5, 7, 8 e 9. RNF-USA-01 (pt-BR, mobile-first) → textos em pt-BR e grades responsivas, conferidos na Tarefa 10 (passo 3, item 5). RNF-USA-04 (WCAG 2.1 AA) → axe em cada tela (Tarefas 7, 8 e 9), com contraste conferido no navegador. RNF-MAN-02 (testes automatizados) → toda a suíte. Os estados do pedido e o resultado *Vendido*/*Sem compra* vêm do diagrama 05a. Ações do pedido (cancelar, renovar, reabrir) ficam de fora de propósito, e o motivo está na seção "Fora do escopo".
- **Estrutura pedida pelo usuário:** componentes e página em `app/`, `api/` e `services/` fora, regra de negócio em `services/` → Tarefas 2 (teste de arquitetura), 3 a 6 e 9.
- **Consistência de nomes:** `Pedido`, `PedidoDto`, `FiltroPedidos`, `FILTROS_PEDIDOS`, `ResumoDosPedidos` (o tipo; o componente é `ResumoPedidos`), `SituacaoValidade`, `ApiError.tipo`, `requisitar`, `listarPedidos`, `carregarPedidos`, `mensagemDeErro`, `umPedidoDto`, `Providers` e `DashboardPedidos` têm a mesma grafia em todas as tarefas.
- **Sem marcadores pendentes:** todo passo de código tem o código. Os passos de configuração (Tarefa 1, passo 4; Tarefa 9, passo 5) dão os comandos e os arquivos completos.

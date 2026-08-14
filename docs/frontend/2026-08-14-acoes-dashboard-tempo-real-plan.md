# Frontend Acoes, Dashboard e Tempo Real — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Entregar logout, administracao de usuarios, acoes de ciclo de vida do chamado, dashboard de indicadores e atualizacao em tempo real por SSE no frontend do Fadex Helpdesk.

**Architecture:** As camadas ja existentes seguem valendo — `types` descrevem contratos, `schemas` validam formularios, `services` fazem HTTP stateless, hooks de `src/features/*` orquestram dados e estado, componentes renderizam. Duas pecas novas atravessam camadas: um cliente SSE singleton em `src/services/notifications-stream.ts`, com contagem de assinantes para nao abrir uma conexao por tela, e a persistencia de sessao em `sessionStorage`, que torna as paginas novas sobreviventes a um F5.

**Tech Stack:** Next.js 15.5.23 (App Router), React 19.2.8, TypeScript 5.9.3, Tailwind CSS 3.4.17, Zod 4.1.13, Axios 1.13.2, Zustand 5.0.9, shadcn/ui, Sonner, lucide-react.

**Spec:** `docs/frontend/2026-08-14-acoes-dashboard-tempo-real-design.md`

## Global Constraints

- Branch de trabalho: `feature(frontend)/acoes-dashboard-tempo-real`, a partir de `dev`.
- Dono apenas de `frontend/**` e `docs/frontend/**`. **Nao tocar em `backend/**`. Nao criar migration.**
- Commits em portugues, com prefixo de escopo (`feat(frontend):`, `docs(frontend):`, `fix(frontend):`).
  **Sem trailer de co-autoria:** nenhuma mensagem leva `Co-Authored-By:` nem `Claude-Session:`.
- `make frontend-lint` e `make frontend-build` precisam passar antes de **cada** commit.
- TypeScript estrito, alias `@/*`, arquivos com responsabilidade unica, kebab-case em arquivos.
- Labels de enum vem de `GET /api/v1/choices`. Nao hardcodar label de enum no frontend.
- Erros de HTTP passam por `toApiErrorMessage`. Erro de formulario e inline; resultado de acao assincrona e toast.
- Endpoint ainda nao publicado: usar o caminho literal do design e cair para fixture **somente em 404**, com marcador visivel de "dados de exemplo" na tela.
- Gating por papel: apenas esconder itens de navegacao de ADMIN. Nao construir sistema de permissoes.
- **Nao commitar codigo de implementacao.** As alteracoes de `frontend/**` ficam no working
  tree para revisao do diff inteiro de uma vez. Nada de `git add`/`git commit` nas tasks, e
  nada de `git stash`, `git checkout .` ou `git reset` — o trabalho nao commitado e o material
  de revisao. Os passos de commit das tasks abaixo ficam suspensos; design e plano seguem
  sendo commitados normalmente.
- Ordem de corte, que **nao** segue a numeracao: a Task 8 (`/admin/jobs`) e a primeira a cair,
  conforme o documento de frentes. A Task 9 (historico) fica **acima** dela na prioridade,
  porque e a unica entrega nova com contrato real e publicado, e o historico de mudancas e
  item obrigatorio do desafio. Cortando de baixo para cima pela numeracao, corta-se justamente
  o trabalho real e mantem-se o trabalho contra contrato inventado.

---

## File Structure

**Sessao e shell**
- `frontend/src/types/auth.ts` (modificar): `refreshToken`, `mustChangePassword`, `AuthRefreshRequest`.
- `frontend/src/services/auth.service.ts` (modificar): `refresh(payload)`.
- `frontend/src/services/api.ts` (modificar): interceptor de `401` com refresh unico compartilhado.
- `frontend/src/stores/session.store.ts` (modificar): middleware `persist` em `sessionStorage`, reidratacao do token.
- `frontend/src/components/layout/user-menu.tsx` (criar): avatar com iniciais, nome, papel e botao de sair.
- `frontend/src/components/layout/app-shell.tsx` (modificar): header em todas as larguras, `UserMenu`, itens de nav de ADMIN.
- `frontend/app/(dashboard)/layout.tsx` (modificar): guarda de sessao que espera a reidratacao.
- `frontend/src/routes/routes.ts` (modificar): `dashboard`, `users`, `adminJobs`.

**Usuarios**
- `frontend/src/types/user.ts` (modificar): remover `password` de `CreateUserRequest`, adicionar `mustChangePassword` em `UserDto`.
- `frontend/src/schemas/user.schema.ts` (modificar): remover `password` de `createUserFormSchema`.
- `frontend/src/features/users/use-users.ts` (criar): choices, listagem, filtros, criacao.
- `frontend/src/features/users/users-page.tsx` (criar): composicao da tela.
- `frontend/src/features/users/user-filter-bar.tsx` (criar): filtros de papel e busca.
- `frontend/src/features/users/user-list.tsx` (criar): tabela em desktop, cards em mobile.
- `frontend/src/features/users/user-detail-dialog.tsx` (criar): detalhe por `getById`.
- `frontend/src/features/users/user-create-dialog.tsx` (criar): formulario de criacao.
- `frontend/app/(dashboard)/usuarios/page.tsx` (criar).

**SSE**
- `frontend/src/types/notification.ts` (criar): nomes de evento e tipo de mensagem.
- `frontend/src/types/api.ts` (modificar): exportar `notification`, `indicator`, `ai-job`.
- `frontend/src/services/sse-parser.ts` (criar): funcoes puras de parse de frame.
- `frontend/src/services/notifications-stream.ts` (criar): cliente singleton.
- `frontend/src/features/notifications/use-notifications.ts` (criar): ponte React.
- `frontend/src/features/tickets/use-ticket-events.ts` (modificar): passa a usar o cliente real.

**Historico do chamado**
- `frontend/src/types/ticket-event.ts` (criar): `TicketEventDto` e `TicketEventFilters`.
- `frontend/src/services/ticket-events.service.ts` (criar): `GET /tickets/{ticketId}/events`.
- `frontend/src/features/tickets/use-ticket-history.ts` (criar).
- `frontend/src/features/tickets/ticket-history-list.tsx` (criar).

**Acoes do chamado**
- `frontend/src/types/ticket.ts` (modificar): campos de IA opcionais e payloads das acoes.
- `frontend/src/services/tickets.service.ts` (modificar): `updateStatus`, `assign`, `unassign`, `updateClassification`.
- `frontend/src/features/tickets/use-ticket-actions.ts` (criar): acoes + estado de envio.
- `frontend/src/features/tickets/ticket-lifecycle-actions.tsx` (criar): status e responsavel.
- `frontend/src/features/tickets/ticket-classification-card.tsx` (criar): sugestao da IA e classificacao manual.
- `frontend/src/features/tickets/ticket-detail-panel.tsx` (modificar): monta os dois blocos.
- `frontend/src/features/tickets/ticket-detail-page.tsx` (modificar): liga acoes e SSE.

**Indicadores**
- `frontend/src/types/indicator.ts` (criar).
- `frontend/src/services/indicators.service.ts` (criar): fallback por 404.
- `frontend/src/services/indicators.fixture.ts` (criar): dado fixo.
- `frontend/src/features/indicators/use-indicators.ts` (criar).
- `frontend/src/features/indicators/indicator-card.tsx` (criar): tile numerico.
- `frontend/src/features/indicators/indicator-breakdown.tsx` (criar): barras proporcionais.
- `frontend/src/features/indicators/dashboard-page.tsx` (criar).
- `frontend/app/(dashboard)/dashboard/page.tsx` (criar).
- `frontend/app/(dashboard)/home/page.tsx` (modificar): redirect para `/dashboard`.

**Jobs de IA**
- `frontend/src/types/ai-job.ts` (criar): enums reais `AiJobType`/`AiJobStatus`.
- `frontend/src/services/ai-jobs.service.ts` (criar).
- `frontend/src/features/ai-jobs/use-ai-jobs.ts` (criar).
- `frontend/src/features/ai-jobs/ai-jobs-page.tsx` (criar).
- `frontend/app/(dashboard)/admin/jobs/page.tsx` (criar).

---

### Task 1: Sessao persistida e guarda de rota

**Files:**
- Modify: `frontend/src/stores/session.store.ts`
- Modify: `frontend/app/(dashboard)/layout.tsx`
- Modify: `frontend/src/routes/routes.ts`

**Interfaces:**
- Consumes: `setApiAccessToken` de `src/services/api-token.ts`.
- Produces: `useSessionStore` com `isHydrated: boolean`; `routes.dashboard`, `routes.users`, `routes.adminJobs`.

- [ ] **Step 1: Adicionar as rotas novas**

```ts
export const routes = {
  login: "/login",
  home: "/home",
  dashboard: "/dashboard",
  tickets: "/tickets",
  ticketDetails: (ticketId: string) => `/tickets/${ticketId}`,
  users: "/usuarios",
  adminJobs: "/admin/jobs"
} as const;
```

- [ ] **Step 2: Envolver a store com `persist`**

Importar `persist`, `createJSONStorage` de `zustand/middleware`. Adicionar `isHydrated: false` ao estado e `setHydrated`. Envolver o criador:

```ts
export const useSessionStore = create<SessionState>()(
  persist(
    (set) => ({ /* estado atual, mais isHydrated: false */ }),
    {
      name: "fadex-helpdesk-session",
      storage: createJSONStorage(() => sessionStorage),
      partialize: (state) => ({
        user: state.user,
        role: state.role,
        accessToken: state.accessToken,
        tokenType: state.tokenType,
        expiresIn: state.expiresIn,
        isAuthenticated: state.isAuthenticated
      }),
      onRehydrateStorage: () => (state) => {
        setApiAccessToken(state?.accessToken ?? null);
        useSessionStore.setState({ isHydrated: true });
      }
    }
  )
);
```

`sessionStorage` so existe no navegador; `createJSONStorage` com funcao adiada evita quebrar o
prerender do Next. Se o `onRehydrateStorage` nao rodar (sem storage), a flag precisa virar
`true` mesmo assim — usar `void useSessionStore.persist.rehydrate()` nao e necessario, mas
garantir `isHydrated` em `onRehydrateStorage` cobre inclusive o caso de storage vazio, porque
o middleware chama o callback de qualquer forma.

- [ ] **Step 3: Guarda no layout do dashboard**

```tsx
"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { AppShell } from "@/src/components/layout/app-shell";
import { routes } from "@/src/routes/routes";
import { useSessionStore } from "@/src/stores/session.store";

export default function DashboardLayout({
  children
}: Readonly<{ children: React.ReactNode }>) {
  const router = useRouter();
  const isHydrated = useSessionStore((state) => state.isHydrated);
  const isAuthenticated = useSessionStore((state) => state.isAuthenticated);

  useEffect(() => {
    if (isHydrated && !isAuthenticated) {
      router.replace(routes.login);
    }
  }, [isAuthenticated, isHydrated, router]);

  if (!isHydrated) {
    return (
      <div className="grid min-h-screen place-items-center bg-slate-50 text-sm text-slate-500">
        Carregando sessao...
      </div>
    );
  }

  if (!isAuthenticated) {
    return null;
  }

  return <AppShell>{children}</AppShell>;
}
```

- [ ] **Step 4: Aceitar `refreshToken` e `mustChangePassword` no tipo de auth**

O backend ja devolve os dois em `AuthResponseDto`; o tipo do frontend e que os descartava.

```ts
export type AuthLoginResponse = {
  accessToken: string;
  refreshToken: string | null;
  tokenType: "Bearer";
  expiresIn: number;
  mustChangePassword: boolean;
  role: RoleValue;
  user: AuthenticatedUser;
};

export type AuthRefreshRequest = { refreshToken: string };
```

Guardar `refreshToken` e `mustChangePassword` na store e no `partialize` do Step 2.

- [ ] **Step 5: `refresh` no auth service**

```ts
async function refresh(payload: AuthRefreshRequest) {
  const response = await api.post<AuthLoginResponse>("/auth/refresh", payload);
  return response.data;
}
```

- [ ] **Step 6: Interceptor de 401 com refresh unico**

A promessa compartilhada e o ponto critico: sem ela, uma tela com quatro requisicoes paralelas
recebendo `401` dispara quatro refreshes e invalida o proprio token em cascata.

```ts
let refreshPromise: Promise<string | null> | null = null;

api.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    if (!axios.isAxiosError(error) || error.response?.status !== 401) {
      return Promise.reject(error);
    }

    const originalRequest = error.config as (InternalAxiosRequestConfig & {
      _hasRetried?: boolean;
    }) | undefined;

    if (
      !originalRequest ||
      originalRequest._hasRetried ||
      originalRequest.url?.includes("/auth/")
    ) {
      return Promise.reject(error);
    }

    originalRequest._hasRetried = true;
    refreshPromise = refreshPromise ?? runRefresh();

    const nextToken = await refreshPromise;
    refreshPromise = null;

    if (!nextToken) {
      onSessionExpired?.();
      return Promise.reject(error);
    }

    originalRequest.headers.Authorization = `Bearer ${nextToken}`;
    return api(originalRequest);
  }
);
```

`runRefresh()` le o `refreshToken` do getter registrado, chama `authService.refresh`, grava o
novo token via `setApiAccessToken` e devolve o `accessToken`; em qualquer falha devolve `null`.

Para nao criar import circular entre `api.ts` e `session.store.ts`, o store **registra** seus
callbacks no modulo de token: `setSessionRefreshHandlers({ getRefreshToken, onRefreshed, onSessionExpired })`
em `api-token.ts`. O `api.ts` so consome esses handlers.

- [ ] **Step 7: Verificar**

Run: `make frontend-lint && make frontend-build`
Expected: ambos sem erro.

Manual: logar, recarregar `/tickets` e confirmar que a listagem carrega em vez de dar `401`.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/stores/session.store.ts frontend/app/\(dashboard\)/layout.tsx frontend/src/routes/routes.ts
git commit -m "feat(frontend): persiste sessao e protege rotas autenticadas"
```

---

### Task 2: Menu de usuario com logout

**Files:**
- Create: `frontend/src/components/layout/user-menu.tsx`
- Modify: `frontend/src/components/layout/app-shell.tsx`

**Interfaces:**
- Consumes: `useSessionStore` (`user`, `role`, `logout`), `routes` da Task 1.
- Produces: `<UserMenu />`, sem props.

- [ ] **Step 1: Criar o `UserMenu`**

Nao existe componente `dropdown-menu` em `src/components/ui`. Em vez de adicionar dependencia
nova de Radix por um menu de dois itens, usar `useState` com painel absoluto e fechamento por
clique fora e por `Escape`.

```tsx
"use client";

import { LogOut, User } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { Button } from "@/src/components/ui/button";
import { routes } from "@/src/routes/routes";
import { useSessionStore } from "@/src/stores/session.store";

function toInitials(name: string) {
  const parts = name.trim().split(/\s+/).slice(0, 2);
  return parts.map((part) => part.charAt(0).toUpperCase()).join("") || "?";
}

export function UserMenu() {
  const router = useRouter();
  const user = useSessionStore((state) => state.user);
  const role = useSessionStore((state) => state.role);
  const logout = useSessionStore((state) => state.logout);
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!isOpen) {
      return;
    }

    function handlePointerDown(event: MouseEvent) {
      if (!containerRef.current?.contains(event.target as Node)) {
        setIsOpen(false);
      }
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setIsOpen(false);
      }
    }

    document.addEventListener("mousedown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);

    return () => {
      document.removeEventListener("mousedown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [isOpen]);

  if (!user) {
    return null;
  }

  function handleLogout() {
    setIsOpen(false);
    logout();
    router.replace(routes.login);
  }

  return (
    <div className="relative" ref={containerRef}>
      <button
        aria-expanded={isOpen}
        aria-haspopup="menu"
        className="flex items-center gap-2 rounded-md px-2 py-1.5 text-sm transition-colors hover:bg-slate-100"
        onClick={() => setIsOpen((value) => !value)}
        type="button"
      >
        <span className="flex h-8 w-8 items-center justify-center rounded-full bg-emerald-700 text-xs font-semibold text-white">
          {toInitials(user.name)}
        </span>
        <span className="hidden max-w-[10rem] truncate font-medium sm:block">
          {user.name}
        </span>
      </button>

      {isOpen ? (
        <div
          className="absolute right-0 z-50 mt-2 w-60 rounded-md border border-slate-200 bg-white p-2 shadow-lg"
          role="menu"
        >
          <div className="flex items-start gap-2 rounded-md px-2 py-2">
            <User className="mt-0.5 h-4 w-4 shrink-0 text-slate-400" />
            <div className="min-w-0">
              <p className="truncate text-sm font-medium text-slate-950">{user.name}</p>
              <p className="text-xs text-slate-500">{role ?? "Sem perfil"}</p>
            </div>
          </div>
          <div className="my-1 h-px bg-slate-200" />
          <Button
            className="w-full justify-start"
            onClick={handleLogout}
            role="menuitem"
            size="sm"
            type="button"
            variant="ghost"
          >
            <LogOut className="h-4 w-4" />
            Sair
          </Button>
        </div>
      ) : null}
    </div>
  );
}
```

- [ ] **Step 2: Header em todas as larguras no `app-shell.tsx`**

Hoje o header tem `lg:hidden` e some no desktop, entao o menu ficaria invisivel exatamente onde
o app e usado. Trocar por um header sempre visivel: logo so no mobile (`lg:hidden`), `UserMenu`
sempre a direita com `ml-auto`. Retirar `lg:hidden` do `<header>` e envolver o bloco do logo
numa `<div className="lg:hidden">`.

- [ ] **Step 3: Itens de navegacao de ADMIN**

Manter `navigationItems` com Home/Chamados e adicionar `dashboard`. Criar
`adminNavigationItems` com `/usuarios` (icone `Users`) e `/admin/jobs` (icone `Cpu`). No corpo
do componente:

```tsx
const role = useSessionStore((state) => state.role);
const visibleItems =
  role === "ADMIN" ? [...navigationItems, ...adminNavigationItems] : navigationItems;
```

Renderizar `visibleItems` no lugar de `navigationItems`.

- [ ] **Step 4: Verificar**

Run: `make frontend-lint && make frontend-build`
Expected: ambos sem erro.

Manual: logar como `admin@fadex.org.br`, ver `/usuarios` e `/admin/jobs` na nav; logar como
`solicitante@fadex.org.br` e confirmar que sumiram; clicar em Sair e cair no `/login`, sem
conseguir voltar para `/tickets` pelo botao de voltar do navegador.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/layout
git commit -m "feat(frontend): adiciona menu de usuario com logout no header"
```

---

### Task 3: Pagina de usuarios

**Files:**
- Modify: `frontend/src/types/user.ts`
- Modify: `frontend/src/schemas/user.schema.ts`
- Create: `frontend/src/features/users/use-users.ts`
- Create: `frontend/src/features/users/user-filter-bar.tsx`
- Create: `frontend/src/features/users/user-list.tsx`
- Create: `frontend/src/features/users/user-detail-dialog.tsx`
- Create: `frontend/src/features/users/user-create-dialog.tsx`
- Create: `frontend/src/features/users/users-page.tsx`
- Create: `frontend/app/(dashboard)/usuarios/page.tsx`

**Interfaces:**
- Consumes: `usersService.list/getById/create`, `choicesService.getChoices`, `routes.users`.
- Produces: `useUsers()` devolvendo `{ users, roleLabels, filters, isLoading, isRefreshing, isCreating, error, loadUsers, updateFilters, createUser }`.

- [ ] **Step 1: Corrigir os contratos de usuario**

`GET /api/v1/users` devolve so `{id, name}` — a listagem nao pode prometer e-mail nem papel na
tabela. E `POST /api/v1/users` nao recebe senha: o backend gera a provisoria e envia por
e-mail. Em `frontend/src/types/user.ts`:

```ts
export type UserDto = UserSummary & {
  email: string;
  role: RoleValue;
  mustChangePassword: boolean;
  createdAt: string;
  updatedAt: string;
};

export type CreateUserRequest = {
  name: string;
  email: string;
  role: RoleValue;
};
```

Em `frontend/src/schemas/user.schema.ts`, remover o campo `password` de `createUserFormSchema`.

- [ ] **Step 2: Criar `use-users.ts`**

Espelhar o formato de `use-ticket-list.ts`: `useState` para `users`, `filters`, `isLoading`,
`isRefreshing`, `isCreating`, `error`; `useEffect` inicial carregando `choicesService.getChoices()`
e `usersService.list(initialUserFilters)` em `Promise.all`; `loadUsers`, `updateFilters` e
`createUser` em `useCallback`. `createUser` chama `usersService.create`, recarrega a lista,
`toast.success("Usuario criado. A senha provisoria foi enviada por e-mail.")` e devolve
`boolean`; no catch, `toast.error` com `toApiErrorMessage`. Expor `roleLabels` como
`Map<string, string>` construido de `choices.roles`.

```ts
export const initialUserFilters: UserFilters = {
  page: 0,
  size: 20,
  sort: "name,asc"
};
```

- [ ] **Step 3: Criar os componentes de tela**

`user-filter-bar.tsx`: `Select` de papel alimentado por `choices.roles` e `Input` de busca,
com botao "Aplicar". Copiar a estrutura de `ticket-filter-bar.tsx`.

`user-list.tsx`: `Card` com `Table` em `md:block` e cards em `md:hidden`, colunas Nome e Acoes,
com skeleton no carregamento e moldura tracejada quando vazio. A acao por linha e "Detalhes",
que abre o `UserDetailDialog`. **Nao** chamar `getById` por linha na renderizacao — so no clique.

`user-detail-dialog.tsx`: recebe `userId: string | null` e `onOpenChange`; ao abrir, chama
`usersService.getById` e mostra nome, e-mail, papel (via `roleLabels`), se precisa trocar senha
e as datas. Estados de carregando e erro proprios.

`user-create-dialog.tsx`: `Dialog` com nome, e-mail e `Select` de papel, validando por
`createUserFormSchema` com erro inline. Sem campo de senha. Texto de apoio explicando que a
senha provisoria vai por e-mail.

`users-page.tsx`: `"use client"`, monta cabecalho, filtros, faixa de erro e lista, no formato
de `tickets-page.tsx`. Inclui a nota de que a listagem so traz nome porque a projecao da API
nao expoe mais campos.

- [ ] **Step 4: Criar a rota**

```tsx
import { UsersPage } from "@/src/features/users/users-page";

export default function UsersRoutePage() {
  return <UsersPage />;
}
```

- [ ] **Step 5: Verificar**

Run: `make frontend-lint && make frontend-build`
Expected: ambos sem erro, com `/usuarios` na lista de rotas.

Manual: como ADMIN, listar, filtrar por papel, abrir detalhe e criar usuario.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/types/user.ts frontend/src/schemas/user.schema.ts frontend/src/features/users frontend/app/\(dashboard\)/usuarios
git commit -m "feat(frontend): adiciona pagina de usuarios com filtros, detalhe e criacao"
```

---

### Task 4: Cliente SSE

**Files:**
- Create: `frontend/src/types/notification.ts`
- Create: `frontend/src/services/sse-parser.ts`
- Create: `frontend/src/services/notifications-stream.ts`
- Create: `frontend/src/features/notifications/use-notifications.ts`
- Modify: `frontend/src/types/api.ts`
- Modify: `frontend/src/features/tickets/use-ticket-events.ts`
- Modify: `frontend/src/features/tickets/tickets-page.tsx`
- Modify: `frontend/src/features/tickets/ticket-detail-page.tsx`

**Interfaces:**
- Consumes: `getApiAccessToken()`, `getPublicEnv().apiBaseUrl`.
- Produces: `parseSseFrame(frame: string): SseFrame | null`; `subscribeToNotifications(listener: NotificationListener): () => void`; `useNotifications({ enabled, onEvent })`.

- [ ] **Step 1: Tipos de notificacao**

```ts
export const notificationEventNames = [
  "CONEXAO_ESTABELECIDA",
  "CHAMADO_ATUALIZADO",
  "CHAMADO_ALTA_PRIORIDADE",
  "INDICADORES_ATUALIZADOS",
  "CLASSIFICACAO_CONCLUIDA",
  "JOB_IA_FALHOU"
] as const;

export type NotificationEventName = (typeof notificationEventNames)[number];

export type NotificationEvent = {
  name: NotificationEventName | string;
  payload: unknown;
};

export type NotificationListener = (event: NotificationEvent) => void;
```

Adicionar `export type * from "./notification";` em `src/types/api.ts`.

- [ ] **Step 2: Parser puro**

O parser fica separado do transporte porque e a peca com maior risco de regressao e a unica
testavel sem rede.

```ts
export type SseFrame = {
  event: string;
  data: string;
  id: string | null;
};

export function parseSseFrame(frame: string): SseFrame | null {
  const dataLines: string[] = [];
  let event = "message";
  let id: string | null = null;
  let hasField = false;

  for (const rawLine of frame.split("\n")) {
    const line = rawLine.replace(/\r$/, "");

    if (line === "" || line.startsWith(":")) {
      continue;
    }

    const separatorIndex = line.indexOf(":");
    const field = separatorIndex === -1 ? line : line.slice(0, separatorIndex);
    const rawValue = separatorIndex === -1 ? "" : line.slice(separatorIndex + 1);
    const value = rawValue.startsWith(" ") ? rawValue.slice(1) : rawValue;

    if (field === "event") {
      event = value;
      hasField = true;
    } else if (field === "data") {
      dataLines.push(value);
      hasField = true;
    } else if (field === "id") {
      id = value;
      hasField = true;
    }
  }

  if (!hasField) {
    return null;
  }

  return { event, data: dataLines.join("\n"), id };
}

export function parseEventPayload(data: string): unknown {
  if (data.trim() === "") {
    return null;
  }

  try {
    return JSON.parse(data);
  } catch {
    return null;
  }
}
```

`hasField` e o que descarta o bloco composto so de `: ping`, sem deixar vazar um evento
`message` fantasma a cada 20 segundos.

- [ ] **Step 3: Cliente singleton**

```ts
import { getPublicEnv } from "@/src/config/public-env";
import type { NotificationListener } from "@/src/types/api";
import { getApiAccessToken } from "./api-token";
import { parseEventPayload, parseSseFrame } from "./sse-parser";

const initialRetryDelayMs = 1000;
const maxRetryDelayMs = 30000;

const listeners = new Set<NotificationListener>();
let controller: AbortController | null = null;
let retryTimer: ReturnType<typeof setTimeout> | null = null;
let retryDelayMs = initialRetryDelayMs;
let isRunning = false;

function emit(name: string, payload: unknown) {
  for (const listener of listeners) {
    listener({ name, payload });
  }
}

async function consumeStream(signal: AbortSignal) {
  const { apiBaseUrl } = getPublicEnv();
  const token = getApiAccessToken();

  if (!token) {
    throw new Error("SEM_TOKEN");
  }

  const response = await fetch(`${apiBaseUrl}/notifications/stream`, {
    headers: { Accept: "text/event-stream", Authorization: `Bearer ${token}` },
    signal
  });

  if (response.status === 401 || response.status === 403) {
    throw new Error("NAO_AUTORIZADO");
  }

  if (!response.ok || !response.body) {
    throw new Error(`STREAM_INDISPONIVEL_${response.status}`);
  }

  retryDelayMs = initialRetryDelayMs;

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  while (true) {
    const { done, value } = await reader.read();

    if (done) {
      return;
    }

    buffer += decoder.decode(value, { stream: true });

    let separatorIndex = buffer.indexOf("\n\n");

    while (separatorIndex !== -1) {
      const rawFrame = buffer.slice(0, separatorIndex);
      buffer = buffer.slice(separatorIndex + 2);

      const frame = parseSseFrame(rawFrame);

      if (frame) {
        emit(frame.event, parseEventPayload(frame.data));
      }

      separatorIndex = buffer.indexOf("\n\n");
    }
  }
}

function scheduleReconnect() {
  if (!isRunning || listeners.size === 0) {
    return;
  }

  retryTimer = setTimeout(() => {
    retryTimer = null;
    void run();
  }, retryDelayMs);

  retryDelayMs = Math.min(retryDelayMs * 2, maxRetryDelayMs);
}

async function run() {
  if (!isRunning || listeners.size === 0) {
    return;
  }

  controller = new AbortController();

  try {
    await consumeStream(controller.signal);
  } catch (error) {
    if (controller.signal.aborted) {
      return;
    }

    if (error instanceof Error && error.message === "NAO_AUTORIZADO") {
      isRunning = false;
      return;
    }
  }

  scheduleReconnect();
}

export function subscribeToNotifications(listener: NotificationListener) {
  listeners.add(listener);

  if (!isRunning) {
    isRunning = true;
    retryDelayMs = initialRetryDelayMs;
    void run();
  }

  return () => {
    listeners.delete(listener);

    if (listeners.size === 0) {
      isRunning = false;
      controller?.abort();
      controller = null;

      if (retryTimer) {
        clearTimeout(retryTimer);
        retryTimer = null;
      }
    }
  };
}
```

Duas decisoes que precisam ficar como estao: a conexao so fecha quando o **ultimo** assinante
sai, o que faz o double-mount do StrictMode terminar com uma conexao viva e nao duas; e `401`
para de tentar em vez de reconectar em loop contra um token expirado.

- [ ] **Step 4: Ponte React**

```ts
"use client";

import { useEffect, useRef } from "react";
import { subscribeToNotifications } from "@/src/services/notifications-stream";
import type { NotificationEvent } from "@/src/types/api";

type UseNotificationsOptions = {
  enabled: boolean;
  onEvent: (event: NotificationEvent) => void;
};

export function useNotifications({ enabled, onEvent }: UseNotificationsOptions) {
  const handlerRef = useRef(onEvent);
  handlerRef.current = onEvent;

  useEffect(() => {
    if (!enabled) {
      return;
    }

    return subscribeToNotifications((event) => handlerRef.current(event));
  }, [enabled]);
}
```

A `ref` e o que impede o efeito de reassinar toda vez que o componente pai recria o callback —
sem ela, cada render derrubaria e reabriria o stream.

- [ ] **Step 5: Ligar `use-ticket-events.ts` ao cliente**

Manter a assinatura atual (`enabled`, `onTicketChanged`, `onCommentChanged`) para nao mexer nos
dois chamadores, e traduzir os nomes de evento reais:

```ts
"use client";

import { useCallback } from "react";
import { useNotifications } from "@/src/features/notifications/use-notifications";
import type { NotificationEvent } from "@/src/types/api";

const ticketEventNames = [
  "CONEXAO_ESTABELECIDA",
  "CHAMADO_ATUALIZADO",
  "CHAMADO_ALTA_PRIORIDADE",
  "CLASSIFICACAO_CONCLUIDA"
];

export function useTicketEvents({
  enabled,
  onTicketChanged,
  onCommentChanged
}: UseTicketEventsOptions) {
  const handleEvent = useCallback(
    (event: NotificationEvent) => {
      if (!ticketEventNames.includes(event.name)) {
        return;
      }

      onTicketChanged();
      onCommentChanged();
    },
    [onCommentChanged, onTicketChanged]
  );

  useNotifications({ enabled, onEvent: handleEvent });
}
```

`CONEXAO_ESTABELECIDA` recarrega de proposito: o contrato nao faz replay de `Last-Event-ID`,
entao reconectar sem recarregar deixaria a tela com o estado de antes da queda.

- [ ] **Step 6: Ativar nos consumidores**

Trocar `enabled: false` por `enabled: true` em `tickets-page.tsx` e `ticket-detail-page.tsx`.

- [ ] **Step 7: Verificar**

Run: `make frontend-lint && make frontend-build`
Expected: ambos sem erro.

Manual, com backend rodando: abrir `/tickets`, ver no DevTools uma unica requisicao pendente
para `notifications/stream`; criar um chamado em outra aba e ver a listagem recarregar; parar o
backend e confirmar reconexao com intervalo crescente, sem laco apertado.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/types/notification.ts frontend/src/types/api.ts frontend/src/services/sse-parser.ts frontend/src/services/notifications-stream.ts frontend/src/features/notifications frontend/src/features/tickets
git commit -m "feat(frontend): consome stream sse de notificacoes com parser proprio"
```

---

### Task 5: Acoes de ciclo de vida do chamado

**Files:**
- Modify: `frontend/src/types/ticket.ts`
- Modify: `frontend/src/services/tickets.service.ts`
- Create: `frontend/src/features/tickets/use-ticket-actions.ts`
- Create: `frontend/src/features/tickets/ticket-lifecycle-actions.tsx`
- Modify: `frontend/src/features/tickets/ticket-detail-panel.tsx`
- Modify: `frontend/src/features/tickets/ticket-detail-page.tsx`

**Interfaces:**
- Consumes: `ticketsService`, `usersService.list`, `ChoiceLabelMap`.
- Produces: `useTicketActions(ticketId, onChanged)` devolvendo `{ isSubmitting, changeStatus, assign, unassign, updateClassification }`, todas devolvendo `Promise<boolean>`.

- [ ] **Step 1: Tipos das acoes**

```ts
export type TicketDto = TicketSummary & {
  description: string;
  updatedAt: string;
  aiSuggestedCategory?: TicketCategoryValue | null;
  aiSuggestedPriority?: TicketPriorityValue | null;
  confidence?: number | null;
  justification?: string | null;
};

export type UpdateTicketStatusRequest = { status: TicketStatusValue };
export type AssignTicketRequest = { assigneeId: string };
export type UpdateTicketClassificationRequest = {
  category: TicketCategoryValue;
  priority: TicketPriorityValue;
  justification?: string;
};
```

- [ ] **Step 2: Metodos no service**

```ts
async function updateStatus(id: string, payload: UpdateTicketStatusRequest) {
  const response = await api.patch<TicketDto>(`/tickets/${id}/status`, payload);
  return response.data;
}

async function assign(id: string, payload: AssignTicketRequest) {
  const response = await api.patch<TicketDto>(`/tickets/${id}/assignee`, payload);
  return response.data;
}

async function unassign(id: string) {
  await api.delete(`/tickets/${id}/assignee`);
}

async function updateClassification(
  id: string,
  payload: UpdateTicketClassificationRequest
) {
  const response = await api.patch<TicketDto>(`/tickets/${id}/classification`, payload);
  return response.data;
}
```

Sem fallback por fixture aqui: sao mutacoes. Fingir sucesso de uma escrita que nao aconteceu e
pior do que mostrar o erro. Enquanto o endpoint nao existir, a acao mostra o `404` normalizado
por `toApiErrorMessage`.

- [ ] **Step 3: Hook de acoes**

```ts
"use client";

import { useCallback, useState } from "react";
import { toast } from "sonner";
import { toApiErrorMessage } from "@/src/services/api-error";
import { ticketsService } from "@/src/services/tickets.service";

export function useTicketActions(ticketId: string | null, onChanged: () => void) {
  const [isSubmitting, setIsSubmitting] = useState(false);

  const runAction = useCallback(
    async (action: () => Promise<unknown>, successMessage: string) => {
      if (!ticketId) {
        return false;
      }

      setIsSubmitting(true);

      try {
        await action();
        toast.success(successMessage);
        onChanged();

        return true;
      } catch (actionError) {
        toast.error("Nao foi possivel concluir a acao.", {
          description: toApiErrorMessage(actionError)
        });

        return false;
      } finally {
        setIsSubmitting(false);
      }
    },
    [onChanged, ticketId]
  );

  // changeStatus, assign, unassign, updateClassification usam runAction
}
```

- [ ] **Step 4: Componente de acoes**

`ticket-lifecycle-actions.tsx` recebe `ticket`, `choices`, `choiceLabels`, `assignees`,
`isSubmitting` e os quatro callbacks. Renderiza:

- `Select` de status com as opcoes de `choices.ticketStatuses` e botao "Salvar status".
  Quando `ticket.status === "FECHADO"`, tudo desabilitado com a nota "Chamado fechado nao reabre".
- `Select` de responsavel alimentado por `usersService.list({ role: "ADMIN", size: 50 })`,
  botao "Atribuir" e, se ja houver responsavel, botao "Recusar atribuicao".
- Renderiza `null` quando `role !== "ADMIN"`.

- [ ] **Step 5: Montar no detalhe**

`ticket-detail-panel.tsx` ganha uma prop opcional `actionsSlot?: React.ReactNode`, renderizada
no topo da aba "Resumo". A pagina de detalhe monta o slot. Prop opcional para nao quebrar o uso
do painel em outros pontos.

- [ ] **Step 6: Verificar**

Run: `make frontend-lint && make frontend-build`
Expected: ambos sem erro.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/types/ticket.ts frontend/src/services/tickets.service.ts frontend/src/features/tickets
git commit -m "feat(frontend): adiciona acoes de status e responsavel no detalhe do chamado"
```

---

### Task 6: Classificacao e sugestao da IA

**Files:**
- Create: `frontend/src/features/tickets/ticket-classification-card.tsx`
- Modify: `frontend/src/features/tickets/ticket-detail-panel.tsx`

**Interfaces:**
- Consumes: `useTicketActions(...).updateClassification` da Task 5, `ChoicesResponse`, `ChoiceLabelMap`.
- Produces: `<TicketClassificationCard ticket choices choiceLabels isSubmitting onUpdateClassification />`.

- [ ] **Step 1: Criar o card**

Duas partes num `Card`:

Bloco de sugestao da IA, renderizado so quando `ticket.aiSuggestedCategory` e
`ticket.aiSuggestedPriority` existem. Mostra as duas com label resolvida por `choiceLabels`,
a confianca formatada como percentual (`Math.round((ticket.confidence ?? 0) * 100)`), a
justificativa quando houver, e um botao "Aceitar sugestao" que chama `onUpdateClassification`
com exatamente os valores sugeridos. Nao ha endpoint separado de aceite — aceitar e reenviar a
sugestao sem alteracao.

Bloco de classificacao manual: `Select` de categoria e `Select` de prioridade, ambos iniciados
com o valor atual do chamado, `Textarea` opcional de justificativa e botao "Salvar
classificacao". Estado local com `useState`, ressincronizado quando `ticket.id` muda.

O card inteiro renderiza `null` quando `role !== "ADMIN"`.

Quando nao ha sugestao da IA, mostrar "Sem sugestao da IA para este chamado." em vez de
esconder o bloco em silencio — a origem `PENDENTE` e informacao util para o avaliador.

- [ ] **Step 2: Montar no painel**

Renderizar dentro do mesmo `actionsSlot` da Task 5, abaixo das acoes de ciclo de vida.

- [ ] **Step 3: Verificar**

Run: `make frontend-lint && make frontend-build`
Expected: ambos sem erro.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/features/tickets
git commit -m "feat(frontend): adiciona revisao de classificacao e sugestao da ia"
```

---

### Task 7: Dashboard de indicadores

**Files:**
- Create: `frontend/src/types/indicator.ts`
- Create: `frontend/src/services/indicators.fixture.ts`
- Create: `frontend/src/services/indicators.service.ts`
- Create: `frontend/src/features/indicators/use-indicators.ts`
- Create: `frontend/src/features/indicators/indicator-card.tsx`
- Create: `frontend/src/features/indicators/indicator-breakdown.tsx`
- Create: `frontend/src/features/indicators/dashboard-page.tsx`
- Create: `frontend/app/(dashboard)/dashboard/page.tsx`
- Modify: `frontend/app/(dashboard)/home/page.tsx`
- Modify: `frontend/src/types/api.ts`

**Interfaces:**
- Consumes: `useNotifications` da Task 4, `ChoiceLabelMap`.
- Produces: `indicatorsService.get(): Promise<{ data: IndicatorsResponse; isFixture: boolean }>`; `useIndicators()` devolvendo `{ indicators, isFixture, isLoading, isRefreshing, error, loadIndicators }`.

- [ ] **Step 1: Tipos**

Todos os campos agregados opcionais — camada 4 e p90 estao na linha de corte do documento de
frentes e podem nao existir na resposta.

```ts
export type IndicatorDuration = {
  media?: number | null;
  mediana?: number | null;
  p90?: number | null;
};

export type IndicatorsResponse = {
  totalPorStatus?: Record<string, number>;
  totalPorPrioridade?: Record<string, number>;
  totalPorCategoria?: Record<string, number>;
  abertosHoje?: number;
  fechadosHoje?: number;
  abertosNaSemana?: number;
  fechadosNaSemana?: number;
  altaPrioridadeEmAberto?: number;
  tempoFechamentoHoras?: IndicatorDuration;
  tempoPrimeiraRespostaHoras?: IndicatorDuration;
  tempoAtribuicaoHoras?: IndicatorDuration;
  agingBacklog?: { ate1Dia?: number; de1A3Dias?: number; acima3Dias?: number };
  idadeChamadoMaisAntigoHoras?: number;
  percentualDentroDoSla?: number;
  concordanciaIaPercentual?: number;
  confiancaMediaIa?: number;
  distribuicaoClassificacao?: Record<string, number>;
  filaJobs?: {
    pendentes?: number;
    falhos?: number;
    tempoMedioProcessamentoSegundos?: number;
  };
  duplicadosDetectados?: number;
  cargaPorResponsavel?: { responsavel: UserSummary; abertos: number }[];
  topSolicitantes?: { solicitante: UserSummary; total: number }[];
};
```

- [ ] **Step 2: Fixture**

`indicators.fixture.ts` exporta `const indicatorsFixture: IndicatorsResponse` com numeros
coerentes com o seed (20 chamados), incluindo todas as camadas. Existe para a tela ter forma
antes do endpoint, e nunca e mostrado sem o aviso do Step 4.

- [ ] **Step 3: Service com fallback por 404**

```ts
import axios from "axios";
import type { IndicatorsResponse } from "@/src/types/api";
import { api } from "./api";
import { indicatorsFixture } from "./indicators.fixture";

export type IndicatorsResult = {
  data: IndicatorsResponse;
  isFixture: boolean;
};

async function get(): Promise<IndicatorsResult> {
  try {
    const response = await api.get<IndicatorsResponse>("/indicators");
    return { data: response.data, isFixture: false };
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.status === 404) {
      return { data: indicatorsFixture, isFixture: true };
    }

    throw error;
  }
}

export const indicatorsService = { get };
```

Somente `404`. Um `401` ou `500` tem que continuar chegando na tela como erro — fallback
generico esconderia backend quebrado atras de numeros bonitos.

- [ ] **Step 4: Hook e tela**

`use-indicators.ts`: carga inicial com `choicesService.getChoices()` e `indicatorsService.get()`,
guardando `isFixture`. Assina `useNotifications({ enabled: true, onEvent })` e recarrega quando
o nome do evento for `INDICADORES_ATUALIZADOS`, `CHAMADO_ATUALIZADO`, `CLASSIFICACAO_CONCLUIDA`
ou `CONEXAO_ESTABELECIDA`. Em `CHAMADO_ALTA_PRIORIDADE`, alem de recarregar, dispara
`toast.warning("Chamado de prioridade ALTA registrado.")` — o alerta de prioridade alta e item
obrigatorio do desafio.

`indicator-card.tsx`: tile com rotulo, valor grande e nota opcional; renderiza "--" quando o
valor for `undefined`.

`indicator-breakdown.tsx`: recebe `Record<string, number>` e um `Map` de labels, e desenha
barras proporcionais em `div` com largura percentual. Sem biblioteca de grafico.

`dashboard-page.tsx`: cabecalho; faixa de aviso `amber` quando `isFixture`; secao 1 com tiles
(abertos, alta em aberto, abertos/fechados hoje) e breakdowns de status, prioridade e categoria;
secao 2 com tempos (sempre media **e** mediana lado a lado, nunca media sozinha, conforme a nota
do documento de frentes), aging e SLA; secao 3 com concordancia IA, confianca media,
distribuicao de origem e fila de jobs; secao 4 com carga por responsavel e top solicitantes,
renderizada so quando os arrays existirem e nao estiverem vazios.

- [ ] **Step 5: Rota e redirect da home**

`app/(dashboard)/dashboard/page.tsx` renderiza `<DashboardPage />`.
`app/(dashboard)/home/page.tsx` vira `redirect(routes.dashboard)`.

- [ ] **Step 6: Verificar**

Run: `make frontend-lint && make frontend-build`
Expected: ambos sem erro, com `/dashboard` na lista de rotas.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/types/indicator.ts frontend/src/types/api.ts frontend/src/services/indicators.service.ts frontend/src/services/indicators.fixture.ts frontend/src/features/indicators frontend/app/\(dashboard\)/dashboard frontend/app/\(dashboard\)/home
git commit -m "feat(frontend): adiciona dashboard de indicadores com atualizacao em tempo real"
```

---

### Task 8: Pagina de jobs de IA

> Ultima da fila. E o primeiro item do bloco "corta primeiro" do documento de frentes; se o
> prazo apertar, para aqui.

**Files:**
- Create: `frontend/src/types/ai-job.ts`
- Create: `frontend/src/services/ai-jobs.service.ts`
- Create: `frontend/src/features/ai-jobs/use-ai-jobs.ts`
- Create: `frontend/src/features/ai-jobs/ai-jobs-page.tsx`
- Create: `frontend/app/(dashboard)/admin/jobs/page.tsx`
- Modify: `frontend/src/types/api.ts`

**Interfaces:**
- Consumes: `useNotifications` da Task 4, `PageResponse`.
- Produces: `aiJobsService.list(filters)`, `aiJobsService.retry(id)`; `useAiJobs()` devolvendo `{ jobs, isFixture, isLoading, isRetrying, error, loadJobs, retryJob }`.

- [ ] **Step 1: Tipos**

Valores reais, lidos de `ai/job/AiJobType.java`, `ai/job/AiJobStatus.java` e do check
constraint da migration V3. Em ingles, ao contrario dos enums de dominio do chamado.

```ts
export const aiJobTypes = ["CLASSIFICATION", "EMBEDDING"] as const;
export const aiJobStatuses = ["PENDING", "PROCESSING", "DONE", "FAILED"] as const;

export type AiJobType = (typeof aiJobTypes)[number];
export type AiJobStatus = (typeof aiJobStatuses)[number];

export type AiJobDto = {
  id: string;
  ticketId: string | null;
  type: AiJobType | string;
  status: AiJobStatus | string;
  attempts: number;
  nextAttemptAt?: string | null;
  lastError?: string | null;
  createdAt: string;
  updatedAt?: string | null;
};

export type AiJobFilters = PageParams & { status?: AiJobStatus };
```

O campo de erro e `lastError`, nao `errorMessage`, e so existe no `AiJobDto`; o
`AiJobSummaryDto` da listagem traz `nextAttemptAt` sem o erro — por isso os dois sao opcionais.

Estes enums **nao** estao em `GET /api/v1/choices`, que so expoe enums de dominio do chamado.
E a unica excecao autorizada a regra de nao hardcodar label de enum, e o mapa de rotulos fica
isolado em `src/features/ai-jobs/ai-job-labels.ts` para ficar obvio quando o backend expuser.

- [ ] **Step 2: Service**

`list` com fallback por `404` para um fixture de tres jobs (um pendente, um falho, um
concluido), no mesmo formato da Task 7. `retry` faz `POST /ai/jobs/{id}/retry` **sem** fallback:
e mutacao.

- [ ] **Step 3: Hook e tela**

`use-ai-jobs.ts`: carga inicial, `retryJob(id)` com toast de sucesso/erro e recarga, e
`useNotifications` recarregando em `JOB_IA_FALHOU`, `CLASSIFICACAO_CONCLUIDA` e
`CONEXAO_ESTABELECIDA`.

`ai-jobs-page.tsx`: cabecalho, aviso de fixture, tabela em desktop e cards no mobile, com
tentativas, mensagem de erro e botao "Reprocessar" por linha. Link para o chamado quando
`ticketId` existir.

- [ ] **Step 4: Verificar**

Run: `make frontend-lint && make frontend-build`
Expected: ambos sem erro, com `/admin/jobs` na lista de rotas.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/types/ai-job.ts frontend/src/types/api.ts frontend/src/services/ai-jobs.service.ts frontend/src/features/ai-jobs frontend/app/\(dashboard\)/admin
git commit -m "feat(frontend): adiciona pagina de jobs de ia com reprocessamento"
```

---

### Task 9: Historico do chamado

> Unica entrega nova com contrato real e publicado. Pode ser feita a qualquer momento depois da
> Task 4; esta no fim so por ser a menor.

**Files:**
- Create: `frontend/src/types/ticket-event.ts`
- Create: `frontend/src/services/ticket-events.service.ts`
- Create: `frontend/src/features/tickets/use-ticket-history.ts`
- Create: `frontend/src/features/tickets/ticket-history-list.tsx`
- Modify: `frontend/src/types/api.ts`
- Modify: `frontend/src/features/tickets/ticket-detail-panel.tsx`
- Modify: `frontend/src/features/tickets/ticket-detail-page.tsx`

**Interfaces:**
- Consumes: `PageResponse`, `UserSummary`, `useTicketEvents` da Task 4.
- Produces: `ticketEventsService.list(ticketId, filters)`; `useTicketHistory(ticketId)` devolvendo `{ events, isLoading, error, loadEvents }`.

- [ ] **Step 1: Tipos, direto do `api.md`**

```ts
export const ticketEventTypes = [
  "CHAMADO_CRIADO",
  "COMENTARIO_ADICIONADO",
  "STATUS_ALTERADO",
  "RESPONSAVEL_ATRIBUIDO",
  "PRIORIDADE_ALTERADA",
  "CATEGORIA_ALTERADA",
  "CLASSIFICACAO_ATUALIZADA"
] as const;

export type TicketEventType = (typeof ticketEventTypes)[number];

export type TicketEventDto = {
  id: string;
  actor: UserSummary | null;
  type: TicketEventType | string;
  description: string;
  createdAt: string;
};

export type TicketEventFilters = PageParams & {
  actorId?: string;
  type?: TicketEventType;
  search?: string;
};
```

`actor` como anulavel e `type` aberto de proposito: eventos gerados pela IA ou pelo seed podem
nao ter ator, e um tipo novo no backend nao pode quebrar a renderizacao da tela.

- [ ] **Step 2: Service**

```ts
async function list(ticketId: string, filters?: TicketEventFilters) {
  const response = await api.get<PageResponse<TicketEventDto>>(
    `/tickets/${ticketId}/events`,
    { params: filters }
  );

  return response.data;
}

export const ticketEventsService = { list };
```

Sem fallback: o endpoint existe.

- [ ] **Step 3: Hook**

`use-ticket-history.ts` no formato de `use-ticket-comments.ts`: `events`, `isLoading`, `error`
e `loadEvents` em `useCallback`, com `useEffect` disparando na troca de `ticketId`. Filtros
padrao `{ page: 0, size: 30, sort: "createdAt,desc" }`.

- [ ] **Step 4: Lista**

`ticket-history-list.tsx`: linha do tempo vertical com marcador, `event.description`, nome do
ator (ou "Sistema" quando `actor` for `null`) e data formatada com o mesmo `Intl.DateTimeFormat`
`pt-BR` ja usado no painel. Skeleton no carregamento, moldura tracejada quando vazio, faixa
vermelha no erro.

Nao criar mapa de label para `type` no frontend: a `description` que o backend manda ja e o
texto legivel, e inventar label aqui duplicaria regra de enum, contra o `frontend/AGENTS.md`.

- [ ] **Step 5: Montar na aba**

Substituir o placeholder da aba "history" em `ticket-detail-panel.tsx` por uma prop
`historySlot?: React.ReactNode`, preenchida pela pagina de detalhe. Ligar `loadEvents` ao
`onTicketChanged` do `useTicketEvents`, para o historico acompanhar o SSE.

- [ ] **Step 6: Verificar**

Run: `make frontend-lint && make frontend-build`
Expected: ambos sem erro.

Manual: abrir um chamado do seed e conferir os eventos de historico ja semeados.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/types/ticket-event.ts frontend/src/types/api.ts frontend/src/services/ticket-events.service.ts frontend/src/features/tickets
git commit -m "feat(frontend): conecta o historico de eventos do chamado"
```

---

## Verificacao Final

- [ ] `make frontend-lint` sem erro, saida colada no relato.
- [ ] `make frontend-build` sem erro, com `/dashboard`, `/usuarios` e `/admin/jobs` na tabela de rotas.
- [ ] Logout esvazia a sessao e redireciona para `/login`.
- [ ] Reload em pagina autenticada mantem a sessao.
- [ ] Uma unica conexao para `notifications/stream` no DevTools, mesmo em modo de desenvolvimento.
- [ ] Nav de ADMIN escondida para SOLICITANTE.
- [ ] Toda tela alimentada por fixture mostra o aviso de dados de exemplo.
- [ ] Aba de historico mostra os eventos semeados de um chamado do seed.

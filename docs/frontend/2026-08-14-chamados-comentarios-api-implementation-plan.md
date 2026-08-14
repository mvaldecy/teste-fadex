# Frontend Chamados e Comentarios API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Entregar uma primeira experiencia operacional de chamados no frontend, integrada aos endpoints de criacao de chamados e comentarios.

**Architecture:** A implementacao segue camadas: tipos e schemas descrevem contratos, services fazem HTTP stateless, hooks de `src/features/tickets` orquestram dados/estado/refresh, componentes renderizam a experiencia e a home apenas monta a feature. `shadcn/ui` fornece componentes base versionados no repo e Sonner fornece toasts no layout raiz.

**Tech Stack:** Next.js 15.5.23, React 19.2.8, TypeScript 5.9.3, Tailwind CSS 3.4.17, Zod 4.1.13, Axios 1.13.2, Zustand 5.0.9, shadcn/ui, Sonner.

## Global Constraints

- Branch de trabalho: `feature(frontend)/chamados-comentarios-api`.
- Documentacao por dominio: planos/specs de frontend ficam em `docs/frontend`.
- Frontend usa TypeScript estrito, Next.js App Router, Tailwind CSS e alias `@/*`.
- Arquivos devem ter responsabilidade unica; se misturar layout, regra, API, schema e tipos, separar.
- Choices de enum devem vir de `GET /api/v1/choices`; nao hardcodar labels no frontend.
- Erros HTTP devem passar por `toApiErrorMessage`.
- Erros de validacao de formulario ficam inline; toasts ficam para sucesso/falha de operacoes assincronas.
- SSE nao sera implementado neste ciclo; deixar apenas fronteira futura por hook.
- Validacao final obrigatoria: `make frontend-lint` e `make frontend-build`.

---

## File Structure

- `frontend/components.json`: configuracao do shadcn/ui apontando para `frontend/src/components/ui` e alias `@/*`.
- `frontend/src/lib/utils.ts`: utilitario `cn` usado por componentes shadcn.
- `frontend/src/components/ui/*.tsx`: componentes base sem regra de dominio (`button`, `input`, `textarea`, `label`, `select`, `badge`, `card`, `dialog`, `skeleton`, `separator`, `sonner`).
- `frontend/app/layout.tsx`: monta `<Toaster />`.
- `frontend/src/types/ticket.ts`: contratos de chamados.
- `frontend/src/types/comment.ts`: contratos de comentarios.
- `frontend/src/types/api.ts`: barrel exportando `comment`.
- `frontend/src/schemas/ticket.schema.ts`: filtros e criacao de chamado.
- `frontend/src/schemas/comment.schema.ts`: criacao de comentario.
- `frontend/src/services/tickets.service.ts`: listagem, detalhe e criacao de chamados.
- `frontend/src/services/ticket-comments.service.ts`: listagem e criacao de comentarios.
- `frontend/src/features/tickets/choice-labels.ts`: helpers puros para resolver labels recebidos de choices.
- `frontend/src/features/tickets/use-ticket-workspace.ts`: hook principal de choices/listagem/selecao/criacao/refresh.
- `frontend/src/features/tickets/use-ticket-comments.ts`: hook de detalhe/comentarios/criacao/refresh do chamado selecionado.
- `frontend/src/features/tickets/use-ticket-events.ts`: hook no-op documentado como fronteira futura de SSE.
- `frontend/src/features/tickets/tickets-dashboard.tsx`: composicao da tela.
- `frontend/src/features/tickets/ticket-filter-bar.tsx`: filtros basicos.
- `frontend/src/features/tickets/ticket-list.tsx`: listagem responsiva.
- `frontend/src/features/tickets/ticket-create-dialog.tsx`: formulario de novo chamado.
- `frontend/src/features/tickets/ticket-detail-panel.tsx`: detalhe do chamado selecionado.
- `frontend/src/features/tickets/ticket-comments-list.tsx`: lista de comentarios.
- `frontend/src/features/tickets/ticket-comment-form.tsx`: formulario de comentario.
- `frontend/app/(dashboard)/home/page.tsx`: renderiza `TicketsDashboard`.
- `AGENTS.md`: ajustar somente se a leitura ainda nao estiver direta sobre docs por dominio e AGENTS internos.

---

### Task 1: Base shadcn/ui e Sonner

**Files:**
- Create: `frontend/components.json`
- Create: `frontend/src/lib/utils.ts`
- Create: `frontend/src/components/ui/button.tsx`
- Create: `frontend/src/components/ui/input.tsx`
- Create: `frontend/src/components/ui/textarea.tsx`
- Create: `frontend/src/components/ui/label.tsx`
- Create: `frontend/src/components/ui/select.tsx`
- Create: `frontend/src/components/ui/badge.tsx`
- Create: `frontend/src/components/ui/card.tsx`
- Create: `frontend/src/components/ui/dialog.tsx`
- Create: `frontend/src/components/ui/skeleton.tsx`
- Create: `frontend/src/components/ui/separator.tsx`
- Create: `frontend/src/components/ui/sonner.tsx`
- Modify: `frontend/package.json`
- Modify: `frontend/package-lock.json`
- Modify: `frontend/tailwind.config.ts`
- Modify: `frontend/app/globals.css`
- Modify: `frontend/app/layout.tsx`

**Interfaces:**
- Consumes: Next.js App Router, Tailwind, alias `@/*`.
- Produces: reusable UI imports such as `Button`, `Input`, `Textarea`, `Label`, `Select`, `Badge`, `Card`, `Dialog`, `Skeleton`, `Separator`, `Toaster`, and `cn(...inputs: ClassValue[]): string`.

- [ ] **Step 1: Initialize shadcn/ui for the existing app**

Run from `frontend/`:

```bash
npx shadcn@latest init -d
```

If npm prompts about React 19 peer dependencies, choose `Use --legacy-peer-deps`. Keep aliases compatible with this repo:

```json
{
  "aliases": {
    "components": "@/src/components",
    "utils": "@/src/lib/utils",
    "ui": "@/src/components/ui",
    "lib": "@/src/lib",
    "hooks": "@/src/hooks"
  }
}
```

- [ ] **Step 2: Add the UI components needed by the tickets screen**

Run from `frontend/`:

```bash
npx shadcn@latest add button input textarea label select badge card dialog skeleton separator sonner
```

If npm prompts about React 19 peer dependencies, choose `Use --legacy-peer-deps`.

- [ ] **Step 3: Mount Sonner globally**

Modify `frontend/app/layout.tsx` so the body renders the toaster after page content:

```tsx
import type { Metadata } from "next";
import { Toaster } from "@/src/components/ui/sonner";
import "./globals.css";

export const metadata: Metadata = {
  title: "Fadex Helpdesk",
  description: "Central de chamados internos com triagem inteligente"
};

export default function RootLayout({
  children
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="pt-BR">
      <body>
        {children}
        <Toaster position="top-right" richColors />
      </body>
    </html>
  );
}
```

- [ ] **Step 4: Preserve repo styling constraints**

Check generated `frontend/app/globals.css` and keep a light theme. If shadcn adds CSS variables, keep them; do not introduce a purple/blue gradient theme or decorative background.

- [ ] **Step 5: Verify the UI base**

Run from repo root:

```bash
make frontend-lint
make frontend-build
```

Expected: both commands pass.

- [ ] **Step 6: Commit**

```bash
git add frontend/package.json frontend/package-lock.json frontend/components.json frontend/tailwind.config.ts frontend/app/globals.css frontend/app/layout.tsx frontend/src/lib frontend/src/components/ui
git commit -m "feat(frontend): configura base de ui"
```

---

### Task 2: Contratos, Schemas e Services da API

**Files:**
- Modify: `frontend/src/types/ticket.ts`
- Create: `frontend/src/types/comment.ts`
- Modify: `frontend/src/types/api.ts`
- Modify: `frontend/src/schemas/ticket.schema.ts`
- Create: `frontend/src/schemas/comment.schema.ts`
- Modify: `frontend/src/services/tickets.service.ts`
- Create: `frontend/src/services/ticket-comments.service.ts`

**Interfaces:**
- Consumes: `PageResponse<T>`, `PageParams`, `UserSummary`, `TicketDto`, `TicketSummary`, `api`.
- Produces:
  - `CreateTicketRequest = { title: string; description: string }`
  - `TicketCommentSummary`
  - `TicketCommentDto`
  - `TicketCommentFilters`
  - `CreateTicketCommentRequest = { text: string }`
  - `createTicketSchema`
  - `createTicketCommentSchema`
  - `ticketsService.create(payload: CreateTicketRequest): Promise<TicketDto>`
  - `ticketCommentsService.list(ticketId: string, filters?: TicketCommentFilters): Promise<PageResponse<TicketCommentSummary>>`
  - `ticketCommentsService.create(ticketId: string, payload: CreateTicketCommentRequest): Promise<TicketCommentDto>`

- [ ] **Step 1: Extend ticket types**

Modify `frontend/src/types/ticket.ts`:

```ts
export type CreateTicketRequest = {
  title: string;
  description: string;
};
```

- [ ] **Step 2: Add comment types**

Create `frontend/src/types/comment.ts`:

```ts
import type { PageParams } from "./pagination";
import type { UserSummary } from "./user";

export type TicketCommentSummary = {
  id: string;
  author: UserSummary;
  text: string;
  createdAt: string;
};

export type TicketCommentDto = TicketCommentSummary & {
  updatedAt: string;
};

export type TicketCommentFilters = PageParams & {
  authorId?: string;
  search?: string;
};

export type CreateTicketCommentRequest = {
  text: string;
};
```

- [ ] **Step 3: Export comment types from the API barrel**

Modify `frontend/src/types/api.ts`:

```ts
export type * from "./api-error";
export type * from "./auth";
export type * from "./choice";
export type * from "./comment";
export type * from "./pagination";
export type * from "./ticket";
export type * from "./user";
```

- [ ] **Step 4: Add ticket creation schema**

Modify `frontend/src/schemas/ticket.schema.ts`:

```ts
export const createTicketSchema = z.object({
  title: z.string().trim().min(1, "Informe o titulo.").max(160, "Use no maximo 160 caracteres."),
  description: z.string().trim().min(1, "Informe a descricao.")
});

export type CreateTicketData = z.infer<typeof createTicketSchema>;
```

Keep the existing `ticketFiltersSchema`.

- [ ] **Step 5: Add comment creation schema**

Create `frontend/src/schemas/comment.schema.ts`:

```ts
import { z } from "zod";

export const createTicketCommentSchema = z.object({
  text: z.string().trim().min(1, "Informe o comentario.")
});

export type CreateTicketCommentData = z.infer<typeof createTicketCommentSchema>;
```

- [ ] **Step 6: Add ticket creation service**

Modify `frontend/src/services/tickets.service.ts`:

```ts
import type {
  CreateTicketRequest,
  PageResponse,
  TicketDto,
  TicketFilters,
  TicketSummary
} from "@/src/types/api";
import { api } from "./api";

async function list(filters?: TicketFilters) {
  const response = await api.get<PageResponse<TicketSummary>>("/tickets", {
    params: filters
  });

  return response.data;
}

async function getById(id: string) {
  const response = await api.get<TicketDto>(`/tickets/${id}`);
  return response.data;
}

async function create(payload: CreateTicketRequest) {
  const response = await api.post<TicketDto>("/tickets", payload);
  return response.data;
}

export const ticketsService = {
  list,
  getById,
  create
};
```

- [ ] **Step 7: Add ticket comments service**

Create `frontend/src/services/ticket-comments.service.ts`:

```ts
import type {
  CreateTicketCommentRequest,
  PageResponse,
  TicketCommentDto,
  TicketCommentFilters,
  TicketCommentSummary
} from "@/src/types/api";
import { api } from "./api";

async function list(ticketId: string, filters?: TicketCommentFilters) {
  const response = await api.get<PageResponse<TicketCommentSummary>>(
    `/tickets/${ticketId}/comments`,
    { params: filters }
  );

  return response.data;
}

async function create(ticketId: string, payload: CreateTicketCommentRequest) {
  const response = await api.post<TicketCommentDto>(
    `/tickets/${ticketId}/comments`,
    payload
  );

  return response.data;
}

export const ticketCommentsService = {
  list,
  create
};
```

- [ ] **Step 8: Verify contracts**

Run from repo root:

```bash
make frontend-lint
make frontend-build
```

Expected: both commands pass.

- [ ] **Step 9: Commit**

```bash
git add frontend/src/types frontend/src/schemas frontend/src/services
git commit -m "feat(frontend): adiciona contratos de chamados"
```

---

### Task 3: Hooks da Feature de Chamados

**Files:**
- Create: `frontend/src/features/tickets/choice-labels.ts`
- Create: `frontend/src/features/tickets/use-ticket-workspace.ts`
- Create: `frontend/src/features/tickets/use-ticket-comments.ts`
- Create: `frontend/src/features/tickets/use-ticket-events.ts`

**Interfaces:**
- Consumes: `choicesService.getChoices`, `ticketsService.list/getById/create`, `ticketCommentsService.list/create`, `toApiErrorMessage`, schemas from Task 2.
- Produces:
  - `buildChoiceLabelMap(choices: ChoicesResponse): ChoiceLabelMap`
  - `resolveChoiceLabel(labels: Map<string, string> | undefined, value: string): string`
  - `useTicketWorkspace()`
  - `useTicketComments(ticketId: string | null)`
  - `useTicketEvents(options: { enabled: boolean; onTicketChanged: () => void; onCommentChanged: () => void }): void`

- [ ] **Step 1: Add choice label helpers**

Create `frontend/src/features/tickets/choice-labels.ts`:

```ts
import type { ChoiceDto, ChoicesResponse } from "@/src/types/api";

export type ChoiceLabelMap = {
  statuses: Map<string, string>;
  priorities: Map<string, string>;
  categories: Map<string, string>;
  classificationOrigins: Map<string, string>;
};

function toLabelMap(items: ChoiceDto<string>[]) {
  return new Map(items.map((item) => [item.value, item.label]));
}

export function buildChoiceLabelMap(choices: ChoicesResponse): ChoiceLabelMap {
  return {
    statuses: toLabelMap(choices.ticketStatuses),
    priorities: toLabelMap(choices.ticketPriorities),
    categories: toLabelMap(choices.ticketCategories),
    classificationOrigins: toLabelMap(choices.classificationOrigins)
  };
}

export function resolveChoiceLabel(
  labels: Map<string, string> | undefined,
  value: string
) {
  return labels?.get(value) ?? value;
}
```

- [ ] **Step 2: Add workspace hook**

Create `frontend/src/features/tickets/use-ticket-workspace.ts` with this shape:

```ts
"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import type {
  ChoicesResponse,
  CreateTicketRequest,
  TicketDto,
  TicketFilters,
  TicketSummary
} from "@/src/types/api";
import { choicesService } from "@/src/services/choices.service";
import { toApiErrorMessage } from "@/src/services/api-error";
import { ticketsService } from "@/src/services/tickets.service";
import { buildChoiceLabelMap } from "./choice-labels";

const initialFilters: TicketFilters = {
  page: 0,
  size: 10,
  sort: "createdAt,desc"
};

export function useTicketWorkspace() {
  const [choices, setChoices] = useState<ChoicesResponse | null>(null);
  const [tickets, setTickets] = useState<TicketSummary[]>([]);
  const [selectedTicket, setSelectedTicket] = useState<TicketDto | null>(null);
  const [selectedTicketId, setSelectedTicketId] = useState<string | null>(null);
  const [filters, setFilters] = useState<TicketFilters>(initialFilters);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [isCreating, setIsCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const choiceLabels = useMemo(
    () => (choices ? buildChoiceLabelMap(choices) : null),
    [choices]
  );

  const loadTickets = useCallback(async (nextFilters = filters) => {
    setIsRefreshing(true);
    setError(null);

    try {
      const response = await ticketsService.list(nextFilters);
      setTickets(response.content);

      if (!selectedTicketId && response.content[0]) {
        setSelectedTicketId(response.content[0].id);
      }
    } catch (loadError) {
      setError(toApiErrorMessage(loadError));
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, [filters, selectedTicketId]);

  const selectTicket = useCallback((ticketId: string) => {
    setSelectedTicketId(ticketId);
  }, []);

  const updateFilters = useCallback((nextFilters: TicketFilters) => {
    const normalizedFilters = { ...initialFilters, ...nextFilters, page: 0 };
    setFilters(normalizedFilters);
    void loadTickets(normalizedFilters);
  }, [loadTickets]);

  const createTicket = useCallback(async (payload: CreateTicketRequest) => {
    setIsCreating(true);

    try {
      const createdTicket = await ticketsService.create(payload);
      setSelectedTicket(createdTicket);
      setSelectedTicketId(createdTicket.id);
      await loadTickets();
      toast.success("Chamado criado.");
      return true;
    } catch (createError) {
      toast.error("Nao foi possivel criar o chamado.", {
        description: toApiErrorMessage(createError)
      });
      return false;
    } finally {
      setIsCreating(false);
    }
  }, [loadTickets]);

  useEffect(() => {
    async function loadInitialData() {
      setIsLoading(true);
      setError(null);

      try {
        const [choicesResponse, ticketsResponse] = await Promise.all([
          choicesService.getChoices(),
          ticketsService.list(initialFilters)
        ]);

        setChoices(choicesResponse);
        setTickets(ticketsResponse.content);

        if (ticketsResponse.content[0]) {
          setSelectedTicketId(ticketsResponse.content[0].id);
        }
      } catch (loadError) {
        setError(toApiErrorMessage(loadError));
      } finally {
        setIsLoading(false);
      }
    }

    void loadInitialData();
  }, []);

  useEffect(() => {
    async function loadSelectedTicket() {
      if (!selectedTicketId) {
        setSelectedTicket(null);
        return;
      }

      try {
        const ticket = await ticketsService.getById(selectedTicketId);
        setSelectedTicket(ticket);
      } catch (loadError) {
        setError(toApiErrorMessage(loadError));
      }
    }

    void loadSelectedTicket();
  }, [selectedTicketId]);

  return {
    choices,
    choiceLabels,
    tickets,
    selectedTicket,
    selectedTicketId,
    filters,
    isLoading,
    isRefreshing,
    isCreating,
    error,
    loadTickets,
    selectTicket,
    updateFilters,
    createTicket
  };
}
```

During implementation, keep this hook type-safe; if lint reports stale closure risk, replace `selectedTicketId` reads with functional state or split callbacks.

- [ ] **Step 3: Add comments hook**

Create `frontend/src/features/tickets/use-ticket-comments.ts`:

```ts
"use client";

import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import type {
  CreateTicketCommentRequest,
  TicketCommentSummary
} from "@/src/types/api";
import { toApiErrorMessage } from "@/src/services/api-error";
import { ticketCommentsService } from "@/src/services/ticket-comments.service";

export function useTicketComments(ticketId: string | null) {
  const [comments, setComments] = useState<TicketCommentSummary[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isCreating, setIsCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadComments = useCallback(async () => {
    if (!ticketId) {
      setComments([]);
      return;
    }

    setIsLoading(true);
    setError(null);

    try {
      const response = await ticketCommentsService.list(ticketId, {
        page: 0,
        size: 10,
        sort: "createdAt,desc"
      });
      setComments(response.content);
    } catch (loadError) {
      setError(toApiErrorMessage(loadError));
    } finally {
      setIsLoading(false);
    }
  }, [ticketId]);

  const createComment = useCallback(async (payload: CreateTicketCommentRequest) => {
    if (!ticketId) {
      return false;
    }

    setIsCreating(true);

    try {
      await ticketCommentsService.create(ticketId, payload);
      await loadComments();
      toast.success("Comentario publicado.");
      return true;
    } catch (createError) {
      toast.error("Nao foi possivel publicar o comentario.", {
        description: toApiErrorMessage(createError)
      });
      return false;
    } finally {
      setIsCreating(false);
    }
  }, [loadComments, ticketId]);

  useEffect(() => {
    void loadComments();
  }, [loadComments]);

  return {
    comments,
    isLoading,
    isCreating,
    error,
    loadComments,
    createComment
  };
}
```

- [ ] **Step 4: Add future SSE boundary**

Create `frontend/src/features/tickets/use-ticket-events.ts`:

```ts
"use client";

import { useEffect } from "react";

type UseTicketEventsOptions = {
  enabled: boolean;
  onTicketChanged: () => void;
  onCommentChanged: () => void;
};

export function useTicketEvents({
  enabled,
  onTicketChanged,
  onCommentChanged
}: UseTicketEventsOptions) {
  useEffect(() => {
    if (!enabled) {
      return;
    }

    void onTicketChanged;
    void onCommentChanged;
  }, [enabled, onCommentChanged, onTicketChanged]);
}
```

This is intentionally a no-op in this cycle. It reserves the component-level API for future SSE without opening `EventSource`.

- [ ] **Step 5: Verify hooks**

Run from repo root:

```bash
make frontend-lint
make frontend-build
```

Expected: both commands pass.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/features/tickets
git commit -m "feat(frontend): cria hooks de chamados"
```

---

### Task 4: Componentes da Experiencia de Chamados

**Files:**
- Create: `frontend/src/features/tickets/tickets-dashboard.tsx`
- Create: `frontend/src/features/tickets/ticket-filter-bar.tsx`
- Create: `frontend/src/features/tickets/ticket-list.tsx`
- Create: `frontend/src/features/tickets/ticket-create-dialog.tsx`
- Create: `frontend/src/features/tickets/ticket-detail-panel.tsx`
- Create: `frontend/src/features/tickets/ticket-comments-list.tsx`
- Create: `frontend/src/features/tickets/ticket-comment-form.tsx`

**Interfaces:**
- Consumes: hooks from Task 3, UI components from Task 1, schemas from Task 2, choice label helpers from Task 3.
- Produces: `TicketsDashboard` component for the `/home` page.

- [ ] **Step 1: Build the dashboard shell**

Create `frontend/src/features/tickets/tickets-dashboard.tsx`:

```tsx
"use client";

import { TicketCreateDialog } from "./ticket-create-dialog";
import { TicketDetailPanel } from "./ticket-detail-panel";
import { TicketFilterBar } from "./ticket-filter-bar";
import { TicketList } from "./ticket-list";
import { useTicketComments } from "./use-ticket-comments";
import { useTicketEvents } from "./use-ticket-events";
import { useTicketWorkspace } from "./use-ticket-workspace";

export function TicketsDashboard() {
  const workspace = useTicketWorkspace();
  const comments = useTicketComments(workspace.selectedTicketId);

  useTicketEvents({
    enabled: false,
    onTicketChanged: () => void workspace.loadTickets(),
    onCommentChanged: () => void comments.loadComments()
  });

  return (
    <main className="min-h-screen bg-slate-50 px-4 py-6 text-slate-950 sm:px-6 lg:px-8">
      <div className="mx-auto grid max-w-7xl gap-6">
        <header className="flex flex-col gap-4 border-b border-slate-200 pb-5 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <p className="text-sm font-semibold uppercase tracking-[0.12em] text-emerald-700">
              Fadex Helpdesk
            </p>
            <h1 className="mt-2 text-3xl font-semibold tracking-normal">
              Chamados
            </h1>
          </div>
          <TicketCreateDialog
            isCreating={workspace.isCreating}
            onCreateTicket={workspace.createTicket}
          />
        </header>

        <TicketFilterBar
          choices={workspace.choices}
          filters={workspace.filters}
          isRefreshing={workspace.isRefreshing}
          onChangeFilters={workspace.updateFilters}
        />

        {workspace.error ? (
          <p className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
            {workspace.error}
          </p>
        ) : null}

        <section className="grid gap-6 lg:grid-cols-[minmax(0,0.95fr)_minmax(420px,1.05fr)]">
          <TicketList
            choiceLabels={workspace.choiceLabels}
            isLoading={workspace.isLoading}
            selectedTicketId={workspace.selectedTicketId}
            tickets={workspace.tickets}
            onSelectTicket={workspace.selectTicket}
          />
          <TicketDetailPanel
            choiceLabels={workspace.choiceLabels}
            comments={comments.comments}
            commentsError={comments.error}
            isCreatingComment={comments.isCreating}
            isLoadingComments={comments.isLoading}
            onCreateComment={comments.createComment}
            ticket={workspace.selectedTicket}
          />
        </section>
      </div>
    </main>
  );
}
```

- [ ] **Step 2: Build filters**

Create `frontend/src/features/tickets/ticket-filter-bar.tsx` with search, status, priority and category controls. Use `choices.ticketStatuses`, `choices.ticketPriorities` and `choices.ticketCategories` for option labels. On submit, call `onChangeFilters` with `search`, `status`, `priority` and `category`.

- [ ] **Step 3: Build ticket list**

Create `frontend/src/features/tickets/ticket-list.tsx`. Render cards/buttons for each ticket using `Badge` for status/priority/category. Use `resolveChoiceLabel(choiceLabels?.statuses, ticket.status)` and equivalent maps. On mobile, keep cards stacked; on desktop, keep list scrollable only inside its own panel if needed.

- [ ] **Step 4: Build create ticket dialog**

Create `frontend/src/features/tickets/ticket-create-dialog.tsx`. Use `createTicketSchema.safeParse` on submit. Show field errors inline for `title` and `description`. Call `onCreateTicket({ title, description })`; when it returns `true`, close the dialog and reset the form.

- [ ] **Step 5: Build ticket detail panel**

Create `frontend/src/features/tickets/ticket-detail-panel.tsx`. If no ticket is selected, render an empty state. If selected, render title, description, requester, assignee fallback `"Sem responsavel"`, created/updated timestamps, badges and comments section.

- [ ] **Step 6: Build comments list**

Create `frontend/src/features/tickets/ticket-comments-list.tsx`. Render empty, loading, error and populated states. Each comment shows author name, creation timestamp and text.

- [ ] **Step 7: Build comment form**

Create `frontend/src/features/tickets/ticket-comment-form.tsx`. Use `createTicketCommentSchema.safeParse`. Show inline error for `text`. Call `onCreateComment({ text })`; when it returns `true`, reset the textarea.

- [ ] **Step 8: Verify components**

Run from repo root:

```bash
make frontend-lint
make frontend-build
```

Expected: both commands pass.

- [ ] **Step 9: Commit**

```bash
git add frontend/src/features/tickets
git commit -m "feat(frontend): cria interface de chamados"
```

---

### Task 5: Integracao da Home e Ajuste de Documentacao

**Files:**
- Modify: `frontend/app/(dashboard)/home/page.tsx`
- Modify: `AGENTS.md`

**Interfaces:**
- Consumes: `TicketsDashboard` from Task 4.
- Produces: `/home` rendering the tickets experience.

- [ ] **Step 1: Replace the home placeholder**

Modify `frontend/app/(dashboard)/home/page.tsx`:

```tsx
import { TicketsDashboard } from "@/src/features/tickets/tickets-dashboard";

export default function HomePage() {
  return <TicketsDashboard />;
}
```

- [ ] **Step 2: Tighten root AGENTS.md docs guidance**

In `AGENTS.md`, keep the existing pointers to `backend/AGENTS.md` and `frontend/AGENTS.md`. In the documentation section, make the domain rule explicit:

```md
Specs, planos e documentos de trabalho tambem devem seguir o dominio correspondente em `docs`, por exemplo `docs/frontend` para entregas do Next.js e `docs/backend` para entregas do Spring Boot.
```

- [ ] **Step 3: Verify route integration**

Run from repo root:

```bash
make frontend-lint
make frontend-build
```

Expected: both commands pass and the build output includes `/home`.

- [ ] **Step 4: Commit**

```bash
git add AGENTS.md frontend/app/\(dashboard\)/home/page.tsx
git commit -m "feat(frontend): integra home com chamados"
```

---

### Task 6: Validacao Final e Revisao

**Files:**
- Review: `frontend/src/features/tickets/*`
- Review: `frontend/src/services/*`
- Review: `frontend/src/types/*`
- Review: `frontend/src/schemas/*`
- Review: `docs/frontend/2026-08-14-chamados-comentarios-api-design.md`
- Review: `docs/frontend/2026-08-14-chamados-comentarios-api-implementation-plan.md`

**Interfaces:**
- Consumes: all previous tasks.
- Produces: verified frontend implementation ready for PR review.

- [ ] **Step 1: Run final lint**

```bash
make frontend-lint
```

Expected: exit code `0`.

- [ ] **Step 2: Run final build**

```bash
make frontend-build
```

Expected: exit code `0`.

- [ ] **Step 3: Manual smoke path**

With backend running locally or via stack, verify:

```text
1. Open /login.
2. Login with admin@fadex.org.br / admin123.
3. Confirm redirect to /home.
4. Confirm choices render as labels in filters and badges.
5. Create a ticket with title and description.
6. Confirm success toast appears and the ticket becomes selected.
7. Add a comment to the selected ticket.
8. Confirm success toast appears and the comment list refreshes.
9. Submit empty ticket/comment forms and confirm inline validation appears.
```

- [ ] **Step 4: Inspect for enum label duplication**

Run:

```bash
rg -n '"ABERTO"|"EM_ANDAMENTO"|"RESOLVIDO"|"FECHADO"|"BAIXA"|"MEDIA"|"ALTA"|"SISTEMAS"|"OUTROS"' frontend/src
```

Expected: enum values may appear in type/schema validation only; user-facing labels must come from choices.

- [ ] **Step 5: Inspect for accidental SSE implementation**

Run:

```bash
rg -n "EventSource|text/event-stream|Sse|SSE" frontend/src
```

Expected: no `EventSource` usage. Only the no-op future boundary hook or comments may mention SSE.

- [ ] **Step 6: Commit any final fixes**

If Steps 1-5 required fixes:

```bash
git add frontend docs AGENTS.md
git commit -m "fix(frontend): ajusta validacao de chamados"
```

If no fixes were required, do not create an empty commit.

## Self-Review

- Spec coverage: tasks cover UI library, Sonner, types, schemas, services, hooks, SSE boundary, home UI, docs-domain guidance, lint/build and manual smoke path.
- Placeholder scan: no placeholder markers, deferred-work wording, or unspecified validation steps remain.
- Type consistency: `CreateTicketRequest`, `CreateTicketCommentRequest`, `TicketCommentSummary`, `TicketCommentDto`, service method names, hook names and component names match across tasks.
- Scope check: status changes, assignment, event history, real SSE, IA and backend contract changes remain outside this plan.

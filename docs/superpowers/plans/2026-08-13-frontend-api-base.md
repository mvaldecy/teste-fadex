# Frontend API Base Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preparar a base de integracao do frontend com a API documentada em `docs/backend/api.md`, deixando telas completas para depois.

**Architecture:** A implementacao sera feita por camadas, com um commit por camada. Tipos ficam em `src/types/`, schemas em `src/schemas/`, configuracao HTTP em `src/services/api.ts`, services por recurso em `src/services/`, estado de sessao em `src/stores/` e componentes/formularios em `src/features/`.

**Tech Stack:** Next.js 15.5.23, React 19, TypeScript 5.9.3, Tailwind CSS 3, Zod 4, Zustand 5, Axios 1.

## Global Constraints

- Nao criar testes automatizados no frontend neste ciclo.
- Validar frontend com `npm run lint` e `npm run build`.
- Fazer commit por camada, nao necessariamente por dominio.
- Nao criar arquivo com multiplas responsabilidades.
- Labels de enums/choices devem vir do backend, nao do frontend.
- Endpoints disponiveis agora: login, choices, users list/detail/create, tickets list/detail.
- Criacao/edicao de chamados, comentarios, historico e indicadores ficam fora deste ciclo porque ainda nao estao publicados no contrato.

---

### Task 1: Tipos Da API

**Files:**
- Create: `frontend/src/types/api-error.ts`
- Create: `frontend/src/types/pagination.ts`
- Create: `frontend/src/types/choice.ts`
- Create: `frontend/src/types/auth.ts`
- Create: `frontend/src/types/user.ts`
- Create: `frontend/src/types/ticket.ts`
- Modify: `frontend/src/types/api.ts`

**Interfaces:**
- Produces: `ApiErrorResponse`, `PageResponse<T>`, `PageParams`, `ChoiceDto`, `ChoicesResponse`, `AuthLoginRequest`, `AuthLoginResponse`, `UserSummary`, `UserDto`, `CreateUserRequest`, `TicketSummary`, `TicketDto`, filter types.

- [ ] Criar tipos pequenos, separados por responsabilidade.
- [ ] Manter `frontend/src/types/api.ts` como barrel de reexports.
- [ ] Rodar `npm run lint`.
- [ ] Commit: `feat: adiciona tipos da api no frontend`.

### Task 2: Schemas De Formulario E Filtros

**Files:**
- Modify: `frontend/src/schemas/auth.schema.ts`
- Create: `frontend/src/schemas/user.schema.ts`
- Create: `frontend/src/schemas/ticket.schema.ts`
- Create: `frontend/src/schemas/pagination.schema.ts`

**Interfaces:**
- Produces: `loginFormSchema`, `createUserFormSchema`, `ticketFiltersSchema`, `paginationParamsSchema` e tipos inferidos.

- [ ] Atualizar credencial mockada do login para `admin@fadex.org.br` / `admin123`.
- [ ] Criar schemas focados em dados de formulario/filtro, sem chamada HTTP.
- [ ] Rodar `npm run lint`.
- [ ] Commit: `feat: adiciona schemas de formularios da api`.

### Task 3: Base Axios

**Files:**
- Modify: `frontend/src/services/api.ts`
- Create: `frontend/src/services/api-token.ts`
- Create: `frontend/src/services/api-error.ts`

**Interfaces:**
- Produces: `api`, `setApiAccessToken(token: string | null)`, `toApiErrorMessage(error: unknown): string`.

- [ ] Manter `api.ts` responsavel apenas pela instancia Axios e interceptors.
- [ ] Isolar token em `api-token.ts`.
- [ ] Isolar normalizacao de erro em `api-error.ts`.
- [ ] Rodar `npm run lint`.
- [ ] Commit: `feat: configura base axios do frontend`.

### Task 4: Services Por Recurso

**Files:**
- Create: `frontend/src/services/auth.service.ts`
- Create: `frontend/src/services/choices.service.ts`
- Create: `frontend/src/services/users.service.ts`
- Create: `frontend/src/services/tickets.service.ts`

**Interfaces:**
- Produces: `authService.login`, `choicesService.getChoices`, `usersService.list/getById/create`, `ticketsService.list/getById`.

- [ ] Cada service deve importar `api` e tipos do dominio.
- [ ] Cada arquivo deve tratar apenas um recurso da API.
- [ ] Nao criar service para endpoints ainda nao publicados.
- [ ] Rodar `npm run lint`.
- [ ] Commit: `feat: adiciona services da api no frontend`.

### Task 5: Sessao E Login Real Preparado

**Files:**
- Modify: `frontend/src/stores/session.store.ts`
- Modify: `frontend/src/features/auth/login-form.tsx`

**Interfaces:**
- Produces: store com `login(credentials)`, `logout()`, `accessToken`, `tokenType`, `expiresIn`, `role`, `user`, `isAuthenticated`; formulario chamando a API real.

- [ ] Trocar `simulateLogin` por `login` chamando `authService.login`.
- [ ] Alimentar `setApiAccessToken` no login/logout.
- [ ] Exibir erro normalizado no formulario.
- [ ] Manter navegacao para `/home` apos login bem-sucedido.
- [ ] Rodar `npm run lint` e `npm run build`.
- [ ] Commit: `feat: integra login do frontend com api`.

## Self-Review

- Spec coverage: cobre types, schemas/forms, base Axios, services por recurso, store e login real preparado.
- Scope check: telas completas de chamados, usuarios e choices ficam fora; somente login e base de integracao entram agora.
- Placeholder scan: nao ha decisoes pendentes neste plano.
- Type consistency: os nomes produzidos por cada camada sao consumidos por camadas posteriores.

# Frontend Shell Inicial Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Criar o scaffold navegavel do frontend em Next.js 15.5.23 com Tailwind CSS 3, Zod, Zustand e Axios, sem testes automatizados no frontend neste ciclo.

**Architecture:** O frontend fica em `frontend/` usando Next.js App Router. A integracao HTTP fica em `src/services/api.ts` com uma instancia Axios base; services de dominio futuros devem importar essa base. Schemas Zod ficam em `src/schemas/`, stores Zustand em `src/stores/`, configuracao publica em `src/config/`, rotas em `src/routes/`, tipos em `src/types/`, componentes compartilhados em `src/components/` e fluxos de tela em `src/features/`.

**Tech Stack:** Next.js 15.5.23 App Router, React, TypeScript 5.9.3, Tailwind CSS 3, Zod, Zustand, Axios, npm.

## Global Constraints

- Usar Next.js 15.5.23 com App Router, React e TypeScript.
- Manter o frontend dentro de `frontend/`, separado do backend.
- Usar Tailwind CSS 3 para estilos utilitarios e responsivos desde o scaffold inicial.
- Usar Zod para validacao de formularios, variaveis de ambiente publicas e contratos de resposta consumidos da API.
- Usar Zustand para estado cliente leve, iniciando por sessao simulada e estado de UI que precise ser compartilhado entre componentes.
- Usar Axios para chamadas HTTP.
- Criar `src/services/api.ts` com a instancia base do Axios.
- Services de dominio devem ficar em `src/services/` e chamar a base Axios para acessar endpoints.
- Nao criar testes automatizados no frontend neste ciclo.
- Verificar frontend com `npm run lint` e `npm run build`.
- Manter `npm audit --omit=dev` sem vulnerabilidades conhecidas de producao.

---

### Task 1: Ajustar Dependencias E Remover Testes

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/package-lock.json`
- Delete: `frontend/vitest.config.mts`
- Delete: `frontend/src/test/setup.ts`
- Delete: `frontend/**/*.test.ts`

**Interfaces:**
- Produces: scripts `dev`, `build`, `start`, `lint`; dependency `axios`.

- [ ] Remover scripts e dependencias de teste do frontend.
- [ ] Adicionar `axios` em dependencies.
- [ ] Rodar `npm install` em `frontend/`.
- [ ] Remover arquivos de teste e configuracao Vitest.

### Task 2: Reorganizar Pastas Base

**Files:**
- Create: `frontend/src/services/api.ts`
- Create: `frontend/src/schemas/auth.schema.ts`
- Create: `frontend/src/stores/session.store.ts`
- Modify/Delete: arquivos antigos em `src/lib/http` e `src/features/auth`.

**Interfaces:**
- Produces: `api`, `loginFormSchema`, `LoginFormData`, `useSessionStore`.

- [ ] Mover schema de login para `src/schemas/auth.schema.ts`.
- [ ] Mover store de sessao para `src/stores/session.store.ts`.
- [ ] Substituir cliente fetch por base Axios em `src/services/api.ts`.
- [ ] Remover pastas antigas `src/lib/http` e arquivos de teste.

### Task 3: Atualizar Shell E Marcadores

**Files:**
- Create: `.gitkeep` nas pastas planejadas vazias.
- Modify: docs se necessario.

**Interfaces:**
- Produces: estrutura de pastas versionada para proximos ciclos.

- [ ] Criar `.gitkeep` em pastas vazias planejadas.
- [ ] Rodar `npm run lint`.
- [ ] Rodar `npm run build`.
- [ ] Rodar `make backend-test`.
- [ ] Commitar ajuste.

## Self-Review

- Spec coverage: o plano cobre remocao de testes do front, Axios base, convencao de pastas, Zod, Zustand, Tailwind e verificacao por lint/build.
- Placeholder scan: nao ha decisoes diferidas.
- Type consistency: `LoginFormData`, `useSessionStore` e `api` sao definidos nos novos caminhos antes de uso por telas futuras.

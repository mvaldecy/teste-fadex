# Frontend Chamados e Comentarios API Design

## Objetivo

Integrar o frontend aos endpoints de criacao de chamados e comentarios publicados no backend, entregando uma primeira experiencia funcional para abrir chamados, consultar detalhes e acompanhar comentarios.

## Escopo

- Criar a branch `feature(frontend)/chamados-comentarios-api` a partir de `dev`.
- Manter a implementacao restrita ao frontend, salvo ajustes pequenos de documentacao de convencoes.
- Adicionar tipos, schemas e services para `POST /api/v1/tickets` e `GET/POST /api/v1/tickets/{ticketId}/comments`.
- Evoluir a home inicial para uma area operacional de chamados com listagem, filtros basicos, detalhe, criacao de chamado e comentarios.
- Consumir labels de status, prioridade, categoria e origem por `GET /api/v1/choices`; o frontend nao deve criar novas regras de label para enums.
- Melhorar o `AGENTS.md` da raiz apenas se necessario para deixar claro que as convencoes especificas ficam em `backend/AGENTS.md` e `frontend/AGENTS.md`.

Fora deste escopo:

- Alteracao de status, atribuicao e historico de eventos.
- CRUD completo de usuarios.
- Notificacoes em tempo real.
- Implementacao de IA/classificacao no frontend.
- Mudancas no contrato do backend.

## Arquitetura

A integracao segue as camadas ja existentes no frontend:

- `src/types`: contratos TypeScript da API.
- `src/schemas`: validacao de formularios e filtros com Zod.
- `src/services`: chamadas HTTP por recurso.
- `src/features/tickets`: componentes e hooks especificos da experiencia de chamados.
- `app/(dashboard)/home/page.tsx`: entrada da area autenticada, mantendo a pagina fina e delegando regra para a feature.

Os comentarios ficam no mesmo dominio de feature dos chamados, mas com tipos e service methods explicitos para preservar responsabilidade unica.

## Fluxo de Dados

Na carga da home autenticada, o frontend busca choices e a primeira pagina de chamados. A listagem mostra dados resumidos de `TicketSummary`; ao selecionar um chamado, o frontend carrega `TicketDto` e a primeira pagina de comentarios.

Ao criar um chamado, o formulario envia apenas `title` e `description`, porque o solicitante vem do token no backend. A resposta criada pode ser usada para atualizar a selecao e recarregar a listagem.

Ao criar um comentario, o formulario envia apenas `text`, porque o autor vem do token no backend. A resposta criada deve atualizar a lista de comentarios do chamado selecionado.

## UI

A tela principal deve ser densa e operacional, sem landing page. Em desktop, a experiencia pode usar listagem e painel de detalhe lado a lado. Em telas menores, a listagem e o detalhe devem empilhar em blocos legiveis, com acoes principais acessiveis sem depender de scroll horizontal.

Estados esperados:

- carregando choices/listagem;
- listagem vazia;
- erro normalizado da API;
- chamado selecionado;
- formulario de novo chamado;
- comentarios vazios;
- envio de chamado ou comentario em andamento.

## Erros

Erros HTTP devem passar por `toApiErrorMessage`. Validacoes de formulario ficam nos schemas locais e devem cobrir:

- `title`: obrigatorio e maximo 160 caracteres;
- `description`: obrigatorio;
- `text`: obrigatorio;
- filtros opcionais com strings vazias convertidas para `undefined`.

## Testes e Validacao

O frontend ainda nao possui testes automatizados de UI. A validacao deste incremento sera:

- `make frontend-lint`;
- `make frontend-build`;
- teste manual com backend local ou stack Docker: login, listar chamados, criar chamado, abrir detalhe, listar comentarios e criar comentario.

## Criterios de Aceite

- Worktree criado em `.worktrees/frontend-chamados-comentarios-api`.
- Branch de trabalho `feature(frontend)/chamados-comentarios-api`.
- Home deixa de ser placeholder e passa a oferecer fluxo funcional de chamados.
- `ticketsService` cobre listagem, detalhe e criacao de chamado.
- Ha service ou metodos dedicados para listar e criar comentarios de um chamado.
- Labels de choices exibidas na UI vêm de `/choices`.
- `AGENTS.md` da raiz aponta claramente para `backend/AGENTS.md` e `frontend/AGENTS.md`.
- `make frontend-lint` e `make frontend-build` passam.

## Self-Review

- Placeholder scan: nao ha `TBD`, `TODO` ou campos em aberto.
- Consistencia: o escopo usa apenas endpoints ja documentados em `docs/backend/api.md`.
- Escopo: a entrega fica restrita ao frontend e a ajuste pequeno de documentacao.
- Ambiguidade: alteracao de status, atribuicao, historico e IA estao explicitamente fora.

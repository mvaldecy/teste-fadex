# Frontend Chamados e Comentarios API Design

## Objetivo

Integrar o frontend aos endpoints de criacao de chamados e comentarios publicados no backend, entregando uma primeira experiencia funcional para abrir chamados, consultar detalhes e acompanhar comentarios.

## Escopo

- Criar a branch `feature(frontend)/chamados-comentarios-api` a partir de `dev`.
- Manter a implementacao restrita ao frontend, salvo ajustes pequenos de documentacao de convencoes.
- Adicionar tipos, schemas e services para `POST /api/v1/tickets` e `GET/POST /api/v1/tickets/{ticketId}/comments`.
- Adicionar uma base de UI com `shadcn/ui`, usando componentes versionados no proprio repositorio em `frontend/src/components/ui`.
- Adicionar toasts com Sonner para feedback de operacoes assincronas.
- Evoluir a home inicial para uma area operacional de chamados com listagem, filtros basicos, detalhe, criacao de chamado e comentarios.
- Criar hooks especificos da feature para orquestrar carregamento, selecao, criacao, comentarios, estados de erro e refresh.
- Consumir labels de status, prioridade, categoria e origem por `GET /api/v1/choices`; o frontend nao deve criar novas regras de label para enums.
- Melhorar o `AGENTS.md` da raiz apenas se necessario para deixar claro que as convencoes especificas ficam em `backend/AGENTS.md` e `frontend/AGENTS.md`.

Fora deste escopo:

- Alteracao de status, atribuicao e historico de eventos.
- CRUD completo de usuarios.
- Notificacoes em tempo real.
- Conexao SSE para eventos de chamados; a implementacao deve apenas deixar um ponto claro de extensao para esse hook futuro.
- Implementacao de IA/classificacao no frontend.
- Mudancas no contrato do backend.

## Arquitetura

A integracao segue as camadas ja existentes no frontend:

- `src/types`: contratos TypeScript da API.
- `src/schemas`: validacao de formularios e filtros com Zod.
- `src/services`: chamadas HTTP por recurso.
- `src/components/ui`: componentes base do `shadcn/ui`, versionados como codigo local.
- `src/features/tickets`: hooks e componentes especificos da experiencia de chamados.
- `app/(dashboard)/home/page.tsx`: entrada da area autenticada, mantendo a pagina fina e delegando regra para a feature.

`shadcn/ui` sera usado como base visual e de acessibilidade para controles comuns, como botoes, inputs, textarea, selects, badges, dialog/sheet e Sonner. A implementacao deve manter os componentes copiados pequenos e sem regra de dominio; regra de chamados continua em `src/features/tickets`.

Os services devem permanecer stateless e focados em HTTP. A coordenacao de dados da tela fica em hooks da feature, por exemplo um hook para o workspace de chamados e outro para comentarios do chamado selecionado. Esses hooks concentram loading, erro normalizado, selecao, refresh e callbacks de criacao, mantendo componentes focados em renderizacao e interacao.

Os comentarios ficam no mesmo dominio de feature dos chamados, mas com tipos, service methods e hooks explicitos para preservar responsabilidade unica.

Para preparar a evolucao com eventos em tempo real, o desenho deve reservar uma fronteira propria para SSE, como `useTicketEvents` ou hook equivalente em `src/features/tickets`. Esse hook futuro devera consumir eventos e acionar refresh/atualizacao incremental nos hooks de dados, sem acoplar `EventSource` diretamente a componentes de lista, detalhe ou formularios.

## Fluxo de Dados

Na carga da home autenticada, um hook da feature busca choices e a primeira pagina de chamados. A listagem mostra dados resumidos de `TicketSummary`; ao selecionar um chamado, outro hook carrega `TicketDto` e a primeira pagina de comentarios.

Ao criar um chamado, o formulario envia apenas `title` e `description`, porque o solicitante vem do token no backend. A resposta criada pode ser usada para atualizar a selecao e recarregar a listagem.

Ao criar um comentario, o formulario envia apenas `text`, porque o autor vem do token no backend. A resposta criada deve atualizar a lista de comentarios do chamado selecionado.

Quando SSE for publicado pela API, o fluxo esperado e adicionar um hook de eventos que assina mudancas de chamados/comentarios e chama os mesmos pontos de refresh usados por criacao manual. Neste ciclo, esses pontos de refresh devem existir, mas sem abrir conexao de tempo real.

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

Toasts devem ser usados para confirmacoes e falhas de operacoes assincronas, como chamado criado, comentario publicado, falha ao salvar chamado e falha ao publicar comentario. Erros de validacao de formulario continuam inline, perto do campo afetado, para nao depender de toast como unica forma de correcao.

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
- `shadcn/ui` fica configurado no frontend com componentes base usados pela tela de chamados.
- Sonner fica montado no layout raiz e e usado para feedback de criacao de chamados/comentarios.
- Hooks de `src/features/tickets` concentram busca, selecao, envio, refresh, loading e erro da experiencia de chamados.
- A arquitetura deixa ponto de extensao documentado para hook futuro de SSE sem implementar tempo real neste ciclo.
- Labels de choices exibidas na UI vêm de `/choices`.
- `AGENTS.md` da raiz aponta claramente para `backend/AGENTS.md` e `frontend/AGENTS.md`.
- `make frontend-lint` e `make frontend-build` passam.

## Self-Review

- Placeholder scan: nao ha `TBD`, `TODO` ou campos em aberto.
- Consistencia: o escopo usa apenas endpoints ja documentados em `docs/backend/api.md`.
- Escopo: a entrega fica restrita ao frontend e a ajuste pequeno de documentacao; SSE fica preparado como fronteira, mas nao implementado.
- Ambiguidade: alteracao de status, atribuicao, historico, SSE e IA estao explicitamente fora.

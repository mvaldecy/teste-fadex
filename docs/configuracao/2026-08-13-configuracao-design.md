# Configuracao Inicial do Projeto Fadex Helpdesk

## Contexto

O desafio tecnico pede uma central de chamados internos com autenticacao, autorizacao por papel, CRUD de chamados, comentarios/historico, triagem inteligente e indicadores em tempo real. O prazo de submissao informado no PDF e 15/08/2026 as 12h.

Esta spec define a base arquitetural inicial. O objetivo do primeiro ciclo nao e implementar regra de negocio, mas criar uma fundacao limpa para commits e PRs pequenos.

## Decisoes Aprovadas

- O repositorio sera um monorepo.
- A branch padrao de desenvolvimento local sera `dev`.
- As branches `hmg` e `prod` existem para estabilizacao/homologacao e entrega.
- O desenvolvimento sera feito com PRs, mesmo sendo um projeto individual.
- PR stacks poderao ser usados quando uma entrega depender de outra, evitando PRs grandes.
- Mensagens de commit, titulos de PR e descricoes de PR devem ser escritos em portugues.
- A aplicacao nao rodara em Docker durante o desenvolvimento diario.
- O PostgreSQL rodara em Docker desde o inicio.
- O frontend ficara previsto no monorepo, mas inicialmente vazio com `.gitkeep`.
- O backend sera inicializado em Spring Boot sem implementar funcionalidades neste primeiro ciclo.

## Estrutura do Repositorio

```text
teste-fadex/
  backend/
  frontend/
    .gitkeep
  docs/
    configuracao/
      convencoes-git.md
    backend/
    frontend/
    ia/
    infraestrutura/
  infra/
  docker-compose.yml
  .env.example
  README.md
```

As specs e planos devem ficar em `docs/` separados por subdominio. Para este ciclo, o subdominio e `configuracao`.

## Stack Planejada

Backend:

- Java 21
- Spring Boot 3
- Spring Web
- Spring Security com JWT stateless
- Spring Data JPA
- PostgreSQL
- Flyway
- Swagger/OpenAPI
- SSE para indicadores em tempo real

Frontend:

- React
- Next.js
- Consumo da API REST
- Choices carregados do backend, sem labels de enum hardcoded

Infraestrutura local:

- PostgreSQL via Docker Compose
- Backend e frontend executados localmente, fora do Docker
- Futuro servico local de IA em container separado

## Estrutura Base do Backend

O backend deve seguir camadas claras, mas mantendo entidades, DTOs e mappers agrupados por modelo.

```text
backend/src/main/java/.../
  config/
  security/
  model/
    user/
    ticket/
    comment/
    choices/
    enums/
  repository/
  service/
  controller/
  exception/
```

Neste primeiro ciclo, essas pastas podem ser criadas com `.gitkeep` quando ainda nao houver codigo real. O objetivo e preparar a organizacao sem ampliar escopo.

## Modelo de Dominio Planejado

Entidades principais:

- `User`
- `Ticket`
- `Comment` ou `TicketHistory`

Enums planejados:

- `Role`: `ADMIN`, `SOLICITANTE`
- `TicketStatus`: `ABERTO`, `EM_ANDAMENTO`, `RESOLVIDO`, `FECHADO`
- `TicketPriority`: `BAIXA`, `MEDIA`, `ALTA`
- `TicketCategory`: `ACESSO`, `SISTEMAS`, `INFRAESTRUTURA`, `EQUIPAMENTOS`, `FINANCEIRO`, `RH`, `OUTROS`
- `ClassificationOrigin`: `IA`, `MANUAL`, `PENDENTE`

Cada enum deve carregar `value` e `label`. O frontend nao deve conhecer labels por conta propria.

## Choices

O backend tera um endpoint agregado para entregar opcoes ao frontend:

```http
GET /api/v1/choices
```

Resposta planejada:

```json
{
  "roles": [],
  "ticketStatuses": [],
  "ticketPriorities": [],
  "ticketCategories": [],
  "classificationOrigins": []
}
```

Cada item seguira o formato:

```json
{
  "value": "EM_ANDAMENTO",
  "label": "Em andamento"
}
```

## Triagem Inteligente

O backend tera um `TriageService` desde o inicio para preservar o ponto de extensao.

Na primeira implementacao funcional:

- o servico apenas registrara log em modo `noop`;
- o chamado podera nascer com `categoria = OUTROS`;
- o chamado podera nascer com `prioridade = MEDIA`;
- o chamado nascera com `origemClassificacao = PENDENTE`.

Depois, a implementacao sera substituida ou complementada por chamada HTTP para um servico local de IA rodando em Docker.

## Fluxo de Branches e PRs

Fluxo base:

- `dev`: desenvolvimento ativo
- `hmg`: homologacao/estabilizacao
- `prod`: entrega final

Cada mudanca relevante deve entrar por branch curta a partir de `dev`, por exemplo:

- `feature/configuracao-base`
- `feature/backend-auth`
- `feature/backend-tickets-crud`
- `feature/frontend-shell`

Commits devem ser granulares e legiveis. O repositorio nao deve ser entregue com commit unico de projeto final.

As convencoes detalhadas de branches, commits e PRs ficam em `docs/configuracao/convencoes-git.md`.

## Primeiro Ciclo de Implementacao

O primeiro PR deve conter apenas:

- scaffold do projeto Spring Boot em `backend/`;
- estrutura de pastas acordada no backend;
- pasta `frontend/` vazia com `.gitkeep`;
- estrutura inicial de `docs/`;
- configuracao basica de Git/README se necessario;
- sem implementar CRUD, auth, IA ou frontend.

Esse recorte reduz o risco e cria um ponto limpo para os proximos PRs.

## Fora de Escopo Neste Ciclo

- Implementar autenticacao.
- Implementar entidades JPA.
- Implementar CRUD de chamados.
- Implementar SSE.
- Implementar IA local.
- Inicializar Next.js.
- Criar deploy.

## Revisao da Spec

- Nao ha placeholders pendentes.
- A estrutura de docs por subdominio esta explicitada.
- O primeiro ciclo esta limitado a configuracao/base.
- O desenho preserva a evolucao para CRUD, frontend e IA local sem acoplar essas entregas ao scaffold inicial.

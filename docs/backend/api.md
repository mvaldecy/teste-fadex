# Contrato da API

Base local:

```text
http://localhost:8080
```

Swagger:

```text
http://localhost:8080/swagger-ui.html
```

## Autenticacao

Endpoints publicos:

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `GET /api/v1/choices`
- `/swagger-ui.html`
- `/swagger-ui/**`
- `/v3/api-docs/**`

Endpoints protegidos devem receber:

```http
Authorization: Bearer <accessToken>
```

Login de desenvolvimento criado pelo seed do profile `dev`:

```json
{
  "email": "admin@fadex.org.br",
  "password": "admin123"
}
```

## Erros

Todos os erros tratados seguem o formato:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Existem campos invalidos na requisicao.",
  "status": 400,
  "path": "/api/v1/users",
  "timestamp": "2026-08-13T20:00:00",
  "fields": [
    {
      "field": "email",
      "message": "must be a well-formed email address"
    }
  ]
}
```

Codigos usados ate agora:

- `VALIDATION_ERROR`
- `INVALID_PARAMETER`
- `INVALID_BODY`
- `UNAUTHORIZED`
- `FORBIDDEN`
- `NOT_FOUND`
- `CONFLICT`
- `INTERNAL_ERROR`

## Paginacao

Listagens usam o formato padrao de `Page` do Spring.

Query params comuns:

- `page`: pagina atual, iniciando em `0`
- `size`: tamanho da pagina. Padrao atual: `10`
- `sort`: campo e direcao. Exemplo: `createdAt,desc`

Ordenacao padrao das listagens:

```text
createdAt,desc
```

Exemplo de resposta paginada:

```json
{
  "content": [],
  "pageable": {},
  "totalElements": 0,
  "totalPages": 0,
  "last": true,
  "size": 10,
  "number": 0,
  "sort": {},
  "first": true,
  "numberOfElements": 0,
  "empty": true
}
```

## Choices

### `GET /api/v1/choices`

Publico. Entrega labels de enums para o front nao hardcodar.

Resposta:

```json
{
  "roles": [
    {
      "value": "ADMIN",
      "label": "Administrador"
    }
  ],
  "ticketStatuses": [
    {
      "value": "ABERTO",
      "label": "Aberto"
    }
  ],
  "ticketPriorities": [
    {
      "value": "MEDIA",
      "label": "Media"
    }
  ],
  "ticketCategories": [
    {
      "value": "SISTEMAS",
      "label": "Sistemas"
    }
  ],
  "classificationOrigins": [
    {
      "value": "PENDENTE",
      "label": "Pendente"
    }
  ]
}
```

Valores atuais:

- `roles`: `ADMIN`, `SOLICITANTE`
- `ticketStatuses`: `ABERTO`, `EM_ANDAMENTO`, `RESOLVIDO`, `FECHADO`
- `ticketPriorities`: `BAIXA`, `MEDIA`, `ALTA`
- `ticketCategories`: `ACESSO`, `SISTEMAS`, `INFRAESTRUTURA`, `EQUIPAMENTOS`, `FINANCEIRO`, `RH`, `OUTROS`
- `classificationOrigins`: `IA`, `MANUAL`, `PENDENTE`

## Auth

### `POST /api/v1/auth/login`

Publico.

Request:

```json
{
  "email": "admin@fadex.org.br",
  "password": "admin123"
}
```

Response `200`:

```json
{
  "accessToken": "<jwt>",
  "refreshToken": "<refresh-token-ou-null>",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "mustChangePassword": false,
  "role": "ADMIN",
  "user": {
    "id": "00000000-0000-0000-0000-000000000000",
    "name": "Administrador"
  }
}
```

Quando `mustChangePassword` for `true`, `refreshToken` retorna `null` e o `accessToken` e limitado para uso em `POST /api/v1/auth/change-password`.

### `POST /api/v1/auth/refresh`

Publico.

Request:

```json
{
  "refreshToken": "<refresh-token>"
}
```

Response `200`:

```json
{
  "accessToken": "<jwt>",
  "refreshToken": "<refresh-token>",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "mustChangePassword": false,
  "role": "ADMIN",
  "user": {
    "id": "00000000-0000-0000-0000-000000000000",
    "name": "Administrador"
  }
}
```

Response `401` quando o refresh token for invalido, expirado, revogado ou quando o usuario ainda tiver troca de senha obrigatoria.

### `POST /api/v1/auth/change-password`

Protegido. Exige `Authorization: Bearer <accessToken>` com token limitado de troca de senha obrigatoria.

Request:

```json
{
  "currentPassword": "senha-provisoria",
  "newPassword": "nova-senha-segura",
  "confirmPassword": "nova-senha-segura"
}
```

Regras de validacao:

- `currentPassword`: obrigatorio
- `newPassword`: obrigatorio, minimo 8 e maximo 72 caracteres
- `confirmPassword`: obrigatorio e deve ser igual a `newPassword`

Response `200`:

```json
{
  "accessToken": "<jwt>",
  "refreshToken": "<refresh-token>",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "mustChangePassword": false,
  "role": "SOLICITANTE",
  "user": {
    "id": "00000000-0000-0000-0000-000000000000",
    "name": "Solicitante"
  }
}
```

Response `403` quando o endpoint for chamado com token normal ou quando um token limitado tentar acessar qualquer outro endpoint protegido.

## Usuarios

Usuarios com role `SOLICITANTE` enxergam apenas os proprios usuarios e chamados.
Ao listar chamados, a API forca `requesterId` igual ao usuario autenticado.
Ao listar usuarios, a API forca `id` igual ao usuario autenticado.

### `GET /api/v1/users`

Protegido.

Filtros:

- `id`: UUID
- `role`: `ADMIN` ou `SOLICITANTE`
- `name`: busca parcial por nome
- `email`: busca parcial por e-mail
- `search`: busca parcial por nome ou e-mail

Tambem aceita `page`, `size` e `sort`.

Response `200`:

```json
{
  "content": [
    {
      "id": "00000000-0000-0000-0000-000000000000",
      "name": "Administrador"
    }
  ]
}
```

### `GET /api/v1/users/{id}`

Protegido.

Response `200`:

```json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "name": "Administrador",
  "email": "admin@fadex.org.br",
  "role": "ADMIN",
  "mustChangePassword": false,
  "createdAt": "2026-08-13T20:00:00",
  "updatedAt": "2026-08-13T20:00:00"
}
```

Response `404` quando nao encontrar.

### `POST /api/v1/users`

Protegido.

Request:

```json
{
  "name": "Solicitante",
  "email": "solicitante@fadex.org.br",
  "role": "SOLICITANTE"
}
```

Regras de validacao:

- `name`: obrigatorio, maximo 120 caracteres
- `email`: obrigatorio, formato de e-mail, maximo 180 caracteres
- `role`: obrigatorio

O backend gera uma senha provisoria, marca `mustChangePassword` como `true` e envia a senha pelo mecanismo de e-mail configurado.

Response `201`: `UserDto`.

```json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "name": "Solicitante",
  "email": "solicitante@fadex.org.br",
  "role": "SOLICITANTE",
  "mustChangePassword": true,
  "createdAt": "2026-08-13T20:00:00",
  "updatedAt": "2026-08-13T20:00:00"
}
```

Response `409` quando o e-mail ja existir.

## Chamados

### `POST /api/v1/tickets`

Protegido.

O solicitante do chamado e definido pelo usuario autenticado no token. O front nao envia dados do solicitante.

Request:

```json
{
  "title": "Erro ao acessar sistema",
  "description": "Nao consigo acessar o sistema interno."
}
```

Regras de validacao:

- `title`: obrigatorio, maximo 160 caracteres
- `description`: obrigatorio

Response `201`:

```json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "title": "Erro ao acessar sistema",
  "description": "Nao consigo acessar o sistema interno.",
  "category": "OUTROS",
  "priority": "MEDIA",
  "status": "ABERTO",
  "classificationOrigin": "PENDENTE",
  "requester": {
    "id": "00000000-0000-0000-0000-000000000000",
    "name": "Solicitante"
  },
  "assignee": null,
  "createdAt": "2026-08-13T20:00:00",
  "updatedAt": "2026-08-13T20:00:00"
}
```

### `GET /api/v1/tickets`

Protegido.

Filtros:

- `status`: `ABERTO`, `EM_ANDAMENTO`, `RESOLVIDO`, `FECHADO`
- `priority`: `BAIXA`, `MEDIA`, `ALTA`
- `category`: `ACESSO`, `SISTEMAS`, `INFRAESTRUTURA`, `EQUIPAMENTOS`, `FINANCEIRO`, `RH`, `OUTROS`
- `requesterId`: UUID
- `assigneeId`: UUID
- `search`: busca parcial por titulo ou descricao

Tambem aceita `page`, `size` e `sort`.

Response `200`:

```json
{
  "content": [
    {
      "id": "00000000-0000-0000-0000-000000000000",
      "title": "Erro ao acessar sistema",
      "category": "SISTEMAS",
      "priority": "MEDIA",
      "status": "ABERTO",
      "classificationOrigin": "PENDENTE",
      "requester": {
        "id": "00000000-0000-0000-0000-000000000000",
        "name": "Solicitante"
      },
      "assignee": null,
      "createdAt": "2026-08-13T20:00:00"
    }
  ]
}
```

### `GET /api/v1/tickets/{id}`

Protegido.

Response `200`:

```json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "title": "Erro ao acessar sistema",
  "description": "Nao consigo acessar o sistema interno.",
  "category": "SISTEMAS",
  "priority": "MEDIA",
  "status": "ABERTO",
  "classificationOrigin": "PENDENTE",
  "requester": {
    "id": "00000000-0000-0000-0000-000000000000",
    "name": "Solicitante"
  },
  "assignee": null,
  "createdAt": "2026-08-13T20:00:00",
  "updatedAt": "2026-08-13T20:00:00"
}
```

Response `404` quando nao encontrar.

### `GET /api/v1/tickets/{ticketId}/events`

Protegido.

Lista o historico de eventos do chamado. O usuario precisa ter acesso ao chamado; usuarios `SOLICITANTE` so acessam eventos dos proprios chamados.

Filtros:

- `actorId`: UUID
- `type`: `CHAMADO_CRIADO`, `COMENTARIO_ADICIONADO`, `STATUS_ALTERADO`, `RESPONSAVEL_ATRIBUIDO`, `PRIORIDADE_ALTERADA`, `CATEGORIA_ALTERADA`, `CLASSIFICACAO_ATUALIZADA`
- `search`: busca parcial pela descricao do evento

Tambem aceita `page`, `size` e `sort`.

Ordenacao padrao:

```text
createdAt,desc
```

Response `200`:

```json
{
  "content": [
    {
      "id": "00000000-0000-0000-0000-000000000000",
      "actor": {
        "id": "00000000-0000-0000-0000-000000000000",
        "name": "Administrador"
      },
      "type": "COMENTARIO_ADICIONADO",
      "description": "Comentario adicionado.",
      "createdAt": "2026-08-13T20:00:00"
    }
  ]
}
```

Response `404` quando o chamado nao existir.

## Comentarios

### `GET /api/v1/tickets/{ticketId}/comments`

Protegido.

Filtros:

- `authorId`: UUID
- `search`: busca parcial pelo texto do comentario

Tambem aceita `page`, `size` e `sort`.

Response `200`:

```json
{
  "content": [
    {
      "id": "00000000-0000-0000-0000-000000000000",
      "author": {
        "id": "00000000-0000-0000-0000-000000000000",
        "name": "Administrador"
      },
      "text": "Consegui reproduzir o erro.",
      "createdAt": "2026-08-13T20:00:00"
    }
  ]
}
```

### `POST /api/v1/tickets/{ticketId}/comments`

Protegido.

O autor do comentario e definido pelo usuario autenticado no token. O front nao envia dados do autor.

Request:

```json
{
  "text": "Consegui reproduzir o erro."
}
```

Regras de validacao:

- `text`: obrigatorio

Response `201`:

```json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "author": {
    "id": "00000000-0000-0000-0000-000000000000",
    "name": "Administrador"
  },
  "text": "Consegui reproduzir o erro.",
  "createdAt": "2026-08-13T20:00:00",
  "updatedAt": "2026-08-13T20:00:00"
}
```

Response `404` quando o chamado nao existir.

## Notificacoes

### `GET /api/v1/notifications/stream`

Protegido. Abre um fluxo Server-Sent Events com as notificacoes do usuario autenticado. A resposta e `text/event-stream` e permanece aberta.

Autenticacao usa o mesmo `Authorization: Bearer <token>` do restante da API. Como o `EventSource` nativo do navegador nao envia headers, o cliente deve consumir o stream via `fetch` com leitura incremental do corpo.

Evento inicial, enviado assim que a conexao e aceita:

```
event: CONEXAO_ESTABELECIDA
id: 4f1c8b2a-1d2e-4f3a-8b9c-0d1e2f3a4b5c
retry: 5000
data: {"connectionId":"4f1c8b2a-1d2e-4f3a-8b9c-0d1e2f3a4b5c","serverTime":"2026-08-14T15:54:58"}
```

A cada vinte segundos o servidor envia um comentario de keep-alive, ignorado pelo parser SSE:

```
: ping
```

Sem token valido, a resposta e `401` no formato padrao de erro da API.

Nao ha reenvio de eventos perdidos: o cabecalho `Last-Event-ID` nao e tratado. Ao reconectar, o cliente deve recarregar os dados pelo endpoint REST correspondente.

## Pendencias Conhecidas

Ja implementado e disponivel (nao reimplementar):

- Triagem automatica por IA, com worker Quartz, cliente local de classificacao e de embedding e
  classificador de fallback. Chamados criados pela API entram na fila de classificacao.
- Motor de notificacoes em tempo real por SSE, em `GET /api/v1/notifications/stream`, com
  audiencia por usuario, por papel ou para todos.
- Historico de eventos do chamado, em `GET /api/v1/tickets/{ticketId}/events`.

Ainda pendente:

- Atualizacao de status e atribuicao de responsavel.
- Revisao da sugestao da IA pelo ADMIN, aceitando ou corrigindo a classificacao.
- Indicadores agregados e alerta de chamado com prioridade ALTA.
- Exposicao dos jobs de IA para o ADMIN; `AiJobService.retry` existe e ainda nao tem endpoint.
- Deteccao de duplicados; a tabela `ticket_links` existe desde a V3 e ainda nao e usada.

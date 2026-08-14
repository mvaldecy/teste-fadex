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
  "classificationJustification": null,
  "requester": {
    "id": "00000000-0000-0000-0000-000000000000",
    "name": "Solicitante"
  },
  "assignee": null,
  "assignedAt": null,
  "firstResponseAt": null,
  "resolvedAt": null,
  "closedAt": null,
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
      "assignedAt": null,
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
  "classificationJustification": null,
  "requester": {
    "id": "00000000-0000-0000-0000-000000000000",
    "name": "Solicitante"
  },
  "assignee": null,
  "assignedAt": null,
  "firstResponseAt": null,
  "resolvedAt": null,
  "closedAt": null,
  "createdAt": "2026-08-13T20:00:00",
  "updatedAt": "2026-08-13T20:00:00"
}
```

Response `404` quando nao encontrar.

## Ciclo de Vida do Chamado

Os tres endpoints desta secao sao exclusivos de `ADMIN`. Um `SOLICITANTE` recebe `403` em todos,
mesmo no proprio chamado.

### Carimbos de tempo

Alem de `createdAt` e `updatedAt`, o chamado tem quatro carimbos, todos anulaveis e todos
presentes no `TicketDto`. `assignedAt` tambem aparece no item de listagem (`TicketMinDto`), por
ser o unico consumido por tela de lista e pelo payload de notificacao.

| Campo | Preenchido quando | Reescrita |
| --- | --- | --- |
| `assignedAt` | primeira atribuicao de responsavel | nunca reescrito, nem ao remover o responsavel |
| `firstResponseAt` | primeiro comentario de um `ADMIN` | nunca reescrito |
| `resolvedAt` | transicao para `RESOLVIDO` | reescrito a cada nova resolucao |
| `closedAt` | transicao para `FECHADO` | escrito uma unica vez |

Ao fechar um chamado que nunca passou por `RESOLVIDO`, `resolvedAt` recebe o mesmo instante de
`closedAt`. Sem isso, chamado fechado direto sairia das metricas de tempo de resolucao.

### Transicoes de status

| De / Para | ABERTO | EM_ANDAMENTO | RESOLVIDO | FECHADO |
| --- | --- | --- | --- | --- |
| `ABERTO` | — | sim | sim | sim |
| `EM_ANDAMENTO` | sim | — | sim | sim |
| `RESOLVIDO` | nao | sim | — | sim |
| `FECHADO` | nao | nao | nao | — |

`FECHADO` e terminal: qualquer transicao a partir dele responde `409`. Transicao para o status
atual tambem responde `409`, em vez de `200` silencioso, para que duplo clique na UI apareca.

`RESOLVIDO` para `ABERTO` nao existe: reabrir um chamado resolvido significa voltar a trabalha-lo,
que corresponde a `EM_ANDAMENTO`.

### `PATCH /api/v1/tickets/{id}/status`

Protegido. Somente `ADMIN`.

Request:

```json
{
  "status": "EM_ANDAMENTO"
}
```

Regras de validacao:

- `status`: obrigatorio, um dos valores de `ticketStatuses`

Response `200`: `TicketDto` com o status novo e os carimbos atualizados.

Registra evento `STATUS_ALTERADO` no historico e emite `CHAMADO_ATUALIZADO` no stream SSE.

| Situacao | Status |
| --- | --- |
| chamado nao encontrado | `404` |
| usuario nao e `ADMIN` | `403` |
| chamado esta `FECHADO` | `409` |
| status igual ao atual | `409` |
| transicao nao permitida pela matriz | `409` |

### `PATCH /api/v1/tickets/{id}/assignee`

Protegido. Somente `ADMIN`.

Request:

```json
{
  "assigneeId": "00000000-0000-0000-0000-000000000000"
}
```

Regras de validacao:

- `assigneeId`: obrigatorio, UUID de usuario existente

**Atribui apenas chamado sem responsavel.** Chamado que ja tem responsavel responde `409`: trocar
de responsavel e `DELETE` seguido de `PATCH`, nunca um `PATCH` sobre o outro. Assim toda troca
deixa os dois eventos no historico, e nao um `RESPONSAVEL_ATRIBUIDO` solitario que apaga quem
saiu.

O responsavel indicado precisa ter papel `ADMIN`. Nao ha regra de "so o proprio": um `ADMIN` pode
se atribuir ou atribuir outro `ADMIN`, indiferentemente. Como os unicos papeis do sistema sao
`ADMIN` e `SOLICITANTE`, responsavel e necessariamente `ADMIN` — o front deve popular o seletor
com `GET /api/v1/users?role=ADMIN`.

Consequencia para a UI: o botao e "Atribuir" quando `assignee` e nulo e "Recusar" quando nao e.
Nunca os dois ao mesmo tempo.

Response `200`: `TicketDto` com o responsavel novo. Preenche `assignedAt` apenas na primeira
atribuicao — uma reatribuicao posterior, depois de um `DELETE`, mantem o carimbo original.
Registra evento `RESPONSAVEL_ATRIBUIDO` e emite `CHAMADO_ATUALIZADO`.

| Situacao | Status |
| --- | --- |
| chamado ou usuario nao encontrado | `404` |
| usuario autenticado nao e `ADMIN` | `403` |
| chamado esta `FECHADO` | `409` |
| chamado ja possui responsavel | `409` |
| responsavel indicado nao tem papel `ADMIN` | `409` |

### `DELETE /api/v1/tickets/{id}/assignee`

Protegido. Somente `ADMIN`. Sem corpo de requisicao.

Cobre a recusa de atribuicao pelo proprio responsavel e a retirada de atribuicao de outro.

Response `200`: `TicketDto` com `assignee` nulo. Devolve o chamado inteiro em vez de `204` para
que o front possa atualizar a tela sem uma segunda chamada; recarregar tambem funciona.

`assignedAt` e preservado — a metrica e "tempo ate a primeira atribuicao" e limpar apagaria o fato
de que a triagem aconteceu.

Registra evento `RESPONSAVEL_REMOVIDO` no historico e emite `CHAMADO_ATUALIZADO`.

| Situacao | Status |
| --- | --- |
| chamado nao encontrado | `404` |
| usuario nao e `ADMIN` | `403` |
| chamado esta `FECHADO` | `409` |
| chamado nao possui responsavel | `409` |

### `GET /api/v1/tickets/{ticketId}/events`

Protegido.

Lista o historico de eventos do chamado. O usuario precisa ter acesso ao chamado; usuarios `SOLICITANTE` so acessam eventos dos proprios chamados.

Filtros:

- `actorId`: UUID
- `type`: `CHAMADO_CRIADO`, `COMENTARIO_ADICIONADO`, `STATUS_ALTERADO`, `RESPONSAVEL_ATRIBUIDO`, `RESPONSAVEL_REMOVIDO`, `PRIORIDADE_ALTERADA`, `CATEGORIA_ALTERADA`, `CLASSIFICACAO_ATUALIZADA`
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

### Eventos de dominio

Cada frame tem `event` com o nome do evento, `id` com um UUID unico e `data` com JSON. O cliente
deve ignorar eventos cujo nome nao reconheca — a lista cresce sem quebrar quem ja consome.

| Evento | Audiencia | `data` |
| --- | --- | --- |
| `CHAMADO_ATUALIZADO` | solicitante e responsavel do chamado | `TicketMinDto` |
| `CHAMADO_ALTA_PRIORIDADE` | todos os `ADMIN` | `TicketMinDto` |
| `CLASSIFICACAO_CONCLUIDA` | solicitante do chamado e todos os `ADMIN` | definido pela frente de IA |
| `JOB_IA_FALHOU` | todos os `ADMIN` | definido pela frente de IA |
| `INDICADORES_ATUALIZADOS` | todos os `ADMIN` | definido pela frente de IA |

`CHAMADO_ATUALIZADO` e emitido em mudanca de status, atribuicao, remocao de responsavel e
reclassificacao. O `data` e o mesmo objeto do item de `GET /api/v1/tickets`, para que a linha da
lista seja atualizada sem uma segunda chamada REST.

`CHAMADO_ALTA_PRIORIDADE` e emitido apenas quando a prioridade **passa a ser** `ALTA`. Chamado que
ja era `ALTA` e reclassificado em outro campo nao gera alerta novo.

Exemplo de frame:

```text
event: CHAMADO_ATUALIZADO
id: 9d2f1a44-3c5b-4e8a-9f10-2b7c6d5e4f31
data: {"id":"00000000-0000-0000-0000-000000000000","title":"Erro ao acessar sistema","category":"SISTEMAS","priority":"ALTA","status":"EM_ANDAMENTO","classificationOrigin":"IA","requester":{"id":"00000000-0000-0000-0000-000000000000","name":"Solicitante"},"assignee":{"id":"00000000-0000-0000-0000-000000000000","name":"Administrador"},"assignedAt":"2026-08-14T10:00:00","createdAt":"2026-08-13T20:00:00"}
```

Os tres eventos da frente de IA tem nome e audiencia fixados aqui; o formato do `data` e definido
por aquela frente.

## Pendencias Conhecidas

Ja implementado e disponivel (nao reimplementar):

- Triagem automatica por IA, com worker Quartz, cliente local de classificacao e de embedding e
  classificador de fallback. Chamados criados pela API entram na fila de classificacao.
- Motor de notificacoes em tempo real por SSE, em `GET /api/v1/notifications/stream`, com
  audiencia por usuario, por papel ou para todos.
- Historico de eventos do chamado, em `GET /api/v1/tickets/{ticketId}/events`.

Ainda pendente:

- Revisao da sugestao da IA pelo ADMIN, aceitando ou corrigindo a classificacao.
- Indicadores agregados e alerta de chamado com prioridade ALTA.
- Exposicao dos jobs de IA para o ADMIN; `AiJobService.retry` existe e ainda nao tem endpoint.
- Deteccao de duplicados; a tabela `ticket_links` existe desde a V3 e ainda nao e usada.

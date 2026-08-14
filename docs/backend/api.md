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

## Frente IA — Classificacao, Indicadores e Jobs

Secao escrita pela frente IA. Todos os endpoints desta secao exigem papel `ADMIN`.

### Compatibilidade com o formato assumido pelo frontend

O frontend comecou a trabalhar contra um formato assumido, achatado e com chaves em portugues. O
formato publicado abaixo e diferente, por tres motivos concretos — nao por preferencia de estilo:

1. **Convencao do projeto.** Todo DTO da API usa chaves em ingles (`classificationOrigin`,
   `createdAt`, `assignee`). Um unico endpoint em portugues quebraria o padrao que o resto do
   contrato ja segue.
2. **Requisito que o formato achatado nao expressa.** O escopo pede tempo de fechamento
   "media, mediana e p90, **por prioridade e categoria**". `tempoFechamentoHoras: {media, mediana,
   p90}` so comporta o numero geral. Por isso cada bloco de duracao tem `overall`, `byPriority` e
   `byCategory`.
3. **`sampleSize` em toda estatistica.** Com 20 chamados, media sem tamanho de amostra engana. Uma
   media de 42h sobre 2 chamados e uma sobre 200 nao sao o mesmo numero, e o painel precisa
   conseguir distinguir.

Mantido do que o front assumiu: campo ausente ou nulo significa "sem dado", e o card deve deixar de
renderizar em vez de quebrar a pagina. **Estatistica sem amostra devolve `null`, nunca `0.0`** —
zero e um valor medido, ausencia de dado nao e.

Mapeamento campo a campo, para o realinhamento ser mecanico:

| Assumido pelo front | Publicado |
| --- | --- |
| `totalPorStatus` | `overview.byStatus` |
| `totalPorPrioridade` | `overview.byPriority` |
| `totalPorCategoria` | `overview.byCategory` |
| `abertosHoje` / `fechadosHoje` | `overview.openedToday` / `overview.closedToday` |
| `abertosNaSemana` / `fechadosNaSemana` | `overview.openedThisWeek` / `overview.closedThisWeek` |
| `altaPrioridadeEmAberto` | `overview.openHighPriority` |
| `tempoFechamentoHoras.{media,mediana,p90}` | `durations.closure.overall.{averageHours,medianHours,p90Hours}` |
| `tempoPrimeiraRespostaHoras` | `durations.firstResponse.overall` |
| `tempoAtribuicaoHoras` | `durations.assignment.overall` |
| `agingBacklog.{ate1Dia,de1A3Dias,acima3Dias}` | `durations.backlogAging.{upToOneDay,oneToThreeDays,overThreeDays}` |
| `idadeChamadoMaisAntigoHoras` | `durations.oldestOpenTicketHours` |
| `percentualDentroDoSla` | `durations.sla.overall.percentage` |
| `concordanciaIaPercentual` | `ai.agreementRate.percentage` |
| `confiancaMediaIa` | `ai.averageConfidence` |
| `distribuicaoClassificacao` | `ai.originDistribution` |
| `filaJobs.pendentes` / `.falhos` | `ai.jobQueue.pending` / `.failed` |
| `filaJobs.tempoMedioProcessamentoSegundos` | `ai.jobQueue.averageQueueToDoneSeconds` (ver nota) |
| `duplicadosDetectados` | `ai.duplicatesDetected` |
| `cargaPorResponsavel[].{responsavel,abertos}` | `workload.openByAssignee[].{user,openTickets}` |
| `topSolicitantes[].{solicitante,total}` | `workload.topRequesters[].{user,tickets}` |
| — (nao assumido) | `workload.closureTimeByAssignee[]` — tempo de fechamento por responsavel |

### `GET /api/v1/indicators`

Resposta `200`:

```json
{
  "generatedAt": "2026-08-14T18:00:00",
  "overview": {
    "total": 20,
    "byStatus": { "ABERTO": 8, "EM_ANDAMENTO": 5, "RESOLVIDO": 4, "FECHADO": 3 },
    "byPriority": { "BAIXA": 6, "MEDIA": 9, "ALTA": 5 },
    "byCategory": { "SISTEMAS": 7, "ACESSO": 4 },
    "openedToday": 3,
    "closedToday": 2,
    "openedThisWeek": 11,
    "closedThisWeek": 7,
    "openHighPriority": 4
  },
  "durations": {
    "closure": {
      "overall": { "sampleSize": 5, "averageHours": 42.5, "medianHours": 30.0, "p90Hours": 96.0 },
      "byPriority": { "ALTA": { "sampleSize": 2, "averageHours": 6.0, "medianHours": 6.0, "p90Hours": 8.0 } },
      "byCategory": { "ACESSO": { "sampleSize": 1, "averageHours": 4.0, "medianHours": 4.0, "p90Hours": 4.0 } }
    },
    "firstResponse": {
      "overall": { "sampleSize": 9, "averageHours": 6.2, "medianHours": 4.0, "p90Hours": 14.0 },
      "byPriority": {},
      "byCategory": {}
    },
    "assignment": {
      "overall": { "sampleSize": 9, "averageHours": 3.1, "medianHours": 2.0, "p90Hours": 8.0 },
      "byPriority": {},
      "byCategory": {}
    },
    "backlogAging": { "upToOneDay": 4, "oneToThreeDays": 6, "overThreeDays": 3 },
    "oldestOpenTicketHours": 480.0,
    "sla": {
      "overall": { "evaluated": 14, "withinTarget": 10, "percentage": 72.5 },
      "byPriority": { "ALTA": { "evaluated": 5, "withinTarget": 2, "percentage": 40.0 } }
    }
  },
  "ai": {
    "agreementRate": { "evaluated": 12, "agreed": 8, "percentage": 68.0 },
    "averageConfidence": 0.81,
    "originDistribution": { "IA": 9, "MANUAL": 7, "PENDENTE": 4 },
    "jobQueue": {
      "pending": 2,
      "processing": 0,
      "failed": 1,
      "done": 37,
      "averageQueueToDoneSeconds": 4.7
    },
    "duplicatesDetected": 3
  },
  "workload": {
    "openByAssignee": [ { "user": { "id": "uuid", "name": "Ana" }, "openTickets": 5 } ],
    "closureTimeByAssignee": [ { "user": { "id": "uuid", "name": "Ana" }, "sampleSize": 3, "averageHours": 18.0, "medianHours": 16.0 } ],
    "topRequesters": [ { "user": { "id": "uuid", "name": "Bruno" }, "tickets": 6 } ]
  }
}
```

Regras de leitura:

- Toda estatistica de duracao carrega `sampleSize`. Se for `0`, os campos de horas vem `null`.
- Mediana: media dos dois centrais quando a amostra e par. `p90Hours`: rank mais proximo.
- Mapas por status/prioridade/categoria **omitem grupos sem ocorrencia** em vez de traze-los zerados.
- `backlogAging` conta apenas chamados em `ABERTO` e `EM_ANDAMENTO`, por idade desde `createdAt`,
  nos cortes 0–1d / 1–3d / >3d.
- `openHighPriority` conta `ALTA` em `ABERTO` ou `EM_ANDAMENTO`. E o numero do alerta de prioridade
  alta.
- SLA: alvos ALTA 4h, MEDIA 24h, BAIXA 72h. Chamado fechado cumpre se fechou dentro do alvo. Chamado
  **ainda aberto e dentro do alvo fica fora do denominador** — so entra como violacao depois de
  estourar. Sem essa regra, todo chamado recem-criado contaria como violacao. `percentage` e `null`
  quando `evaluated` e `0`.
- `topRequesters` traz no maximo 5 itens.
- `averageQueueToDoneSeconds` mede `updatedAt - createdAt` dos jobs `DONE`: fila **mais** execucao.
  `ai_jobs` nao registra o instante de inicio do processamento, entao chamar isso de "tempo medio de
  processamento" seria impreciso; o nome declara a mistura. `null` quando nao ha job concluido.
- `agreementRate` mede aceite real da sugestao da IA: o denominador (`evaluated`) sao os chamados que
  **passaram por revisao do ADMIN** e tinham sugestao registrada; o numerador (`agreed`), aqueles em
  que o ADMIN manteve exatamente a categoria e a prioridade sugeridas. Chamado que a IA classificou e
  ninguem revisou **nao entra na conta** — contar nao-revisado como aceite inflaria a taxa para perto
  de 100% e o numero deixaria de significar qualquer coisa. Ler sempre `evaluated` junto do
  percentual: `percentage` e `null` quando `evaluated` e `0`.

Dispara `INDICADORES_ATUALIZADOS` quando a classificacao de um chamado e concluida pela IA ou
revisada pelo ADMIN.

### `PATCH /api/v1/tickets/{id}/classification`

ADMIN aceita ou corrige a sugestao da IA. Confirmado igual ao que o frontend assumiu — **nao existe
endpoint separado de aceite**: aceitar e reenviar os valores sugeridos sem alteracao.

```json
{
  "category": "SISTEMAS",
  "priority": "ALTA",
  "justification": "texto opcional"
}
```

- `category` e `priority` obrigatorios; `justification` opcional, ate 2000 caracteres.
- Se os valores enviados forem iguais aos sugeridos pela IA, o gesto e aceite e
  `classificationOrigin` permanece `IA`. Se diferirem, vira `MANUAL`.
- Chamado ainda sem sugestao (`PENDENTE`) sempre vira `MANUAL`.
- Aceite e correcao registram `TicketEvent` de `CLASSIFICACAO_ATUALIZADA` — o historico precisa
  mostrar que houve revisao mesmo quando nada mudou. Ambos tambem carimbam o instante da revisao,
  que e o que sustenta o denominador de `agreementRate`.
- Resposta `200` com o `TicketDto` atualizado. `403` para SOLICITANTE, `404` se o chamado nao existe.

### Campos novos no `TicketDto`

```json
{
  "classificationOrigin": "IA",
  "classificationJustification": "Mencao a rede e servidor indica infraestrutura.",
  "aiSuggestedCategory": "INFRAESTRUTURA",
  "aiSuggestedPriority": "ALTA",
  "aiConfidence": 0.87
}
```

Os tres campos `ai*` sao `null` enquanto a IA nao respondeu. `aiConfidence` vai de `0.0` a `1.0`.

### `GET /api/v1/ai/jobs`

Paginado, padrao 10, ordenado por `createdAt` desc. Filtros opcionais: `status`, `type`, `ticketId`.

Item da lista:

```json
{
  "id": "uuid",
  "ticketId": "uuid",
  "type": "CLASSIFICATION",
  "status": "FAILED",
  "attempts": 2,
  "nextAttemptAt": "2026-08-14T18:05:00",
  "lastError": "timeout ao chamar o modelo local",
  "createdAt": "2026-08-14T17:00:00",
  "updatedAt": "2026-08-14T18:00:00"
}
```

**Atencao ao realinhar:** o campo da mensagem de erro chama-se `lastError`, nao `errorMessage`. O
DTO ja existe no backend com esse nome. Ha tambem `nextAttemptAt`, que o front nao havia assumido e
e util na tela de operacao — e quando o job vai ser tentado de novo.

Enums, confirmados: `type` e `CLASSIFICATION` ou `EMBEDDING`; `status` e `PENDING`, `PROCESSING`,
`DONE` ou `FAILED`.

### `POST /api/v1/ai/jobs/{id}/retry`

Sem corpo. Reagenda um job com falha: volta para `PENDING`, limpa `lastError`.

- `200` com o `AiJobDto` atualizado (o front pode ignorar e recarregar a lista).
- `409` quando o job **nao** esta `FAILED` — so job com falha pode ser retentado.
- `404` quando o job nao existe.

### Eventos SSE disparados por esta frente

| Evento | Audiencia | Quando |
| --- | --- | --- |
| `CLASSIFICACAO_CONCLUIDA` | solicitante + `ADMIN` | worker aplicou a classificacao da IA |
| `JOB_IA_FALHOU` | `ADMIN` | job esgotou as tentativas (nao a cada falha) |
| `INDICADORES_ATUALIZADOS` | `ADMIN` | classificacao concluida ou revisada |

`INDICADORES_ATUALIZADOS` leva so `reason` e `occurredAt`, nao o payload de indicadores: o front
refaz o `GET /api/v1/indicators` ao receber o sinal.

## Pendencias Conhecidas

Ja implementado e disponivel (nao reimplementar):

- Triagem automatica por IA, com worker Quartz, cliente local de classificacao e de embedding e
  classificador de fallback. Chamados criados pela API entram na fila de classificacao.
- Motor de notificacoes em tempo real por SSE, em `GET /api/v1/notifications/stream`, com
  audiencia por usuario, por papel ou para todos.
- Historico de eventos do chamado, em `GET /api/v1/tickets/{ticketId}/events`.

Ainda pendente:

- Atualizacao de status e atribuicao de responsavel.

Contrato publicado, implementacao em andamento pela frente IA (ver secao "Frente IA"):

- Revisao da sugestao da IA pelo ADMIN, aceitando ou corrigindo a classificacao.
- Indicadores agregados e alerta de chamado com prioridade ALTA.
- Exposicao dos jobs de IA para o ADMIN; `AiJobService.retry` existe e ainda nao tem endpoint.
- Deteccao de duplicados; a tabela `ticket_links` existe desde a V3 e ainda nao e usada.

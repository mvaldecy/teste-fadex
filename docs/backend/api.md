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

- `status`: `ABERTO`, `EM_ANDAMENTO`, `RESOLVIDO`, `FECHADO`, `CANCELADO`
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

**Cancelamento nao carimba nada.** Chamado `CANCELADO` mantem `resolvedAt` e `closedAt` nulos, e nao
existe coluna `canceledAt` — o instante e o autor do cancelamento ficam no evento
`CHAMADO_CANCELADO` do historico. E o que mantem o chamado cancelado fora da media de tempo de
fechamento e dos contadores de chamado fechado.

### Transicoes de status

| De / Para | ABERTO | EM_ANDAMENTO | RESOLVIDO | FECHADO | CANCELADO |
| --- | --- | --- | --- | --- | --- |
| `ABERTO` | — | sim | sim | sim | sim |
| `EM_ANDAMENTO` | sim | — | sim | sim | sim |
| `RESOLVIDO` | nao | sim | — | sim | nao |
| `FECHADO` | nao | nao | nao | — | nao |
| `CANCELADO` | nao | nao | nao | nao | — |

`FECHADO` e `CANCELADO` sao terminais: qualquer transicao a partir deles responde `409`. Transicao
para o status atual tambem responde `409`, em vez de `200` silencioso, para que duplo clique na UI
apareca.

`RESOLVIDO` para `ABERTO` nao existe: reabrir um chamado resolvido significa voltar a trabalha-lo,
que corresponde a `EM_ANDAMENTO`.

`RESOLVIDO` para `CANCELADO` tambem nao existe: o trabalho ja foi feito, e tirar o chamado do
denominador de SLA depois de concluido seria maquiar indicador. O caminho de um chamado resolvido e
fechar.

**Chamado cancelado nao reabre.** Quem mudou de ideia abre um chamado novo — reabrir devolveria ao
denominador de SLA um chamado cujo relogio ficou parado por tempo indeterminado.

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
| chamado esta `FECHADO` ou `CANCELADO` | `409` |
| status igual ao atual | `409` |
| transicao nao permitida pela matriz | `409` |

`CANCELADO` tambem e um destino valido aqui para o `ADMIN`, porque a matriz o permite. E o mesmo
metodo de dominio de `DELETE /api/v1/tickets/{id}`: mesma validacao, mesmo evento de historico e
mesma notificacao. A diferenca e que o `DELETE` tambem atende o `SOLICITANTE`.

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
| chamado esta `FECHADO` ou `CANCELADO` | `409` |
| chamado nao possui responsavel | `409` |

### `DELETE /api/v1/tickets/{id}`

Protegido. Sem corpo de requisicao.

**Exclusao logica: cancela o chamado.** Nenhum registro e removido — historico, comentarios,
vinculos de similaridade e o que a IA classificou continuam na base. O valor deste sistema e o
rastro, e um chamado aberto por engano nao e um chamado que nunca existiu: e um chamado que nao sera
atendido, e essa e exatamente a informacao que o historico deve preservar.

Response `200`: `TicketDto` com `"status": "CANCELADO"`. Nao `204`: o corpo deixa explicito que o
chamado continua existindo, e o cliente precisa do retrato novo para atualizar a tela.

Quem pode cancelar:

| Papel | Pode cancelar | Em que status |
| --- | --- | --- |
| `ADMIN` | qualquer chamado | `ABERTO`, `EM_ANDAMENTO` |
| `SOLICITANTE` | apenas os proprios | apenas `ABERTO` |

A fronteira do `SOLICITANTE` em `ABERTO` existe porque, a partir de `EM_ANDAMENTO`, ha trabalho de
outra pessoa em curso. Nesse ponto o caminho e comentar pedindo o cancelamento, e o `ADMIN` cancela.

Registra evento `CHAMADO_CANCELADO` no historico e emite `CHAMADO_ATUALIZADO` no stream SSE para o
solicitante e o responsavel. Por e-mail, o cancelamento e o unico caso de mudanca de status que
tambem alcanca o responsavel: quem esta atendendo precisa saber que o chamado morreu.

| Situacao | Status |
| --- | --- |
| chamado nao encontrado | `404` |
| `SOLICITANTE` tentando cancelar chamado de outro | `403` |
| `SOLICITANTE` tentando cancelar o proprio chamado ja em atendimento | `409` |
| chamado ja `CANCELADO`, `RESOLVIDO` ou `FECHADO` | `409` |

A mensagem do `409` diz qual regra barrou: `Chamado fechado nao pode ser cancelado.`,
`O chamado ja esta com o status Cancelado.`, `Chamado ja em atendimento so pode ser cancelado por um
administrador.` ou a recusa da matriz para `RESOLVIDO`.

### `GET /api/v1/tickets/{ticketId}/events`

Protegido.

Lista o historico de eventos do chamado. O usuario precisa ter acesso ao chamado; usuarios `SOLICITANTE` so acessam eventos dos proprios chamados.

Filtros:

- `actorId`: UUID
- `type`: `CHAMADO_CRIADO`, `COMENTARIO_ADICIONADO`, `STATUS_ALTERADO`, `RESPONSAVEL_ATRIBUIDO`, `RESPONSAVEL_REMOVIDO`, `PRIORIDADE_ALTERADA`, `CATEGORIA_ALTERADA`, `CLASSIFICACAO_ATUALIZADA`, `CHAMADO_CANCELADO`
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

## Transicoes de Status

### `GET /api/v1/ticket-status-transitions`

Protegido. Devolve as transicoes de status permitidas, por status de origem, para que o cliente
habilite apenas o que o servidor aceita.

Fica fora de `/choices` de proposito: aquele agrega rotulos de enum e e publico, enquanto isto e
regra de fluxo do dominio.

Response `200`:

```json
{
  "ABERTO": ["CANCELADO", "EM_ANDAMENTO", "FECHADO", "RESOLVIDO"],
  "EM_ANDAMENTO": ["ABERTO", "CANCELADO", "FECHADO", "RESOLVIDO"],
  "RESOLVIDO": ["EM_ANDAMENTO", "FECHADO"],
  "FECHADO": [],
  "CANCELADO": []
}
```

As listas vem ordenadas alfabeticamente; nao dependa da posicao.

`FECHADO` e `CANCELADO` mapeiam para lista vazia: nem um nem outro reabre. Publicar a matriz nao
afrouxa a validacao — `PATCH /api/v1/tickets/{id}/status` continua recusando transicao invalida com
`409`.

**A matriz e do dominio e independe de papel.** Que `ABERTO` aceite `CANCELADO` nao significa que
quem esta na tela possa cancelar: a regra de papel (ver `DELETE /api/v1/tickets/{id}`) e camada de
cima, aplicada pelo cliente sobre esta resposta e reconferida pelo servidor a cada chamada.

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
| `CHAMADO_ATUALIZADO` | na criacao, solicitante + todos os `ADMIN`; nas demais, solicitante e responsavel do chamado | `TicketMinDto` |
| `CHAMADO_ALTA_PRIORIDADE` | todos os `ADMIN` | `TicketMinDto` |
| `CLASSIFICACAO_CONCLUIDA` | solicitante do chamado e todos os `ADMIN` | `ticketId`, `category`, `priority`, `confidence` |
| `JOB_IA_FALHOU` | todos os `ADMIN` | `jobId`, `ticketId`, `type`, `attempts`, `lastError` |
| `INDICADORES_ATUALIZADOS` | todos os `ADMIN` | `reason`, `ticketId`, `occurredAt` |

`CHAMADO_ATUALIZADO` e emitido na **criacao** do chamado e em mudanca de status, atribuicao,
remocao de responsavel e reclassificacao. Na criacao a audiencia inclui todo `ADMIN`, porque o
ADMIN enxerga todos os chamados na listagem e precisa ver a linha nova sem recarregar a pagina;
nas demais mutacoes a audiencia continua sendo solicitante e responsavel. O `data` e o mesmo objeto do item de `GET /api/v1/tickets`, para que a linha da
lista seja atualizada sem uma segunda chamada REST.

`CHAMADO_ALTA_PRIORIDADE` e emitido quando a prioridade **passa a ser** `ALTA` — inclusive quando o
chamado ja **nasce** `ALTA`. Chamado que ja era `ALTA` e reclassificado em outro campo nao gera
alerta novo.

Exemplo de frame:

```text
event: CHAMADO_ATUALIZADO
id: 9d2f1a44-3c5b-4e8a-9f10-2b7c6d5e4f31
data: {"id":"00000000-0000-0000-0000-000000000000","title":"Erro ao acessar sistema","category":"SISTEMAS","priority":"ALTA","status":"EM_ANDAMENTO","classificationOrigin":"IA","requester":{"id":"00000000-0000-0000-0000-000000000000","name":"Solicitante"},"assignee":{"id":"00000000-0000-0000-0000-000000000000","name":"Administrador"},"assignedAt":"2026-08-14T10:00:00","createdAt":"2026-08-13T20:00:00"}
```

Os tres eventos da frente de IA tem nome e audiencia fixados aqui; o formato do `data` e definido
por aquela frente.
## Notificacoes por E-mail

Os e-mails saem do **mesmo evento de dominio** que alimenta o SSE: cada mutacao publica um evento e
dois listeners pos-commit derivam os transportes. Nao ha endpoint para disparar e-mail — o front nao
precisa fazer nada para que a notificacao aconteca.

### Quem recebe o que

| Gatilho | Destinatario | Assunto |
| --- | --- | --- |
| Chamado com prioridade `ALTA` aberto (ou reclassificado para `ALTA`) | todos os `ADMIN` | `[ALTA] Chamado com prioridade alta: <titulo>` |
| Responsavel atribuido | o responsavel | `Voce e o responsavel pelo chamado: <titulo>` |
| Status alterado | solicitante | `O status do seu chamado mudou: <titulo>` |
| Chamado resolvido | solicitante | `Seu chamado foi resolvido: <titulo>` |
| Chamado fechado | solicitante | `Seu chamado foi fechado: <titulo>` |
| Chamado cancelado | solicitante **e** responsavel | `Seu chamado foi cancelado: <titulo>` |
| Comentario adicionado | a contraparte: solicitante quando quem comentou foi `ADMIN`, responsavel quando foi o solicitante | `Novo comentario no chamado: <titulo>` |
| Job de IA falhou | todos os `ADMIN` | `Job de IA falhou` |
| Usuario criado | o usuario criado | `Acesso provisorio ao Fadex Helpdesk` |

Duas regras valem sobre a tabela:

1. **Quem causou a acao nunca recebe o proprio e-mail.** ADMIN que comenta nao recebe copia; ADMIN
   que se auto-atribui nao recebe aviso de atribuicao. A regra e so do e-mail: no SSE o solicitante
   continua recebendo `CHAMADO_ATUALIZADO` do proprio chamado.
2. **Criacao de chamado com prioridade normal nao gera e-mail** — apenas SSE. So o chamado `ALTA`
   alerta por e-mail.

O e-mail de falha de job e derivado do evento SSE `JOB_IA_FALHOU`: a frequencia e o conteudo do
corpo seguem o que a frente de IA publica nesse evento. Payload em texto entra direto no corpo;
payload em DTO usa o campo `lastError`; qualquer outro formato cai numa frase fixa com o link do
painel de jobs.

Casos que **nao** geram e-mail, por decisao de escopo: remocao de responsavel; comentario do
solicitante em chamado sem responsavel (nao ha contraparte a quem entregar).

### Formato

Todas as mensagens saem em `multipart/alternative` com versao HTML e versao em texto puro. O HTML e
renderizado por Thymeleaf com layout unico (cabecalho, bloco de conteudo, rodape), CSS inline, sem
imagem externa e sem fonte remota. Conteudo vindo do banco — titulo de chamado, texto de comentario,
nome de usuario — e escapado.

O botao de acao aponta para `${app.frontend.base-url}/tickets/{id}` (jobs de IA:
`/admin/jobs`; senha provisoria: `/login`). A base e configuravel por `FRONTEND_BASE_URL` e o padrao
e `http://localhost:3000`.

### Falha de entrega

O envio acontece **depois do commit** e fora da thread da requisicao. SMTP fora do ar nao desfaz a
operacao nem muda o status HTTP: `POST /api/v1/users` continua respondendo `201` e o usuario e
criado, com a falha registrada no log do backend. Consequencia pratica: se o SMTP estiver fora, a
senha provisoria nao chega e o ADMIN precisa recriar o usuario.

## Frente IA — Classificacao, Indicadores e Jobs

Secao escrita pela frente IA. Todos os endpoints desta secao exigem papel `ADMIN`.

**Estado hoje:** toda esta secao esta implementada e responde — revisao da classificacao,
indicadores, campos de sugestao no `TicketDto`, operacao da fila de jobs, consulta de semelhantes,
solicitacao manual de triagem e os tres eventos SSE.

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

**Disponivel.**

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
- SLA: alvos ALTA 4h, MEDIA 24h, BAIXA 72h. Chamado **encerrado** cumpre se encerrou dentro do alvo;
  encerrado inclui `FECHADO` (mede ate `closedAt`) e `RESOLVIDO` (mede ate `resolvedAt`) — o
  cronometro do atendimento para quando o trabalho termina, nao quando alguem lembra de fechar o
  chamado. Chamado **ainda aberto e dentro do alvo fica fora do denominador** — so entra como
  violacao depois de estourar. Sem essa regra, todo chamado recem-criado contaria como violacao.
  `percentage` e `null` quando `evaluated` e `0`.
- **Chamado `CANCELADO` fica de fora de SLA, `backlogAging`, `oldestOpenTicketHours`,
  `openHighPriority`, `closure`, `closedToday`/`closedThisWeek` e `workload.openByAssignee`.** Nao
  foi resolvido, mas tambem nao esta pendente de ninguem: medir SLA sobre ele mediria uma espera que
  ninguem mais esta esperando, e sem esse corte todo cancelado viraria violacao permanente, piorando
  sozinho com o tempo.
- Chamado `CANCELADO` **continua** em `total`, nos mapas `byStatus`/`byPriority`/`byCategory`, em
  `topRequesters` e nos indicadores de IA. Volume e volume: o chamado foi aberto, ocupou a fila e
  consumiu triagem. A fatia `byStatus.CANCELADO` e o dado de gestao novo — quantos chamados foram
  abandonados.
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

**Disponivel.**

ADMIN aceita ou corrige a sugestao da IA. Confirmado igual ao que o frontend assumiu — **nao existe
endpoint separado de aceite**: aceitar e reenviar os valores sugeridos sem alteracao.

```json
{
  "category": "SISTEMAS",
  "priority": "ALTA",
  "justification": "texto opcional"
}
```

- `category` e `priority` obrigatorios; `justification` opcional, ate 2000 caracteres. O corpo
  tambem aceita `classificationJustification` como nome alternativo da justificativa, que e o nome
  que o frontend ja envia.
- Se os valores enviados forem iguais aos sugeridos pela IA, o gesto e aceite e
  `classificationOrigin` permanece `IA`. Se diferirem, vira `MANUAL`.
- Chamado ainda sem sugestao (`PENDENTE`) sempre vira `MANUAL`.
- Aceite e correcao registram `TicketEvent` de `CLASSIFICACAO_ATUALIZADA` — o historico precisa
  mostrar que houve revisao mesmo quando nada mudou. Ambos tambem carimbam o instante da revisao,
  que e o que sustenta o denominador de `agreementRate`.
- Resposta `200` com o `TicketDto` atualizado. `403` para SOLICITANTE, `404` se o chamado nao existe.

### Campos novos no `TicketDto`

**Disponivel.**

```json
{
  "classificationOrigin": "IA",
  "classificationJustification": "Mencao a rede e servidor indica infraestrutura.",
  "aiSuggestedCategory": "INFRAESTRUTURA",
  "aiSuggestedPriority": "ALTA",
  "confidence": 0.87
}
```

Os tres campos sao `null` enquanto a IA nao respondeu. `confidence` vai de `0.0` a `1.0`.

O campo da confianca chama-se `confidence`, e nao `aiConfidence` como esta secao publicou antes: o
frontend ja consome `ticket.confidence`, e como nada consumia o nome antigo, alinhar o backend ao
consumidor real custou menos que pedir mudanca em `frontend/`. O prefixo `ai` continua nos dois
campos de sugestao, onde o frontend ja usava `aiSuggestedCategory` e `aiSuggestedPriority`.

### `GET /api/v1/tickets/{id}/similar`

**Disponivel.** ADMIN.

Chamados semelhantes ja detectados por embedding. Nao roda deteccao: apenas le os vinculos que o
worker gravou em `ticket_links`.

Resposta `200`:

```json
[
  {
    "id": "uuid",
    "title": "Servidor de arquivos fora do ar",
    "status": "ABERTO",
    "priority": "ALTA",
    "category": "INFRAESTRUTURA",
    "similarity": 0.9312,
    "createdAt": "2026-08-14T09:00:00"
  }
]
```

- **Restrito a ADMIN, e nao ao dono do chamado.** O resultado expoe titulo e situacao de chamados de
  outros solicitantes; a visibilidade de chamado no projeto e escopada por solicitante, entao liberar
  esta leitura para SOLICITANTE vazaria titulo alheio por um caminho lateral. O front deve esconder a
  aba "Semelhantes" para quem nao e ADMIN em vez de renderizar e tomar `403`.
- `similarity` e o cosseno do par no instante da deteccao, de `-1` a `1`, arredondado a quatro casas.
  **Pode vir `null`**: vinculos gravados antes da `V6` nao registraram o valor, e nao ha backfill —
  o embedding de origem pode ter mudado desde entao. Renderize a ausencia, nao assuma zero.
- A lista sai ordenada pela maior similaridade, com os sem score no fim.
- O vinculo e **bidirecional na leitura**: a deteccao grava `origem -> alvo` quando o job de embedding
  da origem roda, entao para um chamado antigo o par costuma estar gravado na direcao oposta. O
  endpoint consulta as duas direcoes e nao repete o mesmo chamado.
- Lista vazia (`[]`) quando nao ha semelhante. `404` se o chamado nao existe.

### `POST /api/v1/tickets/{id}/ai-triage`

**Disponivel.** ADMIN.

Reenfileira a triagem por IA de um chamado. Sem corpo de requisicao.

Resposta `202 Accepted` com os jobs criados:

```json
[
  {
    "id": "uuid",
    "ticketId": "uuid",
    "type": "CLASSIFICATION",
    "status": "PENDING",
    "attempts": 0,
    "nextAttemptAt": "2026-08-14T18:00:00",
    "lastError": null,
    "createdAt": "2026-08-14T18:00:00",
    "updatedAt": "2026-08-14T18:00:00"
  }
]
```

- **`202`, nao `200`**: a requisicao nao espera o modelo local responder. Ela enfileira e devolve; o
  worker do Quartz processa depois. Acompanhe por `CLASSIFICACAO_CONCLUIDA` ou por
  `GET /api/v1/ai/jobs?ticketId={id}`.
- **Guarda contra job duplicado, por tipo.** Um tipo que ja tenha job `PENDING` ou `PROCESSING` para
  aquele chamado e pulado; os demais sao enfileirados. Dois cliques nao viram dois processamentos
  concorrentes. A guarda e por tipo e nao por chamado de proposito: o job de embedding e o mais
  lento dos dois, e uma guarda por chamado deixaria um embedding ainda `PENDING` bloqueando a
  reclassificacao — que e justamente o que o ADMIN quer refazer.
- `409` quando **nenhum** tipo pode ser enfileirado, ou seja, ja ha triagem em andamento para os dois.
- Jobs `FAILED` nao bloqueiam: tem o proprio caminho em `POST /api/v1/ai/jobs/{id}/retry`. Jobs
  `DONE` tambem nao — reprocessar um chamado ja classificado e o caso de uso principal deste endpoint.
- `404` se o chamado nao existe.

### `GET /api/v1/ai/jobs`

**Disponivel.**

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

**Disponivel.**

Sem corpo. Reagenda um job com falha: volta para `PENDING`, limpa `lastError`.

- `200` com o `AiJobDto` atualizado (o front pode ignorar e recarregar a lista).
- `409` quando o job **nao** esta `FAILED` — so job com falha pode ser retentado.
- `404` quando o job nao existe.

### Eventos SSE disparados por esta frente

**Disponiveis.**

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
- Notificacoes por e-mail em HTML com alternativa em texto (ver "Notificacoes por E-mail").
- Historico de eventos do chamado, em `GET /api/v1/tickets/{ticketId}/events`.
- Atualizacao de status, atribuicao e remocao de responsavel do chamado.
- Cancelamento de chamado (exclusao logica) em `DELETE /api/v1/tickets/{id}`, com regra de papel:
  `ADMIN` cancela qualquer chamado, `SOLICITANTE` cancela o proprio enquanto `ABERTO`.
- Jobs de IA para o ADMIN, em `GET /api/v1/ai/jobs` e `POST /api/v1/ai/jobs/{id}/retry`.
- Deteccao de duplicados por embedding, gravando os pares em `ticket_links`.
- Revisao da sugestao da IA pelo ADMIN, em `PATCH /api/v1/tickets/{id}/classification`.
- Indicadores agregados em `GET /api/v1/indicators`, incluindo `overview.openHighPriority`, que
  sustenta o alerta de chamado com prioridade ALTA.
- Persistencia da sugestao da IA (`aiSuggestedCategory`, `aiSuggestedPriority`, `confidence`) e
  carimbo de revisao pelo ADMIN.

- Consulta de chamados semelhantes, em `GET /api/v1/tickets/{id}/similar`.
- Solicitacao manual de triagem por IA, em `POST /api/v1/tickets/{id}/ai-triage`.

Ainda pendente:

- Remocao de vinculo de duplicidade por acao explicita do ADMIN.

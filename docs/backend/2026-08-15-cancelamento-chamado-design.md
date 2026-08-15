# Cancelamento de chamado — design

Data: 2026-08-15
Frente: Chamados (backend + frontend)
Branch: `feature(backend)/cancelamento-de-chamado`

## 1. Problema

O enunciado do desafio pede CRUD completo de chamados, incluindo **excluir ou cancelar**. Hoje não
existe nenhum dos dois: o único `@DeleteMapping` do `TicketController` é `/{id}/assignee`, que remove
o responsável, não o chamado. `TicketStatus` tem apenas `ABERTO`, `EM_ANDAMENTO`, `RESOLVIDO` e
`FECHADO`. É a única lacuna de requisito obrigatório que resta, conforme
`docs/projeto/2026-08-15-revisao-tecnica.md` §1.2 item 3.

Além do requisito, há uma lacuna de produto: **o SOLICITANTE não tem nenhuma ação de escrita sobre o
próprio chamado além de comentar**. Abrir um chamado por engano hoje é irreversível para quem o
abriu.

## 2. Decisão central: cancelar, não excluir — D1

Cancelamento **por status**, com o chamado permanecendo na base. Nenhum `DELETE` físico.

O valor deste sistema é o rastro: histórico (`ticket_events`), comentários, embeddings, sugestões de
IA e indicadores. Um `DELETE FROM tickets` destrói evidência de forma irreversível e, por tabela,
cascatearia para `ai_jobs`, `ticket_events`, `ticket_links` e `ticket_comments` — apagando também o
trabalho de quem atendeu. Um chamado aberto por engano não é um chamado que nunca existiu; é um
chamado que não será atendido, e essa é exatamente a informação que o histórico deve preservar.

Consequência aceita e documentada: a listagem passa a poder trazer chamados cancelados. É desejável —
o filtro por status resolve quem não quer vê-los, e a contagem por status passa a mostrar quantos
chamados foram abandonados, que é um dado de gestão que hoje não existe.

## 3. Modelo

### 3.1 `TicketStatus.CANCELADO` — D2

Novo valor `CANCELADO("Cancelado")`, no fim do enum. Entra automaticamente em `GET /api/v1/choices`
(que deriva de `TicketStatus.values()`) e no filtro `status` da listagem, sem código novo.

### 3.2 Matriz de transições — D3

| De | Para (depois desta entrega) |
|---|---|
| `ABERTO` | `EM_ANDAMENTO`, `RESOLVIDO`, `FECHADO`, **`CANCELADO`** |
| `EM_ANDAMENTO` | `ABERTO`, `RESOLVIDO`, `FECHADO`, **`CANCELADO`** |
| `RESOLVIDO` | `EM_ANDAMENTO`, `FECHADO` — **sem cancelamento** |
| `FECHADO` | ∅ |
| **`CANCELADO`** | **∅ — terminal** |

`RESOLVIDO` não cancela de propósito: o trabalho já foi feito, e apagá-lo do denominador de SLA
depois de concluído seria maquiar indicador. O caminho para um chamado resolvido é fechar.

`CANCELADO` é terminal com conjunto vazio, o mesmo tratamento que `FECHADO` já recebe — o estado
terminal continua sendo um dado da matriz, e não um `if` no service. **Cancelado não reabre.** Quem
mudou de ideia abre um chamado novo; a alternativa (reabrir) reintroduz no denominador de SLA um
chamado cujo relógio ficou parado por tempo indeterminado.

### 3.3 Sem coluna `canceled_at` — D4

Considerada e recusada. `closedAt` está contratado em `api.md` como "transição para `FECHADO`,
escrito uma única vez", e escrevê-lo no cancelamento poluiria quatro métricas (`closedToday`,
`closedThisWeek`, `closure.*`, `workload.closureTimeByAssignee`). Uma coluna nova custaria entidade +
migração + projeção + query + DTO + mapper + `api.md` para **zero leitor atual**: o `status`
discrimina o cancelado, e a linha de `ticket_events` já carrega o instante e o autor do
cancelamento, que é o que a aba de histórico mostra.

Ou seja: chamado cancelado tem `resolvedAt` e `closedAt` **nulos**. Isso é o que mantém os
indicadores de fechamento intactos sem nenhum código defensivo.

## 4. Autorização: quem pode cancelar — D5

| Papel | Pode cancelar | Em que status |
|---|---|---|
| `ADMIN` | qualquer chamado | `ABERTO`, `EM_ANDAMENTO` |
| `SOLICITANTE` | **apenas os próprios** | **apenas `ABERTO`** |

**Por que o ADMIN em `ABERTO` e `EM_ANDAMENTO`:** é quem opera a fila e precisa poder descartar
duplicata, trote ou chamado aberto na categoria errada, inclusive depois de já ter começado a olhar.

**Por que o SOLICITANTE só em `ABERTO`:** é a regra que dá ao solicitante uma ação real sobre o que é
dele — hoje inexistente — sem deixá-lo desfazer trabalho alheio. A partir de `EM_ANDAMENTO` existe
alguém trabalhando no chamado, e cancelar por baixo dessa pessoa é destruir esforço em curso; nesse
ponto o caminho é comentar pedindo o cancelamento, e o ADMIN cancela. A fronteira `ABERTO` é
verificável no próprio dado, não depende de convenção.

`403` para quem não pode; `409` para transição não permitida pela matriz. A distinção importa: "você
não tem permissão" e "este chamado não está em estado de ser cancelado" são erros diferentes para
quem está na tela.

## 5. Contrato HTTP — D6

### 5.1 `DELETE /api/v1/tickets/{id}`

A ação ganha endpoint próprio, e o verbo é `DELETE`.

O enunciado pede "excluir/cancelar" no CRUD, e `DELETE /tickets/{id}` é onde qualquer avaliador —
e qualquer cliente HTTP — procura essa capacidade. Implementá-lo como **exclusão lógica** entrega o
verbo esperado sem abrir mão de D1: o recurso deixa de estar ativo, e o corpo da resposta prova o que
aconteceu com ele.

- Response `200` com o `TicketDto` já em `CANCELADO`. Não `204`: o cliente precisa do retrato novo
  para atualizar a tela, e um corpo que diz `"status": "CANCELADO"` deixa explícito que a exclusão é
  lógica — um `204` mudo convidaria a supor que o chamado sumiu.
- `403` sem permissão (D5); `409` se o status atual não permite; `404` se não existe.

### 5.2 `PATCH /api/v1/tickets/{id}/status` com `CANCELADO`

Continua funcionando, para ADMIN, porque a matriz passa a permitir a transição. **Não é uma segunda
regra**: os dois caminhos chamam o mesmo método de domínio, com a mesma matriz, o mesmo evento e a
mesma notificação. Proibir `CANCELADO` só nesse endpoint seria um caso especial a mais para manter,
justamente o que `TicketStatusTransition` existe para evitar.

### 5.3 `GET /api/v1/ticket-status-transitions` permanece independente de papel

O endpoint publica a matriz do **domínio**, e é consumido por outras frentes. Ele passa a incluir
`CANCELADO` nas listas de `ABERTO`/`EM_ANDAMENTO` e a expor `"CANCELADO": []`. A regra de papel (D5)
é camada de cima, aplicada no cliente sobre a matriz — não uma matriz diferente por papel.

## 6. Histórico e notificação — D7

**Histórico:** novo `TicketEventType.CHAMADO_CANCELADO("Chamado cancelado")`. A aba de histórico é o
livro-razão do chamado, e "Chamado cancelado por Fulano" é a linha que o leitor procura; deixar isso
como mais um `STATUS_ALTERADO` esconde o evento mais importante do ciclo de vida dentro do mais
genérico. O custo é uma linha a mais na V7 (§7).

**Notificação:** reutiliza `TicketNotificationType.STATUS_ALTERADO`. É um tipo de transporte, não de
domínio: o SSE já entrega `CHAMADO_ATUALIZADO` ao solicitante e ao responsável, e o e-mail de status
já tem template. Um tipo novo obrigaria a um sétimo template para dizer o que o texto do detalhe já
diz. A assimetria com o histórico é deliberada — o histórico é registro permanente e precisa de
precisão; a notificação é entrega e paga por template.

Duas mudanças pontuais no e-mail:

1. `TicketEmailComposer` ganha `case CANCELADO -> "Seu chamado foi cancelado"` no `switch` de título,
   que hoje já tem `default`.
2. `composeStatusChanged` passa a incluir **o responsável** entre os destinatários **quando o status
   novo é `CANCELADO`**. Sem isso, o solicitante cancela e quem estava atendendo continua
   trabalhando num chamado morto. A regra "nunca notificar quem causou a ação" continua valendo para
   os dois destinatários.

## 7. Migração V7 — D8

A V6 já está em uso; nada de V1–V6 é tocado. A V7 faz **duas** coisas, e as duas são obrigatórias
porque os `check` são validados em runtime, não no build:

```sql
alter table tickets drop constraint ck_tickets_status;
alter table tickets add constraint ck_tickets_status
    check (status in ('ABERTO', 'EM_ANDAMENTO', 'RESOLVIDO', 'FECHADO', 'CANCELADO'));

alter table ticket_events drop constraint ck_ticket_events_type;
alter table ticket_events add constraint ck_ticket_events_type check (type in (
    ... os oito atuais ..., 'CHAMADO_CANCELADO'
));
```

O segundo é a armadilha que o escopo não nomeou: `ck_ticket_events_type` foi redefinido na V4 com
oito valores, e o evento novo de D7 seria rejeitado pelo banco exatamente como o status novo. O
padrão de *drop* e *re-add* é o da própria V4.

**A suíte cobre isso.** `application-test.properties` roda Flyway em H2 com
`ddl-auto=validate` — os `check` existem no banco de teste, e H2 os aplica. Um teste de repositório
que persiste chamado `CANCELADO` e evento `CHAMADO_CANCELADO` falha sem a V7 e passa com ela. Ainda
assim, a verificação final é contra o Postgres real (§10).

## 8. Indicadores — a parte que exige cuidado — D9

### 8.1 O que quebra sozinho, e o que não quebra

Auditoria de `IndicatorService` contra um chamado `CANCELADO` com `resolvedAt`/`closedAt` nulos:

| Métrica | Filtro atual | Cancelado entra? | Ação |
|---|---|---|---|
| `backlogAging` | `isOpen()` | não — `isOpen()` é `ABERTO ∨ EM_ANDAMENTO` | **fixar com teste** |
| `oldestOpenTicketHours` | `isOpen()` | não | **fixar com teste** |
| `openHighPriority` | `isOpen()` | não | **fixar com teste** |
| `workload.openByAssignee` | `isOpen()` | não | **fixar com teste** |
| `closure.*`, `closedToday/ThisWeek` | `isClosed()` = `closedAt != null` | não (D4) | **fixar com teste** |
| `workload.closureTimeByAssignee` | `isClosed()` | não | fixar com teste |
| **`durations.sla`** | **nenhum** | **SIM — e conta como violação** | **corrigir** |

### 8.2 A distorção real: SLA

Este é o único número que o cancelamento de fato corrompe, e vale escrever o caminho:

`settledAt()` devolve `closedAt` se houver; senão, `isOpen() ? null : resolvedAt`. Para um cancelado,
os dois carimbos são nulos e `isOpen()` é falso → `settledAt() == null`. Em `buildSla`, `settledAt`
nulo faz `end = now`, e `SlaTarget.evaluate(elapsed, false)` devolve `BREACHED` assim que o tempo
decorrido passa do alvo. Ou seja: **todo chamado cancelado vira violação permanente de SLA, e piora
sozinho com o tempo** — o mesmo erro que o comentário do `SlaTarget` diz ter evitado para o chamado
recém-criado.

Correção: `TicketIndicatorProjection.isCanceled()` e `continue` no topo do laço de `buildSla`.
Chamado cancelado sai do numerador **e do denominador**: não foi resolvido, mas também não está
pendente de ninguém. Medir SLA sobre ele mediria uma espera que ninguém mais está esperando.

### 8.3 O que continua contando, e por quê

`overview.total`, `overview.byStatus`/`byPriority`/`byCategory` e `workload.topRequesters` **seguem
incluindo o cancelado**. Volume é volume: o chamado foi aberto, ocupou a fila e consumiu triagem de
IA. Escondê-lo do total faria os mapas por status não somarem o total, e `byStatus` passa a ter a
fatia `CANCELADO`, que é justamente o dado de gestão novo. Os indicadores de IA
(`agreementRate`, `averageConfidence`, `originDistribution`) também seguem incluindo — a IA
classificou o chamado, e o acerto dela não deixa de ter acontecido porque o chamado foi cancelado
depois.

### 8.4 Explícito, não emergente

As seis métricas da tabela acima estão corretas **por acidente** de como `isOpen()` e `isClosed()`
enumeram hoje. Nada as prende. Cada uma ganha teste que falha se alguém mudar `isOpen()` para incluir
`CANCELADO` — é o que transforma "hoje está certo" em "continua certo".

### 8.5 Evidência antes e depois

Medição contra a base semeada, com a stack de pé, capturando `GET /api/v1/indicators` como ADMIN
antes e depois. O chamado escolhido para o teste precisa estar **atualmente em `BREACHED`** (ALTA >4h,
MEDIA >24h, BAIXA >72h desde `createdAt`): cancelar um chamado recém-criado não move número nenhum e
não provaria nada. Predição a confirmar: `sla.overall.evaluated` cai exatamente 1, o balde de
`backlogAging` correspondente à idade dele cai 1, `byStatus.CANCELADO` aparece com 1, `total` não
muda, e `closure.overall.sampleSize` não muda.

## 9. Bloqueios derivados de `FECHADO` — D10

`assertTicketIsNotClosed` hoje só recusa `FECHADO`, e é usada por `updateAssignee` e
`removeAssignee`. Chamado cancelado não aceita mexer em responsável pela mesma razão que o fechado
não aceita: o ciclo acabou. O método passa a recusar os dois estados terminais, derivando de
`TicketStatusTransition.allowedFrom(status).isEmpty()` em vez de listar estados — assim um terceiro
estado terminal futuro já entra coberto.

Comentários **continuam permitidos** em chamado cancelado, como já são em chamado fechado (o
`TicketCommentService` só checa acesso). Registrar por escrito por que se cancelou é exatamente o uso
legítimo.

## 10. Frontend — D11

1. **A matriz vem da API.** `ticket-status-transitions.ts` hoje **duplica** a matriz do backend, com
   um comentário admitindo a dívida e pedindo o endpoint — que já existe. Este é o momento de pagar:
   o módulo passa a consumir `GET /api/v1/ticket-status-transitions` (serviço + hook, cache em
   memória por sessão), e a constante local sai. Sem isso, o front continuaria sem saber que
   `CANCELADO` existe.
2. **Ação de cancelar** no detalhe do chamado: botão próprio, destrutivo, com confirmação — não uma
   opção a mais no `Select` de status. Cancelar é irreversível; esconder isso dentro do mesmo seletor
   das transições cotidianas é convidar ao clique errado. `CANCELADO` é filtrado para fora das opções
   do `Select` justamente por isso.
3. **Habilitação:** o botão aparece quando `allowedFrom(ticket.status)` inclui `CANCELADO` **e** o
   papel permite (ADMIN sempre; SOLICITANTE dono e `ABERTO`). A matriz vem do servidor; só a camada
   de papel é do cliente — e o servidor recusa de todo jeito.
4. **Rótulos e filtro:** `TicketStatusValue` ganha `"CANCELADO"`; o filtro de status vem de
   `/choices`, então o valor novo aparece sozinho. Badge de status precisa de estilo para o valor
   novo (tom neutro/apagado, não vermelho de erro: cancelado não é falha do sistema).
5. Tela do chamado cancelado fica somente-leitura para ações de ciclo de vida, como já fica a do
   fechado.

## 11. Testes — o que prova o quê

| Prova | Teste |
|---|---|
| Banco aceita `CANCELADO` e `CHAMADO_CANCELADO` | repositório, falha sem a V7 |
| Matriz: `ABERTO`/`EM_ANDAMENTO` → `CANCELADO`; `CANCELADO` → ∅; `RESOLVIDO` não cancela | `TicketStatusTransitionTest` |
| ADMIN cancela chamado de terceiro em `ABERTO` e em `EM_ANDAMENTO` | service |
| SOLICITANTE cancela o próprio em `ABERTO` | service |
| SOLICITANTE **não** cancela o próprio em `EM_ANDAMENTO` → `409` | service |
| SOLICITANTE **não** cancela chamado alheio → `403` | service |
| Cancelar cancelado/fechado/resolvido → `409` | service |
| `resolvedAt` e `closedAt` seguem nulos após cancelar | service |
| Evento `CHAMADO_CANCELADO` gravado com autor | service |
| Notificação emitida ao solicitante **e ao responsável** | composer |
| `DELETE /tickets/{id}` → `200` + `TicketDto` cancelado; `403`/`404`/`409` | controller |
| Cancelado fora do SLA (denominador e numerador) | `IndicatorServiceTest` |
| Cancelado fora de aging, oldest-open, openHighPriority, openByAssignee, closure | `IndicatorServiceTest` |
| Cancelado **dentro** de `total`, `byStatus`, `topRequesters` | `IndicatorServiceTest` |

Piso: **292 testes, 0 falhas** (medido nesta branch antes de qualquer alteração, 57 classes). Nenhum
teste existente pode ser afrouxado para acomodar o novo status.

## 12. Fora de escopo

`ai/client/**`, `ai/job/**`, `application.properties`, `docker-compose.yml` e as migrações V1–V6 —
todos em uso por outra frente neste momento. Reabertura de chamado cancelado, cancelamento em lote e
motivo estruturado de cancelamento (campo próprio) ficam fora: o comentário e a descrição do evento
cobrem o "por quê" sem tabela nova.

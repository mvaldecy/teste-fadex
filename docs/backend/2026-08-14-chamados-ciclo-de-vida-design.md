# Chamados e Ciclo de Vida Design

## Objetivo

Fechar o ciclo de vida do chamado: hoje um chamado nasce `ABERTO` e nunca sai desse estado pela
API. Nao existe endpoint de mudanca de status, nao existe atribuicao de responsavel e nao existe
nenhum carimbo de tempo alem de `created_at`/`updated_at`.

Esta feature entrega as transicoes de status com regra de negocio, a atribuicao e recusa de
responsavel, os carimbos de tempo que tornam as metricas de atendimento calculaveis, e os
contratos compartilhados de que as frentes IA e Frontend dependem para comecar.

## Escopo

Incluido neste ciclo:

- Migration `V4` com colunas de ciclo de vida e de auditoria da sugestao da IA.
- `NotificationEventName` com todos os nomes de evento SSE do projeto, criados de uma vez.
- Seam `TicketService.applyClassification(...)`, unica porta de escrita de classificacao no `Ticket`.
- `PATCH /api/v1/tickets/{id}/status` com maquina de estados explicita.
- `PATCH /api/v1/tickets/{id}/assignee` e `DELETE /api/v1/tickets/{id}/assignee`.
- Preenchimento de `resolved_at`, `closed_at`, `assigned_at` e `first_response_at`.
- Regra de chamado `FECHADO` terminal, com `409` em qualquer tentativa de reabertura.
- Disparo de `CHAMADO_ATUALIZADO` e `CHAMADO_ALTA_PRIORIDADE` via SSE.
- Exposicao dos carimbos novos em `TicketDto` e delta correspondente em `docs/backend/api.md`.
- Extensao do `DevTicketSeeder` para popular os carimbos novos.

Fora deste ciclo:

- Indicadores e estatisticas (`GET /api/v1/indicators`) — pertencem a frente IA.
- `PATCH /api/v1/tickets/{id}/classification` — endpoint de revisao ADMIN, pertence a frente IA.
  Esta feature entrega apenas o seam que ele consome.
- Persistencia do fluxo `AiJobWorker` nas colunas `ai_suggested_*` — pertence a frente IA.
  Esta feature entrega a coluna e o mutator.
- Deteccao de duplicados e `ticket_links`.
- RBAC formal (tabela papel x acao, endurecimento por rota no `SecurityConfig`, gating de
  navegacao). Adiado por decisao registrada em `docs/projeto/2026-08-14-frentes-de-trabalho.md`.
- Qualquer alteracao em `ai/**` ou `frontend/**`.

## Requisitos do Desafio Cobertos

- Atualizacao de status do chamado pelo ADMIN.
- Atribuicao de responsavel pelo ADMIN, com possibilidade de recusar/remover a atribuicao.
- Regra de negocio de chamado fechado que nao reabre.
- Historico de mudancas: toda mutacao registra `TicketEvent`.
- Alerta de chamado de prioridade ALTA em tempo real.
- Base de dados para os indicadores obrigatorios: sem `closed_at`/`first_response_at` os tempos
  medios de atendimento nao sao calculaveis.

## Contratos Compartilhados

As frentes IA e Frontend estao bloqueadas nestes quatro artefatos. Eles saem primeiro, sozinhos,
antes de qualquer endpoint desta feature.

| Artefato | Consumidor | Forma |
| --- | --- | --- |
| Migration `V4` | IA (colunas `ai_suggested_*`) | `V4__add_ticket_lifecycle_columns.sql` |
| `NotificationEventName` | IA e Frontend | `sse/model/NotificationEventName.java` |
| `applyClassification(...)` | IA | metodo publico de `TicketService` |
| Delta do `api.md` | Frontend | secao "Chamados" de `docs/backend/api.md` |

`V5` fica reservada para a frente IA. A frente Frontend nao cria migration.

## Modelo de Dados

A `V4` adiciona sete colunas a `tickets`, todas anulaveis — chamados existentes nao tem historico
de onde derivar os valores, e forcar default falsearia as metricas.

```sql
alter table tickets add column resolved_at timestamp;
alter table tickets add column closed_at timestamp;
alter table tickets add column first_response_at timestamp;
alter table tickets add column assigned_at timestamp;
alter table tickets add column ai_suggested_category varchar(40);
alter table tickets add column ai_suggested_priority varchar(20);
alter table tickets add column ai_confidence double precision;
```

Justificativa da denormalizacao: os quatro carimbos sao derivaveis de `ticket_events`, mas cada
carga de dashboard varreria a tabela inteira de eventos para reconstruir um valor que so muda
uma vez por chamado. As colunas `ai_suggested_*` e `ai_confidence` existem hoje apenas em memoria
no record `TicketClassification`; sem persistir, a taxa de concordancia admin x IA e impossivel
de calcular.

Restricoes de dominio acompanham as colunas de enum, no mesmo padrao ja usado na `V1`:

```sql
alter table tickets add constraint ck_tickets_ai_suggested_category
    check (ai_suggested_category is null or ai_suggested_category in (...));
alter table tickets add constraint ck_tickets_ai_suggested_priority
    check (ai_suggested_priority is null or ai_suggested_priority in ('BAIXA', 'MEDIA', 'ALTA'));
alter table tickets add constraint ck_tickets_ai_confidence_range
    check (ai_confidence is null or (ai_confidence >= 0 and ai_confidence <= 1));
```

Indices para as consultas de indicadores da frente IA:

```sql
create index idx_tickets_closed_at on tickets (closed_at);
create index idx_tickets_created_at on tickets (created_at);
```

### Tipos e `ddl-auto=validate`

Tanto `application.properties` quanto `application-test.properties` usam
`spring.jpa.hibernate.ddl-auto=validate`. Coluna nao mapeada passa; coluna mapeada com tipo Java
incompativel derruba o contexto. Os pares escolhidos:

| Coluna | Tipo SQL | Campo Java |
| --- | --- | --- |
| `resolved_at`, `closed_at`, `first_response_at`, `assigned_at` | `timestamp` | `LocalDateTime` |
| `ai_suggested_category` | `varchar(40)` | `TicketCategory` com `@Enumerated(EnumType.STRING)` |
| `ai_suggested_priority` | `varchar(20)` | `TicketPriority` com `@Enumerated(EnumType.STRING)` |
| `ai_confidence` | `double precision` | `Double` |

`Double` e nao `double`: a coluna e anulavel e o primitivo mapearia `null` para `0.0`, que num
indicador de confianca media e um valor com significado proprio e errado.

## Maquina de Estados de Status

```text
ABERTO ──────► EM_ANDAMENTO ──────► RESOLVIDO ──────► FECHADO
   │                 │                   │               │
   │                 └──────► ABERTO     └──► EM_ANDAMENTO│
   ├──────────────────────────► RESOLVIDO                 │
   └──────────────────────────► FECHADO ◄─────────────────┘
                                                    (terminal)
```

Matriz explicita das transicoes permitidas:

| De \ Para | ABERTO | EM_ANDAMENTO | RESOLVIDO | FECHADO |
| --- | --- | --- | --- | --- |
| `ABERTO` | — | sim | sim | sim |
| `EM_ANDAMENTO` | sim | — | sim | sim |
| `RESOLVIDO` | nao | sim | — | sim |
| `FECHADO` | nao | nao | nao | — |

Regras:

- `FECHADO` e terminal. Qualquer transicao a partir dele responde `409 CONFLICT`. E a regra de
  negocio explicitamente cobrada pelo desafio.
- Transicao para o mesmo status responde `409 CONFLICT`. Nao e erro de validacao: o corpo e valido,
  o estado do recurso e que torna a operacao sem sentido, e silenciar como `200` esconderia bug de
  UI que dispara a mesma acao duas vezes.
- `RESOLVIDO → ABERTO` fica de fora: reabrir um chamado resolvido significa voltar a trabalha-lo,
  o que corresponde a `EM_ANDAMENTO`. Voltar para `ABERTO` produziria um chamado resolvido, sem
  responsavel ativo e sem fila — um estado que nenhuma tela sabe representar.
- `EM_ANDAMENTO → ABERTO` fica permitido: e o par natural do `DELETE /assignee`, quando o
  responsavel recusa a atribuicao e o chamado volta para a fila.

A matriz vive em `TicketStatusTransition`, classe propria em `model/ticket`, e nao espalhada em
`if` dentro do service. Motivo: a frente IA precisa ler a matriz para o calculo de aging e a
frente Frontend precisa dela para habilitar botoes; uma estrutura de dados consultavel evita que
as tres frentes reimplementem a mesma regra em tres lugares.

## Carimbos de Tempo

Cada carimbo tem uma regra de escrita explicita. Ambiguidade aqui corrompe as metricas da frente
IA sem produzir nenhum erro visivel.

| Coluna | Quando escreve | Regra de reescrita |
| --- | --- | --- |
| `resolved_at` | transicao para `RESOLVIDO` | **ultima vence** |
| `closed_at` | transicao para `FECHADO` | escrita unica (estado terminal) |
| `assigned_at` | atribuicao de responsavel | **primeira vence**, nunca limpa |
| `first_response_at` | primeiro comentario de um ADMIN | **primeira vence** |

Detalhamento das decisoes que nao sao obvias:

**`resolved_at` — ultima vence.** O ciclo `RESOLVIDO → EM_ANDAMENTO → RESOLVIDO` significa que a
primeira resolucao nao resolveu. O tempo ate a resolucao que de fato encerrou o problema e o
segundo. Guardar o primeiro reportaria um atendimento melhor do que o real.

**Fechamento sem passar por `RESOLVIDO`.** Nas transicoes `ABERTO → FECHADO` e
`EM_ANDAMENTO → FECHADO`, se `resolved_at` ainda for `null` ele recebe o mesmo instante de
`closed_at`. Sem isso, todo chamado cancelado ou fechado direto entraria como `resolved_at is
null` e sumiria da metrica de tempo de resolucao, enviesando a media para baixo.

**`assigned_at` — primeira vence e nunca limpa.** A metrica e "tempo ate a primeira atribuicao",
que mede a velocidade da triagem. Reescrever a cada reatribuicao mediria outra coisa; limpar no
`DELETE /assignee` apagaria o fato de que a triagem aconteceu.

**`first_response_at` — so ADMIN conta.** Comentario do proprio solicitante nao e resposta do
atendimento. A escrita acontece em `TicketCommentService.create`, quando o autor tem role `ADMIN`
e o campo ainda esta `null`.

## Atribuicao de Responsavel

`PATCH /api/v1/tickets/{id}/assignee` recebe `{"assigneeId": "<uuid>"}`. O responsavel precisa
existir e precisa ter role `ADMIN` — atribuir chamado a um `SOLICITANTE` seria dar trabalho de
atendimento a quem nao tem permissao de mudar status, produzindo um chamado travado.

**Confirmada na revisao.** A razao e que o sistema so tem os papeis `ADMIN` e `SOLICITANTE`:
responsavel e necessariamente `ADMIN`. O seletor de responsavel do Frontend usa
`GET /api/v1/users?role=ADMIN`.

A revisao fixou tambem a semantica da atribuicao, mais restrita do que este design supunha:

- `PATCH /assignee` **so atribui chamado sem responsavel**. Chamado ja atribuido responde `409`.
  Trocar de responsavel e `DELETE` seguido de `PATCH`. Assim toda troca deixa os dois eventos no
  historico; um `PATCH` sobrepondo o anterior registraria so a chegada e apagaria a saida.
- Nao ha regra de "so o proprio": um `ADMIN` pode se atribuir ou atribuir outro, indiferentemente.
- Efeito na UI: o botao e "Atribuir" quando `assignee` e nulo e "Recusar" quando nao e, nunca os
  dois.

Violacao responde `409 CONFLICT` via `ConflictException`. As alternativas foram descartadas:
`ValidationException` **nao existe** no projeto, e criar uma reutilizando o codigo
`VALIDATION_ERROR` colidiria com o erro de bean validation, que o `api.md` documenta carregando
um array `fields` — um `VALIDATION_ERROR` sem `fields` quebraria qualquer front que use esse
array. Criar um codigo novo apenas para esta regra nao se paga. O corpo enviado e sintaticamente
valido; o que impede a operacao e o papel do usuario referenciado, que e estado do recurso.

`DELETE /api/v1/tickets/{id}/assignee` remove a atribuicao. Cobre os dois casos do enunciado:
o ADMIN recusa um chamado que lhe foi atribuido, e o ADMIN retira a atribuicao de outro. Como
descrito acima, `assigned_at` e preservado.

A remocao registra `TicketEventType.RESPONSAVEL_REMOVIDO`, constante nova com label
"Responsavel removido". A revisao autorizou mexer em `model/enums/TicketEventType.java`, fora da
posse nominal desta frente, porque nenhuma outra frente toca nesse arquivo — e reaproveitar
`RESPONSAVEL_ATRIBUIDO` faria a aba de historico, visivel nesta rodada, escrever exatamente o
oposto do que aconteceu.

**A `V4` precisa alterar o check constraint `ck_ticket_events_type`.** Ele foi fixado na `V2` com
a lista fechada de sete tipos e rejeitaria o valor novo no insert. Constraint congelada em
migration antiga e o tipo de detalhe que so aparece em runtime, no primeiro `DELETE /assignee`.

Ambos exigem chamado nao `FECHADO`: mexer no responsavel de um chamado terminal responde `409`.

## Seam de Classificacao

```java
void applyClassification(
        UUID ticketId,
        TicketCategory category,
        TicketPriority priority,
        ClassificationOrigin origin,
        String justification
);
```

E a unica porta de escrita de classificacao no `Ticket`. A frente IA a usa tanto no `AiJobWorker`
(origem `IA`) quanto no endpoint de revisao ADMIN (origem `MANUAL`), e nao muta `Ticket` nem
salva via `TicketRepository` por conta propria.

Responsabilidades do metodo:

1. Carrega o chamado, `404` se nao existir.
2. Aplica categoria, prioridade, origem e justificativa.
3. Registra `TicketEvent` de `CLASSIFICACAO_ATUALIZADA`.
4. Publica `CHAMADO_ATUALIZADO`.
5. Publica `CHAMADO_ALTA_PRIORIDADE` quando a prioridade **passa a ser** `ALTA`.

**O metodo nao chama `assertAdmin()`.** Ele roda em dois contextos: o worker Quartz, sem nenhum
usuario autenticado no `SecurityContext`, e o endpoint ADMIN da frente IA, que faz o
`assertAdmin()` na sua propria camada. Colocar a assercao aqui quebraria o worker com
`UnauthorizedException` a cada classificacao automatica.

Como consequencia, a resolucao do ator do evento precisa tolerar ausencia de autenticacao.
`AccessControlService` ganha `findAuthenticatedUserId(): Optional<UUID>`, que devolve vazio em vez
de lancar quando nao ha JWT no contexto. Evento com `actor` nulo ja e suportado: a coluna
`ticket_events.actor_id` e anulavel desde a `V2`.

Para o mutator de entidade, `Ticket.applyClassification(category, priority, origin, justification)`
recebe a origem como parametro. Os metodos existentes `applyAutomaticClassification` e
`applyManualClassification` permanecem, delegando ao novo — `AiJobWorker` os chama hoje e `ai/**`
esta fora do escopo desta frente.

### Sugestao da IA

A frente IA precisa persistir `ai_suggested_*` e `ai_confidence`, mas nao pode editar
`model/ticket`. Se esta feature entregasse so as colunas, elas nasceriam sem nenhum escritor
possivel. Entao entra junto o mutator:

```java
void applyAiSuggestion(TicketCategory category, TicketPriority priority, Double confidence);
```

Guardar a sugestao separada da classificacao efetiva e o que torna a concordancia mensuravel: a
taxa de aceite e a fracao de chamados em que `category == ai_suggested_category` e
`priority == ai_suggested_priority`. Se a correcao do ADMIN sobrescrevesse a sugestao, a metrica
se tornaria identicamente 100%.

## Notificacoes SSE

`NotificationEventName` e uma classe final de constantes `String`, nao um enum:
`NotificationMessage.of(String eventName, ...)` ja recebe `String`, e um enum obrigaria a mudar a
assinatura do record — arquivo do motor SSE, fora do delta desta feature.

```java
public final class NotificationEventName {
    public static final String CHAMADO_ATUALIZADO = "CHAMADO_ATUALIZADO";
    public static final String CHAMADO_ALTA_PRIORIDADE = "CHAMADO_ALTA_PRIORIDADE";
    public static final String INDICADORES_ATUALIZADOS = "INDICADORES_ATUALIZADOS";
    public static final String CLASSIFICACAO_CONCLUIDA = "CLASSIFICACAO_CONCLUIDA";
    public static final String JOB_IA_FALHOU = "JOB_IA_FALHOU";
}
```

Os cinco nomes nascem juntos, mesmo os tres que esta feature nao dispara, porque o arquivo tem
dono unico. Se cada frente acrescentasse a sua constante, as tres colidiriam no mesmo arquivo no
merge.

| Evento | Audiencia | Disparado nesta feature por |
| --- | --- | --- |
| `CHAMADO_ATUALIZADO` | `Users(solicitante + responsavel)` | status, atribuicao, classificacao |
| `CHAMADO_ALTA_PRIORIDADE` | `Roles(ADMIN)` | classificacao que resulta em `ALTA` |
| `INDICADORES_ATUALIZADOS` | `Roles(ADMIN)` | — (frente IA) |
| `CLASSIFICACAO_CONCLUIDA` | `Users(solicitante)` + `Roles(ADMIN)` | — (frente IA) |
| `JOB_IA_FALHOU` | `Roles(ADMIN)` | — (frente IA) |

A publicacao usa `ApplicationEventPublisher`, nunca `NotificationService` direto: o
`NotificationDispatcher` e quem tem a barreira `@TransactionalEventListener(AFTER_COMMIT)`. Chamar
o service direto entregaria ao front um chamado atualizado que ainda pode sofrer rollback.

Cuidado de implementacao: o responsavel e anulavel e `Set.of(null)` lanca `NullPointerException`.
A montagem da audiencia `Users` monta o conjunto condicionalmente.

`CHAMADO_ALTA_PRIORIDADE` dispara na **transicao** para `ALTA`, nao em toda classificacao de um
chamado ja `ALTA`. Reclassificar categoria de um chamado que ja era `ALTA` nao e um alerta novo,
e repetir o alerta treinaria o ADMIN a ignora-lo.

### Payload dos eventos

O `data` dos dois eventos desta feature e um `TicketMinDto` — o mesmo objeto do item de listagem,
para que o Frontend atualize a linha da lista sem uma segunda chamada REST. Isso precisa estar
escrito em `docs/backend/api.md`, e nao apenas aqui: o documento de frentes define o `api.md` como
o contrato contra o qual o Frontend trabalha, e a secao "Notificacoes" de hoje so descreve
`CONEXAO_ESTABELECIDA`.

`TicketMinDto` ganha `assignedAt`. Sem isso o payload do evento nao carrega nenhum carimbo de
ciclo de vida, e um dashboard que se atualiza so pelo evento nao consegue recalcular nenhuma
metrica de tempo — teria de recarregar tudo por REST a cada notificacao, que e exatamente o que o
SSE deveria evitar. Os outros tres carimbos ficam so no `TicketDto`: sao do detalhe do chamado e
inflariam todo item de listagem.

## Autorizacao

O RBAC de leitura ja existe e nao e reimplementado: `AccessControlService.assertCanAccessTicket`
e `TicketService.resolveFilterByRole` ja restringem `SOLICITANTE` aos proprios chamados.

Toda mutacao nova desta feature nasce com `accessControlService.assertAdmin()`:

| Operacao | Autorizacao |
| --- | --- |
| `PATCH /tickets/{id}/status` | `assertAdmin()` |
| `PATCH /tickets/{id}/assignee` | `assertAdmin()` |
| `DELETE /tickets/{id}/assignee` | `assertAdmin()` |
| `applyClassification(...)` | nenhuma (ver secao do seam) |
| `POST /tickets/{id}/comments` | **inalterado** — `assertCanAccessTicket` |

A ultima linha e deliberada. `first_response_at` e preenchido dentro de
`TicketCommentService.create`, mas comentar continua sendo permitido ao `SOLICITANTE` no proprio
chamado. Acrescentar `assertAdmin()` ali por simetria mataria silenciosamente a funcionalidade de
comentario do solicitante.

## Erros

Nenhum codigo novo. O mapeamento usa o que `GlobalExceptionHandler` ja expoe:

| Situacao | Excecao | Status |
| --- | --- | --- |
| chamado inexistente | `NotFoundException` | `404` |
| responsavel inexistente | `NotFoundException` | `404` |
| usuario nao ADMIN | `ForbiddenException` | `403` |
| transicao invalida | `ConflictException` | `409` |
| chamado `FECHADO` | `ConflictException` | `409` |
| status igual ao atual | `ConflictException` | `409` |
| responsavel sem role ADMIN | `ConflictException` | `409` |
| remover responsavel de chamado sem responsavel | `ConflictException` | `409` |
| corpo sem `status`/`assigneeId` | bean validation | `400` |

Nenhuma excecao nova e criada. Todas as regras desta feature cabem em `ConflictException`,
`NotFoundException` e `ForbiddenException`, que ja existem em `exception/`.

## Seed de Desenvolvimento

`DevTicketSeeder` ja tem os dados necessarios: `resolvedAfterHours` e `firstReplyAfterHours` de
cada registro hoje so posicionam eventos de historico. Com a `V4`, o `insert` passa a preencher:

- `assigned_at` = `created_at + 1h` quando ha responsavel — o mesmo instante ja usado no evento
  `RESPONSAVEL_ATRIBUIDO`.
- `first_response_at` = `created_at + firstReplyAfterHours` quando ha comentario de responsavel.
- `resolved_at` = `created_at + resolvedAfterHours` para status `RESOLVIDO` e `FECHADO`.
- `closed_at` = mesmo instante, apenas para status `FECHADO`.
- `ai_suggested_category`, `ai_suggested_priority` e `ai_confidence` em todo chamado com origem
  `IA` ou `MANUAL` (origem `PENDENTE` nunca foi classificada e fica sem sugestao).

Nos chamados de origem `MANUAL`, **parte das sugestoes precisa divergir** da classificacao
efetiva: sao justamente os chamados em que o ADMIN corrigiu a IA. Se toda sugestao batesse com a
classificacao final, a taxa de concordancia admin x IA sairia 100% e a metrica nao mostraria nada.
A confianca tambem varia — sugestao corrigida recebe confianca mais baixa, que e o padrao realista
e o que da sentido ao cruzamento entre confianca e acerto.

Sem isso o dashboard da frente IA renderiza todas as metricas de tempo como vazias mesmo com 20
chamados no banco, e o avaliador ve um dashboard zerado.

## Testes

Cobertura em `backend/src/test/java/br/org/fadex/helpdesk`:

- `model/ticket/TicketStatusTransitionTest`: matriz completa de transicoes, incluindo o terminal.
- `service/TicketServiceTest`: transicoes validas e invalidas, carimbos de tempo em cada caminho,
  fechamento sem resolucao previa, atribuicao e desatribuicao, `assertAdmin()` em cada mutacao,
  `applyClassification` com origem `IA` sem autenticacao, disparo condicional de
  `CHAMADO_ALTA_PRIORIDADE`.
- `service/TicketCommentServiceTest`: `first_response_at` preenchido no primeiro comentario ADMIN,
  nao preenchido em comentario de `SOLICITANTE`, nao sobrescrito no segundo comentario ADMIN.
- `repository/TicketPersistenceTest`: colunas da `V4` persistem e recarregam.

Os testes de service usam mocks de repository, no padrao ja estabelecido pelos testes existentes.
A `V4` e validada de fato pelo `ddl-auto=validate` a cada `@SpringBootTest`: divergencia entre
migration e entidade derruba o contexto e quebra a suite inteira.

## Criterios de Aceite

- `make backend-test` verde.
- `V4` aplicada e `V5` livre para a frente IA.
- `NotificationEventName` com as cinco constantes.
- `applyClassification(...)` com a assinatura exata acordada, chamavel sem `SecurityContext`.
- `docs/backend/api.md` descrevendo status, atribuicao, carimbos novos e a matriz de transicoes.
- `SOLICITANTE` recebe `403` nas tres mutacoes novas e continua comentando nos proprios chamados.
- Chamado `FECHADO` responde `409` em qualquer transicao.

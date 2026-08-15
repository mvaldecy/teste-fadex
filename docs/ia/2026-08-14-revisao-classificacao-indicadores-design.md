# Revisao de Classificacao e Indicadores Design

## Objetivo

Fechar o ciclo de IA da central de chamados: hoje o modelo local classifica, mas ninguem revisa a
sugestao, nada da sugestao e persistido e nao existe leitura agregada do que o sistema produziu.

Esta frente entrega tres coisas que dependem uma da outra:

1. **Revisao da classificacao pelo ADMIN** — requisito obrigatorio do desafio. Sem ela a IA e uma
   caixa fechada: o operador nao consegue corrigir uma categoria errada.
2. **Auditoria da sugestao** — gravar o que a IA sugeriu, separado do que vale hoje no chamado. Sem
   isso a taxa de concordancia admin x IA e matematicamente impossivel de calcular, porque depois de
   uma correcao manual a sugestao original desaparece.
3. **Indicadores** — um endpoint, um payload, um evento SSE, quatro camadas de estatistica.

Complementam o ciclo a operacao da fila de IA (listar e retentar jobs) e a deteccao de duplicados
por embedding, ambos ja com infraestrutura pronta e nunca expostos.

## Escopo

Incluido:

- `PATCH /api/v1/tickets/{id}/classification` — ADMIN aceita ou corrige a sugestao da IA.
- Persistencia de `ai_suggested_category`, `ai_suggested_priority` e `ai_confidence` no fluxo do
  `AiJobWorker`.
- `confidence` e `justification` expostos no DTO do chamado.
- `GET /api/v1/indicators` — camadas 1 a 4 num payload unico.
- `GET /api/v1/ai/jobs` e `POST /api/v1/ai/jobs/{id}/retry`, ambos ADMIN.
- Deteccao de duplicados por embedding, gravando em `ticket_links`.
- Disparo de `CLASSIFICACAO_CONCLUIDA`, `JOB_IA_FALHOU` e `INDICADORES_ATUALIZADOS`.

Fora deste ciclo:

- Endpoint de busca de similares sob demanda (`GET /tickets/{id}/similar`). O design anterior o
  previu; esta frente grava o vinculo detectado, nao a consulta interativa.
- Remocao de vinculo por acao explicita do ADMIN.
- Cache ou materializacao dos indicadores.
- Retreino, ajuste de prompt ou troca de modelo.
- Qualquer mudanca em `TicketController`, `TicketService`, `security/` ou `frontend/`.

## Dependencia de Bloqueio

A frente API entrega antes desta frente:

| Artefato | Do que esta frente precisa |
| --- | --- |
| Migration `V4` | Colunas `ai_suggested_category`, `ai_suggested_priority`, `ai_confidence`, `closed_at`, `resolved_at`, `first_response_at`, `assigned_at` em `tickets`. |
| `NotificationEventName` | Constantes dos nomes de eventos SSE. |
| `TicketService.applyClassification(...)` | Unico caminho de escrita em `Ticket.category`, `priority` e `classificationOrigin`. |

Assinatura acordada no documento de frentes:

```java
void applyClassification(
    UUID ticketId,
    TicketCategory category,
    TicketPriority priority,
    ClassificationOrigin origin,
    String justification
);
```

`V5` fica reservada para esta frente. Nenhuma migration desta frente cria as colunas da `V4`.

Estado verificado na base `95da427` (`merge: prepara base para divisao em tres frentes`): a `V4` ainda
nao existe, `NotificationEventName` ainda nao existe e `TicketService.applyClassification(...)` ainda
nao existe. As ultimas migrations sao `V1`, `V2` e `V3`. O plano ordena primeiro tudo que independe
da `V4`.

### Lacuna no seed que precisa ser fechada pela frente API

O `DevTicketSeeder` grava `classification_origin` (mistura de `IA`, `MANUAL` e `PENDENTE`) mas **nao
tem como gravar `ai_suggested_category`, `ai_suggested_priority` e `ai_confidence`** — as colunas
ainda nao existem. A pendencia registrada no documento de frentes cita apenas `closed_at`,
`first_response_at` e `assigned_at`.

Consequencia direta: com os 20 chamados do seed e sem as sugestoes preenchidas, a **taxa de
concordancia admin x IA e a confianca media saem vazias** (`evaluated: 0`, `percentage: null`), porque
o denominador definido em D7 exige `ai_suggested_category` nao nula. A camada 3 renderiza um buraco
justamente na metrica que mais mostra o trabalho de IA para o avaliador.

Pedido a frente API, junto com a `V4`: preencher tambem `ai_suggested_*` e `ai_confidence` no seed.
Os chamados com origem `IA` recebem sugestao igual a classificacao vigente; os com origem `MANUAL`
recebem sugestao **diferente** em pelo menos parte dos casos, para a taxa nao sair 100% e o numero
significar alguma coisa; os `PENDENTE` ficam nulos. Confianca entre 0.55 e 0.95, variada — confianca
constante nao exercita nada.

Confirmada a saida A de D7, o seed precisa tambem carimbar `classification_reviewed_at` em parte dos
chamados; sem isso o denominador da concordancia fica zerado mesmo com as sugestoes preenchidas. Esse
carimbo e da `V5`, ou seja, **desta frente** — nao e pedido a frente API. Entra como passo proprio,
em SQL nativo, aplicado sobre os chamados ja semeados que tenham sugestao registrada.

Se a frente API nao fizer isso a tempo, a saida desta frente e um seed complementar proprio,
condicionado a `app.seed.enabled`, gravando so essas tres colunas. Nao entra sem necessidade — e
duplicacao de responsabilidade.

## Decisoes de Arquitetura

### D1 — Caminho de escrita das colunas de auditoria da IA

**Problema.** A seam `applyClassification(...)` nao carrega `confidence` nem as sugestoes. Quatro
entregas dependem desses tres campos (persistencia da sugestao, `confidence` no DTO, taxa de
concordancia, confianca media) e nenhuma tem por onde escrever.

**Criterio de decisao.** A seam existe porque mudanca em `category`, `priority` e
`classificationOrigin` precisa registrar `TicketEvent` — e regra de negocio com historico.
`ai_suggested_category`, `ai_suggested_priority` e `ai_confidence` sao colunas de **auditoria pura**:
nao mudam o que o chamado e, nao tem transicao valida ou invalida, nao aparecem no historico.

**Decisao (revisada).** Usar `Ticket.applyAiSuggestion(...)`, metodo que a frente API vai entregar
junto com a `V4`. O worker chama o metodo na entidade gerenciada e o Hibernate persiste no flush.

**Esta decisao substitui a anterior**, que era um repository proprio com `UPDATE` nativo estreito,
espelhando o `TicketEmbeddingRepository`. A troca nao e cosmetica — ela **elimina um bug** que a
versao anterior tinha e que so apareceria em producao:

```text
versao antiga (descartada):
1. updateSuggestion(...) grava ai_suggested_* por SQL nativo.
2. O Ticket gerenciado continua com null nesses campos.
3. applyClassification(...) muta category/priority; o flush emite UPDATE de todas as colunas
   mapeadas e sobrescreve com null o que o passo 1 gravou.
```

Aquele risco existia porque o precedente do `TicketEmbeddingRepository` **nao e equivalente**: o
commit `96521c0` removeu o mapeamento JPA da coluna `embedding`, e e justamente por ela nao ser um
campo da entidade que a escrita nativa nao briga com o Hibernate. As tres colunas de auditoria, ao
contrario, precisam ficar mapeadas — o `TicketMapper` le os getters para expor `confidence` no DTO.

Com `applyAiSuggestion(...)` mutando a mesma entidade gerenciada, sai um `UPDATE` unico e coerente:
nao ha duas escritas competindo, nao ha ordem obrigatoria entre elas, nao ha necessidade de
`flushAutomatically`/`clearAutomatically`, e o `TicketAiAuditRepository` deixa de existir.

**Fronteira preservada.** O metodo e entregue pela frente API, dona da entidade, exatamente para este
uso — nao e a frente IA abrindo caminho proprio. Ele toca **so** as tres colunas de auditoria.
`category`, `priority` e `classificationOrigin` continuam exclusivos de `applyClassification(...)`.

**Premissa a confirmar no rebase:** assinatura esperada
`applyAiSuggestion(TicketCategory category, TicketPriority priority, Double confidence)`, e o worker
precisa estar numa transacao com a entidade gerenciada para o flush acontecer. Se o metodo chegar
diferente, o ajuste e local ao worker.

**Revisado na implementacao — a premissa da transacao nao se sustentava.** A assinatura chegou como
esperado, mas o `AiJobWorker` e instanciado pelo Quartz, e nao resolvido como bean proxiado: depender
da transacao ambiente daquela thread deixaria a mutacao silenciosamente sem efeito, que e exatamente
o modo de falha que esta decisao existe para evitar. A escrita passou entao por
`TicketAiSuggestionService`, um `@Service` transacional em `ai/classification/`, que carrega o
chamado por `TicketService.findEntityById` (leitura e livre), chama `applyAiSuggestion` e devolve o
id do solicitante — o relacionamento e lazy e so pode ser lido dentro daquela transacao, e o worker
precisa desse id para a audiencia do `CLASSIFICACAO_CONCLUIDA`. A fronteira continua intacta:
categoria, prioridade e origem seguem exclusivas de `applyClassification`.

### D2 — O worker hoje viola a fronteira e precisa parar

`AiJobWorker.processClassification` chama `ticket.applyAutomaticClassification(...)` na entidade
obtida de `job.getTicket()`, dentro de um metodo `@Transactional`. O dirty checking do JPA persiste
essa mutacao no commit. Nao chamar `TicketRepository.save()` nao muda nada: a escrita acontece.

A correcao **remove** a mutacao e passa a chamar `applyClassification(...)`. Nao e adicionar uma
chamada ao lado — as duas juntas gravariam duas vezes, e o `TicketEvent` sairia inconsistente com o
estado.

```text
antes:  worker -> ticket.applyAutomaticClassification(...)  [dirty checking, sem historico]
depois: worker -> ticketService.applyClassification(id, cat, pri, IA, justification)  [com historico]
        worker -> ticketAiAuditRepository.updateSuggestion(id, cat, pri, confidence)  [auditoria]
```

### D3 — Nomes de eventos SSE enquanto a `V4` nao chega

**Encerrada.** `NotificationEventName` chegou em `dev` com as cinco constantes; o holder temporario
`AiNotificationEventName` foi apagado e todos os usos apontam para o enum da frente API.

`NotificationMessage.of(String eventName, ...)` recebe **String**, nao enum. Esta frente nao esta
bloqueada pela existencia do `NotificationEventName`.

Ate a frente API mergear, os tres nomes que esta frente dispara ficam como constantes em um unico
holder dentro de `ai/`, grafados exatamente como a tabela do documento de frentes:
`CLASSIFICACAO_CONCLUIDA`, `JOB_IA_FALHOU`, `INDICADORES_ATUALIZADOS`. No rebase e uma troca de
import por arquivo. Nenhum enum concorrente e criado no pacote da frente API.

### D4 — Leitura dos indicadores em repository proprio

`TicketRepository` pertence a frente API e hoje esta vazio (`JpaRepository` +
`JpaSpecificationExecutor`, sem nenhuma query). Adicionar meia duzia de `@Query` de agregacao nele
garante conflito no merge.

Esta frente cria um repository proprio, **somente leitura**, dentro de `ai/`, mesma forma do
`TicketEmbeddingRepository`. Ele nao tem nenhum metodo de escrita.

### D5 — Agregacao em memoria a partir de uma projecao unica

Os indicadores precisam de mediana e p90 por prioridade e por categoria, buckets de aging, SLA por
prioridade, agrupamento por responsavel e por solicitante. Em SQL isso vira mais de vinte
agregacoes, boa parte com `percentile_cont`, que nao existe no H2 usado nos testes.

**Decisao.** Uma projecao unica carrega uma linha enxuta por chamado e toda a agregacao acontece em
Java, com `Duration` e listas ordenadas.

Ganhos: os testes rodam em H2 sem SQL especifico de Postgres; mediana e p90 usam o mesmo codigo em
qualquer camada; adicionar uma metrica nao adiciona uma query.

Custo: a carga cresce linear com o numero de chamados. Com o volume do seed (20 chamados) e com o
volume plausivel de uma central interna, isso e irrelevante. A saida, se um dia deixar de ser, e
trocar a projecao por agregacao no banco atras da mesma interface de service — o contrato do
endpoint nao muda.

A projecao **nao** carrega `title`, `description` nem `embedding`. Sao os campos pesados, e nenhum
indicador usa.

### D6 — SLA como enum, nao como tabela

O documento de frentes ja decide: ALTA 4h, MEDIA 24h, BAIXA 72h, como configuracao e nao como
tabela. Fica um enum com o alvo em horas por prioridade, no pacote de indicadores.

Regra de apuracao, que precisa ser explicita para o numero significar algo:

- Chamado **fechado**: cumpre o SLA se `closed_at - created_at <= alvo`.
- Chamado **em aberto**: so conta como violado se a idade atual **ja passou** do alvo. Um chamado
  aberto ha 1h com alvo de 4h nao e violacao nem cumprimento — fica fora do denominador.

Sem essa segunda regra, todo chamado recem-aberto entraria como violacao e o percentual afundaria
sozinho com o tempo.

### D7 — Concordancia admin x IA

**DECIDIDO pelo Marcos: saida A — coluna `classification_reviewed_at` na `V5`.**

A metrica pedida e "% de sugestoes **aceitas** sem correcao". Aceitar e um ato do ADMIN. O problema
que motivou a decisao: com a regra do contrato ("origem vira `MANUAL` so quando corrigida"), **nada
no schema distinguia um chamado aceito de um chamado que ninguem olhou** — os dois ficam com origem
`IA` e com `category`/`priority` iguais a sugestao.

A primeira versao deste design definia concordancia como "a sugestao continua valendo" e tratava
isso como virtude da formulacao. Estava errado, e o erro fica registrado em vez de apagado: aquela
definicao **contava chamado nunca revisado como aceite**. Com os 20 chamados do seed, quase nenhum
revisado, o painel mostraria de 90% a 100% de concordancia — um numero que so mede que ninguem mexeu
em nada. Pior que metrica ausente e metrica bonita que nao mede o que diz medir.

**Definicao final:**

- **Denominador** (`evaluated`): chamados com `classification_reviewed_at` nao nulo **e** sugestao
  registrada (`ai_suggested_category` nao nula).
- **Numerador** (`agreed`): destes, os que tem `category == ai_suggested_category` **e**
  `priority == ai_suggested_priority`.
- `percentage` e `null` quando `evaluated` e `0`. O payload sempre expoe `evaluated` junto do
  percentual — percentual sem tamanho de amostra nao permite julgar se o numero significa algo.

Chamado que a IA classificou e ninguem revisou fica fora da conta, que e exatamente o ponto.
Chamados `PENDENTE` tambem ficam fora: a IA nao respondeu, nao ha o que concordar.

**Ganho colateral:** "% da fila que ja passou por revisao humana" passa a ser calculavel de graca,
com a mesma coluna.

### D7.1 — Migration `V5`

Unica migration desta frente. `V4` e da frente API e nao e tocada.

```sql
alter table tickets add column classification_reviewed_at timestamp;
```

Escrita exclusivamente pelo endpoint de revisao, no mesmo `applyClassification(...)`? Nao — a seam
pertence a frente API e nao tem esse parametro. O carimbo e feito pelo
`TicketClassificationReviewService`, na mesma transacao, pelo metodo `markClassificationReviewed(...)`
adicionado a entidade `Ticket`.

Como `Ticket` pertence a frente API, essa adicao e aditiva e minima: um campo mapeado, um getter e um
setter de dominio. Fica registrada nos Riscos como ponto de conflito esperado, junto com `TicketDto`
e `TicketMapper`.

### D8 — Duplicados: cosseno em Java, nao `<=>` no Postgres

`application-test.properties` mapeia a coluna de embedding para `varchar(20000)` e desliga o indice
pgvector no H2. Um `order by embedding <=> :vetor` nao roda em teste — a deteccao ficaria sem
cobertura.

**Decisao.** O worker de embedding, ao gravar o vetor do chamado, carrega os vetores dos chamados
candidatos, calcula similaridade de cosseno em Java e grava em `ticket_links` os pares acima do
limiar. Testavel em H2, e o volume de comparacao e o de uma central interna.

Candidatos: chamados com embedding gravado, excluindo o proprio.

**Configuracao reusada, nao criada.** `app.ai.similarity.threshold` e `app.ai.similarity.limit` ja
existiam em `application.properties` desde a V3 e **nunca foram lidas por nenhuma classe** — foram
declaradas para a busca de similares que nao chegou a ser implementada. Esta frente passa a consumi-las
em vez de inventar `app.ai.duplicate.*`, que deixaria quatro propriedades para o mesmo conceito.

Os defaults foram ajustados de `0.75`/`5` para `0.90`/`3`. Alterar default de propriedade viva seria
mudanca de comportamento; como nada as lia, nao ha regressao possivel. O motivo: `0.75` de cosseno em
embeddings `all-minilm` e frouxo para duplicidade — dois chamados de assuntos diferentes na mesma
categoria passam desse valor com facilidade, e o limiar precisa ser conservador porque um falso
positivo vira vinculo persistido que alguem tem que desfazer. O teto de 3 evita que um chamado
generico ("nao consigo acessar") vire hub ligado a meia base.

Candidatos com vetor de dimensao diferente da do chamado de origem sao ignorados, nao comparados: o
cosseno exigiria mesmo tamanho e um `IllegalArgumentException` no meio do worker derrubaria o job de
embedding inteiro. Isso acontece de verdade quando `AI_EMBEDDING_MODEL` muda e a base fica com vetores
de dois modelos.

O vinculo e gravado com `created_by` apontando para o solicitante do chamado de origem — a coluna e
`not null` na `V3` e nao existe usuario de sistema. Alternativa (usuario tecnico) exigiria migration
e nao paga o custo neste ciclo.

Deteccao de duplicado **nao bloqueia** criacao de chamado e nao altera status. E sinal, nao regra.

## Contratos

### PATCH /api/v1/tickets/{id}/classification

ADMIN. Aceita ou corrige a sugestao da IA.

```http
PATCH /api/v1/tickets/{id}/classification
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "category": "INFRAESTRUTURA",
  "priority": "ALTA",
  "justification": "Reclassificado: impacto em rede afeta o predio inteiro."
}
```

- `category` e `priority` obrigatorios. `justification` opcional (max 2000).
- `classificationOrigin` vira `MANUAL` **quando corrigida**, conforme o documento de frentes. Se o
  ADMIN envia exatamente os valores sugeridos pela IA, o gesto e aceite e a origem permanece `IA`.
  A comparacao e contra `ai_suggested_category`/`ai_suggested_priority` — a sugestao — e nao contra
  o valor corrente do chamado.
- Quando ainda nao ha sugestao registrada (`classificationOrigin` = `PENDENTE`, IA nao respondeu),
  qualquer chamada do endpoint e classificacao manual: origem vira `MANUAL`. Nao ha sugestao a
  aceitar.
- Aceite e correcao **ambos** registram `CLASSIFICACAO_ATUALIZADA`: houve ato do ADMIN nos dois
  casos, e o historico precisa mostrar que o chamado foi revisado mesmo quando nada mudou.
- Registra `TicketEvent` de `CLASSIFICACAO_ATUALIZADA` — por dentro de `applyClassification(...)`.
- Resposta `200` com `TicketDto`.
- `403` para SOLICITANTE, `404` para chamado inexistente.

O controller vive em `ai/`, mapeado no mesmo base path `/api/v1/tickets`. Nao ha colisao: o
`TicketController` atual so declara `GET /`, `GET /{id}` e `POST /`.

### TicketDto — campos novos

```json
{
  "classificationOrigin": "IA",
  "classificationJustification": "Mencao a rede e servidor indica infraestrutura.",
  "aiSuggestedCategory": "INFRAESTRUTURA",
  "aiSuggestedPriority": "ALTA",
  "confidence": 0.87
}
```

`classificationJustification` ja existe. Os tres campos novos sao nulos enquanto a IA nao respondeu.

**Revisado na implementacao:** o campo da confianca chama-se `confidence`, e nao `aiConfidence` como
este documento previa. `frontend/` esta fora do escopo desta frente e ja consome `ticket.confidence`;
publicar `aiConfidence` deixaria o selo de confianca sem renderizar por divergencia de nome. Como
nenhum consumidor usava o nome antigo, alinhar ao consumidor real custou menos que pedir mudanca no
frontend. Os dois campos de sugestao mantiveram o prefixo `ai`, que o frontend ja usava.

### GET /api/v1/ai/jobs

ADMIN, `@PreAuthorize("hasRole('ADMIN')")`. Paginado, padrao 10, ordenado por `createdAt` desc.

Filtros: `status`, `type`, `ticketId`. Filtro dinamico com `hasStatus`/`hasType`/`hasTicketId` e
`AiJobSpecification.createSpecification`, como manda o `backend/AGENTS.md`.

Resposta: `Page<AiJobDto>` — o DTO ja existe e ja carrega `lastError`, que e o campo que interessa
ao ADMIN na tela de operacao.

### POST /api/v1/ai/jobs/{id}/retry

ADMIN. `AiJobService.retry(UUID)` ja existe, ja valida que so job `FAILED` pode ser retentado
(`409` via `ConflictException`) e ja devolve `AiJobDto`. Falta apenas o controller.

### GET /api/v1/indicators

ADMIN. Nomes de campo em ingles, como todo DTO do projeto.

```json
{
  "generatedAt": "2026-08-14T18:00:00",
  "overview": {
    "total": 20,
    "byStatus": { "ABERTO": 6, "EM_ANDAMENTO": 5, "RESOLVIDO": 4, "FECHADO": 5 },
    "byPriority": { "BAIXA": 6, "MEDIA": 9, "ALTA": 5 },
    "byCategory": { "ACESSO": 4, "SISTEMAS": 5, "INFRAESTRUTURA": 3, "OUTROS": 8 },
    "openedToday": 2, "closedToday": 1,
    "openedThisWeek": 7, "closedThisWeek": 4,
    "openHighPriority": 3
  },
  "durations": {
    "closure": {
      "overall": { "sampleSize": 5, "averageHours": 30.4, "medianHours": 22.0, "p90Hours": 61.5 },
      "byPriority": { "ALTA": { "sampleSize": 2, "averageHours": 6.0, "medianHours": 6.0, "p90Hours": 8.0 } },
      "byCategory": { "ACESSO": { "sampleSize": 1, "averageHours": 4.0, "medianHours": 4.0, "p90Hours": 4.0 } }
    },
    "firstResponse": { "overall": {}, "byPriority": {}, "byCategory": {} },
    "assignment": { "overall": {}, "byPriority": {}, "byCategory": {} },
    "backlogAging": { "upToOneDay": 3, "oneToThreeDays": 4, "overThreeDays": 4 },
    "oldestOpenTicketHours": 496.2,
    "sla": {
      "overall": { "evaluated": 14, "withinTarget": 9, "percentage": 64.3 },
      "byPriority": { "ALTA": { "evaluated": 5, "withinTarget": 2, "percentage": 40.0 } }
    }
  },
  "ai": {
    "agreementRate": { "evaluated": 12, "agreed": 9, "percentage": 75.0 },
    "averageConfidence": 0.82,
    "originDistribution": { "IA": 9, "MANUAL": 6, "PENDENTE": 5 },
    "jobQueue": {
      "pending": 2, "processing": 0, "failed": 1, "done": 37,
      "averageQueueToDoneSeconds": 4.6
    },
    "duplicatesDetected": 3
  },
  "workload": {
    "openByAssignee": [ { "user": { "id": "...", "name": "..." }, "openTickets": 4 } ],
    "closureTimeByAssignee": [ { "user": { "id": "...", "name": "..." }, "sampleSize": 3, "averageHours": 18.0, "medianHours": 16.0 } ],
    "topRequesters": [ { "user": { "id": "...", "name": "..." }, "tickets": 7 } ]
  }
}
```

Regras de leitura do payload:

- Toda estatistica de duracao carrega `sampleSize`. Com o volume do seed, media sem tamanho de
  amostra engana; media sem mediana engana mais. Por isso as duas sempre saem juntas, com o n.
- `sampleSize` zero devolve `null` em `averageHours`, `medianHours` e `p90Hours` — nao `0.0`. Zero e
  um valor; ausencia de dado nao e.
- Mediana: media dos dois centrais quando o n e par. p90: rank mais proximo (`ceil(0.9 * n)`).
- `backlogAging` conta chamados em `ABERTO` e `EM_ANDAMENTO`, por idade desde `created_at`.
- `openHighPriority` conta `ALTA` em `ABERTO` ou `EM_ANDAMENTO` — e o numero que sustenta o alerta
  de prioridade alta exigido pelo desafio.
- `topRequesters` traz no maximo 5.
- `averageQueueToDoneSeconds` mede `updatedAt - createdAt` dos jobs `DONE`: fila **mais** execucao.
  `AiJob` nao registra o inicio do processamento, entao o nome declara a mistura em vez de chamar
  isso de tempo de processamento. `null` so quando nao ha nenhum job concluido.
- Grupos vazios nao aparecem nos mapas por prioridade/categoria, em vez de aparecerem zerados.

### Eventos SSE disparados

| Evento | Quando | Audiencia | Payload |
| --- | --- | --- | --- |
| `CLASSIFICACAO_CONCLUIDA` | worker aplica classificacao da IA | `Users(solicitante)` + `Roles(ADMIN)` | `ticketId`, `category`, `priority`, `confidence` |
| `JOB_IA_FALHOU` | job esgota as tentativas | `Roles(ADMIN)` | `jobId`, `ticketId`, `type`, `attempts`, `lastError` |
| `INDICADORES_ATUALIZADOS` | classificacao aplicada ou revisada | `Roles(ADMIN)` | `reason`, `occurredAt` |

Publicacao sempre por `ApplicationEventPublisher.publishEvent(NotificationMessage.of(...))`. Nunca
`NotificationService` direto — a barreira `AFTER_COMMIT` esta no `NotificationDispatcher` e chamar o
service pula a barreira, notificando algo que a transacao ainda pode desfazer.

`JOB_IA_FALHOU` dispara **quando as tentativas acabam**, nao em toda falha. O worker ja reagenda
enquanto `attempts < maxAttempts`; notificar cada tentativa transformaria uma falha transitoria de
rede em tres alertas para o ADMIN.

`INDICADORES_ATUALIZADOS` leva so o motivo, nao o payload de indicadores. O payload e grande e o
front precisa dele fresco no momento em que renderiza — o evento e um sinal para refazer o `GET`.
A frente API dispara o mesmo evento nas mudancas de status; audiencia e nome sao os mesmos.

## Estrutura

```text
backend/src/main/java/br/org/fadex/helpdesk/ai/
├── classification/
│   ├── TicketClassificationController.java   PATCH /tickets/{id}/classification
│   ├── TicketClassificationReviewService.java  revisao pelo ADMIN
│   └── TicketClassificationUpdateDto.java
├── duplicate/
│   ├── DuplicateDetectionService.java        cosseno em Java, grava ticket_links (D8)
│   └── EmbeddingSimilarity.java              cosseno e parse do literal pgvector
├── indicator/
│   ├── IndicatorController.java              GET /indicators
│   ├── IndicatorService.java                 orquestra as quatro camadas
│   ├── IndicatorRepository.java              projecao unica, somente leitura (D4)
│   ├── TicketIndicatorProjection.java
│   ├── DurationStats.java                    media, mediana, p90 sobre uma lista
│   ├── SlaTarget.java                        ALTA 4h, MEDIA 24h, BAIXA 72h (D6)
│   └── ...Dto.java                           DTOs do payload
├── job/
│   ├── AiJobController.java                  GET /ai/jobs, POST /ai/jobs/{id}/retry
│   ├── AiJobFilter.java / AiJobFields.java / AiJobSpecification.java
│   └── (existentes)
└── notification/
    └── AiNotificationEventName.java          holder temporario dos nomes (D3)
```

Fora de `ai/`, apenas dois arquivos aditivos: `TicketDto` e `TicketMapper`, para expor
`aiSuggestedCategory`, `aiSuggestedPriority` e `aiConfidence`. Sao campos novos no fim do record —
merge trivial, e a exposicao do `confidence` e requisito explicito da frente.

## Testes

- `TicketClassificationReviewServiceTest` — origem vira `MANUAL`; delega a `applyClassification`;
  SOLICITANTE recebe `403`; chamado inexistente recebe `404`.
- `AiJobWorkerTest` (ja existe, sera estendido) — worker chama `applyClassification` e **nao** muta a
  entidade; grava as tres colunas de auditoria; dispara `CLASSIFICACAO_CONCLUIDA`; dispara
  `JOB_IA_FALHOU` so ao esgotar tentativas.
- `DurationStatsTest` — n par, n impar, n=1, lista vazia devolve nulos, p90 por rank.
- `SlaTargetTest` — fechado dentro, fechado fora, aberto ainda dentro do alvo fica fora do
  denominador, aberto ja estourado conta como violacao.
- `IndicatorServiceTest` — camadas 1 a 4 sobre projecoes montadas a mao, com datas fixas.
- `AiJobControllerTest` — `403` para SOLICITANTE nos dois endpoints.
- `EmbeddingSimilarityTest` — cosseno de vetores identicos, ortogonais e opostos; parse do literal.
- `DuplicateDetectionServiceTest` — grava acima do limiar, ignora abaixo, respeita o maximo por
  chamado, nao cria par duplicado.

Testes de duracao e SLA usam datas fixas, nunca `LocalDateTime.now()` — o relogio entra por
parametro para o teste nao ficar dependente do instante em que roda.

## Riscos

- **`TicketDto`, `TicketMapper` e `Ticket` pertencem a frente API** e esta frente os edita. Sao
  edicoes aditivas: tres campos no fim do record, tres linhas no mapper, e em `Ticket` o campo
  `classificationReviewedAt` com `markClassificationReviewed(...)`. O `confidence` no DTO e exigencia
  do documento de frentes. Conflito esperado: trivial.
- **`V5` e desta frente e cria uma unica coluna.** A frente API foi avisada e nao a invade. A frente
  API tambem adiciona `TicketEventType.RESPONSAVEL_REMOVIDO` e altera o check constraint
  `ck_ticket_events_type` na `V4` — esta frente nao cria evento novo, entao nao ha colisao ali.
- **`V4` pode nao estar em `dev`** quando o codigo precisar dela. O trabalho segue contra a
  assinatura documentada; a ordem do plano coloca tudo que independe da `V4` primeiro.
- **`AiJobWorker` fica com dependencia de `TicketService`**, que depende de `AiJobService`. Nao ha
  ciclo de bean: o worker e criado pelo Quartz e nao e injetado em `TicketService`.
- **Agregacao em memoria** nao escala indefinidamente (D5). Aceito, com a saida documentada.
- **Duplicados e `/ai/jobs` estao na linha de corte** — sao os primeiros a cair, e por isso ficam no
  fim do plano.

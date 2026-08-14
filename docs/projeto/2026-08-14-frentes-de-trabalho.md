# Frentes de Trabalho — Reta Final

Base: `dev` em `e8695b8` (motor SSE mergeado). Prazo de submissão: 15/08/2026 às 12h.

Documento de divisão de escopo para execução em paralelo. Cada frente trabalha em worktree próprio,
parte de `dev` e abre PR separada.

## Contratos Compartilhados (definidos pela frente API antes das demais começarem)

Estes três artefatos desbloqueiam as outras frentes e precisam sair primeiro:

1. **Migration `V4`** com as colunas de ciclo de vida e de auditoria da IA.
2. **`NotificationEventName`** — nomes de eventos SSE, criados de uma vez só.
3. **Delta em `docs/backend/api.md`** com os endpoints novos, antes da implementação.
4. **Seam `TicketService.applyClassification(...)`** — assinatura única pela qual a frente IA
   escreve no `Ticket` (detalhe abaixo).
5. ~~Seed de desenvolvimento com volume~~ — **feito antes da divisão** (detalhe abaixo).

### Reserva de versões do Flyway

`V4` pertence à frente API. `V5` fica reservada para a frente IA, caso precise de coluna própria.
A frente Frontend não cria migration. Sem essa reserva, duas frentes criam `V5` e a falha só
aparece no merge.

### Colunas da V4

```
tickets: closed_at, resolved_at, first_response_at, assigned_at,
         ai_suggested_category, ai_suggested_priority, ai_confidence
```

Justificativa: tempo de fechamento e tempo de primeira resposta não são calculáveis hoje
(`Ticket` só tem `createdAt`/`updatedAt`). Denormalizar evita varredura de `ticket_events`
em toda carga de dashboard. `ai_suggested_*` e `ai_confidence` existem hoje apenas em memória
no record `TicketClassification` — sem persistir, a métrica de concordância admin×IA é impossível.

### Nomes de eventos SSE

| Evento | Audiência | Disparado por |
| --- | --- | --- |
| `CHAMADO_ATUALIZADO` | `Users` (solicitante + responsável) | API |
| `CHAMADO_ALTA_PRIORIDADE` | `Roles(ADMIN)` | API |
| `INDICADORES_ATUALIZADOS` | `Roles(ADMIN)` | API |
| `CLASSIFICACAO_CONCLUIDA` | `Users` (solicitante) + `Roles(ADMIN)` | IA |
| `JOB_IA_FALHOU` | `Roles(ADMIN)` | IA |

Como publicar (não chamar `NotificationService` direto — a barreira pós-commit é do dispatcher):

```java
applicationEventPublisher.publishEvent(
    NotificationMessage.of(NotificationEventName.CHAMADO_ATUALIZADO, payload, audience)
);
```

Regra anti-conflito: **somente a frente API edita o arquivo de nomes de eventos.** As demais
apenas referenciam constantes já existentes.

### Seam entre API e IA

Dizer que "a revisão vive em service próprio dentro de `ai/`" não impede a frente IA de mutar
`Ticket.category`, `priority` e `classificationOrigin` e salvar via `TicketRepository` — que é
exatamente a posse dupla que queremos evitar. A frente API expõe **um** método e a frente IA usa
só ele:

```java
void applyClassification(
    UUID ticketId,
    TicketCategory category,
    TicketPriority priority,
    ClassificationOrigin origin,
    String justification
);
```

O método é quem registra o `TicketEvent` de `CLASSIFICACAO_ATUALIZADA`. A assinatura entra no
passo 1 para não ser negociada na hora do merge.

### Seed de desenvolvimento (já entregue)

`DevDataSeeder` criava dois usuários e **zero chamados** — toda a camada 2 e a camada 3 de
estatísticas renderizariam vazio para o avaliador.

Entregue antes da divisão, em `DevTicketSeeder`: 20 chamados distribuídos entre os quatro status
e as três prioridades, `created_at` retroagido de 6h a 500h para gerar dispersão, mistura de
`classification_origin` (IA / MANUAL / PENDENTE), responsáveis variados, 15 comentários e 60
eventos de histórico. Usuários passaram de 2 para 6, com dois ADMIN e três SOLICITANTE para as
métricas por responsável e por solicitante fazerem sentido.

O seed deixou de ser preso ao profile `dev`: passou a ser controlado por `app.seed.enabled`
(`APP_SEED_ENABLED`, padrão `true`), desligado em `application-test.properties`. Motivo: sem o
profile, o `@SpringBootTest` executa os `CommandLineRunner` e o seed colide com os usuários
criados pelos próprios testes. No deploy, basta `APP_SEED_ENABLED=false`.

A escrita é em SQL nativo por necessidade: `@CreatedDate` sobrescreveria `created_at` com o
horário atual e `Ticket` não expõe troca de status. A verificação de duplicidade é por título,
não por contagem da tabela, para conviver com chamados criados manualmente.

**Pendência para a frente API**: quando a V4 existir, preencher `closed_at`, `first_response_at`
e `assigned_at` no seed. Os dados já estão lá — `resolvedAfterHours` e `firstReplyAfterHours` de
cada registro definem os instantes, hoje usados só para posicionar os eventos de histórico.

---

## Frente 1 — API (Chamados, RBAC e Indicadores)

Dona de: `controller/`, `service/`, `security/`, `repository/`, `model/ticket`, `model/event`,
`db/migration`.

### Escopo

- Migration `V4` + `NotificationEventName` + delta do `api.md` (**entregar primeiro, sozinha**).
- `PATCH /api/v1/tickets/{id}/status` — transições válidas, preenche `closed_at`/`resolved_at`,
  registra `TicketEvent` de `STATUS_ALTERADO`.
- `PATCH /api/v1/tickets/{id}/assignee` — atribuir responsável, preenche `assigned_at`,
  evento `RESPONSAVEL_ATRIBUIDO`.
- `DELETE /api/v1/tickets/{id}/assignee` — recusar/remover atribuição.
- Regra: chamado `FECHADO` não reabre (409).
- RBAC por ação: `SOLICITANTE` só enxerga e comenta os próprios chamados; mudança de status,
  atribuição e classificação são de `ADMIN`.
- `first_response_at` preenchido no primeiro comentário de um `ADMIN`.
- `GET /api/v1/tickets/indicators` — camadas 1, 2 e 4 das estatísticas (abaixo).
- Disparo de `CHAMADO_ATUALIZADO`, `CHAMADO_ALTA_PRIORIDADE` e `INDICADORES_ATUALIZADOS`.

### Estatísticas sob responsabilidade desta frente

Camada 1 — contagem por status, prioridade e categoria; abertos vs. fechados hoje/semana;
chamados ALTA em aberto.

Camada 2 — tempo de fechamento (**média, mediana e p90**, por prioridade e categoria);
tempo até primeira resposta; tempo até atribuição; aging do backlog em buckets 0–1d / 1–3d / >3d;
idade do chamado aberto mais antigo; % dentro do SLA (ALTA 4h, MÉDIA 24h, BAIXA 72h — como enum
de configuração, **não** como tabela).

Camada 4 — carga aberta por responsável; tempo médio de fechamento por responsável; top solicitantes.

Nota: com volume de seed, média isolada é enganosa. Sempre expor mediana junto.

---

## Frente 2 — IA (Revisão, Duplicados e Operação)

Dona de: `ai/**`, `config/AiJobQuartzConfig`, `docs/ia`.

Não edita `TicketService` — a revisão de classificação vive em service próprio dentro de `ai/`.

### Escopo

- Persistir `ai_suggested_category`, `ai_suggested_priority` e `ai_confidence` no fluxo do
  `AiJobWorker` (colunas criadas pela V4).
- `PATCH /api/v1/tickets/{id}/classification` — ADMIN aceita ou corrige a sugestão da IA,
  muda `classificationOrigin` para `MANUAL` quando corrigida, registra `TicketEvent` de
  `CLASSIFICACAO_ATUALIZADA`. **Requisito obrigatório do desafio.**
- Expor `confidence` e `justification` no DTO do chamado.
- Detecção de duplicados por embedding, gravando em `ticket_links` (tabela já existe na V3, sem uso).
- `GET /api/v1/ai/jobs` e `POST /api/v1/ai/jobs/{id}/retry` — **`@PreAuthorize("hasRole('ADMIN')")`**.
  O `AiJobService.retry()` já existe e nunca foi exposto.
- `GET /api/v1/ai/indicators` — camada 3 das estatísticas.
- Disparo de `CLASSIFICACAO_CONCLUIDA` e `JOB_IA_FALHOU`.

### Estatísticas sob responsabilidade desta frente

Camada 3 — taxa de concordância admin×IA (% de sugestões aceitas sem correção); confiança média;
distribuição IA / Manual / Pendente; fila de jobs (pendentes, falhos, tempo médio de processamento);
duplicados detectados.

---

## Frente 3 — Frontend

Dona de: `frontend/**`.

Trabalha contra o contrato escrito em `docs/backend/api.md`, com dados fixos no lugar da chamada
real enquanto o endpoint não existe, trocando a fonte quando a API subir.

### Escopo

- **Menu de usuário no header do `app-shell.tsx` com logout.** O `logout()` já existe em
  `session.store.ts` e nunca foi ligado à UI.
- **Página `/usuarios`** (somente ADMIN). O `usersService` já tem `list/getById/create` prontos.
- **Página `/dashboard`** com os indicadores das três camadas, atualizando via SSE.
- Ações no detalhe do chamado: alterar status, atribuir responsável, recusar atribuição,
  classificar manualmente / aceitar sugestão da IA. Hoje `ticket-actions.tsx` só tem "Visualizar".
- **Página `/admin/jobs`** (somente ADMIN) com retry de job.
- Gating por papel na navegação e nas rotas.
- **Consumo do stream SSE** em `use-ticket-events.ts` (hoje é um stub com `useEffect` vazio).

### Atenção no SSE

O `EventSource` nativo não envia header `Authorization`. O contrato exige consumir
`GET /api/v1/notifications/stream` via `fetch` com leitura incremental do corpo e parse manual
dos frames. Não há replay de `Last-Event-ID`: ao reconectar, recarregar os dados via REST.
Tratar isso como tarefa dedicada, não como plugar um `EventSource`.

---

## Frente 4 — Entrega (pequena, mas bloqueia a submissão)

Itens de checklist avaliados que não aparecem em nenhuma das três frentes acima e estão como
Pendente em `docs/projeto/acompanhamento-desenvolvimento.md`:

- README com descrição, stack e passo a passo de execução local.
- README com credenciais de ADMIN e SOLICITANTE.
- README com justificativa da abordagem de IA.
- Exemplos de requisição (curl ou coleção Postman/Insomnia).
- Repositório tornado público antes da submissão.

Documentação vale 5% e os itens de README estão na checklist obrigatória de submissão. Precisa de
dono e de horário reservado — não é polimento opcional.

## Linha de Corte

Restam ~18h. Se o tempo apertar, corta de baixo para cima.

**Obrigatório do desafio — não corta:** `PATCH` de status, atribuição de responsável, RBAC
ADMIN×SOLICITANTE, revisão da sugestão da IA pelo ADMIN, indicadores + alerta de prioridade ALTA,
regra de não reabrir chamado fechado, histórico de mudanças, README e credenciais.

**Corta primeiro, nesta ordem:** camada 4 (estatísticas por responsável), p90 e percentis,
detecção de duplicados via `ticket_links`, página `/admin/jobs`, `% dentro do SLA`.

## Ordem de Execução

1. Frente API entrega V4 + seed + `applyClassification` + nomes de eventos + delta do `api.md`.
   **Sozinha.**
2. Frentes IA e Frontend seguem em paralelo a partir daí.
3. Frente Entrega fecha README e exemplos de requisição perto do fim, com horário reservado.

## Riscos

- `docs/backend/api.md` é editado pelas três frentes. Conflito trivial esperado; cada frente escreve
  na sua própria seção.
- `TicketService` é o ponto de colisão entre API e IA. Resolvido pela regra de posse acima.
- O arquivo de nomes de eventos SSE tem dono único (API) justamente para não conflitar.

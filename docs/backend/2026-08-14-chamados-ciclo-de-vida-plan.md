# Chamados e Ciclo de Vida Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fechar o ciclo de vida do chamado — transicoes de status com maquina de estados, atribuicao e recusa de responsavel, carimbos de tempo que tornam as metricas calculaveis — e entregar antes disso os contratos compartilhados que desbloqueiam as frentes IA e Frontend.

**Architecture:** A `V4` acrescenta quatro carimbos de ciclo de vida e tres colunas de auditoria da sugestao da IA em `tickets`. A matriz de transicoes vive em `TicketStatusTransition`, consultavel pelas tres frentes, e nao espalhada em `if` no service. `TicketService` ganha as mutacoes de status e responsavel, todas com `assertAdmin()`, e o seam `applyClassification(...)` — unica porta de escrita de classificacao no `Ticket`, chamavel sem `SecurityContext` porque o worker de IA a usa. Notificacoes saem por `ApplicationEventPublisher`, nunca por `NotificationService` direto.

**Tech Stack:** Java 21, Spring Boot, Spring MVC, Spring Security, Spring Data JPA, Flyway, PostgreSQL 17, H2 em testes, JUnit 5, Mockito.

**Spec:** `docs/backend/2026-08-14-chamados-ciclo-de-vida-design.md`

## Global Constraints

- Branch de trabalho: `feature(backend)/chamados-ciclo-de-vida`, a partir de `dev`.
- **Fase de implementacao sem commits.** Por decisao de revisao, o codigo das Tasks 1 a 10 fica no
  working tree, sem `git add` e sem `git commit`, para revisao do diff inteiro de uma vez. Os
  passos "Commit" de cada task ficam registrados abaixo como marcos de conclusao e como o commit
  que sera feito depois da revisao — **nao execute-os enquanto a revisao nao liberar.** Nada de
  `git stash`, `git checkout .` ou `git reset` no codigo: o trabalho nao commitado e o material de
  revisao.
- Documento de design e plano de implementacao: esses sim, commitados normalmente.
- `make backend-test` precisa passar, com saida real conferida, antes de declarar qualquer task
  concluida.
- A migration desta frente e a **`V4`**. **`V5` esta reservada para a frente IA — nao usar.**
- `NotificationEventName` tem dono unico: esta frente. As demais apenas referenciam.
- A assinatura `applyClassification(UUID, TicketCategory, TicketPriority, ClassificationOrigin, String)` e contrato fechado com a frente IA e nao pode mudar.
- Nao tocar em `ai/**` nem em `frontend/**`.
- Nao implementar indicadores/estatisticas — pertencem a frente IA.
- Nao implementar RBAC formal; apenas `accessControlService.assertAdmin()` em cada mutacao nova.
- `Ticket.changeStatus(...)` e `Ticket.unassign()` **ja existem** na `dev` — nao recriar.
- Services mantem variaveis intermediarias, sem concentrar chamadas no `return` (`backend/AGENTS.md`).
- Controllers retornam `ResponseEntity`.
- `spring.jpa.hibernate.ddl-auto=validate`: coluna mapeada com tipo Java incompativel derruba a suite inteira.

---

## File Structure

**Passo 1 — contratos compartilhados (entregar primeiro, sozinho):**

- Create `backend/src/main/resources/db/migration/V4__add_ticket_lifecycle_columns.sql`: sete colunas novas, constraints de dominio e indices.
- Modify `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/Ticket.java`: campos e mutators dos carimbos e da sugestao da IA.
- Create `backend/src/main/java/br/org/fadex/helpdesk/sse/model/NotificationEventName.java`: as cinco constantes de evento.
- Modify `backend/src/main/java/br/org/fadex/helpdesk/security/AccessControlService.java`: `findAuthenticatedUserId()` tolerante a ausencia de JWT.
- Modify `backend/src/main/java/br/org/fadex/helpdesk/service/TicketService.java`: seam `applyClassification(...)`.
- Modify `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketDto.java` e `TicketMapper.java`: expor os carimbos.
- Modify `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketFields.java`: constantes dos campos novos.
- Modify `docs/backend/api.md`: delta dos endpoints e dos campos novos, **antes** da implementacao.
- Modify `backend/src/main/java/br/org/fadex/helpdesk/config/DevTicketSeeder.java`: preencher os carimbos.

**Passo 2 — ciclo de vida:**

- Create `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketStatusTransition.java`: matriz de transicoes.
- Create `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketStatusUpdateDto.java`: corpo do `PATCH /status`.
- Create `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketAssigneeUpdateDto.java`: corpo do `PATCH /assignee`.
- Modify `backend/src/main/java/br/org/fadex/helpdesk/service/TicketService.java`: `updateStatus`, `updateAssignee`, `removeAssignee`.
- Modify `backend/src/main/java/br/org/fadex/helpdesk/controller/TicketController.java`: os tres endpoints.
- Modify `backend/src/main/java/br/org/fadex/helpdesk/service/TicketCommentService.java`: `first_response_at`.

**Testes:**

- Create `backend/src/test/java/br/org/fadex/helpdesk/model/ticket/TicketStatusTransitionTest.java`.
- Modify `backend/src/test/java/br/org/fadex/helpdesk/service/TicketServiceTest.java`.
- Modify `backend/src/test/java/br/org/fadex/helpdesk/service/TicketCommentServiceTest.java`.
- Modify `backend/src/test/java/br/org/fadex/helpdesk/repository/TicketPersistenceTest.java`.

---

## Passo 1 — Contratos Compartilhados

### Task 1: Migration V4 e campos de ciclo de vida na entidade

**Files:**
- Create: `backend/src/main/resources/db/migration/V4__add_ticket_lifecycle_columns.sql`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/Ticket.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketFields.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/repository/TicketPersistenceTest.java`

**Interfaces:**
- Consumes: nada.
- Produces: `Ticket.markResolved(LocalDateTime)`, `Ticket.markClosed(LocalDateTime)`, `Ticket.markAssigned(LocalDateTime)`, `Ticket.markFirstResponse(LocalDateTime)`, `Ticket.applyAiSuggestion(TicketCategory, TicketPriority, Double)`, e os getters `getResolvedAt()`, `getClosedAt()`, `getAssignedAt()`, `getFirstResponseAt()`, `getAiSuggestedCategory()`, `getAiSuggestedPriority()`, `getAiConfidence()`. Colunas `resolved_at`, `closed_at`, `first_response_at`, `assigned_at`, `ai_suggested_category`, `ai_suggested_priority`, `ai_confidence`.

- [ ] **Step 1: Escrever a migration**

Create `backend/src/main/resources/db/migration/V4__add_ticket_lifecycle_columns.sql`:

```sql
alter table tickets add column resolved_at timestamp;
alter table tickets add column closed_at timestamp;
alter table tickets add column first_response_at timestamp;
alter table tickets add column assigned_at timestamp;
alter table tickets add column ai_suggested_category varchar(40);
alter table tickets add column ai_suggested_priority varchar(20);
alter table tickets add column ai_confidence double precision;

alter table tickets add constraint ck_tickets_ai_suggested_category
    check (ai_suggested_category is null or ai_suggested_category in (
        'ACESSO', 'SISTEMAS', 'INFRAESTRUTURA', 'EQUIPAMENTOS', 'FINANCEIRO', 'RH', 'OUTROS'
    ));

alter table tickets add constraint ck_tickets_ai_suggested_priority
    check (ai_suggested_priority is null or ai_suggested_priority in ('BAIXA', 'MEDIA', 'ALTA'));

alter table tickets add constraint ck_tickets_ai_confidence_range
    check (ai_confidence is null or (ai_confidence >= 0 and ai_confidence <= 1));

create index idx_tickets_closed_at on tickets (closed_at);
create index idx_tickets_created_at on tickets (created_at);
```

Nota: os `alter table` sao separados de proposito. H2 em modo PostgreSQL nao aceita `add column` multiplo com a mesma sintaxe do Postgres, e a suite roda em H2.

- [ ] **Step 2: Escrever o teste de persistencia**

Em `backend/src/test/java/br/org/fadex/helpdesk/repository/TicketPersistenceTest.java`, acrescentar:

```java
@Test
void devePersistirCarimbosDeCicloDeVidaESugestaoDaIa() {
    User requester = userRepository.save(newUser("carimbos@fadex.org.br", Role.SOLICITANTE));
    Ticket ticket = new Ticket(
            "Chamado com carimbos",
            "Descricao do chamado com carimbos de ciclo de vida.",
            TicketCategory.SISTEMAS,
            TicketPriority.MEDIA,
            ClassificationOrigin.PENDENTE,
            requester
    );
    LocalDateTime instant = LocalDateTime.of(2026, 8, 14, 10, 0);

    ticket.markAssigned(instant);
    ticket.markFirstResponse(instant.plusHours(1));
    ticket.markResolved(instant.plusHours(2));
    ticket.markClosed(instant.plusHours(3));
    ticket.applyAiSuggestion(TicketCategory.ACESSO, TicketPriority.ALTA, 0.87);

    Ticket saved = ticketRepository.saveAndFlush(ticket);
    entityManager.clear();
    Ticket reloaded = ticketRepository.findById(saved.getId()).orElseThrow();

    assertThat(reloaded.getAssignedAt()).isEqualTo(instant);
    assertThat(reloaded.getFirstResponseAt()).isEqualTo(instant.plusHours(1));
    assertThat(reloaded.getResolvedAt()).isEqualTo(instant.plusHours(2));
    assertThat(reloaded.getClosedAt()).isEqualTo(instant.plusHours(3));
    assertThat(reloaded.getAiSuggestedCategory()).isEqualTo(TicketCategory.ACESSO);
    assertThat(reloaded.getAiSuggestedPriority()).isEqualTo(TicketPriority.ALTA);
    assertThat(reloaded.getAiConfidence()).isEqualTo(0.87);
}
```

Conferir no topo do arquivo se `entityManager` ja esta injetado; se nao estiver, seguir o padrao ja usado pelos outros testes do arquivo para forcar releitura do banco.

- [ ] **Step 3: Rodar o teste e ver falhar**

Run: `make backend-test`
Expected: FAIL na compilacao — `markAssigned`, `markResolved`, `applyAiSuggestion` etc. nao existem.

- [ ] **Step 4: Acrescentar campos e mutators na entidade**

Em `Ticket.java`, apos o campo `embeddingUpdatedAt`:

```java
	@Column(name = "resolved_at")
	private LocalDateTime resolvedAt;

	@Column(name = "closed_at")
	private LocalDateTime closedAt;

	@Column(name = "first_response_at")
	private LocalDateTime firstResponseAt;

	@Column(name = "assigned_at")
	private LocalDateTime assignedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "ai_suggested_category", length = 40)
	private TicketCategory aiSuggestedCategory;

	@Enumerated(EnumType.STRING)
	@Column(name = "ai_suggested_priority", length = 20)
	private TicketPriority aiSuggestedPriority;

	@Column(name = "ai_confidence")
	private Double aiConfidence;
```

E os mutators, junto dos ja existentes:

```java
	public void markResolved(LocalDateTime resolvedAt) {
		this.resolvedAt = resolvedAt;
	}

	public void markClosed(LocalDateTime closedAt) {
		this.closedAt = closedAt;
	}

	public void markAssigned(LocalDateTime assignedAt) {
		this.assignedAt = assignedAt;
	}

	public void markFirstResponse(LocalDateTime firstResponseAt) {
		this.firstResponseAt = firstResponseAt;
	}

	public void applyAiSuggestion(TicketCategory category, TicketPriority priority, Double confidence) {
		this.aiSuggestedCategory = category;
		this.aiSuggestedPriority = priority;
		this.aiConfidence = confidence;
	}
```

Mais os sete getters correspondentes, no mesmo estilo dos existentes.

`Double` e nao `double`: a coluna e anulavel e o primitivo transformaria ausencia de sugestao em confianca `0.0`.

- [ ] **Step 5: Acrescentar as constantes em `TicketFields`**

```java
	public static final String RESOLVED_AT = "resolvedAt";
	public static final String CLOSED_AT = "closedAt";
	public static final String FIRST_RESPONSE_AT = "firstResponseAt";
	public static final String ASSIGNED_AT = "assignedAt";
	public static final String AI_SUGGESTED_CATEGORY = "aiSuggestedCategory";
	public static final String AI_SUGGESTED_PRIORITY = "aiSuggestedPriority";
	public static final String AI_CONFIDENCE = "aiConfidence";
```

- [ ] **Step 6: Rodar os testes e ver passar**

Run: `make backend-test`
Expected: BUILD SUCCESSFUL. `ddl-auto=validate` valida a `V4` contra a entidade em todo `@SpringBootTest`; divergencia de tipo derruba o contexto.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/resources/db/migration/V4__add_ticket_lifecycle_columns.sql \
        backend/src/main/java/br/org/fadex/helpdesk/model/ticket/Ticket.java \
        backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketFields.java \
        backend/src/test/java/br/org/fadex/helpdesk/repository/TicketPersistenceTest.java
git commit -m "feat(backend): adiciona colunas de ciclo de vida e sugestao da ia no chamado"
```

---

### Task 2: NotificationEventName

**Files:**
- Create: `backend/src/main/java/br/org/fadex/helpdesk/sse/model/NotificationEventName.java`

**Interfaces:**
- Consumes: nada.
- Produces: as cinco constantes `String`, consumidas pelas frentes IA e Frontend.

- [ ] **Step 1: Criar o arquivo**

```java
package br.org.fadex.helpdesk.sse.model;

/**
 * Nomes dos eventos SSE do projeto.
 *
 * Constantes de String, e nao enum, porque {@link NotificationMessage#of} recebe o nome do evento
 * como String; um enum obrigaria a mudar a assinatura do record do motor de notificacoes.
 *
 * Este arquivo tem dono unico — a frente API. As demais frentes apenas referenciam constantes
 * ja existentes, para que os nomes de evento nao colidam no merge.
 */
public final class NotificationEventName {

	public static final String CHAMADO_ATUALIZADO = "CHAMADO_ATUALIZADO";
	public static final String CHAMADO_ALTA_PRIORIDADE = "CHAMADO_ALTA_PRIORIDADE";
	public static final String INDICADORES_ATUALIZADOS = "INDICADORES_ATUALIZADOS";
	public static final String CLASSIFICACAO_CONCLUIDA = "CLASSIFICACAO_CONCLUIDA";
	public static final String JOB_IA_FALHOU = "JOB_IA_FALHOU";

	private NotificationEventName() {
	}
}
```

As cinco constantes nascem juntas, mesmo as tres que esta frente nao dispara. Se cada frente acrescentasse a sua, as tres colidiriam no mesmo arquivo.

- [ ] **Step 2: Rodar os testes**

Run: `make backend-test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/br/org/fadex/helpdesk/sse/model/NotificationEventName.java
git commit -m "feat(backend): define nomes dos eventos sse do projeto"
```

---

### Task 3: Seam applyClassification

**Files:**
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/security/AccessControlService.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/Ticket.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/service/TicketService.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/service/TicketServiceTest.java`

**Interfaces:**
- Consumes: `NotificationEventName` (Task 2), mutators de `Ticket` (Task 1).
- Produces: `TicketService.applyClassification(UUID, TicketCategory, TicketPriority, ClassificationOrigin, String)`, consumido pela frente IA; `AccessControlService.findAuthenticatedUserId(): Optional<UUID>`; `Ticket.applyClassification(TicketCategory, TicketPriority, ClassificationOrigin, String)`.

- [ ] **Step 1: Escrever os testes que falham**

Em `TicketServiceTest.java`:

```java
@Test
void applyClassificationDeveAplicarClassificacaoERegistrarEvento() {
    Ticket ticket = newTicket(TicketPriority.MEDIA);
    UUID ticketId = ticket.getId();
    when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
    when(accessControlService.findAuthenticatedUserId()).thenReturn(Optional.empty());

    ticketService.applyClassification(
            ticketId,
            TicketCategory.ACESSO,
            TicketPriority.MEDIA,
            ClassificationOrigin.IA,
            "Justificativa da IA."
    );

    assertThat(ticket.getCategory()).isEqualTo(TicketCategory.ACESSO);
    assertThat(ticket.getClassificationOrigin()).isEqualTo(ClassificationOrigin.IA);
    assertThat(ticket.getClassificationJustification()).isEqualTo("Justificativa da IA.");
    verify(ticketEventService).record(
            eq(ticket), isNull(), eq(TicketEventType.CLASSIFICACAO_ATUALIZADA), anyString()
    );
}

@Test
void applyClassificationNaoDeveExigirUsuarioAutenticado() {
    Ticket ticket = newTicket(TicketPriority.MEDIA);
    when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
    when(accessControlService.findAuthenticatedUserId()).thenReturn(Optional.empty());

    ticketService.applyClassification(
            ticket.getId(), TicketCategory.RH, TicketPriority.BAIXA, ClassificationOrigin.IA, null
    );

    verify(accessControlService, never()).assertAdmin();
}

@Test
void applyClassificationDevePublicarAlertaQuandoPrioridadeViraAlta() {
    Ticket ticket = newTicket(TicketPriority.BAIXA);
    when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
    when(accessControlService.findAuthenticatedUserId()).thenReturn(Optional.empty());

    ticketService.applyClassification(
            ticket.getId(), TicketCategory.ACESSO, TicketPriority.ALTA, ClassificationOrigin.IA, null
    );

    verify(applicationEventPublisher, times(2)).publishEvent(notificationCaptor.capture());
    List<String> nomes = notificationCaptor.getAllValues().stream()
            .map(NotificationMessage::eventName)
            .toList();
    assertThat(nomes).containsExactlyInAnyOrder(
            NotificationEventName.CHAMADO_ATUALIZADO,
            NotificationEventName.CHAMADO_ALTA_PRIORIDADE
    );
}

@Test
void applyClassificationNaoDeveRepetirAlertaQuandoPrioridadeJaEraAlta() {
    Ticket ticket = newTicket(TicketPriority.ALTA);
    when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
    when(accessControlService.findAuthenticatedUserId()).thenReturn(Optional.empty());

    ticketService.applyClassification(
            ticket.getId(), TicketCategory.ACESSO, TicketPriority.ALTA, ClassificationOrigin.IA, null
    );

    verify(applicationEventPublisher, times(1)).publishEvent(any(NotificationMessage.class));
}

@Test
void applyClassificationDeveLancarNotFoundQuandoChamadoNaoExistir() {
    UUID ticketId = UUID.randomUUID();
    when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> ticketService.applyClassification(
            ticketId, TicketCategory.RH, TicketPriority.BAIXA, ClassificationOrigin.IA, null
    )).isInstanceOf(NotFoundException.class);
}
```

Acrescentar ao setup da classe o mock `@Mock ApplicationEventPublisher applicationEventPublisher;`, o captor `@Captor ArgumentCaptor<NotificationMessage> notificationCaptor;`, e o helper:

```java
private Ticket newTicket(TicketPriority priority) {
    User requester = newUser("solicitante@fadex.org.br", Role.SOLICITANTE);
    Ticket ticket = new Ticket(
            "Chamado", "Descricao", TicketCategory.OUTROS, priority,
            ClassificationOrigin.PENDENTE, requester
    );
    ReflectionTestUtils.setField(ticket, "id", UUID.randomUUID());
    return ticket;
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `make backend-test`
Expected: FAIL na compilacao — `applyClassification` e `findAuthenticatedUserId` nao existem.

- [ ] **Step 3: Acrescentar `findAuthenticatedUserId` no `AccessControlService`**

```java
	public Optional<UUID> findAuthenticatedUserId() {
		try {
			return Optional.of(getAuthenticatedUserId());
		} catch (UnauthorizedException exception) {
			return Optional.empty();
		}
	}
```

Necessario porque `applyClassification` roda tambem no worker Quartz, sem `SecurityContext`, e `getUserId()` lanca `UnauthorizedException` nesse caso.

- [ ] **Step 4: Acrescentar o mutator parametrizado na entidade**

Em `Ticket.java`, acrescentar o metodo novo e fazer os dois existentes delegarem a ele:

```java
	public void applyClassification(
			TicketCategory category,
			TicketPriority priority,
			ClassificationOrigin classificationOrigin,
			String classificationJustification
	) {
		this.category = category;
		this.priority = priority;
		this.classificationOrigin = classificationOrigin;
		this.classificationJustification = classificationJustification;
	}

	public void applyAutomaticClassification(
			TicketCategory category,
			TicketPriority priority,
			String classificationJustification
	) {
		applyClassification(category, priority, ClassificationOrigin.IA, classificationJustification);
	}

	public void applyManualClassification(
			TicketCategory category,
			TicketPriority priority,
			String classificationJustification
	) {
		applyClassification(category, priority, ClassificationOrigin.MANUAL, classificationJustification);
	}
```

Os dois metodos antigos permanecem: `AiJobWorker` os chama hoje e `ai/**` esta fora do escopo desta frente.

- [ ] **Step 5: Implementar o seam no `TicketService`**

Injetar `ApplicationEventPublisher applicationEventPublisher` no construtor e acrescentar:

```java
	@Transactional
	public void applyClassification(
			UUID ticketId,
			TicketCategory category,
			TicketPriority priority,
			ClassificationOrigin origin,
			String justification
	) {
		Ticket ticket = findEntityById(ticketId);
		TicketPriority previousPriority = ticket.getPriority();

		ticket.applyClassification(category, priority, origin, justification);
		Ticket savedTicket = ticketRepository.save(ticket);

		User actor = resolveActor();
		String description = "Classificacao atualizada para " + category.getLabel()
				+ " / " + priority.getLabel() + " (" + origin.getLabel() + ").";
		ticketEventService.record(savedTicket, actor, TicketEventType.CLASSIFICACAO_ATUALIZADA, description);

		publishTicketUpdated(savedTicket);
		publishHighPriorityAlertIfNeeded(savedTicket, previousPriority);
	}

	private User resolveActor() {
		Optional<UUID> authenticatedUserId = accessControlService.findAuthenticatedUserId();

		return authenticatedUserId.map(userService::findEntityById).orElse(null);
	}
```

Sem `assertAdmin()`: o metodo roda no worker Quartz, sem usuario autenticado. A autorizacao do endpoint de revisao ADMIN e responsabilidade da frente IA, na camada dela.

E os dois publicadores privados:

```java
	private void publishTicketUpdated(Ticket ticket) {
		Set<UUID> userIds = new HashSet<>();
		userIds.add(ticket.getRequester().getId());

		User assignee = ticket.getAssignee();

		if (assignee != null) {
			userIds.add(assignee.getId());
		}

		NotificationMessage message = NotificationMessage.of(
				NotificationEventName.CHAMADO_ATUALIZADO,
				TicketMapper.toMinDto(ticket),
				new NotificationAudience.Users(userIds)
		);

		applicationEventPublisher.publishEvent(message);
	}

	private void publishHighPriorityAlertIfNeeded(Ticket ticket, TicketPriority previousPriority) {
		boolean becameHigh = ticket.getPriority() == TicketPriority.ALTA
				&& previousPriority != TicketPriority.ALTA;

		if (!becameHigh) {
			return;
		}

		NotificationMessage message = NotificationMessage.of(
				NotificationEventName.CHAMADO_ALTA_PRIORIDADE,
				TicketMapper.toMinDto(ticket),
				new NotificationAudience.Roles(Set.of(Role.ADMIN))
		);

		applicationEventPublisher.publishEvent(message);
	}
```

`HashSet` e nao `Set.of(...)`: o responsavel e anulavel e `Set.of(null)` lanca `NullPointerException`. O alerta so dispara na **transicao** para `ALTA` — repetir o alerta a cada reclassificacao de um chamado ja `ALTA` treinaria o ADMIN a ignora-lo.

Publicar via `ApplicationEventPublisher`, nunca `NotificationService` direto: a barreira `AFTER_COMMIT` e do `NotificationDispatcher`.

- [ ] **Step 6: Rodar e ver passar**

Run: `make backend-test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/br/org/fadex/helpdesk/security/AccessControlService.java \
        backend/src/main/java/br/org/fadex/helpdesk/model/ticket/Ticket.java \
        backend/src/main/java/br/org/fadex/helpdesk/service/TicketService.java \
        backend/src/test/java/br/org/fadex/helpdesk/service/TicketServiceTest.java
git commit -m "feat(backend): adiciona seam de classificacao de chamado para a frente de ia"
```

---

### Task 4: Delta do contrato da API

**Files:**
- Modify: `docs/backend/api.md`

**Interfaces:**
- Consumes: nada.
- Produces: contrato escrito contra o qual a frente Frontend trabalha.

Este delta sai **antes** da implementacao do passo 2, nao depois: a frente Frontend usa dados fixos no lugar da chamada real enquanto o endpoint nao existe, e precisa do formato exato.

- [ ] **Step 1: Acrescentar os campos novos nas respostas de chamado**

Nos exemplos de `GET /api/v1/tickets/{id}` e `POST /api/v1/tickets`, acrescentar ao JSON:

```json
  "assignedAt": null,
  "firstResponseAt": null,
  "resolvedAt": null,
  "closedAt": null,
```

- [ ] **Step 2: Documentar os tres endpoints novos**

Acrescentar a secao "Chamados", depois de `GET /api/v1/tickets/{id}`, o texto da secao "Ciclo de Vida do Chamado": matriz de transicoes, regras de carimbo, `PATCH /status`, `PATCH /assignee`, `DELETE /assignee`, com request, response e tabela de erros — conforme as secoes "Maquina de Estados de Status", "Carimbos de Tempo", "Atribuicao de Responsavel" e "Erros" do design.

- [ ] **Step 3: Documentar os eventos SSE na secao "Notificacoes"**

Sem isto o passo 1 **nao desbloqueia o Frontend**: a secao "Notificacoes" do `api.md` hoje so
descreve `CONEXAO_ESTABELECIDA`, e o Frontend teria de ler o Java para descobrir o payload.

Acrescentar, depois do exemplo de `CONEXAO_ESTABELECIDA`:

```markdown
Eventos de dominio:

| Evento | Audiencia | `data` |
| --- | --- | --- |
| `CHAMADO_ATUALIZADO` | solicitante e responsavel do chamado | `TicketMinDto` |
| `CHAMADO_ALTA_PRIORIDADE` | todos os ADMIN | `TicketMinDto` |
| `CLASSIFICACAO_CONCLUIDA` | solicitante do chamado e todos os ADMIN | definido pela frente IA |
| `JOB_IA_FALHOU` | todos os ADMIN | definido pela frente IA |
| `INDICADORES_ATUALIZADOS` | todos os ADMIN | definido pela frente IA |

Exemplo de frame de `CHAMADO_ATUALIZADO`:

    event: CHAMADO_ATUALIZADO
    id: 9d2f1a44-3c5b-4e8a-9f10-2b7c6d5e4f31
    data: {"id":"00000000-0000-0000-0000-000000000000","title":"Erro ao acessar sistema","category":"SISTEMAS","priority":"ALTA","status":"EM_ANDAMENTO","classificationOrigin":"IA","requester":{"id":"...","name":"Solicitante"},"assignee":{"id":"...","name":"Administrador"},"assignedAt":"2026-08-14T10:00:00","createdAt":"2026-08-13T20:00:00"}

Nao ha reenvio: ao reconectar, recarregar a lista por `GET /api/v1/tickets`.
```

Os tres eventos da frente IA entram com o `data` marcado como responsabilidade dela — os nomes e
audiencias sao contrato desta frente, o payload nao.

- [ ] **Step 4: Acrescentar `assignedAt` ao `TicketMinDto` no documento**

No exemplo de `GET /api/v1/tickets`, acrescentar `"assignedAt": null` antes de `"createdAt"`.

- [ ] **Step 5: Atualizar "Pendencias Conhecidas"**

Remover "Atualizacao de status e atribuicao de responsavel" da lista de pendentes.

- [ ] **Step 6: Commit**

```bash
git add docs/backend/api.md
git commit -m "docs(backend): documenta ciclo de vida do chamado no contrato da api"
```

---

### Task 5: Carimbos no seed de desenvolvimento

**Files:**
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/config/DevTicketSeeder.java`

**Interfaces:**
- Consumes: colunas da `V4` (Task 1).
- Produces: seed com carimbos preenchidos, consumido pelos indicadores da frente IA.

Os dados ja existem no seeder: `resolvedAfterHours` e `firstReplyAfterHours` hoje so posicionam eventos de historico. Sem esta task, o dashboard da frente IA renderiza todas as metricas de tempo vazias com 20 chamados no banco.

- [ ] **Step 1: Calcular os instantes no `insertTicket`**

Depois do calculo de `resolvedAt` ja existente:

```java
		LocalDateTime assignedAt = assigneeId == null ? null : createdAt.plusHours(1);
		LocalDateTime firstResponseAt = (seed.firstReplyAfterHours() == null || assigneeId == null)
				? null
				: createdAt.plusHours(seed.firstReplyAfterHours());
		LocalDateTime closedAt = seed.status() == TicketStatus.FECHADO ? resolvedAt : null;
```

`assignedAt` usa `createdAt.plusHours(1)`, o mesmo instante ja usado no evento `RESPONSAVEL_ATRIBUIDO` logo abaixo — os dois precisam contar a mesma historia.

- [ ] **Step 2: Incluir as colunas no insert**

Trocar o `insert into tickets` por:

```java
		jdbcTemplate.update(
				"""
				insert into tickets (
					id, title, description, category, priority, status, requester_id, assignee_id,
					classification_origin, classification_justification, created_at, updated_at,
					assigned_at, first_response_at, resolved_at, closed_at
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				ticketId,
				seed.title(),
				seed.description(),
				seed.category().name(),
				seed.priority().name(),
				seed.status().name(),
				requesterId,
				assigneeId,
				seed.classificationOrigin().name(),
				seed.justification(),
				Timestamp.valueOf(createdAt),
				Timestamp.valueOf(updatedAt),
				assignedAt == null ? null : Timestamp.valueOf(assignedAt),
				firstResponseAt == null ? null : Timestamp.valueOf(firstResponseAt),
				resolvedAt == null ? null : Timestamp.valueOf(resolvedAt),
				closedAt == null ? null : Timestamp.valueOf(closedAt)
		);
```

- [ ] **Step 3: Rodar os testes**

Run: `make backend-test`
Expected: BUILD SUCCESSFUL. O seed nao roda na suite (`app.seed.enabled=false` em `application-test.properties`), entao a verificacao real e subir o backend com banco limpo e conferir que `select count(*) from tickets where closed_at is not null` devolve 4.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/br/org/fadex/helpdesk/config/DevTicketSeeder.java
git commit -m "feat(backend): preenche carimbos de ciclo de vida no seed de chamados"
```

**Fim do passo 1. As frentes IA e Frontend estao desbloqueadas a partir daqui.**

---

## Passo 2 — Ciclo de Vida

### Task 6: Matriz de transicoes de status

**Files:**
- Create: `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketStatusTransition.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/model/ticket/TicketStatusTransitionTest.java`

**Interfaces:**
- Consumes: `TicketStatus`.
- Produces: `TicketStatusTransition.isAllowed(TicketStatus from, TicketStatus to): boolean` e `TicketStatusTransition.allowedFrom(TicketStatus from): Set<TicketStatus>`.

- [ ] **Step 1: Escrever o teste**

```java
package br.org.fadex.helpdesk.model.ticket;

import br.org.fadex.helpdesk.model.enums.TicketStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TicketStatusTransitionTest {

	@Test
	void deveAceitarAsTransicoesValidas() {
		assertThat(TicketStatusTransition.isAllowed(TicketStatus.ABERTO, TicketStatus.EM_ANDAMENTO)).isTrue();
		assertThat(TicketStatusTransition.isAllowed(TicketStatus.ABERTO, TicketStatus.RESOLVIDO)).isTrue();
		assertThat(TicketStatusTransition.isAllowed(TicketStatus.ABERTO, TicketStatus.FECHADO)).isTrue();
		assertThat(TicketStatusTransition.isAllowed(TicketStatus.EM_ANDAMENTO, TicketStatus.ABERTO)).isTrue();
		assertThat(TicketStatusTransition.isAllowed(TicketStatus.EM_ANDAMENTO, TicketStatus.RESOLVIDO)).isTrue();
		assertThat(TicketStatusTransition.isAllowed(TicketStatus.EM_ANDAMENTO, TicketStatus.FECHADO)).isTrue();
		assertThat(TicketStatusTransition.isAllowed(TicketStatus.RESOLVIDO, TicketStatus.EM_ANDAMENTO)).isTrue();
		assertThat(TicketStatusTransition.isAllowed(TicketStatus.RESOLVIDO, TicketStatus.FECHADO)).isTrue();
	}

	@Test
	void naoDevePermitirSairDeFechado() {
		assertThat(TicketStatusTransition.isAllowed(TicketStatus.FECHADO, TicketStatus.ABERTO)).isFalse();
		assertThat(TicketStatusTransition.isAllowed(TicketStatus.FECHADO, TicketStatus.EM_ANDAMENTO)).isFalse();
		assertThat(TicketStatusTransition.isAllowed(TicketStatus.FECHADO, TicketStatus.RESOLVIDO)).isFalse();
		assertThat(TicketStatusTransition.allowedFrom(TicketStatus.FECHADO)).isEmpty();
	}

	@Test
	void naoDevePermitirReabrirChamadoResolvidoParaAberto() {
		assertThat(TicketStatusTransition.isAllowed(TicketStatus.RESOLVIDO, TicketStatus.ABERTO)).isFalse();
	}

	@Test
	void naoDevePermitirTransicaoParaOMesmoStatus() {
		for (TicketStatus status : TicketStatus.values()) {
			assertThat(TicketStatusTransition.isAllowed(status, status)).isFalse();
		}
	}
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `make backend-test`
Expected: FAIL — `TicketStatusTransition` nao existe.

- [ ] **Step 3: Implementar**

```java
package br.org.fadex.helpdesk.model.ticket;

import br.org.fadex.helpdesk.model.enums.TicketStatus;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Matriz de transicoes de status do chamado.
 *
 * Fica em estrutura de dados consultavel, e nao espalhada em ifs no service, porque a frente IA
 * precisa da matriz para calculo de aging e a frente Frontend para habilitar botoes.
 */
public final class TicketStatusTransition {

	private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED = buildAllowed();

	private TicketStatusTransition() {
	}

	public static boolean isAllowed(TicketStatus from, TicketStatus to) {
		return allowedFrom(from).contains(to);
	}

	public static Set<TicketStatus> allowedFrom(TicketStatus from) {
		return ALLOWED.getOrDefault(from, Set.of());
	}

	private static Map<TicketStatus, Set<TicketStatus>> buildAllowed() {
		Map<TicketStatus, Set<TicketStatus>> allowed = new EnumMap<>(TicketStatus.class);

		allowed.put(TicketStatus.ABERTO, Set.of(
				TicketStatus.EM_ANDAMENTO, TicketStatus.RESOLVIDO, TicketStatus.FECHADO
		));
		allowed.put(TicketStatus.EM_ANDAMENTO, Set.of(
				TicketStatus.ABERTO, TicketStatus.RESOLVIDO, TicketStatus.FECHADO
		));
		allowed.put(TicketStatus.RESOLVIDO, Set.of(
				TicketStatus.EM_ANDAMENTO, TicketStatus.FECHADO
		));
		allowed.put(TicketStatus.FECHADO, Set.of());

		return Map.copyOf(allowed);
	}
}
```

`FECHADO` mapeia para conjunto vazio, tornando o estado terminal um dado e nao um `if` especial.

- [ ] **Step 4: Rodar e ver passar**

Run: `make backend-test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketStatusTransition.java \
        backend/src/test/java/br/org/fadex/helpdesk/model/ticket/TicketStatusTransitionTest.java
git commit -m "feat(backend): define matriz de transicoes de status do chamado"
```

---

### Task 7: PATCH de status

**Files:**
- Create: `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketStatusUpdateDto.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/service/TicketService.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/controller/TicketController.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketDto.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketMapper.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/service/TicketServiceTest.java`

**Interfaces:**
- Consumes: `TicketStatusTransition` (Task 6), mutators de `Ticket` (Task 1), publicadores privados (Task 3).
- Produces: `TicketService.updateStatus(UUID, TicketStatusUpdateDto): TicketDto`; `TicketDto` com `assignedAt`, `firstResponseAt`, `resolvedAt`, `closedAt`.

- [ ] **Step 1: Escrever os testes**

```java
@Test
void updateStatusDeveExigirAdmin() {
    Ticket ticket = newTicket(TicketPriority.MEDIA);
    doThrow(new ForbiddenException("Acesso negado ao recurso solicitado."))
            .when(accessControlService).assertAdmin();

    assertThatThrownBy(() -> ticketService.updateStatus(
            ticket.getId(), new TicketStatusUpdateDto(TicketStatus.EM_ANDAMENTO)
    )).isInstanceOf(ForbiddenException.class);
}

@Test
void updateStatusDeveAlterarStatusERegistrarEvento() {
    Ticket ticket = newTicket(TicketPriority.MEDIA);
    when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
    when(ticketRepository.save(ticket)).thenReturn(ticket);
    when(accessControlService.findAuthenticatedUserId()).thenReturn(Optional.of(adminId));
    when(userService.findEntityById(adminId)).thenReturn(admin);

    ticketService.updateStatus(ticket.getId(), new TicketStatusUpdateDto(TicketStatus.EM_ANDAMENTO));

    assertThat(ticket.getStatus()).isEqualTo(TicketStatus.EM_ANDAMENTO);
    verify(ticketEventService).record(
            eq(ticket), eq(admin), eq(TicketEventType.STATUS_ALTERADO), anyString()
    );
}

@Test
void updateStatusDeveCarimbarResolvedAt() {
    Ticket ticket = newTicket(TicketPriority.MEDIA);
    stubUpdate(ticket);

    ticketService.updateStatus(ticket.getId(), new TicketStatusUpdateDto(TicketStatus.RESOLVIDO));

    assertThat(ticket.getResolvedAt()).isNotNull();
    assertThat(ticket.getClosedAt()).isNull();
}

@Test
void updateStatusDeveCarimbarClosedAtEResolvedAtAoFecharSemResolverAntes() {
    Ticket ticket = newTicket(TicketPriority.MEDIA);
    stubUpdate(ticket);

    ticketService.updateStatus(ticket.getId(), new TicketStatusUpdateDto(TicketStatus.FECHADO));

    assertThat(ticket.getClosedAt()).isNotNull();
    assertThat(ticket.getResolvedAt()).isEqualTo(ticket.getClosedAt());
}

@Test
void updateStatusDeveSobrescreverResolvedAtNaSegundaResolucao() {
    Ticket ticket = newTicket(TicketPriority.MEDIA);
    ticket.changeStatus(TicketStatus.RESOLVIDO);
    ticket.markResolved(LocalDateTime.of(2026, 1, 1, 0, 0));
    stubUpdate(ticket);

    ticketService.updateStatus(ticket.getId(), new TicketStatusUpdateDto(TicketStatus.EM_ANDAMENTO));
    ticketService.updateStatus(ticket.getId(), new TicketStatusUpdateDto(TicketStatus.RESOLVIDO));

    assertThat(ticket.getResolvedAt()).isAfter(LocalDateTime.of(2026, 1, 1, 0, 0));
}

@Test
void updateStatusDeveRecusarChamadoFechado() {
    Ticket ticket = newTicket(TicketPriority.MEDIA);
    ticket.changeStatus(TicketStatus.FECHADO);
    when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

    assertThatThrownBy(() -> ticketService.updateStatus(
            ticket.getId(), new TicketStatusUpdateDto(TicketStatus.EM_ANDAMENTO)
    )).isInstanceOf(ConflictException.class);
}

@Test
void updateStatusDeveRecusarTransicaoParaOMesmoStatus() {
    Ticket ticket = newTicket(TicketPriority.MEDIA);
    when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

    assertThatThrownBy(() -> ticketService.updateStatus(
            ticket.getId(), new TicketStatusUpdateDto(TicketStatus.ABERTO)
    )).isInstanceOf(ConflictException.class);
}
```

Com o helper:

```java
private void stubUpdate(Ticket ticket) {
    when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
    when(ticketRepository.save(ticket)).thenReturn(ticket);
    when(accessControlService.findAuthenticatedUserId()).thenReturn(Optional.of(adminId));
    when(userService.findEntityById(adminId)).thenReturn(admin);
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `make backend-test`
Expected: FAIL na compilacao — `TicketStatusUpdateDto` e `updateStatus` nao existem.

- [ ] **Step 3: Criar o DTO**

```java
package br.org.fadex.helpdesk.model.ticket;

import br.org.fadex.helpdesk.model.enums.TicketStatus;
import jakarta.validation.constraints.NotNull;

public record TicketStatusUpdateDto(
		@NotNull TicketStatus status
) {
}
```

- [ ] **Step 4: Implementar `updateStatus`**

```java
	@Transactional
	public TicketDto updateStatus(UUID id, TicketStatusUpdateDto ticketStatusUpdateDto) {
		accessControlService.assertAdmin();

		Ticket ticket = findEntityById(id);
		TicketStatus currentStatus = ticket.getStatus();
		TicketStatus newStatus = ticketStatusUpdateDto.status();

		assertTransitionAllowed(currentStatus, newStatus);

		LocalDateTime now = LocalDateTime.now();

		ticket.changeStatus(newStatus);

		if (newStatus == TicketStatus.RESOLVIDO) {
			ticket.markResolved(now);
		}

		if (newStatus == TicketStatus.FECHADO) {
			ticket.markClosed(now);

			if (ticket.getResolvedAt() == null) {
				ticket.markResolved(now);
			}
		}

		Ticket savedTicket = ticketRepository.save(ticket);
		User actor = resolveActor();
		String description = "Status alterado de " + currentStatus.getLabel()
				+ " para " + newStatus.getLabel() + ".";
		ticketEventService.record(savedTicket, actor, TicketEventType.STATUS_ALTERADO, description);
		publishTicketUpdated(savedTicket);

		TicketDto response = TicketMapper.toResponseDto(savedTicket);

		return response;
	}

	private void assertTransitionAllowed(TicketStatus currentStatus, TicketStatus newStatus) {
		if (currentStatus == TicketStatus.FECHADO) {
			throw new ConflictException("Chamado fechado nao pode ser reaberto.");
		}

		if (currentStatus == newStatus) {
			throw new ConflictException("O chamado ja esta com o status " + newStatus.getLabel() + ".");
		}

		if (!TicketStatusTransition.isAllowed(currentStatus, newStatus)) {
			throw new ConflictException(
					"Transicao de " + currentStatus.getLabel() + " para " + newStatus.getLabel()
							+ " nao e permitida."
			);
		}
	}
```

O caso `FECHADO` tem mensagem propria antes da checagem geral: e a regra de negocio cobrada pelo desafio e merece um erro que o usuario entenda.

Fechar sem resolver antes tambem carimba `resolved_at`. Sem isso, chamado fechado direto sairia da metrica de tempo de resolucao e enviesaria a media para baixo.

- [ ] **Step 5: Expor os carimbos no DTO**

Acrescentar a `TicketDto`, depois de `assignee`:

```java
		LocalDateTime assignedAt,
		LocalDateTime firstResponseAt,
		LocalDateTime resolvedAt,
		LocalDateTime closedAt,
```

E no `TicketMapper.toResponseDto`, na mesma posicao:

```java
				ticket.getAssignedAt(),
				ticket.getFirstResponseAt(),
				ticket.getResolvedAt(),
				ticket.getClosedAt(),
```

Acrescentar tambem `LocalDateTime assignedAt` a `TicketMinDto`, antes de `createdAt`, e
`ticket.getAssignedAt()` na posicao equivalente de `TicketMapper.toMinDto`. `TicketMinDto` e o
payload dos eventos SSE; sem nenhum carimbo, um dashboard que se atualiza pelo evento nao
recalcula metrica de tempo nenhuma. Os outros tres carimbos ficam so no `TicketDto` — sao do
detalhe e inflariam todo item de listagem.

- [ ] **Step 6: Acrescentar o endpoint no controller**

```java
	@PatchMapping("/{id}/status")
	public ResponseEntity<TicketDto> updateStatus(
			@PathVariable UUID id,
			@Valid @RequestBody TicketStatusUpdateDto ticketStatusUpdateDto
	) {
		TicketDto ticket = ticketService.updateStatus(id, ticketStatusUpdateDto);

		return ResponseEntity.ok(ticket);
	}
```

- [ ] **Step 7: Rodar e ver passar**

Run: `make backend-test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/br/org/fadex/helpdesk/model/ticket/ \
        backend/src/main/java/br/org/fadex/helpdesk/service/TicketService.java \
        backend/src/main/java/br/org/fadex/helpdesk/controller/TicketController.java \
        backend/src/test/java/br/org/fadex/helpdesk/service/TicketServiceTest.java
git commit -m "feat(backend): adiciona atualizacao de status do chamado"
```

---

### Task 8: Atribuicao e recusa de responsavel

**Files:**
- Create: `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketAssigneeUpdateDto.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/service/TicketService.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/controller/TicketController.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/service/TicketServiceTest.java`

**Interfaces:**
- Consumes: `Ticket.assignTo`, `Ticket.unassign`, `Ticket.markAssigned` (Task 1).
- Produces: `TicketService.updateAssignee(UUID, TicketAssigneeUpdateDto): TicketDto` e `TicketService.removeAssignee(UUID): TicketDto`.

- [ ] **Step 1: Escrever os testes**

```java
@Test
void updateAssigneeDeveExigirAdmin() {
    Ticket ticket = newTicket(TicketPriority.MEDIA);
    doThrow(new ForbiddenException("Acesso negado ao recurso solicitado."))
            .when(accessControlService).assertAdmin();

    assertThatThrownBy(() -> ticketService.updateAssignee(
            ticket.getId(), new TicketAssigneeUpdateDto(adminId)
    )).isInstanceOf(ForbiddenException.class);
}

@Test
void updateAssigneeDeveAtribuirECarimbarAssignedAt() {
    Ticket ticket = newTicket(TicketPriority.MEDIA);
    stubUpdate(ticket);

    ticketService.updateAssignee(ticket.getId(), new TicketAssigneeUpdateDto(adminId));

    assertThat(ticket.getAssignee()).isEqualTo(admin);
    assertThat(ticket.getAssignedAt()).isNotNull();
    verify(ticketEventService).record(
            eq(ticket), eq(admin), eq(TicketEventType.RESPONSAVEL_ATRIBUIDO), anyString()
    );
}

@Test
void updateAssigneeNaoDeveSobrescreverAssignedAtNaReatribuicao() {
    Ticket ticket = newTicket(TicketPriority.MEDIA);
    LocalDateTime original = LocalDateTime.of(2026, 1, 1, 0, 0);
    ticket.markAssigned(original);
    stubUpdate(ticket);

    ticketService.updateAssignee(ticket.getId(), new TicketAssigneeUpdateDto(adminId));

    assertThat(ticket.getAssignedAt()).isEqualTo(original);
}

@Test
void updateAssigneeDeveRecusarResponsavelSemPapelAdmin() {
    Ticket ticket = newTicket(TicketPriority.MEDIA);
    User solicitante = newUser("outro@fadex.org.br", Role.SOLICITANTE);
    UUID solicitanteId = UUID.randomUUID();
    ReflectionTestUtils.setField(solicitante, "id", solicitanteId);
    when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
    when(userService.findEntityById(solicitanteId)).thenReturn(solicitante);

    assertThatThrownBy(() -> ticketService.updateAssignee(
            ticket.getId(), new TicketAssigneeUpdateDto(solicitanteId)
    )).isInstanceOf(ConflictException.class);
}

@Test
void updateAssigneeDeveRecusarChamadoFechado() {
    Ticket ticket = newTicket(TicketPriority.MEDIA);
    ticket.changeStatus(TicketStatus.FECHADO);
    when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

    assertThatThrownBy(() -> ticketService.updateAssignee(
            ticket.getId(), new TicketAssigneeUpdateDto(adminId)
    )).isInstanceOf(ConflictException.class);
}

@Test
void removeAssigneeDeveRemoverResponsavelEPreservarAssignedAt() {
    Ticket ticket = newTicket(TicketPriority.MEDIA);
    ticket.assignTo(admin);
    LocalDateTime original = LocalDateTime.of(2026, 1, 1, 0, 0);
    ticket.markAssigned(original);
    stubUpdate(ticket);

    ticketService.removeAssignee(ticket.getId());

    assertThat(ticket.getAssignee()).isNull();
    assertThat(ticket.getAssignedAt()).isEqualTo(original);
    verify(ticketEventService).record(
            eq(ticket), eq(admin), eq(TicketEventType.RESPONSAVEL_ATRIBUIDO), anyString()
    );
}

@Test
void removeAssigneeDeveRecusarChamadoSemResponsavel() {
    Ticket ticket = newTicket(TicketPriority.MEDIA);
    when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

    assertThatThrownBy(() -> ticketService.removeAssignee(ticket.getId()))
            .isInstanceOf(ConflictException.class);
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `make backend-test`
Expected: FAIL na compilacao.

- [ ] **Step 3: Criar o DTO**

```java
package br.org.fadex.helpdesk.model.ticket;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TicketAssigneeUpdateDto(
		@NotNull UUID assigneeId
) {
}
```

- [ ] **Step 4: Implementar os dois metodos**

```java
	@Transactional
	public TicketDto updateAssignee(UUID id, TicketAssigneeUpdateDto ticketAssigneeUpdateDto) {
		accessControlService.assertAdmin();

		Ticket ticket = findEntityById(id);
		assertTicketIsOpen(ticket);

		User assignee = userService.findEntityById(ticketAssigneeUpdateDto.assigneeId());

		if (assignee.getRole() != Role.ADMIN) {
			throw new ConflictException("O responsavel pelo chamado precisa ter papel de administrador.");
		}

		ticket.assignTo(assignee);

		if (ticket.getAssignedAt() == null) {
			ticket.markAssigned(LocalDateTime.now());
		}

		Ticket savedTicket = ticketRepository.save(ticket);
		String description = "Responsavel atribuido: " + assignee.getName() + ".";
		ticketEventService.record(
				savedTicket, assignee, TicketEventType.RESPONSAVEL_ATRIBUIDO, description
		);
		publishTicketUpdated(savedTicket);

		TicketDto response = TicketMapper.toResponseDto(savedTicket);

		return response;
	}

	@Transactional
	public TicketDto removeAssignee(UUID id) {
		accessControlService.assertAdmin();

		Ticket ticket = findEntityById(id);
		assertTicketIsOpen(ticket);

		User previousAssignee = ticket.getAssignee();

		if (previousAssignee == null) {
			throw new ConflictException("O chamado nao possui responsavel atribuido.");
		}

		ticket.unassign();

		Ticket savedTicket = ticketRepository.save(ticket);
		String description = "Atribuicao removida de " + previousAssignee.getName() + ".";
		ticketEventService.record(
				savedTicket, previousAssignee, TicketEventType.RESPONSAVEL_ATRIBUIDO, description
		);
		publishTicketUpdated(savedTicket);

		TicketDto response = TicketMapper.toResponseDto(savedTicket);

		return response;
	}

	private void assertTicketIsOpen(Ticket ticket) {
		if (ticket.getStatus() == TicketStatus.FECHADO) {
			throw new ConflictException("Chamado fechado nao pode ser alterado.");
		}
	}
```

`assigned_at` so e escrito na primeira atribuicao e nunca limpo no `removeAssignee`: a metrica e "tempo ate a primeira atribuicao", que mede a velocidade da triagem. Limpar apagaria o fato de que a triagem aconteceu.

Nenhuma excecao nova: `ConflictException` ja existe. Nao criar `ValidationException` reutilizando o codigo `VALIDATION_ERROR` — ele ja pertence a bean validation e o `api.md` o documenta carregando um array `fields`.

Os dois eventos usam `TicketEventType.RESPONSAVEL_ATRIBUIDO`, inclusive o de remocao. `TicketEventType` fica em `model/enums`, fora da posse desta frente. **Efeito colateral conhecido:** o Frontend renderiza `getLabel()`, entao a linha de remocao no historico aparece rotulada como "Responsavel atribuido", com a descricao correta apenas no texto do evento.

- [ ] **Step 5: Acrescentar os endpoints no controller**

```java
	@PatchMapping("/{id}/assignee")
	public ResponseEntity<TicketDto> updateAssignee(
			@PathVariable UUID id,
			@Valid @RequestBody TicketAssigneeUpdateDto ticketAssigneeUpdateDto
	) {
		TicketDto ticket = ticketService.updateAssignee(id, ticketAssigneeUpdateDto);

		return ResponseEntity.ok(ticket);
	}

	@DeleteMapping("/{id}/assignee")
	public ResponseEntity<TicketDto> removeAssignee(@PathVariable UUID id) {
		TicketDto ticket = ticketService.removeAssignee(id);

		return ResponseEntity.ok(ticket);
	}
```

- [ ] **Step 6: Rodar e ver passar**

Run: `make backend-test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/br/org/fadex/helpdesk/ \
        backend/src/test/java/br/org/fadex/helpdesk/service/TicketServiceTest.java
git commit -m "feat(backend): adiciona atribuicao e recusa de responsavel no chamado"
```

---

### Task 9: first_response_at no primeiro comentario de ADMIN

**Files:**
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/service/TicketCommentService.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/service/TicketCommentServiceTest.java`

**Interfaces:**
- Consumes: `Ticket.markFirstResponse` e `Ticket.getFirstResponseAt` (Task 1).
- Produces: nada consumido por outras tasks.

**Atencao:** `POST /tickets/{id}/comments` **nao** recebe `assertAdmin()`. O `SOLICITANTE` precisa continuar comentando nos proprios chamados; a regra "toda mutacao nova nasce com assertAdmin()" vale para os endpoints novos desta frente, nao para o de comentario, que ja existia.

- [ ] **Step 1: Escrever os testes**

```java
@Test
void createDevePreencherPrimeiraRespostaQuandoAutorForAdmin() {
    Ticket ticket = newTicket();
    User admin = newUser("admin@fadex.org.br", Role.ADMIN);
    stubCreate(ticket, admin);

    ticketCommentService.create(ticket.getId(), new TicketCommentCreationDto("Estamos analisando."));

    assertThat(ticket.getFirstResponseAt()).isNotNull();
}

@Test
void createNaoDevePreencherPrimeiraRespostaQuandoAutorForSolicitante() {
    Ticket ticket = newTicket();
    User solicitante = newUser("solicitante@fadex.org.br", Role.SOLICITANTE);
    stubCreate(ticket, solicitante);

    ticketCommentService.create(ticket.getId(), new TicketCommentCreationDto("Alguma novidade?"));

    assertThat(ticket.getFirstResponseAt()).isNull();
}

@Test
void createNaoDeveSobrescreverPrimeiraResposta() {
    Ticket ticket = newTicket();
    LocalDateTime original = LocalDateTime.of(2026, 1, 1, 0, 0);
    ticket.markFirstResponse(original);
    User admin = newUser("admin@fadex.org.br", Role.ADMIN);
    stubCreate(ticket, admin);

    ticketCommentService.create(ticket.getId(), new TicketCommentCreationDto("Mais uma atualizacao."));

    assertThat(ticket.getFirstResponseAt()).isEqualTo(original);
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `make backend-test`
Expected: FAIL — `getFirstResponseAt()` devolve `null` nos dois primeiros testes.

- [ ] **Step 3: Implementar**

Em `TicketCommentService.create`, depois de `ticketCommentRepository.save(...)` e antes do `record` do evento:

```java
		boolean isFirstAdminResponse = author.getRole() == Role.ADMIN
				&& ticket.getFirstResponseAt() == null;

		if (isFirstAdminResponse) {
			ticket.markFirstResponse(LocalDateTime.now());
		}
```

`ticket` e entidade gerenciada dentro da transacao; o dirty checking persiste a mudanca sem `save` explicito. Comentario do proprio solicitante nao conta como resposta do atendimento.

- [ ] **Step 4: Rodar e ver passar**

Run: `make backend-test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/br/org/fadex/helpdesk/service/TicketCommentService.java \
        backend/src/test/java/br/org/fadex/helpdesk/service/TicketCommentServiceTest.java
git commit -m "feat(backend): carimba primeira resposta do admin no chamado"
```

---

### Task 10: Verificacao final

**Files:**
- Modify: `docs/backend/api.md` (ajustes finais se o contrato divergiu da implementacao)
- Modify: `docs/projeto/acompanhamento-desenvolvimento.md`

- [ ] **Step 1: Conferir o contrato contra a implementacao**

Reler o delta escrito na Task 4 comparando com os DTOs e status HTTP reais. Corrigir o documento onde divergir — o documento e o contrato da frente Frontend.

- [ ] **Step 2: Rodar a suite completa**

Run: `make backend-test`
Expected: BUILD SUCCESSFUL. Conferir a saida real; nao afirmar que passou sem ver.

- [ ] **Step 3: Atualizar o acompanhamento**

Marcar como concluidos, em `docs/projeto/acompanhamento-desenvolvimento.md`: atualizacao de status, atribuicao de responsavel, regra de nao reabrir chamado fechado.

- [ ] **Step 4: Commit**

```bash
git add docs/
git commit -m "docs(backend): fecha contrato e acompanhamento do ciclo de vida do chamado"
```

---

## Self-Review Checklist

- [x] **Cobertura da spec:** migration `V4` (Task 1), `NotificationEventName` (Task 2), seam `applyClassification` (Task 3), delta do `api.md` (Task 4), seed (Task 5), matriz de transicoes (Task 6), `PATCH /status` (Task 7), `PATCH`/`DELETE /assignee` (Task 8), `first_response_at` (Task 9), verificacao (Task 10).
- [x] **Sem placeholders:** todo passo de codigo traz o codigo real.
- [x] **Consistencia de tipos:** `applyClassification` tem a mesma assinatura no design, no seam e nos testes; `markResolved`/`markClosed`/`markAssigned`/`markFirstResponse` sao definidos na Task 1 e usados nas Tasks 7, 8 e 9; `TicketStatusTransition.isAllowed` e definido na Task 6 e usado na Task 7.
- [x] **Ordem de dependencia:** o passo 1 (Tasks 1 a 5) nao depende do passo 2 e desbloqueia as outras frentes sozinho.
- [x] **`V5` nao usada.**
- [x] **`assertAdmin()`** presente nas tres mutacoes novas e deliberadamente ausente em `applyClassification` e no comentario.

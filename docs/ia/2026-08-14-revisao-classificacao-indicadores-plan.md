# Revisao de Classificacao e Indicadores Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fechar o ciclo de IA da central de chamados — revisao da classificacao pelo ADMIN, auditoria da sugestao da IA, indicadores agregados, operacao da fila de jobs e deteccao de duplicados por embedding.

**Architecture:** Tudo novo vive no boundary `br.org.fadex.helpdesk.ai`. Escrita em `Ticket.category`/`priority`/`classificationOrigin` passa exclusivamente por `TicketService.applyClassification(...)`; as tres colunas de auditoria da IA sao escritas por repository proprio com update nativo estreito, repetindo o precedente do `TicketEmbeddingRepository`. Indicadores carregam uma projecao unica e agregam em Java, o que mantem media/mediana/p90 testaveis em H2. Notificacoes saem por `ApplicationEventPublisher`, nunca por `NotificationService` direto.

**Tech Stack:** Java 21, Spring Boot 4.1.0, Spring MVC, Spring Security (`@EnableMethodSecurity`), Spring Data JPA, Flyway, Quartz, PostgreSQL 17 com pgvector, H2 em testes, JUnit 5, Mockito, Gradle.

**Spec:** `docs/ia/2026-08-14-revisao-classificacao-indicadores-design.md`

## Global Constraints

- Branch de trabalho: `feature(ia)/revisao-classificacao-indicadores`, criada a partir de `dev` (base `95da427`).
- Commits em portugues, com escopo: `feat(backend):`, `test(backend):`, `docs(ia):`. **Sem trailer de co-autoria** — nada de `Co-Authored-By:` ou `Claude-Session:`.
- **Regime de revisao vigente: durante a implementacao, NAO commitar codigo.** Os passos "Commit" das Tasks 1 a 14 ficam suspensos: o codigo permanece no working tree, sem `git add` e sem `git commit`, para o Marcos revisar o diff inteiro de uma vez. Proibido `git stash`, `git checkout .` e `git reset` sobre o codigo de implementacao — trabalho nao commitado e o material de revisao. Design e plano (Task 0) commitam normalmente. Quando o regime for suspenso, os passos de commit voltam a valer como escritos.
- `make backend-test` precisa passar antes de declarar qualquer etapa concluida. Rodar de verdade e conferir a saida — nao afirmar que passou sem evidencia.
- **Design aprovado pelo Marcos.** Implementacao liberada.
- **D7 decidido: saida A.** A `V5` cria `tickets.classification_reviewed_at`; o denominador da concordancia sao os chamados revisados com sugestao registrada. O campo do payload chama-se `agreementRate` e mede aceite real.
- A frente API adiciona `TicketEventType.RESPONSAVEL_REMOVIDO` e altera `ck_ticket_events_type` na `V4`. Esta frente nao cria tipo de evento novo — usa `CLASSIFICACAO_ATUALIZADA`, que ja existe.
- **Proibido escrever em `Ticket.category`, `Ticket.priority` ou `Ticket.classificationOrigin`** fora de `TicketService.applyClassification(...)`. Isso inclui mutar a entidade dentro de metodo `@Transactional` — o dirty checking persiste igual.
- **Proibido editar** `controller/TicketController.java`, `service/TicketService.java`, `security/**`, `db/migration/V4__*.sql`, `frontend/**` e o arquivo de nomes de eventos SSE da frente API.
- `TicketRepository` e leitura apenas; nenhum metodo novo e adicionado nele.
- `V5` e a unica versao de migration disponivel para esta frente. As colunas `ai_suggested_category`, `ai_suggested_priority`, `ai_confidence`, `closed_at`, `resolved_at`, `first_response_at` e `assigned_at` vem da `V4`, da frente API — nunca recriar.
- Notificacoes publicadas com `applicationEventPublisher.publishEvent(NotificationMessage.of(nome, payload, audiencia))`.
- Endpoints ADMIN usam `@PreAuthorize("hasRole('ADMIN')")` ou `accessControlService.assertAdmin()`.
- Listagens nascem paginadas: tamanho 10, ordenacao `createdAt` desc.
- Services mantem variaveis intermediarias; nada de encadear specification, repository e mapper dentro do `return`.
- DTOs seguem `NomeCreationDto` / `NomeDto` / `NomeMinDto`; conversao em mapper do subdominio.
- Filtros expoem `hasCampo` por campo opcional; specification em classe propria com `createSpecification`; nada de string solta em criteria — usar classe `Fields`.
- Testes que envolvem tempo recebem o instante por parametro. Nunca `LocalDateTime.now()` dentro da asserçao.

---

## File Structure

Pacote base novo: `backend/src/main/java/br/org/fadex/helpdesk/ai`.

**Notificacao (Task 1)**
- Create `ai/notification/AiNotificationEventName.java`: constantes `CLASSIFICACAO_CONCLUIDA`, `JOB_IA_FALHOU`, `INDICADORES_ATUALIZADOS`. Holder temporario ate a `V4`.

**Operacao da fila (Tasks 2-3)**
- Create `ai/job/AiJobFields.java`: constantes de nome de campo para criteria.
- Create `ai/job/AiJobFilter.java`: `status`, `type`, `ticketId` com metodos `hasCampo`.
- Create `ai/job/AiJobSpecification.java`: `createSpecification`.
- Create `ai/job/AiJobController.java`: `GET /api/v1/ai/jobs`, `POST /api/v1/ai/jobs/{id}/retry`.
- Modify `ai/job/AiJobService.java`: metodo `findAll(AiJobFilter, Pageable)`.

**Auditoria e revisao (Tasks 4-bis a 7)**
- Create `backend/src/main/resources/db/migration/V5__add_classification_reviewed_at.sql`: coluna do carimbo de revisao.
- Modify `model/ticket/Ticket.java`: campo `classificationReviewedAt` e `markClassificationReviewed(...)`.
- Create `ai/classification/TicketClassificationUpdateDto.java`: request da revisao.
- Create `ai/classification/TicketClassificationReviewService.java`: regra de aceite/correcao.
- Create `ai/classification/TicketClassificationController.java`: `PATCH /api/v1/tickets/{id}/classification`.
- Modify `ai/job/AiJobWorker.java`: parar de mutar `Ticket`, chamar `applyClassification`, gravar auditoria, publicar eventos.
- Modify `model/ticket/TicketDto.java`: tres campos novos no fim do record.
- Modify `model/ticket/TicketMapper.java`: mapear os tres campos.

**Indicadores (Tasks 8-12)**
- Create `ai/indicator/DurationStats.java`: media, mediana, p90 sobre lista de `Duration`.
- Create `ai/indicator/DurationStatsDto.java`: `sampleSize`, `averageHours`, `medianHours`, `p90Hours`.
- Create `ai/indicator/SlaTarget.java`: enum ALTA 4h / MEDIA 24h / BAIXA 72h.
- Create `ai/indicator/TicketIndicatorProjection.java`: linha enxuta por chamado.
- Create `ai/indicator/IndicatorRepository.java`: projecao unica, somente leitura.
- Create `ai/indicator/OverviewIndicatorsDto.java`, `DurationGroupDto.java`, `DurationIndicatorsDto.java`, `BacklogAgingDto.java`, `SlaIndicatorsDto.java`, `SlaSliceDto.java`, `AiIndicatorsDto.java`, `AgreementRateDto.java`, `JobQueueIndicatorsDto.java`, `WorkloadIndicatorsDto.java`, `AssigneeLoadDto.java`, `AssigneeClosureDto.java`, `RequesterVolumeDto.java`, `IndicatorsDto.java`.
- Create `ai/indicator/IndicatorService.java`: orquestra as quatro camadas.
- Create `ai/indicator/IndicatorController.java`: `GET /api/v1/indicators`.

**Duplicados (Tasks 13-14)**
- Create `ai/duplicate/EmbeddingSimilarity.java`: parse do literal pgvector e cosseno.
- Create `ai/duplicate/DuplicateCandidate.java`: projecao `ticketId` + literal do vetor.
- Create `ai/duplicate/DuplicateEmbeddingRepository.java`: leitura dos embeddings gravados, com `cast(... as varchar)`.
- Create `ai/duplicate/DuplicateDetectionService.java`: detecta e grava `ticket_links`.
- Modify `ai/job/AiJobWorker.java`: chamar a deteccao apos gravar o embedding.
- Modify `backend/src/main/resources/application.properties`: `app.ai.duplicate.*`.
- Modify `backend/src/main/resources/application-test.properties`: mesmos valores para teste.

**Documentacao (Task 15)**
- Modify `docs/backend/api.md`: secao da frente IA.
- Modify `docs/projeto/acompanhamento-desenvolvimento.md`: status dos itens desta frente.

---

## Task 0: Documentos de design e plano

**Files:**
- Create: `docs/ia/2026-08-14-revisao-classificacao-indicadores-design.md`
- Create: `docs/ia/2026-08-14-revisao-classificacao-indicadores-plan.md`

**Interfaces:**
- Produces: as decisoes D1 a D8 que todas as tarefas seguintes consomem.

- [ ] **Step 1: Confirmar a branch**

```bash
git rev-parse --abbrev-ref HEAD   # esperado: feature(ia)/revisao-classificacao-indicadores
```

- [ ] **Step 2: Commit dos documentos**

```bash
git add docs/ia
git commit -m "docs(ia): adiciona design e plano de revisao de classificacao e indicadores"
```

Commit docs-only de proposito: mantem `make backend-test` verde enquanto a `V4` nao existe e deixa o artefato duravel antes de qualquer codigo.

---

## Task 1: Holder dos nomes de eventos SSE

**Files:**
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/notification/AiNotificationEventName.java`

**Interfaces:**
- Produces: `AiNotificationEventName.CLASSIFICACAO_CONCLUIDA`, `.JOB_IA_FALHOU`, `.INDICADORES_ATUALIZADOS` — todas `String`.

Contexto: `NotificationMessage.of(String eventName, Object data, NotificationAudience audience)` recebe String. Nenhuma tarefa desta frente esta bloqueada pelo `NotificationEventName` da frente API. Os nomes sao grafados exatamente como a tabela do documento de frentes.

- [ ] **Step 1: Criar o holder**

```java
package br.org.fadex.helpdesk.ai.notification;

/**
 * Nomes de eventos SSE disparados pela frente IA.
 *
 * Temporario: quando a frente API mergear {@code NotificationEventName}, trocar os usos por aquelas
 * constantes e apagar esta classe. As grafias aqui sao identicas as da tabela de eventos do
 * documento de frentes de trabalho.
 */
public abstract class AiNotificationEventName {

	public static final String CLASSIFICACAO_CONCLUIDA = "CLASSIFICACAO_CONCLUIDA";
	public static final String JOB_IA_FALHOU = "JOB_IA_FALHOU";
	public static final String INDICADORES_ATUALIZADOS = "INDICADORES_ATUALIZADOS";

	private AiNotificationEventName() {
	}
}
```

- [ ] **Step 2: Compilar**

Run: `make backend-test`
Expected: PASS (nenhum teste novo; apenas garante que o pacote compila)

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/br/org/fadex/helpdesk/ai/notification/AiNotificationEventName.java
git commit -m "feat(backend): adiciona nomes de eventos sse da frente ia"
```

---

## Task 2: Filtro e specification de jobs de IA

**Files:**
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/job/AiJobFields.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/job/AiJobFilter.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/job/AiJobSpecification.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/ai/job/AiJobService.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/ai/job/AiJobServiceTest.java`

**Interfaces:**
- Consumes: `AiJobRepository` (ja existe, ja estende `JpaSpecificationExecutor<AiJob>`), `AiJobMapper.toResponseDto`.
- Produces: `AiJobService.findAll(AiJobFilter filter, Pageable pageable) -> Page<AiJobDto>`; `AiJobFilter(AiJobStatus status, AiJobType type, UUID ticketId)`; `AiJobFields.CREATED_AT` = `"createdAt"`.

Esta tarefa nao depende da `V4`.

- [ ] **Step 1: Escrever o teste que falha**

Adicionar em `AiJobServiceTest`:

```java
@Test
void deveListarJobsPaginadosAplicandoFiltro() {
	AiJobFilter filter = new AiJobFilter(AiJobStatus.FAILED, null, null);
	Pageable pageable = PageRequest.of(0, 10);
	AiJob job = new AiJob(UUID.randomUUID(), AiJobType.CLASSIFICATION, LocalDateTime.now());
	Page<AiJob> page = new PageImpl<>(List.of(job), pageable, 1);

	when(aiJobRepository.findAll(ArgumentMatchers.<Specification<AiJob>>any(), eq(pageable)))
			.thenReturn(page);

	Page<AiJobDto> response = aiJobService.findAll(filter, pageable);

	assertThat(response.getTotalElements()).isEqualTo(1);
	assertThat(response.getContent().getFirst().type()).isEqualTo(AiJobType.CLASSIFICATION);
}
```

- [ ] **Step 2: Rodar o teste e confirmar a falha**

Run: `cd backend && ./gradlew test --tests '*AiJobServiceTest*'`
Expected: FAIL — `AiJobFilter` nao existe / `findAll` nao existe

- [ ] **Step 3: Criar `AiJobFields`**

```java
package br.org.fadex.helpdesk.ai.job;

public abstract class AiJobFields {

	public static final String STATUS = "status";
	public static final String TYPE = "type";
	public static final String TICKET_ID = "ticketId";
	public static final String CREATED_AT = "createdAt";

	private AiJobFields() {
	}
}
```

- [ ] **Step 4: Criar `AiJobFilter`**

```java
package br.org.fadex.helpdesk.ai.job;

import java.util.UUID;

public record AiJobFilter(AiJobStatus status, AiJobType type, UUID ticketId) {

	public boolean hasStatus() {
		return status != null;
	}

	public boolean hasType() {
		return type != null;
	}

	public boolean hasTicketId() {
		return ticketId != null;
	}
}
```

- [ ] **Step 5: Criar `AiJobSpecification`**

```java
package br.org.fadex.helpdesk.ai.job;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public abstract class AiJobSpecification {

	private AiJobSpecification() {
	}

	public static Specification<AiJob> createSpecification(AiJobFilter filter) {
		return (root, query, criteriaBuilder) -> {
			List<Predicate> predicates = new ArrayList<>();

			if (filter.hasStatus()) {
				predicates.add(criteriaBuilder.equal(root.get(AiJobFields.STATUS), filter.status()));
			}
			if (filter.hasType()) {
				predicates.add(criteriaBuilder.equal(root.get(AiJobFields.TYPE), filter.type()));
			}
			if (filter.hasTicketId()) {
				predicates.add(criteriaBuilder.equal(root.get(AiJobFields.TICKET_ID), filter.ticketId()));
			}

			return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
		};
	}
}
```

- [ ] **Step 6: Adicionar `findAll` em `AiJobService`**

```java
@Transactional(readOnly = true)
public Page<AiJobDto> findAll(AiJobFilter filter, Pageable pageable) {
	Specification<AiJob> spec = AiJobSpecification.createSpecification(filter);
	Page<AiJob> jobs = aiJobRepository.findAll(spec, pageable);
	Page<AiJobDto> response = jobs.map(AiJobMapper::toResponseDto);

	return response;
}
```

- [ ] **Step 7: Rodar o teste e confirmar que passa**

Run: `cd backend && ./gradlew test --tests '*AiJobServiceTest*'`
Expected: PASS

- [ ] **Step 8: Rodar a suite inteira e commitar**

```bash
make backend-test
git add backend/src/main/java/br/org/fadex/helpdesk/ai/job backend/src/test/java/br/org/fadex/helpdesk/ai/job
git commit -m "feat(backend): adiciona filtro e listagem paginada de jobs de ia"
```

---

## Task 3: Endpoints ADMIN de operacao da fila

**Files:**
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/job/AiJobController.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/ai/job/AiJobControllerTest.java`

**Interfaces:**
- Consumes: `AiJobService.findAll(AiJobFilter, Pageable)`, `AiJobService.retry(UUID)`.
- Produces: `GET /api/v1/ai/jobs -> Page<AiJobDto>`; `POST /api/v1/ai/jobs/{id}/retry -> AiJobDto`.

`AiJobService.retry(UUID)` ja existe: valida que o job esta `FAILED` (senao `ConflictException` -> 409), reseta para `PENDING`, limpa `lastError` e devolve `AiJobDto`. So falta o controller. Esta tarefa nao depende da `V4`.

- [ ] **Step 1: Escrever o teste que falha**

```java
package br.org.fadex.helpdesk.ai.job;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiJobControllerTest {

	@Mock
	private AiJobService aiJobService;

	@InjectMocks
	private AiJobController aiJobController;

	@Test
	void deveListarJobs() {
		Pageable pageable = PageRequest.of(0, 10);
		AiJobDto dto = new AiJobDto(
				UUID.randomUUID(), UUID.randomUUID(), AiJobType.CLASSIFICATION, AiJobStatus.FAILED,
				2, LocalDateTime.now(), "timeout", LocalDateTime.now(), LocalDateTime.now()
		);
		Page<AiJobDto> page = new PageImpl<>(List.of(dto), pageable, 1);
		when(aiJobService.findAll(any(), any())).thenReturn(page);

		ResponseEntity<Page<AiJobDto>> response =
				aiJobController.findAll(new AiJobFilter(null, null, null), pageable);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().getTotalElements()).isEqualTo(1);
	}

	@Test
	void deveRetentarJob() {
		UUID id = UUID.randomUUID();
		AiJobDto dto = new AiJobDto(
				id, UUID.randomUUID(), AiJobType.EMBEDDING, AiJobStatus.PENDING,
				2, LocalDateTime.now(), null, LocalDateTime.now(), LocalDateTime.now()
		);
		when(aiJobService.retry(id)).thenReturn(dto);

		ResponseEntity<AiJobDto> response = aiJobController.retry(id);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().status()).isEqualTo(AiJobStatus.PENDING);
	}
}
```

- [ ] **Step 2: Rodar e confirmar a falha**

Run: `cd backend && ./gradlew test --tests '*AiJobControllerTest*'`
Expected: FAIL — `AiJobController` nao existe

- [ ] **Step 3: Criar o controller**

```java
package br.org.fadex.helpdesk.ai.job;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai/jobs")
@PreAuthorize("hasRole('ADMIN')")
public class AiJobController {

	private final AiJobService aiJobService;

	public AiJobController(AiJobService aiJobService) {
		this.aiJobService = aiJobService;
	}

	@GetMapping
	public ResponseEntity<Page<AiJobDto>> findAll(
			@ModelAttribute AiJobFilter filter,
			@PageableDefault(size = 10, sort = AiJobFields.CREATED_AT, direction = Sort.Direction.DESC) Pageable pageable
	) {
		Page<AiJobDto> jobs = aiJobService.findAll(filter, pageable);

		return ResponseEntity.ok(jobs);
	}

	@PostMapping("/{id}/retry")
	public ResponseEntity<AiJobDto> retry(@PathVariable UUID id) {
		AiJobDto job = aiJobService.retry(id);

		return ResponseEntity.ok(job);
	}
}
```

`@EnableMethodSecurity` ja esta ligado no `SecurityConfig` e `/api/v1/**` cai em `anyRequest().authenticated()`, entao `@PreAuthorize` na classe basta — nao ha regra de rota a adicionar (e `security/` nao pode ser tocado).

- [ ] **Step 4: Rodar e confirmar que passa**

Run: `cd backend && ./gradlew test --tests '*AiJobControllerTest*'`
Expected: PASS

- [ ] **Step 5: Suite inteira e commit**

```bash
make backend-test
git add backend/src/main/java/br/org/fadex/helpdesk/ai/job/AiJobController.java backend/src/test/java/br/org/fadex/helpdesk/ai/job/AiJobControllerTest.java
git commit -m "feat(backend): expoe endpoints admin de operacao da fila de ia"
```

---

## Task 4: (removida) Repository de auditoria da sugestao

**Esta task foi removida.** A frente API entrega `Ticket.applyAiSuggestion(...)` junto com a `V4`, e o
worker passa a usar esse metodo em vez de um `UPDATE` nativo proprio. Ver D1 revisado no design: a
troca elimina o risco de o flush do `applyClassification(...)` sobrescrever a auditoria com `null`.

Nao criar `TicketAiAuditRepository`. A escrita das tres colunas acontece na Task 5.

---

## Task 4-bis: Migration V5 e carimbo de revisao

**Depende da `V4`** (a `V5` roda depois dela). Implementa a saida A de D7.

**Files:**
- Create: `backend/src/main/resources/db/migration/V5__add_classification_reviewed_at.sql`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/Ticket.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/repository/TicketPersistenceTest.java`

**Interfaces:**
- Produces: coluna `tickets.classification_reviewed_at`; `Ticket.markClassificationReviewed(LocalDateTime)`; `Ticket.getClassificationReviewedAt()`.

`V5` e a unica migration desta frente. Nao adicionar nada alem desta coluna — as colunas de `V4` sao da frente API.

**Nao criar a `V5` antes de a `V4` estar em `dev`, e nao reordenar esta task por conveniencia.** Se um banco local aplicar a `V5` e a `V4` chegar depois, o Flyway aborta com "Detected resolved migration not applied to database: 4" e a saida e recriar o banco.

- [ ] **Step 1: Criar a migration**

```sql
alter table tickets add column classification_reviewed_at timestamp;
```

Sem default e sem `not null`: chamado nunca revisado precisa ficar nulo, e e justamente esse nulo que
mantem o chamado fora do denominador da concordancia.

- [ ] **Step 2: Mapear na entidade**

Em `Ticket`, junto dos demais campos de classificacao:

```java
	@Column(name = "classification_reviewed_at")
	private LocalDateTime classificationReviewedAt;
```

E o metodo de dominio, junto de `applyManualClassification`:

```java
	public void markClassificationReviewed(LocalDateTime reviewedAt) {
		this.classificationReviewedAt = reviewedAt;
	}

	public LocalDateTime getClassificationReviewedAt() {
		return classificationReviewedAt;
	}
```

`Ticket` pertence a frente API; esta e a unica alteracao desta frente no arquivo, e e aditiva.

- [ ] **Step 3: Rodar a suite**

Run: `make backend-test`
Expected: PASS. `spring.jpa.hibernate.ddl-auto=validate` so passa se a migration e o mapeamento
casarem — e o que prova que a coluna existe com o tipo certo.

- [ ] **Step 4: Commit**

Suspenso pelo regime de revisao vigente. Deixar no working tree.

---

## Task 5: Worker grava a sugestao e passa a usar applyClassification

**Depende da `V4`** (colunas de auditoria) **e da seam** `TicketService.applyClassification(...)`.

**Files:**
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/ai/job/AiJobWorker.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/ai/job/AiJobWorkerTest.java`

**Interfaces:**
- Consumes: `TicketService.applyClassification(UUID, TicketCategory, TicketPriority, ClassificationOrigin, String)`, `Ticket.applyAiSuggestion(TicketCategory, TicketPriority, Double)` (entregue pela frente API com a `V4`), `AiNotificationEventName`.
- Produces: nenhuma assinatura publica nova.

Contexto obrigatorio (D2 do design): hoje `processClassification` chama `ticket.applyAutomaticClassification(...)` na entidade gerenciada, dentro de metodo `@Transactional`. O dirty checking persiste isso. Esta tarefa **remove** essa linha; nao adiciona uma chamada ao lado dela.

- [ ] **Step 1: Escrever os testes que falham**

Adicionar em `AiJobWorkerTest`:

```java
@Test
void deveAplicarClassificacaoPelaSeamENaoMutarOTicket() {
	UUID ticketId = UUID.randomUUID();
	Ticket ticket = mock(Ticket.class);
	when(ticket.getId()).thenReturn(ticketId);
	when(ticket.getTitle()).thenReturn("Sem acesso ao VPN");
	when(ticket.getDescription()).thenReturn("Nao consigo conectar na rede.");

	AiJob job = jobDeClassificacao(ticket);
	when(aiJobService.findDueJobs(any(), anyInt())).thenReturn(List.of(job));
	when(aiTriageClient.classify(any(), any())).thenReturn(
			new TicketClassification(TicketCategory.INFRAESTRUTURA, TicketPriority.ALTA, 0.87, "Rede.")
	);

	worker.processDueJobs();

	verify(ticketService).applyClassification(
			ticketId, TicketCategory.INFRAESTRUTURA, TicketPriority.ALTA, ClassificationOrigin.IA, "Rede."
	);
	verify(ticket, never()).applyAutomaticClassification(any(), any(), any());
}

@Test
void deveGravarSugestaoEConfiancaDaIa() {
	UUID ticketId = UUID.randomUUID();
	Ticket ticket = mock(Ticket.class);
	when(ticket.getId()).thenReturn(ticketId);
	when(ticket.getTitle()).thenReturn("Sem acesso ao VPN");
	when(ticket.getDescription()).thenReturn("Nao consigo conectar na rede.");
	when(aiJobService.findDueJobs(any(), anyInt())).thenReturn(List.of(jobDeClassificacao(ticket)));
	when(aiTriageClient.classify(any(), any())).thenReturn(
			new TicketClassification(TicketCategory.INFRAESTRUTURA, TicketPriority.ALTA, 0.87, "Rede.")
	);

	worker.processDueJobs();

	verify(ticket).applyAiSuggestion(TicketCategory.INFRAESTRUTURA, TicketPriority.ALTA, 0.87);
}

@Test
void devePublicarClassificacaoConcluida() {
	UUID ticketId = UUID.randomUUID();
	Ticket ticket = mock(Ticket.class);
	User requester = mock(User.class);
	when(requester.getId()).thenReturn(UUID.randomUUID());
	when(ticket.getId()).thenReturn(ticketId);
	when(ticket.getRequester()).thenReturn(requester);
	when(ticket.getTitle()).thenReturn("Sem acesso ao VPN");
	when(ticket.getDescription()).thenReturn("Nao consigo conectar na rede.");
	when(ticketService.findEntityById(ticketId)).thenReturn(ticket);
	when(aiJobService.findDueJobs(any(), anyInt())).thenReturn(List.of(jobDeClassificacao(ticket)));
	when(aiTriageClient.classify(any(), any())).thenReturn(
			new TicketClassification(TicketCategory.INFRAESTRUTURA, TicketPriority.ALTA, 0.87, "Rede.")
	);

	worker.processDueJobs();

	ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
	verify(applicationEventPublisher, atLeastOnce()).publishEvent(captor.capture());

	assertThat(captor.getAllValues())
			.extracting(NotificationMessage::eventName)
			.contains(AiNotificationEventName.CLASSIFICACAO_CONCLUIDA);
}

@Test
void devePublicarJobIaFalhouSomenteAoEsgotarTentativas() {
	// job com attempts = maxAttempts - 1, cliente lanca AiIntegrationException
	worker.processDueJobs();

	ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
	verify(applicationEventPublisher, atLeastOnce()).publishEvent(captor.capture());

	assertThat(captor.getAllValues())
			.extracting(NotificationMessage::eventName)
			.contains(AiNotificationEventName.JOB_IA_FALHOU);
}

@Test
void naoDevePublicarJobIaFalhouQuandoAindaHaTentativas() {
	// job com attempts = 0, cliente lanca RuntimeException nao tratada pelo fallback
	worker.processDueJobs();

	verify(applicationEventPublisher, never()).publishEvent(
			argThat((NotificationMessage message) ->
					AiNotificationEventName.JOB_IA_FALHOU.equals(message.eventName()))
	);
}
```

- [ ] **Step 2: Rodar e confirmar a falha**

Run: `cd backend && ./gradlew test --tests '*AiJobWorkerTest*'`
Expected: FAIL — construtor sem as dependencias novas

- [ ] **Step 3: Adicionar as dependencias novas ao construtor do worker**

Acrescentar `TicketService ticketService` e `ApplicationEventPublisher applicationEventPublisher` aos parametros e aos campos, mantendo os existentes na mesma ordem.

- [ ] **Step 4: Reescrever `processClassification`**

```java
private void processClassification(AiJob job) {
	Ticket ticket = job.getTicket();
	TicketClassification classification = triageEnabled
			? classifyWithFallback(ticket)
			: fallbackTicketClassifier.classify(ticket.getTitle(), ticket.getDescription());

	// Auditoria na propria entidade gerenciada: um UPDATE unico e coerente, sem ordem obrigatoria
	// entre as duas escritas e sem risco de sobrescrita por flush (D1 revisado).
	ticket.applyAiSuggestion(
			classification.category(),
			classification.priority(),
			classification.confidence()
	);

	ticketService.applyClassification(
			ticket.getId(),
			classification.category(),
			classification.priority(),
			ClassificationOrigin.IA,
			classification.justification()
	);

	publishClassificationDone(ticket.getId(), classification);
}
```

A linha `ticket.applyAutomaticClassification(...)` sai. Nao fica comentada, nao fica ao lado.

- [ ] **Step 5: Adicionar a publicacao dos eventos**

```java
private void publishClassificationDone(UUID ticketId, TicketClassification classification) {
	Ticket ticket = ticketService.findEntityById(ticketId);
	UUID requesterId = ticket.getRequester().getId();

	Map<String, Object> payload = Map.of(
			"ticketId", ticketId,
			"category", classification.category().name(),
			"priority", classification.priority().name(),
			"confidence", classification.confidence()
	);

	applicationEventPublisher.publishEvent(NotificationMessage.of(
			AiNotificationEventName.CLASSIFICACAO_CONCLUIDA,
			payload,
			new NotificationAudience.Users(Set.of(requesterId))
	));
	applicationEventPublisher.publishEvent(NotificationMessage.of(
			AiNotificationEventName.CLASSIFICACAO_CONCLUIDA,
			payload,
			new NotificationAudience.Roles(Set.of(Role.ADMIN))
	));
	applicationEventPublisher.publishEvent(NotificationMessage.of(
			AiNotificationEventName.INDICADORES_ATUALIZADOS,
			Map.of("reason", "CLASSIFICACAO_CONCLUIDA", "occurredAt", LocalDateTime.now()),
			new NotificationAudience.Roles(Set.of(Role.ADMIN))
	));
}
```

`NotificationAudience` e uma sealed interface com `Users(Set<UUID>)`, `Roles(Set<Role>)` e `Everyone()` — nao existe variante que combine usuario e papel, por isso saem duas mensagens para a mesma audiencia logica.

- [ ] **Step 6: Publicar `JOB_IA_FALHOU` so no esgotamento**

```java
private void handleFailure(AiJob job, RuntimeException exception) {
	int nextDelay = job.getAttempts() + 1;
	LocalDateTime nextAttemptAt = LocalDateTime.now().plusMinutes(nextDelay);
	String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();

	job.markFailed(message, nextAttemptAt);
	if (job.getAttempts() < maxAttempts) {
		job.scheduleRetry();
		return;
	}

	applicationEventPublisher.publishEvent(NotificationMessage.of(
			AiNotificationEventName.JOB_IA_FALHOU,
			Map.of(
					"jobId", job.getId(),
					"ticketId", job.getTicketId(),
					"type", job.getType().name(),
					"attempts", job.getAttempts(),
					"lastError", message
			),
			new NotificationAudience.Roles(Set.of(Role.ADMIN))
	));
}
```

`markFailed` ja incrementa `attempts`, entao a comparacao acontece depois do incremento e o evento sai exatamente uma vez, na ultima tentativa.

- [ ] **Step 7: Rodar e confirmar que passa**

Run: `cd backend && ./gradlew test --tests '*AiJobWorkerTest*'`
Expected: PASS

- [ ] **Step 8: Suite inteira e commit**

```bash
make backend-test
git add backend/src/main/java/br/org/fadex/helpdesk/ai/job/AiJobWorker.java backend/src/test/java/br/org/fadex/helpdesk/ai/job/AiJobWorkerTest.java
git commit -m "feat(backend): persiste sugestao da ia e roteia classificacao pela seam do ticket"
```

---

## Task 6: Expor confidence e sugestao no DTO do chamado

**Depende da `V4`** (getters novos em `Ticket`, criados pela frente API junto com as colunas).

**Files:**
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketDto.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketMapper.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/model/ticket/TicketMapperTest.java`

**Interfaces:**
- Produces: `TicketDto.aiSuggestedCategory()`, `.aiSuggestedPriority()`, `.aiConfidence()`.

Estes dois arquivos pertencem a frente API. A edicao e aditiva — tres componentes no fim do record — e o `confidence` no DTO e requisito explicito desta frente. Conflito de merge esperado e trivial.

- [ ] **Step 1: Escrever o teste que falha**

```java
@Test
void deveMapearSugestaoEConfiancaDaIa() {
	Ticket ticket = ticketComSugestao(TicketCategory.INFRAESTRUTURA, TicketPriority.ALTA, 0.87);

	TicketDto dto = TicketMapper.toResponseDto(ticket);

	assertThat(dto.aiSuggestedCategory()).isEqualTo(TicketCategory.INFRAESTRUTURA);
	assertThat(dto.aiSuggestedPriority()).isEqualTo(TicketPriority.ALTA);
	assertThat(dto.aiConfidence()).isEqualTo(0.87);
}

@Test
void deveMapearSugestaoComoNulaQuandoIaAindaNaoRespondeu() {
	Ticket ticket = ticketSemSugestao();

	TicketDto dto = TicketMapper.toResponseDto(ticket);

	assertThat(dto.aiSuggestedCategory()).isNull();
	assertThat(dto.aiConfidence()).isNull();
}
```

- [ ] **Step 2: Rodar e confirmar a falha**

Run: `cd backend && ./gradlew test --tests '*TicketMapperTest*'`
Expected: FAIL — `aiSuggestedCategory()` nao existe

- [ ] **Step 3: Adicionar os campos no fim do record `TicketDto`**

```java
		LocalDateTime createdAt,
		LocalDateTime updatedAt,
		TicketCategory aiSuggestedCategory,
		TicketPriority aiSuggestedPriority,
		Double aiConfidence
) {
```

`Double`, nao `double`: ausencia de sugestao precisa ser `null` no JSON, nao `0.0`.

- [ ] **Step 4: Mapear em `TicketMapper.toResponseDto`**

Acrescentar, depois de `ticket.getUpdatedAt()`:

```java
				ticket.getAiSuggestedCategory(),
				ticket.getAiSuggestedPriority(),
				ticket.getAiConfidence()
```

- [ ] **Step 5: Rodar e confirmar que passa**

Run: `cd backend && ./gradlew test --tests '*TicketMapperTest*'`
Expected: PASS

- [ ] **Step 6: Suite inteira e commit**

```bash
make backend-test
git add backend/src/main/java/br/org/fadex/helpdesk/model/ticket backend/src/test/java/br/org/fadex/helpdesk/model/ticket
git commit -m "feat(backend): expoe sugestao e confianca da ia no dto do chamado"
```

---

## Task 7: PATCH de revisao da classificacao

**Depende das Tasks 5 e 6** e da seam `applyClassification`. **Requisito obrigatorio do desafio.**

**Files:**
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/classification/TicketClassificationUpdateDto.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/classification/TicketClassificationReviewService.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/classification/TicketClassificationController.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/ai/classification/TicketClassificationReviewServiceTest.java`

**Interfaces:**
- Consumes: `TicketService.findEntityById(UUID)`, `TicketService.applyClassification(...)`, `AccessControlService.assertAdmin()`.
- Produces: `TicketClassificationReviewService.review(UUID id, TicketClassificationUpdateDto dto) -> TicketDto`.

Regra (D7 e contrato do design): origem vira `MANUAL` quando os valores enviados **diferem da sugestao da IA**. Aceite dos valores sugeridos mantem `IA`. Chamado ainda `PENDENTE`, sem sugestao registrada, sempre vira `MANUAL`.

**Aceite e correcao carimbam `classificationReviewedAt`.** E esse carimbo que sustenta o denominador de `agreementRate` — sem ele, aceite e "ninguem olhou" voltam a ser indistinguiveis. Adicionar teste: `deveCarimbarInstanteDaRevisaoNosDoisCaminhos`.

- [ ] **Step 1: Escrever os testes que falham**

```java
@Test
void deveManterOrigemIaQuandoAdminAceitaASugestao() {
	Ticket ticket = ticketComSugestao(TicketCategory.ACESSO, TicketPriority.MEDIA);
	when(ticketService.findEntityById(id)).thenReturn(ticket);

	service.review(id, new TicketClassificationUpdateDto(TicketCategory.ACESSO, TicketPriority.MEDIA, null));

	verify(ticketService).applyClassification(
			eq(id), eq(TicketCategory.ACESSO), eq(TicketPriority.MEDIA), eq(ClassificationOrigin.IA), any()
	);
}

@Test
void deveVirarManualQuandoAdminCorrigeASugestao() {
	Ticket ticket = ticketComSugestao(TicketCategory.ACESSO, TicketPriority.MEDIA);
	when(ticketService.findEntityById(id)).thenReturn(ticket);

	service.review(id, new TicketClassificationUpdateDto(
			TicketCategory.INFRAESTRUTURA, TicketPriority.ALTA, "Afeta o predio inteiro."
	));

	verify(ticketService).applyClassification(
			id, TicketCategory.INFRAESTRUTURA, TicketPriority.ALTA, ClassificationOrigin.MANUAL,
			"Afeta o predio inteiro."
	);
}

@Test
void deveVirarManualQuandoNaoHaSugestaoRegistrada() {
	Ticket ticket = ticketSemSugestao();
	when(ticketService.findEntityById(id)).thenReturn(ticket);

	service.review(id, new TicketClassificationUpdateDto(TicketCategory.RH, TicketPriority.BAIXA, null));

	verify(ticketService).applyClassification(
			eq(id), eq(TicketCategory.RH), eq(TicketPriority.BAIXA), eq(ClassificationOrigin.MANUAL), any()
	);
}

Helpers usados pelos testes acima, definidos no proprio arquivo de teste:

```java
private static final UUID id = UUID.randomUUID();
private static final TicketClassificationUpdateDto dtoValido =
		new TicketClassificationUpdateDto(TicketCategory.ACESSO, TicketPriority.MEDIA, null);

private Ticket ticketComSugestao(TicketCategory category, TicketPriority priority) {
	Ticket ticket = mock(Ticket.class);
	when(ticket.getAiSuggestedCategory()).thenReturn(category);
	when(ticket.getAiSuggestedPriority()).thenReturn(priority);
	return ticket;
}

private Ticket ticketSemSugestao() {
	Ticket ticket = mock(Ticket.class);
	when(ticket.getAiSuggestedCategory()).thenReturn(null);
	when(ticket.getAiSuggestedPriority()).thenReturn(null);
	return ticket;
}
```

```java
@Test
void deveNegarRevisaoParaSolicitante() {
	doThrow(new ForbiddenException("Acesso negado ao recurso solicitado."))
			.when(accessControlService).assertAdmin();

	assertThatThrownBy(() -> service.review(id, dtoValido))
			.isInstanceOf(ForbiddenException.class);

	verify(ticketService, never()).applyClassification(any(), any(), any(), any(), any());
}

@Test
void devePublicarIndicadoresAtualizados() {
	when(ticketService.findEntityById(id)).thenReturn(ticketComSugestao(TicketCategory.ACESSO, TicketPriority.MEDIA));

	service.review(id, dtoValido);

	ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
	verify(applicationEventPublisher, atLeastOnce()).publishEvent(captor.capture());
	assertThat(captor.getAllValues())
			.extracting(NotificationMessage::eventName)
			.contains(AiNotificationEventName.INDICADORES_ATUALIZADOS);
}
```

- [ ] **Step 2: Rodar e confirmar a falha**

Run: `cd backend && ./gradlew test --tests '*TicketClassificationReviewServiceTest*'`
Expected: FAIL — classes nao existem

- [ ] **Step 3: Criar o DTO de request**

```java
package br.org.fadex.helpdesk.ai.classification;

import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TicketClassificationUpdateDto(
		@NotNull TicketCategory category,
		@NotNull TicketPriority priority,
		@Size(max = 2000) String justification
) {
}
```

- [ ] **Step 4: Criar o service**

```java
package br.org.fadex.helpdesk.ai.classification;

import br.org.fadex.helpdesk.ai.notification.AiNotificationEventName;
import br.org.fadex.helpdesk.model.enums.ClassificationOrigin;
import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.model.ticket.Ticket;
import br.org.fadex.helpdesk.model.ticket.TicketDto;
import br.org.fadex.helpdesk.security.AccessControlService;
import br.org.fadex.helpdesk.service.TicketService;
import br.org.fadex.helpdesk.sse.model.NotificationAudience;
import br.org.fadex.helpdesk.sse.model.NotificationMessage;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class TicketClassificationReviewService {

	private static final String ACCEPTED_JUSTIFICATION = "Sugestao da IA aceita pelo administrador.";

	private final TicketService ticketService;
	private final AccessControlService accessControlService;
	private final ApplicationEventPublisher applicationEventPublisher;

	public TicketClassificationReviewService(
			TicketService ticketService,
			AccessControlService accessControlService,
			ApplicationEventPublisher applicationEventPublisher
	) {
		this.ticketService = ticketService;
		this.accessControlService = accessControlService;
		this.applicationEventPublisher = applicationEventPublisher;
	}

	@Transactional
	public TicketDto review(UUID id, TicketClassificationUpdateDto dto) {
		accessControlService.assertAdmin();

		Ticket ticket = ticketService.findEntityById(id);
		ClassificationOrigin origin = resolveOrigin(ticket, dto);
		String justification = resolveJustification(dto, origin);

		ticketService.applyClassification(id, dto.category(), dto.priority(), origin, justification);
		// O carimbo persiste por dirty checking: findEntityById e @Transactional(readOnly = true), mas
		// com propagacao REQUIRED ele entra nesta transacao read-write e o hint readOnly do metodo
		// interno e ignorado. Nao mover a leitura para fora da transacao — o carimbo se perderia.
		ticket.markClassificationReviewed(LocalDateTime.now(clock));
		publishIndicatorsUpdated(id);

		TicketDto response = ticketService.findById(id);

		return response;
	}

	private ClassificationOrigin resolveOrigin(Ticket ticket, TicketClassificationUpdateDto dto) {
		boolean hasSuggestion = ticket.getAiSuggestedCategory() != null
				&& ticket.getAiSuggestedPriority() != null;
		if (!hasSuggestion) {
			return ClassificationOrigin.MANUAL;
		}

		boolean matchesSuggestion = ticket.getAiSuggestedCategory() == dto.category()
				&& ticket.getAiSuggestedPriority() == dto.priority();

		return matchesSuggestion ? ClassificationOrigin.IA : ClassificationOrigin.MANUAL;
	}

	private String resolveJustification(TicketClassificationUpdateDto dto, ClassificationOrigin origin) {
		if (dto.justification() != null && !dto.justification().isBlank()) {
			return dto.justification();
		}

		return origin == ClassificationOrigin.IA ? ACCEPTED_JUSTIFICATION : "Classificacao ajustada manualmente.";
	}

	private void publishIndicatorsUpdated(UUID ticketId) {
		applicationEventPublisher.publishEvent(NotificationMessage.of(
				AiNotificationEventName.INDICADORES_ATUALIZADOS,
				Map.of("reason", "CLASSIFICACAO_REVISADA", "ticketId", ticketId, "occurredAt", LocalDateTime.now()),
				new NotificationAudience.Roles(Set.of(Role.ADMIN))
		));
	}
}
```

- [ ] **Step 5: Criar o controller**

```java
package br.org.fadex.helpdesk.ai.classification;

import br.org.fadex.helpdesk.model.ticket.TicketDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tickets")
public class TicketClassificationController {

	private final TicketClassificationReviewService ticketClassificationReviewService;

	public TicketClassificationController(TicketClassificationReviewService ticketClassificationReviewService) {
		this.ticketClassificationReviewService = ticketClassificationReviewService;
	}

	@PatchMapping("/{id}/classification")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<TicketDto> review(
			@PathVariable UUID id,
			@Valid @RequestBody TicketClassificationUpdateDto ticketClassificationUpdateDto
	) {
		TicketDto ticket = ticketClassificationReviewService.review(id, ticketClassificationUpdateDto);

		return ResponseEntity.ok(ticket);
	}
}
```

Mapear `/api/v1/tickets` num segundo `@RestController` e legitimo: o `TicketController` da frente API declara apenas `GET /`, `GET /{id}` e `POST /`, entao nao ha par (path, method) duplicado. Spring so rejeita colisao exata.

- [ ] **Step 6: Rodar e confirmar que passa**

Run: `cd backend && ./gradlew test --tests '*TicketClassificationReviewServiceTest*'`
Expected: PASS

- [ ] **Step 7: Suite inteira e commit**

```bash
make backend-test
git add backend/src/main/java/br/org/fadex/helpdesk/ai/classification backend/src/test/java/br/org/fadex/helpdesk/ai/classification
git commit -m "feat(backend): adiciona revisao de classificacao pelo admin"
```

---

## Task 8: Estatistica de duracao — media, mediana e p90

**Nao depende da `V4`.** Pura funcao sobre listas.

**Files:**
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/indicator/DurationStats.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/indicator/DurationStatsDto.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/ai/indicator/DurationStatsTest.java`

**Interfaces:**
- Produces: `DurationStats.of(List<Duration> durations) -> DurationStatsDto`; `DurationStatsDto(int sampleSize, Double averageHours, Double medianHours, Double p90Hours)`.

- [ ] **Step 1: Escrever os testes que falham**

```java
package br.org.fadex.helpdesk.ai.indicator;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DurationStatsTest {

	@Test
	void deveDevolverNulosParaAmostraVazia() {
		DurationStatsDto stats = DurationStats.of(List.of());

		assertThat(stats.sampleSize()).isZero();
		assertThat(stats.averageHours()).isNull();
		assertThat(stats.medianHours()).isNull();
		assertThat(stats.p90Hours()).isNull();
	}

	@Test
	void deveCalcularComUmUnicoElemento() {
		DurationStatsDto stats = DurationStats.of(List.of(Duration.ofHours(4)));

		assertThat(stats.sampleSize()).isEqualTo(1);
		assertThat(stats.averageHours()).isEqualTo(4.0);
		assertThat(stats.medianHours()).isEqualTo(4.0);
		assertThat(stats.p90Hours()).isEqualTo(4.0);
	}

	@Test
	void deveUsarMediaDosDoisCentraisQuandoAmostraEPar() {
		DurationStatsDto stats = DurationStats.of(List.of(
				Duration.ofHours(2), Duration.ofHours(4), Duration.ofHours(6), Duration.ofHours(8)
		));

		assertThat(stats.medianHours()).isEqualTo(5.0);
		assertThat(stats.averageHours()).isEqualTo(5.0);
	}

	@Test
	void deveUsarOCentralQuandoAmostraEImpar() {
		DurationStatsDto stats = DurationStats.of(List.of(
				Duration.ofHours(1), Duration.ofHours(2), Duration.ofHours(30)
		));

		assertThat(stats.medianHours()).isEqualTo(2.0);
		assertThat(stats.averageHours()).isEqualTo(11.0);
	}

	@Test
	void deveCalcularP90PorRankMaisProximo() {
		List<Duration> durations = new java.util.ArrayList<>();
		for (int hour = 1; hour <= 10; hour++) {
			durations.add(Duration.ofHours(hour));
		}

		DurationStatsDto stats = DurationStats.of(durations);

		assertThat(stats.p90Hours()).isEqualTo(9.0);
	}

	@Test
	void deveOrdenarAmostraDesordenada() {
		DurationStatsDto stats = DurationStats.of(List.of(
				Duration.ofHours(8), Duration.ofHours(2), Duration.ofHours(4), Duration.ofHours(6)
		));

		assertThat(stats.medianHours()).isEqualTo(5.0);
	}
}
```

- [ ] **Step 2: Rodar e confirmar a falha**

Run: `cd backend && ./gradlew test --tests '*DurationStatsTest*'`
Expected: FAIL — `DurationStats` nao existe

- [ ] **Step 3: Criar o DTO**

```java
package br.org.fadex.helpdesk.ai.indicator;

public record DurationStatsDto(
		int sampleSize,
		Double averageHours,
		Double medianHours,
		Double p90Hours
) {

	public static DurationStatsDto empty() {
		return new DurationStatsDto(0, null, null, null);
	}
}
```

- [ ] **Step 4: Criar `DurationStats`**

```java
package br.org.fadex.helpdesk.ai.indicator;

import java.time.Duration;
import java.util.List;

public abstract class DurationStats {

	private static final double SECONDS_PER_HOUR = 3600.0;
	private static final double P90 = 0.9;

	private DurationStats() {
	}

	public static DurationStatsDto of(List<Duration> durations) {
		if (durations == null || durations.isEmpty()) {
			return DurationStatsDto.empty();
		}

		List<Double> hours = durations.stream()
				.map(duration -> duration.toSeconds() / SECONDS_PER_HOUR)
				.sorted()
				.toList();

		double average = hours.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
		double median = median(hours);
		double p90 = percentile(hours, P90);

		return new DurationStatsDto(hours.size(), round(average), round(median), round(p90));
	}

	private static double median(List<Double> sortedHours) {
		int size = sortedHours.size();
		int middle = size / 2;

		if (size % 2 == 1) {
			return sortedHours.get(middle);
		}

		return (sortedHours.get(middle - 1) + sortedHours.get(middle)) / 2.0;
	}

	private static double percentile(List<Double> sortedHours, double percentile) {
		int rank = (int) Math.ceil(percentile * sortedHours.size());
		int index = Math.max(0, Math.min(rank - 1, sortedHours.size() - 1));

		return sortedHours.get(index);
	}

	private static double round(double value) {
		return Math.round(value * 10.0) / 10.0;
	}
}
```

- [ ] **Step 5: Rodar e confirmar que passa**

Run: `cd backend && ./gradlew test --tests '*DurationStatsTest*'`
Expected: PASS

- [ ] **Step 6: Suite inteira e commit**

```bash
make backend-test
git add backend/src/main/java/br/org/fadex/helpdesk/ai/indicator backend/src/test/java/br/org/fadex/helpdesk/ai/indicator
git commit -m "feat(backend): adiciona calculo de media mediana e p90 de duracao"
```

---

## Task 9: Alvos de SLA por prioridade

**Nao depende da `V4`.**

**Files:**
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/indicator/SlaTarget.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/ai/indicator/SlaTargetTest.java`

**Interfaces:**
- Produces: `SlaTarget.forPriority(TicketPriority) -> SlaTarget`; `SlaTarget.getTargetHours() -> int`; `SlaTarget.evaluate(Duration elapsed, boolean closed) -> SlaOutcome` com `SlaOutcome` em `WITHIN`, `BREACHED`, `NOT_EVALUABLE`.

Regra de D6 do design: chamado aberto ainda dentro do alvo nao entra no denominador. Sem isso, todo chamado recem-criado contaria como violacao.

- [ ] **Step 1: Escrever os testes que falham**

```java
package br.org.fadex.helpdesk.ai.indicator;

import br.org.fadex.helpdesk.model.enums.TicketPriority;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SlaTargetTest {

	@Test
	void deveExporAlvosDoDocumentoDeFrentes() {
		assertThat(SlaTarget.forPriority(TicketPriority.ALTA).getTargetHours()).isEqualTo(4);
		assertThat(SlaTarget.forPriority(TicketPriority.MEDIA).getTargetHours()).isEqualTo(24);
		assertThat(SlaTarget.forPriority(TicketPriority.BAIXA).getTargetHours()).isEqualTo(72);
	}

	@Test
	void chamadoFechadoDentroDoAlvoCumpre() {
		SlaTarget target = SlaTarget.forPriority(TicketPriority.ALTA);

		assertThat(target.evaluate(Duration.ofHours(3), true)).isEqualTo(SlaOutcome.WITHIN);
	}

	@Test
	void chamadoFechadoForaDoAlvoViola() {
		SlaTarget target = SlaTarget.forPriority(TicketPriority.ALTA);

		assertThat(target.evaluate(Duration.ofHours(9), true)).isEqualTo(SlaOutcome.BREACHED);
	}

	@Test
	void chamadoAbertoAindaDentroDoAlvoFicaForaDoDenominador() {
		SlaTarget target = SlaTarget.forPriority(TicketPriority.MEDIA);

		assertThat(target.evaluate(Duration.ofHours(2), false)).isEqualTo(SlaOutcome.NOT_EVALUABLE);
	}

	@Test
	void chamadoAbertoJaEstouradoViola() {
		SlaTarget target = SlaTarget.forPriority(TicketPriority.MEDIA);

		assertThat(target.evaluate(Duration.ofHours(30), false)).isEqualTo(SlaOutcome.BREACHED);
	}

	@Test
	void limiteExatoCumpre() {
		SlaTarget target = SlaTarget.forPriority(TicketPriority.ALTA);

		assertThat(target.evaluate(Duration.ofHours(4), true)).isEqualTo(SlaOutcome.WITHIN);
	}
}
```

- [ ] **Step 2: Rodar e confirmar a falha**

Run: `cd backend && ./gradlew test --tests '*SlaTargetTest*'`
Expected: FAIL — `SlaTarget` nao existe

- [ ] **Step 3: Criar `SlaOutcome`**

```java
package br.org.fadex.helpdesk.ai.indicator;

public enum SlaOutcome {
	WITHIN,
	BREACHED,
	NOT_EVALUABLE
}
```

- [ ] **Step 4: Criar `SlaTarget`**

```java
package br.org.fadex.helpdesk.ai.indicator;

import br.org.fadex.helpdesk.model.enums.TicketPriority;

import java.time.Duration;

/**
 * Alvos de SLA por prioridade, como configuracao e nao como tabela (decisao D6 do design).
 */
public enum SlaTarget {

	ALTA(TicketPriority.ALTA, 4),
	MEDIA(TicketPriority.MEDIA, 24),
	BAIXA(TicketPriority.BAIXA, 72);

	private final TicketPriority priority;
	private final int targetHours;

	SlaTarget(TicketPriority priority, int targetHours) {
		this.priority = priority;
		this.targetHours = targetHours;
	}

	public static SlaTarget forPriority(TicketPriority priority) {
		for (SlaTarget target : values()) {
			if (target.priority == priority) {
				return target;
			}
		}

		throw new IllegalArgumentException("Prioridade sem alvo de SLA: " + priority);
	}

	public SlaOutcome evaluate(Duration elapsed, boolean closed) {
		boolean withinTarget = elapsed.toSeconds() <= Duration.ofHours(targetHours).toSeconds();

		if (closed) {
			return withinTarget ? SlaOutcome.WITHIN : SlaOutcome.BREACHED;
		}

		return withinTarget ? SlaOutcome.NOT_EVALUABLE : SlaOutcome.BREACHED;
	}

	public TicketPriority getPriority() {
		return priority;
	}

	public int getTargetHours() {
		return targetHours;
	}
}
```

- [ ] **Step 5: Rodar e confirmar que passa**

Run: `cd backend && ./gradlew test --tests '*SlaTargetTest*'`
Expected: PASS

- [ ] **Step 6: Suite inteira e commit**

```bash
make backend-test
git add backend/src/main/java/br/org/fadex/helpdesk/ai/indicator backend/src/test/java/br/org/fadex/helpdesk/ai/indicator
git commit -m "feat(backend): adiciona alvos de sla por prioridade"
```

---

## Task 10: Projecao de leitura dos indicadores

**Depende da `V4`** (colunas de tempo e de auditoria na projecao).

**Files:**
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/indicator/TicketIndicatorProjection.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/indicator/IndicatorRepository.java`

**Interfaces:**
- Produces: `IndicatorRepository.findAllProjections() -> List<TicketIndicatorProjection>`.
- Produces: `TicketIndicatorProjection(UUID ticketId, TicketStatus status, TicketPriority priority, TicketCategory category, ClassificationOrigin classificationOrigin, TicketCategory aiSuggestedCategory, TicketPriority aiSuggestedPriority, Double aiConfidence, UUID requesterId, String requesterName, UUID assigneeId, String assigneeName, LocalDateTime createdAt, LocalDateTime assignedAt, LocalDateTime firstResponseAt, LocalDateTime closedAt, LocalDateTime classificationReviewedAt)`.

Decisao D4 e D5 do design: repository proprio, somente leitura, e uma unica projecao com JPQL construtora. Nao carrega `title`, `description` nem `embedding` — campos pesados que nenhum indicador usa. `TicketRepository` da frente API nao e tocado.

- [ ] **Step 1: Criar a projecao**

```java
package br.org.fadex.helpdesk.ai.indicator;

import br.org.fadex.helpdesk.model.enums.ClassificationOrigin;
import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import br.org.fadex.helpdesk.model.enums.TicketStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record TicketIndicatorProjection(
		UUID ticketId,
		TicketStatus status,
		TicketPriority priority,
		TicketCategory category,
		ClassificationOrigin classificationOrigin,
		TicketCategory aiSuggestedCategory,
		TicketPriority aiSuggestedPriority,
		Double aiConfidence,
		UUID requesterId,
		String requesterName,
		UUID assigneeId,
		String assigneeName,
		LocalDateTime createdAt,
		LocalDateTime assignedAt,
		LocalDateTime firstResponseAt,
		LocalDateTime closedAt,
		LocalDateTime classificationReviewedAt
) {

	public boolean isOpen() {
		return status == TicketStatus.ABERTO || status == TicketStatus.EM_ANDAMENTO;
	}

	public boolean isClosed() {
		return closedAt != null;
	}

	public boolean hasSuggestion() {
		return aiSuggestedCategory != null && aiSuggestedPriority != null;
	}

	public boolean isReviewed() {
		return classificationReviewedAt != null;
	}
}
```

- [ ] **Step 2: Criar o repository de leitura**

```java
package br.org.fadex.helpdesk.ai.indicator;

import br.org.fadex.helpdesk.model.ticket.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

/**
 * Leitura agregada de chamados para os indicadores. SOMENTE LEITURA — nenhum metodo de escrita pode
 * ser adicionado aqui (decisao D4 do design). {@code TicketRepository} pertence a frente API e nao e
 * alterado por esta frente.
 */
public interface IndicatorRepository extends JpaRepository<Ticket, UUID> {

	@Query("""
			select new br.org.fadex.helpdesk.ai.indicator.TicketIndicatorProjection(
				ticket.id,
				ticket.status,
				ticket.priority,
				ticket.category,
				ticket.classificationOrigin,
				ticket.aiSuggestedCategory,
				ticket.aiSuggestedPriority,
				ticket.aiConfidence,
				requester.id,
				requester.name,
				assignee.id,
				assignee.name,
				ticket.createdAt,
				ticket.assignedAt,
				ticket.firstResponseAt,
				ticket.closedAt,
				ticket.classificationReviewedAt
			)
			from Ticket ticket
			join ticket.requester requester
			left join ticket.assignee assignee
			""")
	List<TicketIndicatorProjection> findAllProjections();
}
```

`requester.name` e `assignee.name` conferem com a entidade: `User` expoe `name` (verificado), e `UserMinDto` e exatamente `(UUID id, String name)`.

`left join` no responsavel porque `assignee_id` e nulavel; `join` no solicitante porque `requester_id` e `not null`. Trocar por `join` no responsavel esconderia todo chamado sem atribuicao dos indicadores.

- [ ] **Step 3: Compilar e commitar**

```bash
make backend-test
git add backend/src/main/java/br/org/fadex/helpdesk/ai/indicator
git commit -m "feat(backend): adiciona projecao de leitura para indicadores"
```

---

## Task 11: DTOs e service de indicadores

**Depende das Tasks 8, 9 e 10.**

**Files:**
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/indicator/IndicatorsDto.java` e demais DTOs listados no File Structure
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/indicator/IndicatorService.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/ai/indicator/IndicatorServiceTest.java`

**Interfaces:**
- Consumes: `IndicatorRepository.findAllProjections()`, `AiJobRepository.countByStatus(AiJobStatus)`, `TicketLinkRepository.count()`, `DurationStats.of(...)`, `SlaTarget`.
- Produces: `IndicatorService.getIndicators() -> IndicatorsDto`.

O relogio entra por `Clock` injetado, com `Clock.systemDefaultZone()` como bean padrao, para os testes de "hoje" e "semana" nao dependerem do instante em que rodam.

- [ ] **Step 1: Escrever os testes que falham**

```java
@Test
void deveContarChamadosPorStatusPrioridadeECategoria() {
	when(indicatorRepository.findAllProjections()).thenReturn(List.of(
			projecao(TicketStatus.ABERTO, TicketPriority.ALTA, TicketCategory.ACESSO),
			projecao(TicketStatus.ABERTO, TicketPriority.ALTA, TicketCategory.SISTEMAS),
			projecao(TicketStatus.FECHADO, TicketPriority.BAIXA, TicketCategory.ACESSO)
	));

	IndicatorsDto indicators = indicatorService.getIndicators();

	assertThat(indicators.overview().total()).isEqualTo(3);
	assertThat(indicators.overview().byStatus()).containsEntry(TicketStatus.ABERTO, 2L);
	assertThat(indicators.overview().byPriority()).containsEntry(TicketPriority.ALTA, 2L);
	assertThat(indicators.overview().byCategory()).containsEntry(TicketCategory.ACESSO, 2L);
}

@Test
void deveContarAltaPrioridadeEmAberto() {
	// duas ALTA abertas, uma ALTA fechada
	assertThat(indicatorService.getIndicators().overview().openHighPriority()).isEqualTo(2);
}

@Test
void deveClassificarAgingDoBacklogEmTresBuckets() {
	// chamados abertos com 6h, 40h e 200h de idade, relogio fixo
	BacklogAgingDto aging = indicatorService.getIndicators().durations().backlogAging();

	assertThat(aging.upToOneDay()).isEqualTo(1);
	assertThat(aging.oneToThreeDays()).isEqualTo(1);
	assertThat(aging.overThreeDays()).isEqualTo(1);
}

@Test
void deveCalcularConcordanciaComoSugestaoQueContinuaValendo() {
	// 1: sugeriu ACESSO/MEDIA, vale ACESSO/MEDIA, revisado    -> concorda (aceite)
	// 2: sugeriu ACESSO/MEDIA, vale RH/ALTA, revisado         -> discorda (corrigido)
	// 3: sugeriu RH/BAIXA, vale RH/BAIXA, revisado            -> concorda
	// 4: sugeriu SISTEMAS/ALTA, vale SISTEMAS/ALTA, NAO revisado -> fora do denominador
	// 5: sem sugestao, origem PENDENTE                        -> fora do denominador
	AgreementRateDto agreement = indicatorService.getIndicators().ai().agreementRate();

	assertThat(agreement.evaluated()).isEqualTo(3);
	assertThat(agreement.agreed()).isEqualTo(2);
	assertThat(agreement.percentage()).isEqualTo(66.7);
}

@Test
void naoDeveContarChamadoNaoRevisadoComoAceite() {
	// unico chamado: sugestao registrada, valores batem, classificationReviewedAt nulo
	AgreementRateDto agreement = indicatorService.getIndicators().ai().agreementRate();

	assertThat(agreement.evaluated()).isZero();
	assertThat(agreement.percentage()).isNull();
}

@Test
void deveIgnorarConfiancaNulaNaMedia() {
	// confidences 0.8, 0.6 e null
	assertThat(indicatorService.getIndicators().ai().averageConfidence()).isEqualTo(0.7);
}

@Test
void deveDevolverConfiancaMediaNulaQuandoNenhumChamadoTemSugestao() {
	assertThat(indicatorService.getIndicators().ai().averageConfidence()).isNull();
}

@Test
void deveExcluirDoDenominadorDeSlaChamadoAbertoAindaDentroDoAlvo() {
	// uma ALTA aberta ha 1h (alvo 4h) e uma ALTA fechada em 2h
	SlaIndicatorsDto sla = indicatorService.getIndicators().durations().sla();

	assertThat(sla.overall().evaluated()).isEqualTo(1);
	assertThat(sla.overall().withinTarget()).isEqualTo(1);
}

@Test
void deveAgruparCargaAbertaPorResponsavel() {
	WorkloadIndicatorsDto workload = indicatorService.getIndicators().workload();

	assertThat(workload.openByAssignee()).hasSize(2);
	assertThat(workload.openByAssignee().getFirst().openTickets()).isEqualTo(3);
}

@Test
void deveLimitarTopSolicitantesACinco() {
	// sete solicitantes distintos
	assertThat(indicatorService.getIndicators().workload().topRequesters()).hasSize(5);
}

@Test
void deveOmitirGrupoVazioEmVezDeZerar() {
	// nenhum chamado FINANCEIRO
	assertThat(indicatorService.getIndicators().overview().byCategory())
			.doesNotContainKey(TicketCategory.FINANCEIRO);
}
```

- [ ] **Step 2: Rodar e confirmar a falha**

Run: `cd backend && ./gradlew test --tests '*IndicatorServiceTest*'`
Expected: FAIL — `IndicatorService` nao existe

- [ ] **Step 3: Criar os DTOs do payload**

```java
public record IndicatorsDto(
		LocalDateTime generatedAt,
		OverviewIndicatorsDto overview,
		DurationIndicatorsDto durations,
		AiIndicatorsDto ai,
		WorkloadIndicatorsDto workload
) {
}

public record OverviewIndicatorsDto(
		long total,
		Map<TicketStatus, Long> byStatus,
		Map<TicketPriority, Long> byPriority,
		Map<TicketCategory, Long> byCategory,
		long openedToday,
		long closedToday,
		long openedThisWeek,
		long closedThisWeek,
		long openHighPriority
) {
}

public record DurationIndicatorsDto(
		DurationGroupDto closure,
		DurationGroupDto firstResponse,
		DurationGroupDto assignment,
		BacklogAgingDto backlogAging,
		Double oldestOpenTicketHours,
		SlaIndicatorsDto sla
) {
}

public record DurationGroupDto(
		DurationStatsDto overall,
		Map<TicketPriority, DurationStatsDto> byPriority,
		Map<TicketCategory, DurationStatsDto> byCategory
) {
}

public record BacklogAgingDto(long upToOneDay, long oneToThreeDays, long overThreeDays) {
}

public record SlaIndicatorsDto(SlaSliceDto overall, Map<TicketPriority, SlaSliceDto> byPriority) {
}

public record SlaSliceDto(long evaluated, long withinTarget, Double percentage) {
}

public record AiIndicatorsDto(
		AgreementRateDto agreementRate,
		Double averageConfidence,
		Map<ClassificationOrigin, Long> originDistribution,
		JobQueueIndicatorsDto jobQueue,
		long duplicatesDetected
) {
}

public record AgreementRateDto(long evaluated, long agreed, Double percentage) {
}

public record JobQueueIndicatorsDto(
		long pending,
		long processing,
		long failed,
		long done,
		Double averageQueueToDoneSeconds
) {
}

public record WorkloadIndicatorsDto(
		List<AssigneeLoadDto> openByAssignee,
		List<AssigneeClosureDto> closureTimeByAssignee,
		List<RequesterVolumeDto> topRequesters
) {
}

public record AssigneeLoadDto(UserMinDto user, long openTickets) {
}

public record AssigneeClosureDto(UserMinDto user, int sampleSize, Double averageHours, Double medianHours) {
}

public record RequesterVolumeDto(UserMinDto user, long tickets) {
}
```

Cada record no seu arquivo, no pacote `br.org.fadex.helpdesk.ai.indicator`, com os imports correspondentes.

- [ ] **Step 4: Criar `IndicatorService`**

Esqueleto obrigatorio, com variaveis intermediarias como exige o `backend/AGENTS.md`:

```java
@Service
public class IndicatorService {

	private static final int TOP_REQUESTERS_LIMIT = 5;

	private final IndicatorRepository indicatorRepository;
	private final AiJobRepository aiJobRepository;
	private final TicketLinkRepository ticketLinkRepository;
	private final Clock clock;

	// construtor com os quatro campos

	@Transactional(readOnly = true)
	public IndicatorsDto getIndicators() {
		LocalDateTime now = LocalDateTime.now(clock);
		List<TicketIndicatorProjection> projections = indicatorRepository.findAllProjections();

		OverviewIndicatorsDto overview = buildOverview(projections, now);
		DurationIndicatorsDto durations = buildDurations(projections, now);
		AiIndicatorsDto ai = buildAi(projections);
		WorkloadIndicatorsDto workload = buildWorkload(projections);

		IndicatorsDto response = new IndicatorsDto(now, overview, durations, ai, workload);

		return response;
	}
}
```

Regras que os metodos privados precisam respeitar:

- `byStatus`/`byPriority`/`byCategory`/`originDistribution`: `Collectors.groupingBy(..., Collectors.counting())`. Grupo sem ocorrencia nao aparece no mapa.
- `openedToday` / `closedToday`: comparam com `now.toLocalDate()`. `openedThisWeek` / `closedThisWeek`: a partir de `now.toLocalDate().with(DayOfWeek.MONDAY)`.
- `openHighPriority`: `priority == ALTA && projection.isOpen()`.
- `closure`: `Duration.between(createdAt, closedAt)` para `closedAt != null`.
- `firstResponse`: `Duration.between(createdAt, firstResponseAt)` para `firstResponseAt != null`.
- `assignment`: `Duration.between(createdAt, assignedAt)` para `assignedAt != null`.
- Cada um dos tres agrupa tambem por prioridade e por categoria, sempre via `DurationStats.of(...)`.
- `backlogAging`: so `isOpen()`; idade `Duration.between(createdAt, now)`; buckets `<= 24h`, `> 24h && <= 72h`, `> 72h`.
- `oldestOpenTicketHours`: maior idade entre os abertos; `null` se nao ha abertos.
- `sla`: para cada projecao, `SlaTarget.forPriority(priority).evaluate(elapsed, isClosed())`, onde `elapsed` e `createdAt -> closedAt` se fechado e `createdAt -> now` se aberto. `NOT_EVALUABLE` nao entra em `evaluated`. `percentage` e `null` quando `evaluated == 0`.
- `agreementRate`: denominador `hasSuggestion() && classificationReviewedAt != null`; concorda quando `category == aiSuggestedCategory && priority == aiSuggestedPriority`. `percentage` arredondado a uma casa, `null` quando `evaluated == 0`. Chamado nao revisado fica fora — e o ponto da saida A de D7.
- `averageConfidence`: media de `aiConfidence` nao nulos, arredondada a duas casas; `null` se nao ha nenhum.
- `jobQueue`: `aiJobRepository.countByStatus(...)` para os quatro status. O tempo sai como `averageQueueToDoneSeconds` — media de `updatedAt - createdAt` nos jobs `DONE`. O nome diz o que o numero e: tempo de fila somado ao de execucao, nao tempo puro de processamento. `AiJob` nao guarda instante de inicio, entao "tempo de processamento" seria mentira; um numero honesto com nome preciso vale mais que um `null`, que o avaliador le como funcionalidade faltando. `null` apenas quando nao ha nenhum job `DONE`. Exige `AiJobRepository.findByStatus(AiJobStatus)` ou uma projecao equivalente.
- `duplicatesDetected`: `ticketLinkRepository.count()`.
- `openByAssignee`: so `isOpen()` e `assigneeId != null`; ordenado desc por quantidade.
- `closureTimeByAssignee`: so fechados com `assigneeId != null`; usa `DurationStats.of(...)` por responsavel.
- `topRequesters`: agrupa por `requesterId`, ordena desc, limita a `TOP_REQUESTERS_LIMIT`.

- [ ] **Step 5: Registrar o bean de `Clock`**

Em `config/`, ou como `@Bean` no proprio pacote de indicadores:

```java
@Bean
public Clock clock() {
	return Clock.systemDefaultZone();
}
```

Verificar antes se ja existe um bean de `Clock` no projeto; se existir, reutilizar em vez de declarar outro.

- [ ] **Step 6: Rodar e confirmar que passa**

Run: `cd backend && ./gradlew test --tests '*IndicatorServiceTest*'`
Expected: PASS

- [ ] **Step 7: Suite inteira e commit**

```bash
make backend-test
git add backend/src/main/java/br/org/fadex/helpdesk/ai/indicator backend/src/test/java/br/org/fadex/helpdesk/ai/indicator
git commit -m "feat(backend): adiciona calculo das quatro camadas de indicadores"
```

---

## Task 12: Endpoint de indicadores

**Depende da Task 11.**

**Files:**
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/indicator/IndicatorController.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/ai/indicator/IndicatorControllerTest.java`

**Interfaces:**
- Produces: `GET /api/v1/indicators -> IndicatorsDto`.

- [ ] **Step 1: Escrever o teste que falha**

```java
@Test
void deveDevolverIndicadores() {
	IndicatorsDto indicators = new IndicatorsDto(LocalDateTime.now(), null, null, null, null);
	when(indicatorService.getIndicators()).thenReturn(indicators);

	ResponseEntity<IndicatorsDto> response = indicatorController.getIndicators();

	assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	assertThat(response.getBody()).isSameAs(indicators);
}
```

- [ ] **Step 2: Rodar e confirmar a falha**

Run: `cd backend && ./gradlew test --tests '*IndicatorControllerTest*'`
Expected: FAIL — `IndicatorController` nao existe

- [ ] **Step 3: Criar o controller**

```java
package br.org.fadex.helpdesk.ai.indicator;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/indicators")
@PreAuthorize("hasRole('ADMIN')")
public class IndicatorController {

	private final IndicatorService indicatorService;

	public IndicatorController(IndicatorService indicatorService) {
		this.indicatorService = indicatorService;
	}

	@GetMapping
	public ResponseEntity<IndicatorsDto> getIndicators() {
		IndicatorsDto indicators = indicatorService.getIndicators();

		return ResponseEntity.ok(indicators);
	}
}
```

- [ ] **Step 4: Rodar e confirmar que passa**

Run: `cd backend && ./gradlew test --tests '*IndicatorControllerTest*'`
Expected: PASS

- [ ] **Step 5: Suite inteira e commit**

```bash
make backend-test
git add backend/src/main/java/br/org/fadex/helpdesk/ai/indicator/IndicatorController.java backend/src/test/java/br/org/fadex/helpdesk/ai/indicator/IndicatorControllerTest.java
git commit -m "feat(backend): expoe endpoint de indicadores para admin"
```

---

## Task 13: Similaridade de cosseno sobre embeddings

**Item de linha de corte — cai antes dos indicadores.** Nao depende da `V4`.

**Files:**
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/duplicate/EmbeddingSimilarity.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/ai/duplicate/EmbeddingSimilarityTest.java`

**Interfaces:**
- Produces: `EmbeddingSimilarity.parse(String literal) -> List<Double>`; `EmbeddingSimilarity.cosine(List<Double> left, List<Double> right) -> double`.

Decisao D8 do design: cosseno em Java, nao `<=>` no Postgres, porque o H2 dos testes mapeia a coluna de embedding para `varchar(20000)` e nao roda operador vetorial.

- [ ] **Step 1: Escrever os testes que falham**

```java
package br.org.fadex.helpdesk.ai.duplicate;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class EmbeddingSimilarityTest {

	@Test
	void deveFazerParseDoLiteralPgvector() {
		List<Double> values = EmbeddingSimilarity.parse("[0.1,0.2,0.3]");

		assertThat(values).containsExactly(0.1, 0.2, 0.3);
	}

	@Test
	void deveFazerParseDeLiteralComEspacos() {
		List<Double> values = EmbeddingSimilarity.parse("[0.1, 0.2, 0.3]");

		assertThat(values).containsExactly(0.1, 0.2, 0.3);
	}

	@Test
	void vetoresIdenticosTemCossenoUm() {
		List<Double> vector = List.of(1.0, 2.0, 3.0);

		assertThat(EmbeddingSimilarity.cosine(vector, vector)).isCloseTo(1.0, within(1e-9));
	}

	@Test
	void vetoresOrtogonaisTemCossenoZero() {
		assertThat(EmbeddingSimilarity.cosine(List.of(1.0, 0.0), List.of(0.0, 1.0)))
				.isCloseTo(0.0, within(1e-9));
	}

	@Test
	void vetoresOpostosTemCossenoMenosUm() {
		assertThat(EmbeddingSimilarity.cosine(List.of(1.0, 0.0), List.of(-1.0, 0.0)))
				.isCloseTo(-1.0, within(1e-9));
	}

	@Test
	void vetorNuloTemCossenoZero() {
		assertThat(EmbeddingSimilarity.cosine(List.of(0.0, 0.0), List.of(1.0, 1.0))).isZero();
	}

	@Test
	void deveRejeitarVetoresDeTamanhosDiferentes() {
		assertThatThrownBy(() -> EmbeddingSimilarity.cosine(List.of(1.0), List.of(1.0, 2.0)))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
```

- [ ] **Step 2: Rodar e confirmar a falha**

Run: `cd backend && ./gradlew test --tests '*EmbeddingSimilarityTest*'`
Expected: FAIL — `EmbeddingSimilarity` nao existe

- [ ] **Step 3: Criar a classe**

```java
package br.org.fadex.helpdesk.ai.duplicate;

import java.util.Arrays;
import java.util.List;

public abstract class EmbeddingSimilarity {

	private EmbeddingSimilarity() {
	}

	public static List<Double> parse(String literal) {
		if (literal == null || literal.isBlank()) {
			return List.of();
		}

		String content = literal.trim().replace("[", "").replace("]", "");
		if (content.isBlank()) {
			return List.of();
		}

		return Arrays.stream(content.split(","))
				.map(String::trim)
				.map(Double::valueOf)
				.toList();
	}

	public static double cosine(List<Double> left, List<Double> right) {
		if (left.size() != right.size()) {
			throw new IllegalArgumentException("Vetores devem ter o mesmo tamanho.");
		}

		double dotProduct = 0.0;
		double leftNorm = 0.0;
		double rightNorm = 0.0;

		for (int index = 0; index < left.size(); index++) {
			double leftValue = left.get(index);
			double rightValue = right.get(index);
			dotProduct += leftValue * rightValue;
			leftNorm += leftValue * leftValue;
			rightNorm += rightValue * rightValue;
		}

		if (leftNorm == 0.0 || rightNorm == 0.0) {
			return 0.0;
		}

		return dotProduct / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
	}
}
```

- [ ] **Step 4: Rodar e confirmar que passa**

Run: `cd backend && ./gradlew test --tests '*EmbeddingSimilarityTest*'`
Expected: PASS

- [ ] **Step 5: Suite inteira e commit**

```bash
make backend-test
git add backend/src/main/java/br/org/fadex/helpdesk/ai/duplicate backend/src/test/java/br/org/fadex/helpdesk/ai/duplicate
git commit -m "feat(backend): adiciona similaridade de cosseno sobre embeddings"
```

---

## Task 14: Deteccao de duplicados gravando em ticket_links

**Item de linha de corte.** Depende da Task 13.

**Files:**
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/duplicate/DuplicateCandidate.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/duplicate/DuplicateEmbeddingRepository.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/duplicate/DuplicateDetectionService.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/ai/job/AiJobWorker.java`
- Modify: `backend/src/main/resources/application.properties`
- Modify: `backend/src/main/resources/application-test.properties`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/ai/duplicate/DuplicateDetectionServiceTest.java`

**Interfaces:**
- Consumes: `EmbeddingSimilarity`, `TicketLinkRepository.existsBySourceTicketIdAndTargetTicketId(UUID, UUID)`, `TicketLinkRepository.save(TicketLink)`.
- Produces: `DuplicateDetectionService.detect(UUID ticketId) -> int` (quantidade de vinculos criados).

`TicketLink(Ticket source, Ticket target, User createdBy)` exige `createdBy` nao nulo (constraint da `V3`). Nao existe usuario de sistema, entao usa-se o solicitante do chamado de origem — decisao D8 do design.

- [ ] **Step 1: Escrever os testes que falham**

```java
@Test
void deveCriarVinculoAcimaDoLimiar() {
	// candidato com cosseno 0.95, limiar 0.90
	int created = service.detect(ticketId);

	assertThat(created).isEqualTo(1);
	verify(ticketLinkRepository).save(any(TicketLink.class));
}

@Test
void naoDeveCriarVinculoAbaixoDoLimiar() {
	// candidato com cosseno 0.40
	int created = service.detect(ticketId);

	assertThat(created).isZero();
	verify(ticketLinkRepository, never()).save(any());
}

@Test
void naoDeveVincularOChamadoAEleMesmo() {
	// candidatos incluem o proprio ticketId
	service.detect(ticketId);

	verify(ticketLinkRepository, never()).save(argThat(
			link -> link.getTargetTicket().getId().equals(ticketId)
	));
}

@Test
void deveRespeitarOMaximoDeVinculosPorChamado() {
	// cinco candidatos acima do limiar, maximo 3
	assertThat(service.detect(ticketId)).isEqualTo(3);
}

@Test
void naoDeveDuplicarVinculoJaExistente() {
	when(ticketLinkRepository.existsBySourceTicketIdAndTargetTicketId(any(), any())).thenReturn(true);

	assertThat(service.detect(ticketId)).isZero();
}

@Test
void deveIgnorarChamadoSemEmbedding() {
	// chamado de origem sem embedding gravado
	assertThat(service.detect(ticketId)).isZero();
	verify(ticketLinkRepository, never()).save(any());
}
```

- [ ] **Step 2: Rodar e confirmar a falha**

Run: `cd backend && ./gradlew test --tests '*DuplicateDetectionServiceTest*'`
Expected: FAIL — `DuplicateDetectionService` nao existe

- [ ] **Step 3: Criar `DuplicateCandidate` e a query de candidatos**

```java
package br.org.fadex.helpdesk.ai.duplicate;

import java.util.UUID;

public record DuplicateCandidate(UUID ticketId, String embedding) {
}
```

Criar tambem `backend/src/main/java/br/org/fadex/helpdesk/ai/duplicate/DuplicateEmbeddingRepository.java`, `interface DuplicateEmbeddingRepository extends JpaRepository<Ticket, UUID>`, somente leitura, com:

```java
@Query(value = """
		select cast(id as varchar) as ticket_id, cast(embedding as varchar) as embedding
		from tickets
		where embedding is not null
		""", nativeQuery = true)
List<Object[]> findEmbeddedTickets();
```

`cast(... as varchar)` mantem a query identica em Postgres (onde a coluna e `vector`) e em H2 (onde e `varchar`). Sem o cast, o driver do Postgres nao converte `vector` para `String`.

- [ ] **Step 4: Criar `DuplicateDetectionService`**

Regras que a implementacao precisa seguir:

- Le o embedding do proprio chamado; se nao houver, retorna `0` sem tocar em nada.
- Percorre os candidatos, pula o proprio `ticketId`, calcula `EmbeddingSimilarity.cosine(...)`.
- Ordena os que passam do limiar por similaridade desc, corta em `maxLinks`.
- Para cada um, checa `existsBySourceTicketIdAndTargetTicketId` antes de salvar.
- `createdBy` e o solicitante do chamado de origem.
- Retorna a quantidade de vinculos criados.
- Nunca altera status, prioridade ou categoria de nenhum chamado. Duplicado e sinal, nao regra.

Propriedades, com os mesmos valores nos dois `application*.properties`:

```properties
app.ai.duplicate.enabled=${AI_DUPLICATE_ENABLED:true}
app.ai.duplicate.similarity-threshold=${AI_DUPLICATE_THRESHOLD:0.90}
app.ai.duplicate.max-links=${AI_DUPLICATE_MAX_LINKS:3}
```

- [ ] **Step 5: Ligar no worker**

Em `AiJobWorker.processEmbedding`, depois de `ticketEmbeddingRepository.updateEmbedding(...)`:

```java
	duplicateDetectionService.detect(ticket.getId());
```

A deteccao roda dentro do `try` do `process(...)`: se falhar, o job ja e marcado como falho e reagendado pelo caminho de erro existente.

- [ ] **Step 6: Rodar e confirmar que passa**

Run: `cd backend && ./gradlew test --tests '*DuplicateDetectionServiceTest*'`
Expected: PASS

- [ ] **Step 7: Suite inteira e commit**

```bash
make backend-test
git add backend/src/main/java/br/org/fadex/helpdesk/ai backend/src/test/java/br/org/fadex/helpdesk/ai backend/src/main/resources
git commit -m "feat(backend): detecta chamados duplicados por embedding"
```

---

## Task 15: Documentacao do contrato

**Files:**
- Modify: `docs/backend/api.md`
- Modify: `docs/projeto/acompanhamento-desenvolvimento.md`

**Interfaces:**
- Consumes: contratos das Tasks 3, 7, 12.

`docs/backend/api.md` e editado pelas tres frentes. Escrever em secao propria, no fim do arquivo, para o conflito ficar trivial.

- [ ] **Step 1: Documentar os endpoints**

Adicionar uma secao "Frente IA" ao `api.md` com:

- `PATCH /api/v1/tickets/{id}/classification` — corpo, regra de origem `IA` no aceite e `MANUAL` na correcao, `403` e `404`.
- `GET /api/v1/ai/jobs` — filtros `status`, `type`, `ticketId`, paginacao padrao.
- `POST /api/v1/ai/jobs/{id}/retry` — `409` quando o job nao esta `FAILED`.
- `GET /api/v1/indicators` — payload completo, com as regras de leitura do design: `sampleSize` sempre presente, nulos em vez de zeros, e por que o tempo da fila se chama `averageQueueToDoneSeconds` e nao "tempo de processamento".
- Campos novos do `TicketDto`.
- Tabela dos eventos SSE disparados por esta frente.

- [ ] **Step 2: Atualizar o acompanhamento**

Marcar como concluidos os itens desta frente em `docs/projeto/acompanhamento-desenvolvimento.md`. Nao mexer em itens de outras frentes.

- [ ] **Step 3: Commit**

```bash
make backend-test
git add docs
git commit -m "docs(ia): documenta contratos de revisao de classificacao indicadores e jobs"
```

---

## Ordem sob a linha de corte

O prazo e 15/08/2026 as 12h. Se o tempo apertar, corta de baixo para cima:

| Ordem de corte | Tarefas | Motivo |
| --- | --- | --- |
| 1o a cair | Tasks 13-14 (duplicados) | Diferencial, nao requisito |
| 2o a cair | Tasks 2-3 (`/ai/jobs`) | Operacao, nao requisito |
| 3o a cair | p90 dentro da Task 8 | Media e mediana bastam |
| 4o a cair | Camada 4 dentro da Task 11 | Estatistica por responsavel |
| 5o a cair | SLA (Task 9 + fatia da 11) | Ultimo item cortavel |
| **Nunca corta** | Tasks 4-bis, 5-7, 10-12 | Revisao de classificacao e indicadores sao obrigatorios do desafio |

Tasks 1, 8, 9, 10 nao dependem da `V4` e podem ser executadas antes dela chegar em `dev`; Tasks 2 e 3 tambem. Tasks 4-bis, 5, 6, 7, 11 e 12 esperam a `V4`.

## Self-Review

**Cobertura do design.** D1 -> Task 5 (via `Ticket.applyAiSuggestion`). D2 -> Task 5. D3 -> Task 1. D4 -> Task 10. D5 -> Tasks 10-11. D6 -> Task 9. D7 -> Tasks 4-bis, 7 e 11. D8 -> Tasks 13-14. Contratos: `PATCH classification` -> Task 7; `TicketDto` -> Task 6; `GET /ai/jobs` e retry -> Tasks 2-3; `GET /indicators` -> Tasks 11-12; eventos SSE -> Tasks 5 e 7; documentacao -> Task 15.

**Consistencia de tipos.** `DurationStatsDto` (Task 8) e consumido por `DurationGroupDto` e `AssigneeClosureDto` (Task 11). `SlaOutcome` (Task 9) e consumido pelo calculo de `SlaSliceDto` (Task 11). `TicketIndicatorProjection` (Task 10) alimenta os quatro construtores da Task 11. `AiNotificationEventName` (Task 1) e usado nas Tasks 5 e 7. `AiJobFilter`/`AiJobFields` (Task 2) sao usados na Task 3.

**Lacuna assumida.** `AiJob` nao registra o instante em que o processamento comecou, entao tempo puro de execucao nao e calculavel sem coluna nova. Em vez de devolver `null`, a Task 11 expoe `averageQueueToDoneSeconds` (`updatedAt - createdAt` dos jobs `DONE`), cujo nome declara que fila e execucao estao somadas. Documentado no `api.md` na Task 15.

**D7 resolvido (saida A).** A `V5` entra como Task 4-bis, o carimbo fica na Task 7 e o denominador da Task 11 filtra por `classificationReviewedAt != null`. O campo do payload e `agreementRate`.

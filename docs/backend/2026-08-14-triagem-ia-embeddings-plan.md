# Triagem IA e Embeddings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar triagem automatica assincrona com IA local, embeddings em pgvector, busca de chamados similares, vinculos persistidos entre chamados e observabilidade ADMIN da fila de IA.

**Architecture:** A criacao de chamado continua rapida: salva o chamado como `PENDENTE`, cria jobs em `ai_jobs` e retorna sem chamar o modelo local. Quartz consome a fila persistida com baixa concorrencia; Ollama fornece classificacao e embeddings; pgvector armazena vetores e calcula similares por distancia cosseno. Funcionalidades de ticket ficam no dominio de chamados, enquanto integracao, fila e worker ficam no boundary `br.org.fadex.helpdesk.ai`.

**Tech Stack:** Java 21, Spring Boot 4.1.0, Spring MVC, Spring Security, Spring Data JPA, Flyway, Quartz, PostgreSQL 17 com pgvector, H2 em testes com Flyway placeholders, Docker Compose, Ollama.

## Global Constraints

- Documentacao desta feature fica em `docs/backend`, `docs/configuracao`, `docs/projeto` e `README.md`, organizada por dominio.
- Mensagens de commit devem ficar em portugues.
- A branch de trabalho e `feature(backend)/triagem-ia`.
- Criacao de chamado nunca chama Ollama de forma sincrona.
- `AI_TRIAGE_ENABLED=false` e o default seguro para desenvolvimento sem Ollama.
- Worker deve iniciar com `AI_WORKER_BATCH_SIZE=1` e `QUARTZ_THREAD_COUNT=1`.
- Embedding padrao usa `AI_EMBEDDING_MODEL=all-minilm` com `AI_EMBEDDING_DIMENSIONS=384`.
- Banco Docker usa pgvector real; testes H2 usam placeholders Flyway para substituir `vector(384)` por tipo textual compativel.
- Nao adicionar RabbitMQ, Kafka, API externa de IA, treinamento de modelo, entidade de incidente ou bloqueio de criacao por duplicidade.
- Services devem manter variaveis intermediarias e evitar concentrar chamadas diretamente no `return`.

---

## File Structure

- Modify `backend/build.gradle`: adicionar Quartz; usar `RestClient` ja disponivel pela stack Web MVC.
- Modify `docker-compose.yml`: remover `container_name`, parametrizar project name, trocar Postgres para pgvector e adicionar `ollama` e `ollama-models`.
- Modify `.env.example`: adicionar portas, compose project e variaveis IA.
- Modify `backend/.env.example`: adicionar variaveis IA, worker e Quartz.
- Modify `backend/src/main/resources/application.properties`: mapear propriedades IA, Quartz e Flyway placeholders.
- Modify `backend/src/main/resources/application-test.properties`: definir placeholders H2 para coluna de embedding.
- Create `backend/src/main/resources/db/migration/V2__add_ai_triage_embeddings.sql`: schema de IA, links e colunas novas.
- Modify `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/Ticket.java`: campos de justificativa e metodos de atualizacao.
- Modify `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketDto.java`: incluir `classificationJustification`.
- Modify `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketMapper.java`: mapear justificativa.
- Create `backend/src/main/java/br/org/fadex/helpdesk/ai/AiIntegrationException.java`: excecao de infraestrutura de IA.
- Create `backend/src/main/java/br/org/fadex/helpdesk/ai/model/TicketClassification.java`: resultado de classificacao.
- Create `backend/src/main/java/br/org/fadex/helpdesk/ai/model/TicketEmbedding.java`: vetor validado.
- Create `backend/src/main/java/br/org/fadex/helpdesk/ai/client/AiTriageClient.java`: contrato para classificacao.
- Create `backend/src/main/java/br/org/fadex/helpdesk/ai/client/AiEmbeddingClient.java`: contrato para embeddings.
- Create `backend/src/main/java/br/org/fadex/helpdesk/ai/client/LocalAiTriageClient.java`: cliente Ollama para classificacao.
- Create `backend/src/main/java/br/org/fadex/helpdesk/ai/client/LocalAiEmbeddingClient.java`: cliente Ollama para embeddings.
- Create `backend/src/main/java/br/org/fadex/helpdesk/ai/triage/FallbackTicketClassifier.java`: fallback deterministico.
- Create `backend/src/main/java/br/org/fadex/helpdesk/ai/job/AiJob.java`: entidade da fila.
- Create `backend/src/main/java/br/org/fadex/helpdesk/ai/job/AiJobType.java`: enum `CLASSIFICATION`, `EMBEDDING`.
- Create `backend/src/main/java/br/org/fadex/helpdesk/ai/job/AiJobStatus.java`: enum `PENDING`, `PROCESSING`, `DONE`, `FAILED`.
- Create `backend/src/main/java/br/org/fadex/helpdesk/ai/job/AiJobDto.java`: DTO de item da fila.
- Create `backend/src/main/java/br/org/fadex/helpdesk/ai/job/AiJobSummaryDto.java`: contadores por status.
- Create `backend/src/main/java/br/org/fadex/helpdesk/ai/job/AiJobFilter.java`: filtros admin.
- Create `backend/src/main/java/br/org/fadex/helpdesk/ai/job/AiJobFields.java`: constantes de criteria.
- Create `backend/src/main/java/br/org/fadex/helpdesk/ai/job/AiJobMapper.java`: conversao entity -> DTO.
- Create `backend/src/main/java/br/org/fadex/helpdesk/ai/job/AiJobRepository.java`: JPA repository.
- Create `backend/src/main/java/br/org/fadex/helpdesk/ai/job/AiJobSpecification.java`: filtros dinamicos.
- Create `backend/src/main/java/br/org/fadex/helpdesk/ai/job/AiJobService.java`: cria, busca, marca e retenta jobs.
- Create `backend/src/main/java/br/org/fadex/helpdesk/ai/job/AiJobWorker.java`: worker Quartz.
- Create `backend/src/main/java/br/org/fadex/helpdesk/ai/job/AiJobController.java`: endpoints ADMIN.
- Create `backend/src/main/java/br/org/fadex/helpdesk/repository/TicketEmbeddingRepository.java`: queries nativas pgvector.
- Create `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketSimilarityDto.java`: item similar.
- Create `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketStatusGroup.java`: `active`, `closed`, `all`.
- Create `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketClassificationUpdateDto.java`: request de revisao.
- Create `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketLink.java`: entidade de vinculo.
- Create `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketLinkCreationDto.java`: request de vinculo.
- Create `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketLinkDto.java`: resposta de vinculo.
- Create `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketLinkMapper.java`: conversao de link.
- Create `backend/src/main/java/br/org/fadex/helpdesk/repository/TicketLinkRepository.java`: repository de vinculos.
- Create `backend/src/main/java/br/org/fadex/helpdesk/service/TicketSimilarityService.java`: busca de similares.
- Create `backend/src/main/java/br/org/fadex/helpdesk/service/TicketLinkService.java`: gerencia vinculos.
- Modify `backend/src/main/java/br/org/fadex/helpdesk/controller/TicketController.java`: endpoints de classificacao, similares e links.
- Modify `docs/backend/api.md`: documentar campos e endpoints.
- Modify `docs/configuracao/env.md`: documentar pgvector, Ollama e stacks por worktree.
- Modify `docs/projeto/acompanhamento-desenvolvimento.md`: atualizar status.
- Modify `README.md`: justificar IA local, fallback e execucao.

### Task 1: Infraestrutura, Dependencias e Migracao pgvector

**Files:**
- Modify: `backend/build.gradle`
- Modify: `docker-compose.yml`
- Modify: `.env.example`
- Modify: `backend/.env.example`
- Modify: `backend/src/main/resources/application.properties`
- Modify: `backend/src/main/resources/application-test.properties`
- Create: `backend/src/main/resources/db/migration/V2__add_ai_triage_embeddings.sql`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/config/ApplicationPropertiesTest.java`

**Interfaces:**
- Produces: properties `app.ai.*`, Flyway placeholders `ticket.embedding-column-type`, Quartz thread config.
- Produces: tables/columns `tickets.classification_justification`, `tickets.embedding`, `tickets.embedding_model`, `tickets.embedding_updated_at`, `ai_jobs`, `ticket_links`.

- [ ] **Step 1: Write failing property assertions**

Add to `ApplicationPropertiesTest.deveCarregarProfileDeTesteComBancoEmMemoria()`:

```java
assertThat(environment.getProperty("app.ai.triage.enabled")).isEqualTo("false");
assertThat(environment.getProperty("app.ai.base-url")).isEqualTo("http://localhost:11434");
assertThat(environment.getProperty("app.ai.embedding-dimensions")).isEqualTo("384");
assertThat(environment.getProperty("app.ai.worker.batch-size")).isEqualTo("1");
assertThat(environment.getProperty("spring.quartz.properties.org.quartz.threadPool.threadCount")).isEqualTo("1");
```

- [ ] **Step 2: Run property test and verify failure**

Run: `cd backend && ./gradlew test --tests br.org.fadex.helpdesk.config.ApplicationPropertiesTest`

Expected: FAIL because AI and Quartz properties do not exist.

- [ ] **Step 3: Add dependencies**

Modify `backend/build.gradle`:

```gradle
implementation 'org.springframework.boot:spring-boot-starter-quartz'
```

Use Spring Framework `RestClient` from the existing Spring Web MVC stack for Ollama HTTP calls; do not add WebFlux.

- [ ] **Step 4: Add properties**

Add to `backend/src/main/resources/application.properties`:

```properties
app.ai.triage.enabled=${AI_TRIAGE_ENABLED:false}
app.ai.base-url=${AI_BASE_URL:http://localhost:11434}
app.ai.classification-model=${AI_CLASSIFICATION_MODEL:llama3.2:1b}
app.ai.embedding-model=${AI_EMBEDDING_MODEL:all-minilm}
app.ai.embedding-dimensions=${AI_EMBEDDING_DIMENSIONS:384}
app.ai.similarity.threshold=${AI_SIMILARITY_THRESHOLD:0.75}
app.ai.similarity.limit=${AI_SIMILARITY_LIMIT:5}
app.ai.worker.enabled=${AI_WORKER_ENABLED:true}
app.ai.worker.batch-size=${AI_WORKER_BATCH_SIZE:1}
app.ai.worker.max-attempts=${AI_WORKER_MAX_ATTEMPTS:3}
app.ai.worker.request-timeout-seconds=${AI_REQUEST_TIMEOUT_SECONDS:20}

spring.quartz.properties.org.quartz.threadPool.threadCount=${QUARTZ_THREAD_COUNT:1}

spring.flyway.placeholders.pgvector-extension=CREATE EXTENSION IF NOT EXISTS vector
spring.flyway.placeholders.ticket-embedding-column-type=vector(384)
spring.flyway.placeholders.ticket-embedding-index=CREATE INDEX idx_tickets_embedding_hnsw ON tickets USING hnsw (embedding vector_cosine_ops) WHERE embedding IS NOT NULL
```

Add to `backend/src/main/resources/application-test.properties`:

```properties
spring.flyway.placeholders.pgvector-extension=-- pgvector disabled in H2 tests
spring.flyway.placeholders.ticket-embedding-column-type=varchar(20000)
spring.flyway.placeholders.ticket-embedding-index=-- pgvector index disabled in H2 tests
```

- [ ] **Step 5: Add env examples**

Add to `.env.example` and `backend/.env.example`:

```env
AI_TRIAGE_ENABLED=false
AI_BASE_URL=http://localhost:11434
AI_CLASSIFICATION_MODEL=llama3.2:1b
AI_EMBEDDING_MODEL=all-minilm
AI_EMBEDDING_DIMENSIONS=384
AI_WORKER_ENABLED=true
AI_WORKER_BATCH_SIZE=1
AI_WORKER_MAX_ATTEMPTS=3
AI_REQUEST_TIMEOUT_SECONDS=20
AI_SIMILARITY_THRESHOLD=0.75
AI_SIMILARITY_LIMIT=5
QUARTZ_THREAD_COUNT=1
OLLAMA_PORT=11434
COMPOSE_PROJECT_NAME=fadex-helpdesk
```

- [ ] **Step 6: Update Docker Compose for parallel stacks and pgvector**

Modify `docker-compose.yml`:

```yaml
name: ${COMPOSE_PROJECT_NAME:-fadex-helpdesk}
```

For `postgres`, use:

```yaml
image: pgvector/pgvector:pg17
```

Remove every fixed `container_name`.

Add `ollama` and `ollama-models`:

```yaml
  ollama:
    image: ollama/ollama:latest
    restart: unless-stopped
    ports:
      - "${OLLAMA_PORT:-11434}:11434"
    volumes:
      - ollama-data:/root/.ollama

  ollama-models:
    image: ollama/ollama:latest
    restart: "no"
    depends_on:
      ollama:
        condition: service_started
    environment:
      OLLAMA_HOST: http://ollama:11434
      AI_CLASSIFICATION_MODEL: ${AI_CLASSIFICATION_MODEL:-llama3.2:1b}
      AI_EMBEDDING_MODEL: ${AI_EMBEDDING_MODEL:-all-minilm}
    entrypoint: ["/bin/sh", "-c"]
    command: >
      "ollama pull $$AI_CLASSIFICATION_MODEL &&
       ollama pull $$AI_EMBEDDING_MODEL"
```

Add `ollama-data:` under `volumes`.

Add backend env:

```yaml
      AI_TRIAGE_ENABLED: ${AI_TRIAGE_ENABLED:-false}
      AI_BASE_URL: ${AI_BASE_URL:-http://ollama:11434}
      AI_CLASSIFICATION_MODEL: ${AI_CLASSIFICATION_MODEL:-llama3.2:1b}
      AI_EMBEDDING_MODEL: ${AI_EMBEDDING_MODEL:-all-minilm}
      AI_EMBEDDING_DIMENSIONS: ${AI_EMBEDDING_DIMENSIONS:-384}
      AI_WORKER_ENABLED: ${AI_WORKER_ENABLED:-true}
      AI_WORKER_BATCH_SIZE: ${AI_WORKER_BATCH_SIZE:-1}
      AI_WORKER_MAX_ATTEMPTS: ${AI_WORKER_MAX_ATTEMPTS:-3}
      AI_REQUEST_TIMEOUT_SECONDS: ${AI_REQUEST_TIMEOUT_SECONDS:-20}
      AI_SIMILARITY_THRESHOLD: ${AI_SIMILARITY_THRESHOLD:-0.75}
      AI_SIMILARITY_LIMIT: ${AI_SIMILARITY_LIMIT:-5}
      QUARTZ_THREAD_COUNT: ${QUARTZ_THREAD_COUNT:-1}
```

- [ ] **Step 7: Create migration**

Create `backend/src/main/resources/db/migration/V2__add_ai_triage_embeddings.sql`:

```sql
${pgvector-extension};

alter table tickets
    add column classification_justification text,
    add column embedding ${ticket-embedding-column-type},
    add column embedding_model varchar(120),
    add column embedding_updated_at timestamp;

create table ai_jobs (
    id uuid primary key,
    ticket_id uuid not null,
    type varchar(30) not null,
    status varchar(30) not null,
    attempts integer not null,
    next_attempt_at timestamp not null,
    last_error text,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint fk_ai_jobs_ticket foreign key (ticket_id) references tickets (id),
    constraint ck_ai_jobs_type check (type in ('CLASSIFICATION', 'EMBEDDING')),
    constraint ck_ai_jobs_status check (status in ('PENDING', 'PROCESSING', 'DONE', 'FAILED')),
    constraint ck_ai_jobs_attempts_non_negative check (attempts >= 0)
);

create table ticket_links (
    id uuid primary key,
    source_ticket_id uuid not null,
    target_ticket_id uuid not null,
    created_by uuid not null,
    created_at timestamp not null,
    constraint fk_ticket_links_source foreign key (source_ticket_id) references tickets (id),
    constraint fk_ticket_links_target foreign key (target_ticket_id) references tickets (id),
    constraint fk_ticket_links_created_by foreign key (created_by) references users (id),
    constraint ck_ticket_links_distinct check (source_ticket_id <> target_ticket_id),
    constraint uk_ticket_links_pair unique (source_ticket_id, target_ticket_id)
);

create index idx_ai_jobs_status_next_attempt_at on ai_jobs (status, next_attempt_at);
create index idx_ai_jobs_ticket_id on ai_jobs (ticket_id);
create index idx_ticket_links_source on ticket_links (source_ticket_id);
create index idx_ticket_links_target on ticket_links (target_ticket_id);

${ticket-embedding-index};
```

- [ ] **Step 8: Run tests and Compose config**

Run: `cd backend && ./gradlew test --tests br.org.fadex.helpdesk.config.ApplicationPropertiesTest`

Expected: PASS.

Run: `docker compose config`

Expected: PASS and no fixed `container_name` entries.

- [ ] **Step 9: Commit**

```bash
git add backend/build.gradle docker-compose.yml .env.example backend/.env.example backend/src/main/resources/application.properties backend/src/main/resources/application-test.properties backend/src/main/resources/db/migration/V2__add_ai_triage_embeddings.sql backend/src/test/java/br/org/fadex/helpdesk/config/ApplicationPropertiesTest.java
git commit -m "chore(backend): configura ia local e pgvector"
```

### Task 2: Modelo de Ticket, Jobs e Vinculos

**Files:**
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/Ticket.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketDto.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketMapper.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/job/AiJob.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/job/AiJobType.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/job/AiJobStatus.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketLink.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/repository/TicketLinkRepository.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/repository/TicketPersistenceTest.java`

**Interfaces:**
- Produces: `Ticket.applyAutomaticClassification(TicketCategory, TicketPriority, String)`.
- Produces: `Ticket.applyManualClassification(TicketCategory, TicketPriority, String)`.
- Produces: `Ticket.updateEmbedding(String embeddingValue, String embeddingModel, LocalDateTime embeddingUpdatedAt)`.
- Produces: `AiJob(UUID ticketId, AiJobType type, LocalDateTime nextAttemptAt)`.

- [ ] **Step 1: Extend persistence test**

Add to `TicketPersistenceTest.devePersistirChamadoComSolicitanteResponsavelEnumsEComentarios()` after creating `ticket`:

```java
ticket.applyAutomaticClassification(
		TicketCategory.SISTEMAS,
		TicketPriority.ALTA,
		"Classificacao automatica por fallback deterministico."
);
ticket.updateEmbedding("[0.1,0.2,0.3]", "all-minilm", LocalDateTime.of(2026, 8, 14, 10, 0));
```

Add assertions:

```java
assertThat(foundTicket.getCategory()).isEqualTo(TicketCategory.SISTEMAS);
assertThat(foundTicket.getPriority()).isEqualTo(TicketPriority.ALTA);
assertThat(foundTicket.getClassificationOrigin()).isEqualTo(ClassificationOrigin.IA);
assertThat(foundTicket.getClassificationJustification()).isEqualTo("Classificacao automatica por fallback deterministico.");
assertThat(foundTicket.getEmbeddingModel()).isEqualTo("all-minilm");
assertThat(foundTicket.getEmbeddingUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 8, 14, 10, 0));
```

- [ ] **Step 2: Run test and verify failure**

Run: `cd backend && ./gradlew test --tests br.org.fadex.helpdesk.repository.TicketPersistenceTest`

Expected: FAIL because methods and fields do not exist.

- [ ] **Step 3: Update `Ticket`**

Add fields:

```java
@Column(name = "classification_justification", columnDefinition = "text")
private String classificationJustification;

@Column(columnDefinition = "text")
private String embedding;

@Column(name = "embedding_model", length = 120)
private String embeddingModel;

@Column(name = "embedding_updated_at")
private LocalDateTime embeddingUpdatedAt;
```

Add methods:

```java
public void applyAutomaticClassification(
		TicketCategory category,
		TicketPriority priority,
		String classificationJustification
) {
	this.category = category;
	this.priority = priority;
	this.classificationOrigin = ClassificationOrigin.IA;
	this.classificationJustification = classificationJustification;
}

public void applyManualClassification(
		TicketCategory category,
		TicketPriority priority,
		String classificationJustification
) {
	this.category = category;
	this.priority = priority;
	this.classificationOrigin = ClassificationOrigin.MANUAL;
	this.classificationJustification = classificationJustification;
}

public void updateEmbedding(String embedding, String embeddingModel, LocalDateTime embeddingUpdatedAt) {
	this.embedding = embedding;
	this.embeddingModel = embeddingModel;
	this.embeddingUpdatedAt = embeddingUpdatedAt;
}
```

Add getters for the four new fields.

- [ ] **Step 4: Update DTO and mapper**

Add `String classificationJustification` to `TicketDto` after `classificationOrigin`.

In `TicketMapper.toResponseDto`, pass `ticket.getClassificationJustification()`.

- [ ] **Step 5: Create AI job entity and enums**

Create `AiJobType.java`:

```java
package br.org.fadex.helpdesk.ai.job;

public enum AiJobType {
	CLASSIFICATION,
	EMBEDDING
}
```

Create `AiJobStatus.java`:

```java
package br.org.fadex.helpdesk.ai.job;

public enum AiJobStatus {
	PENDING,
	PROCESSING,
	DONE,
	FAILED
}
```

Create `AiJob.java` with UUID id, lazy `Ticket ticket`, `AiJobType type`, `AiJobStatus status`, `int attempts`, `LocalDateTime nextAttemptAt`, `String lastError`, created/updated auditing, constructor setting `PENDING` and attempts `0`, and methods `markProcessing()`, `markDone()`, `markFailed(String lastError, LocalDateTime nextAttemptAt)`, `retry(LocalDateTime nextAttemptAt)`.

- [ ] **Step 6: Create ticket link entity**

Create `TicketLink.java` in `model/ticket` with id, lazy source ticket, lazy target ticket, lazy createdBy user, createdAt auditing, protected constructor, public constructor, and getters.

Create `TicketLinkRepository.java`:

```java
package br.org.fadex.helpdesk.repository;

import br.org.fadex.helpdesk.model.ticket.TicketLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketLinkRepository extends JpaRepository<TicketLink, UUID> {
	List<TicketLink> findBySourceTicketId(UUID sourceTicketId);
	Optional<TicketLink> findBySourceTicketIdAndTargetTicketId(UUID sourceTicketId, UUID targetTicketId);
	boolean existsBySourceTicketIdAndTargetTicketId(UUID sourceTicketId, UUID targetTicketId);
}
```

- [ ] **Step 7: Run repository test**

Run: `cd backend && ./gradlew test --tests br.org.fadex.helpdesk.repository.TicketPersistenceTest`

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/br/org/fadex/helpdesk/model/ticket backend/src/main/java/br/org/fadex/helpdesk/ai/job backend/src/main/java/br/org/fadex/helpdesk/repository/TicketLinkRepository.java backend/src/test/java/br/org/fadex/helpdesk/repository/TicketPersistenceTest.java
git commit -m "feat(backend): modela jobs de ia e vinculos"
```

### Task 3: Boundary de IA, Fallback e Clientes Ollama

**Files:**
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/AiIntegrationException.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/model/TicketClassification.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/model/TicketEmbedding.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/client/AiTriageClient.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/client/AiEmbeddingClient.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/client/LocalAiTriageClient.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/client/LocalAiEmbeddingClient.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/triage/FallbackTicketClassifier.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/ai/triage/FallbackTicketClassifierTest.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/ai/model/TicketEmbeddingTest.java`

**Interfaces:**
- Produces: `TicketClassification(TicketCategory category, TicketPriority priority, double confidence, String justification)`.
- Produces: `TicketEmbedding(List<Double> values, String model)`.
- Produces: `AiTriageClient.classify(String title, String description)`.
- Produces: `AiEmbeddingClient.embed(String text)`.
- Produces: `FallbackTicketClassifier.classify(String title, String description)`.

- [ ] **Step 1: Write fallback tests**

Create tests:

```java
@Test
void deveClassificarAcessoComPrioridadeAltaQuandoTextoIndicarBloqueioDeLogin() {
	TicketClassification classification = classifier.classify(
			"Login bloqueado",
			"Usuario nao consegue acessar o sistema e precisa desbloquear senha com urgencia."
	);

	assertThat(classification.category()).isEqualTo(TicketCategory.ACESSO);
	assertThat(classification.priority()).isEqualTo(TicketPriority.ALTA);
	assertThat(classification.justification()).contains("fallback");
}

@Test
void deveClassificarFinanceiroComPrioridadeMedia() {
	TicketClassification classification = classifier.classify(
			"Problema com nota fiscal",
			"Preciso corrigir informacoes de pagamento e financeiro."
	);

	assertThat(classification.category()).isEqualTo(TicketCategory.FINANCEIRO);
	assertThat(classification.priority()).isEqualTo(TicketPriority.MEDIA);
}
```

- [ ] **Step 2: Run fallback tests and verify failure**

Run: `cd backend && ./gradlew test --tests br.org.fadex.helpdesk.ai.triage.FallbackTicketClassifierTest`

Expected: FAIL because classes do not exist.

- [ ] **Step 3: Create model records**

Create `TicketClassification` record validating non-null category/priority, confidence between `0.0` and `1.0`, and non-blank justification.

Create `TicketEmbedding` record validating non-empty values, finite numbers, and non-blank model. Add method:

```java
public String toPgVectorLiteral() {
	return values.stream()
			.map(String::valueOf)
			.collect(Collectors.joining(",", "[", "]"));
}
```

- [ ] **Step 4: Implement fallback**

Create `FallbackTicketClassifier` as `@Component` with keyword rules:

```java
private TicketCategory resolveCategory(String normalizedText) {
	if (containsAny(normalizedText, "senha", "login", "acesso", "bloqueado")) {
		return TicketCategory.ACESSO;
	}
	if (containsAny(normalizedText, "sistema", "erro", "aplicacao", "interno")) {
		return TicketCategory.SISTEMAS;
	}
	if (containsAny(normalizedText, "rede", "internet", "servidor", "infra")) {
		return TicketCategory.INFRAESTRUTURA;
	}
	if (containsAny(normalizedText, "computador", "impressora", "teclado", "mouse")) {
		return TicketCategory.EQUIPAMENTOS;
	}
	if (containsAny(normalizedText, "financeiro", "pagamento", "nota fiscal", "boleto")) {
		return TicketCategory.FINANCEIRO;
	}
	if (containsAny(normalizedText, "rh", "ferias", "folha", "beneficio")) {
		return TicketCategory.RH;
	}
	return TicketCategory.OUTROS;
}
```

Priority rule: `ALTA` for `"urgente"`, `"indisponivel"`, `"parado"`, `"bloqueado"`, `"nao consegue acessar"`; `BAIXA` for `"duvida"`, `"orientacao"`, `"quando possivel"`; otherwise `MEDIA`.

- [ ] **Step 5: Create client interfaces**

Create:

```java
public interface AiTriageClient {
	TicketClassification classify(String title, String description);
}
```

```java
public interface AiEmbeddingClient {
	TicketEmbedding embed(String text);
}
```

- [ ] **Step 6: Implement Ollama clients**

`LocalAiTriageClient` uses `RestClient` against `${app.ai.base-url}/api/chat`, `stream=false`, `format` JSON object, temperature `0`, and validates enums. On timeout, invalid JSON, blank response, or HTTP error, throw `AiIntegrationException`.

`LocalAiEmbeddingClient` posts to `${app.ai.base-url}/api/embed` with body:

```json
{
  "model": "all-minilm",
  "input": "titulo\n\ndescricao"
}
```

Parse first array from `embeddings` and return `TicketEmbedding`.

- [ ] **Step 7: Run tests**

Run: `cd backend && ./gradlew test --tests br.org.fadex.helpdesk.ai.*`

Expected: PASS for model and fallback tests. Client behavior is covered through worker tests in Task 5 with mocked `AiTriageClient` and `AiEmbeddingClient`.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/br/org/fadex/helpdesk/ai backend/src/test/java/br/org/fadex/helpdesk/ai
git commit -m "feat(backend): cria boundary de ia local"
```

### Task 4: AiJobService, Fila Persistida e Integracao na Criacao

**Files:**
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/job/AiJobRepository.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/job/AiJobService.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/job/AiJobDto.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/job/AiJobSummaryDto.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/job/AiJobMapper.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/service/TicketService.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/ai/job/AiJobServiceTest.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/service/TicketServiceTest.java`

**Interfaces:**
- Produces: `AiJobService.enqueueTicketJobs(Ticket ticket)`.
- Produces: `AiJobService.findDueJobs(LocalDateTime now, int limit)`.
- Produces: `AiJobService.retry(UUID id)`.
- Consumes: `TicketService.create(TicketCreationDto)`.

- [ ] **Step 1: Write service tests**

Create `AiJobServiceTest` asserting:

```java
@Test
void deveCriarJobsDeClassificacaoEEmbeddingParaChamado() {
	service.enqueueTicketJobs(ticket);

	verify(aiJobRepository).save(argThat(job -> job.getType() == AiJobType.CLASSIFICATION));
	verify(aiJobRepository).save(argThat(job -> job.getType() == AiJobType.EMBEDDING));
}
```

Add retry tests: FAILED becomes PENDING, non-FAILED throws `ConflictException` with message `"Apenas jobs com falha podem ser retentados."`.

- [ ] **Step 2: Update TicketServiceTest**

Add mock `AiJobService aiJobService`.

After `ticketRepository.save`, verify:

```java
verify(aiJobService).enqueueTicketJobs(ticketToSave);
```

- [ ] **Step 3: Run tests and verify failure**

Run: `cd backend && ./gradlew test --tests br.org.fadex.helpdesk.ai.job.AiJobServiceTest --tests br.org.fadex.helpdesk.service.TicketServiceTest`

Expected: FAIL because repository/service integration does not exist.

- [ ] **Step 4: Implement repository**

```java
public interface AiJobRepository extends JpaRepository<AiJob, UUID>, JpaSpecificationExecutor<AiJob> {
	List<AiJob> findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
			AiJobStatus status,
			LocalDateTime nextAttemptAt,
			Pageable pageable
	);

	long countByStatus(AiJobStatus status);
}
```

- [ ] **Step 5: Implement service**

`enqueueTicketJobs(Ticket ticket)` saves two jobs with `LocalDateTime.now()`.

`findDueJobs(LocalDateTime now, int limit)` delegates to repository with `PageRequest.of(0, limit)`.

`retry(UUID id)` loads job, throws `NotFoundException("Job de IA nao encontrado.")` when absent, throws `ConflictException("Apenas jobs com falha podem ser retentados.")` when status is not `FAILED`, calls `job.retry(LocalDateTime.now())`, saves and returns DTO.

- [ ] **Step 6: Integrate TicketService**

Inject `AiJobService` into `TicketService`.

In `create`, after `Ticket savedTicket = ticketRepository.save(ticket);`, call:

```java
aiJobService.enqueueTicketJobs(savedTicket);
```

- [ ] **Step 7: Run tests**

Run: `cd backend && ./gradlew test --tests br.org.fadex.helpdesk.ai.job.AiJobServiceTest --tests br.org.fadex.helpdesk.service.TicketServiceTest`

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/br/org/fadex/helpdesk/ai/job backend/src/main/java/br/org/fadex/helpdesk/service/TicketService.java backend/src/test/java/br/org/fadex/helpdesk/ai/job backend/src/test/java/br/org/fadex/helpdesk/service/TicketServiceTest.java
git commit -m "feat(backend): enfileira jobs de ia ao criar chamado"
```

### Task 5: Worker Quartz para Classificacao e Embedding

**Files:**
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/job/AiJobWorker.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/repository/TicketEmbeddingRepository.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/repository/TicketRepository.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/ai/job/AiJobWorkerTest.java`

**Interfaces:**
- Consumes: `AiJobService.findDueJobs(LocalDateTime now, int limit)`.
- Consumes: `AiTriageClient`, `AiEmbeddingClient`, `FallbackTicketClassifier`.
- Produces: automatic classification updates and persisted vector literals through `TicketEmbeddingRepository.updateEmbedding(UUID ticketId, String embedding, String embeddingModel, LocalDateTime embeddingUpdatedAt)`.

- [ ] **Step 1: Write worker tests**

Test classification:

```java
when(aiTriageClient.classify(ticket.getTitle(), ticket.getDescription())).thenReturn(classification);

worker.processDueJobs();

assertThat(ticket.getClassificationOrigin()).isEqualTo(ClassificationOrigin.IA);
verify(aiJobRepository).save(argThat(job -> job.getStatus() == AiJobStatus.DONE));
```

Test fallback:

```java
when(aiTriageClient.classify(anyString(), anyString())).thenThrow(new AiIntegrationException("Falha IA"));
when(fallbackTicketClassifier.classify(anyString(), anyString())).thenReturn(classification);
```

Test embedding:

```java
when(aiEmbeddingClient.embed("Erro\n\nDescricao")).thenReturn(new TicketEmbedding(List.of(0.1, 0.2, 0.3), "all-minilm"));
verify(ticketEmbeddingRepository).updateEmbedding(ticket.getId(), "[0.1,0.2,0.3]", "all-minilm", now);
```

- [ ] **Step 2: Run worker tests and verify failure**

Run: `cd backend && ./gradlew test --tests br.org.fadex.helpdesk.ai.job.AiJobWorkerTest`

Expected: FAIL because worker and embedding repository do not exist.

- [ ] **Step 3: Create native embedding repository**

Create method:

```java
@Modifying
@Query(value = """
		update tickets
		set embedding = cast(:embedding as vector),
		    embedding_model = :embeddingModel,
		    embedding_updated_at = :embeddingUpdatedAt
		where id = :ticketId
		""", nativeQuery = true)
int updateEmbedding(
		@Param("ticketId") UUID ticketId,
		@Param("embedding") String embedding,
		@Param("embeddingModel") String embeddingModel,
		@Param("embeddingUpdatedAt") LocalDateTime embeddingUpdatedAt
);
```

For H2 tests, avoid invoking this native query directly. Unit-test worker with mocked `TicketEmbeddingRepository`.

- [ ] **Step 4: Implement worker**

Annotate worker with `@Component`.

Add scheduled method:

```java
@Scheduled(fixedDelayString = "${app.ai.worker.interval-millis:10000}")
@Transactional
public void processDueJobs() {
	if (!workerEnabled) {
		return;
	}
	List<AiJob> jobs = aiJobService.findDueJobs(LocalDateTime.now(), batchSize);
	for (AiJob job : jobs) {
		process(job);
	}
}
```

For `CLASSIFICATION`, call local client first when `app.ai.triage.enabled=true`; otherwise call fallback directly. On client exception, call fallback. Apply automatic classification and mark job done.

For `EMBEDDING`, skip and mark failed with `"Triagem IA desabilitada para embeddings."` when `AI_TRIAGE_ENABLED=false`; otherwise call `AiEmbeddingClient`, persist vector, and mark done.

On any unhandled failure, call `job.markFailed(message, LocalDateTime.now().plusMinutes(nextDelay))`, where `nextDelay` is `attempts + 1` minutes, until max attempts. At max attempts, keep status `FAILED`.

- [ ] **Step 5: Enable scheduling**

Add `@EnableScheduling` to `HelpdeskApplication` or create `SchedulingConfig` in `config`.

- [ ] **Step 6: Run worker tests**

Run: `cd backend && ./gradlew test --tests br.org.fadex.helpdesk.ai.job.AiJobWorkerTest`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/br/org/fadex/helpdesk/ai/job/AiJobWorker.java backend/src/main/java/br/org/fadex/helpdesk/repository/TicketEmbeddingRepository.java backend/src/main/java/br/org/fadex/helpdesk/HelpdeskApplication.java backend/src/test/java/br/org/fadex/helpdesk/ai/job/AiJobWorkerTest.java
git commit -m "feat(backend): processa fila de ia assincrona"
```

### Task 6: Revisao Manual da Classificacao

**Files:**
- Create: `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketClassificationUpdateDto.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/service/TicketService.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/controller/TicketController.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/service/TicketServiceTest.java`

**Interfaces:**
- Produces: `TicketService.updateClassification(UUID id, TicketClassificationUpdateDto dto)`.
- Produces: `PATCH /api/v1/tickets/{id}/classification`.

- [ ] **Step 1: Write service test**

Add test:

```java
@Test
void deveAtualizarClassificacaoManualDoChamado() {
	Ticket ticket = new Ticket("Erro", "Descricao", TicketCategory.OUTROS, TicketPriority.MEDIA, ClassificationOrigin.PENDENTE, requester);
	TicketClassificationUpdateDto dto = new TicketClassificationUpdateDto(
			TicketCategory.INFRAESTRUTURA,
			TicketPriority.ALTA,
			"Corrigido manualmente pelo ADMIN."
	);

	when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

	TicketDto response = ticketService.updateClassification(ticketId, dto);

	assertThat(ticket.getClassificationOrigin()).isEqualTo(ClassificationOrigin.MANUAL);
	assertThat(response.category()).isEqualTo(TicketCategory.INFRAESTRUTURA);
	assertThat(response.priority()).isEqualTo(TicketPriority.ALTA);
	assertThat(response.classificationJustification()).isEqualTo("Corrigido manualmente pelo ADMIN.");
}
```

- [ ] **Step 2: Run test and verify failure**

Run: `cd backend && ./gradlew test --tests br.org.fadex.helpdesk.service.TicketServiceTest`

Expected: FAIL because DTO and service method do not exist.

- [ ] **Step 3: Create DTO**

```java
public record TicketClassificationUpdateDto(
		@NotNull TicketCategory category,
		@NotNull TicketPriority priority,
		@NotBlank String justification
) {
}
```

- [ ] **Step 4: Implement service method**

```java
@Transactional
public TicketDto updateClassification(UUID id, TicketClassificationUpdateDto dto) {
	Ticket ticket = findEntityById(id);
	ticket.applyManualClassification(dto.category(), dto.priority(), dto.justification());
	TicketDto response = TicketMapper.toResponseDto(ticket);
	return response;
}
```

- [ ] **Step 5: Add controller endpoint**

```java
@PatchMapping("/{id}/classification")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<TicketDto> updateClassification(
		@PathVariable UUID id,
		@Valid @RequestBody TicketClassificationUpdateDto dto
) {
	TicketDto ticket = ticketService.updateClassification(id, dto);
	return ResponseEntity.ok(ticket);
}
```

- [ ] **Step 6: Run tests**

Run: `cd backend && ./gradlew test --tests br.org.fadex.helpdesk.service.TicketServiceTest`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketClassificationUpdateDto.java backend/src/main/java/br/org/fadex/helpdesk/service/TicketService.java backend/src/main/java/br/org/fadex/helpdesk/controller/TicketController.java backend/src/test/java/br/org/fadex/helpdesk/service/TicketServiceTest.java
git commit -m "feat(backend): permite revisao manual da classificacao"
```

### Task 7: Observabilidade ADMIN da Fila de IA

**Files:**
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/job/AiJobController.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/job/AiJobFilter.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/job/AiJobFields.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/ai/job/AiJobSpecification.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/ai/job/AiJobService.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/ai/job/AiJobControllerTest.java`

**Interfaces:**
- Produces: `GET /api/v1/admin/ai-jobs`.
- Produces: `GET /api/v1/admin/ai-jobs/summary`.
- Produces: `GET /api/v1/admin/ai-jobs/{id}`.
- Produces: `POST /api/v1/admin/ai-jobs/{id}/retry`.

- [ ] **Step 1: Write controller tests**

Use `@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles("test")`, `@MockBean AiJobService`.

Test summary with ADMIN JWT:

```java
mockMvc.perform(get("/api/v1/admin/ai-jobs/summary")
		.with(jwt().jwt(jwt -> jwt.claim("role", "ADMIN"))))
		.andExpect(status().isOk())
		.andExpect(jsonPath("$.pending").value(3));
```

Test SOLICITANTE forbidden:

```java
mockMvc.perform(get("/api/v1/admin/ai-jobs/summary")
		.with(jwt().jwt(jwt -> jwt.claim("role", "SOLICITANTE"))))
		.andExpect(status().isForbidden());
```

- [ ] **Step 2: Run test and verify failure**

Run: `cd backend && ./gradlew test --tests br.org.fadex.helpdesk.ai.job.AiJobControllerTest`

Expected: FAIL because controller does not exist.

- [ ] **Step 3: Implement filter/specification**

`AiJobFilter` record:

```java
public record AiJobFilter(AiJobStatus status, AiJobType type, UUID ticketId) {
	public boolean hasStatus() { return status != null; }
	public boolean hasType() { return type != null; }
	public boolean hasTicketId() { return ticketId != null; }
}
```

Specification adds predicates for status, type and ticket id.

- [ ] **Step 4: Implement service read methods**

Add:

```java
@Transactional(readOnly = true)
public Page<AiJobDto> findAll(AiJobFilter filter, Pageable pageable)

@Transactional(readOnly = true)
public AiJobDto findById(UUID id)

@Transactional(readOnly = true)
public AiJobSummaryDto getSummary()
```

- [ ] **Step 5: Implement controller**

Use `@RestController`, `@RequestMapping("/api/v1/admin/ai-jobs")`, `@PreAuthorize("hasRole('ADMIN')")`.

Default pageable: size `10`, sort `createdAt`, direction `DESC`.

- [ ] **Step 6: Run controller tests**

Run: `cd backend && ./gradlew test --tests br.org.fadex.helpdesk.ai.job.AiJobControllerTest`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/br/org/fadex/helpdesk/ai/job backend/src/test/java/br/org/fadex/helpdesk/ai/job/AiJobControllerTest.java
git commit -m "feat(backend): expõe fila de ia para admin"
```

### Task 8: Similaridade com pgvector e Endpoint de Similares

**Files:**
- Create: `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketSimilarityDto.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketStatusGroup.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/repository/TicketEmbeddingRepository.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/service/TicketSimilarityService.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/controller/TicketController.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/service/TicketSimilarityServiceTest.java`

**Interfaces:**
- Produces: `TicketSimilarityService.findSimilar(UUID ticketId, TicketStatusGroup statusGroup)`.
- Produces: `GET /api/v1/tickets/{id}/similar?statusGroup=active|closed|all`.

- [ ] **Step 1: Write service tests**

Mock `TicketRepository` and `TicketEmbeddingRepository`.

Test missing embedding:

```java
when(ticketEmbeddingRepository.hasEmbedding(ticketId)).thenReturn(false);

List<TicketSimilarityDto> response = service.findSimilar(ticketId, TicketStatusGroup.ALL);

assertThat(response).isEmpty();
```

Test active group:

```java
when(ticketEmbeddingRepository.hasEmbedding(ticketId)).thenReturn(true);
when(ticketEmbeddingRepository.findSimilar(ticketId, List.of("ABERTO", "EM_ANDAMENTO"), 0.75, 5))
		.thenReturn(List.of(projection));
```

- [ ] **Step 2: Run test and verify failure**

Run: `cd backend && ./gradlew test --tests br.org.fadex.helpdesk.service.TicketSimilarityServiceTest`

Expected: FAIL because classes do not exist.

- [ ] **Step 3: Create DTO and status group**

`TicketSimilarityDto`:

```java
public record TicketSimilarityDto(
		TicketMinDto ticket,
		double similarity
) {
}
```

`TicketStatusGroup` enum values `ACTIVE`, `CLOSED`, `ALL`, with parser accepting lowercase query values.

- [ ] **Step 4: Add native repository queries**

Create projection interface:

```java
public interface TicketSimilarityProjection {
	UUID getId();
	double getSimilarity();
}
```

Add methods:

```java
@Query(value = "select embedding is not null from tickets where id = :ticketId", nativeQuery = true)
Boolean hasEmbedding(@Param("ticketId") UUID ticketId);
```

```java
@Query(value = """
		select t.id as id,
		       1 - (t.embedding <=> base.embedding) as similarity
		from tickets t
		join tickets base on base.id = :ticketId
		where t.id <> :ticketId
		  and t.embedding is not null
		  and base.embedding is not null
		  and (:statusCount = 0 or t.status in (:statuses))
		  and (1 - (t.embedding <=> base.embedding)) >= :threshold
		order by t.embedding <=> base.embedding
		limit :limit
		""", nativeQuery = true)
List<TicketSimilarityProjection> findSimilar(
		@Param("ticketId") UUID ticketId,
		@Param("statuses") List<String> statuses,
		@Param("statusCount") int statusCount,
		@Param("threshold") double threshold,
		@Param("limit") int limit
);
```

If H2 cannot parse this native query during context startup, keep it in repository but only execute it in mocked service tests; Spring Data will not validate native SQL syntax until execution.

- [ ] **Step 5: Implement service**

Load base ticket with `ticketService.findEntityById(ticketId)` to preserve 404 behavior.

If `hasEmbedding` returns false, return empty list.

Resolve statuses:

```java
ACTIVE -> ABERTO, EM_ANDAMENTO
CLOSED -> RESOLVIDO, FECHADO
ALL -> empty status list and statusCount 0
```

Map projections to `TicketMinDto` by fetching ids with `ticketRepository.findAllById`.

- [ ] **Step 6: Add controller endpoint**

```java
@GetMapping("/{id}/similar")
public ResponseEntity<List<TicketSimilarityDto>> findSimilar(
		@PathVariable UUID id,
		@RequestParam(defaultValue = "all") TicketStatusGroup statusGroup
) {
	List<TicketSimilarityDto> tickets = ticketSimilarityService.findSimilar(id, statusGroup);
	return ResponseEntity.ok(tickets);
}
```

- [ ] **Step 7: Run tests**

Run: `cd backend && ./gradlew test --tests br.org.fadex.helpdesk.service.TicketSimilarityServiceTest`

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketSimilarityDto.java backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketStatusGroup.java backend/src/main/java/br/org/fadex/helpdesk/repository/TicketEmbeddingRepository.java backend/src/main/java/br/org/fadex/helpdesk/service/TicketSimilarityService.java backend/src/main/java/br/org/fadex/helpdesk/controller/TicketController.java backend/src/test/java/br/org/fadex/helpdesk/service/TicketSimilarityServiceTest.java
git commit -m "feat(backend): busca chamados similares com pgvector"
```

### Task 9: Vinculos Persistidos entre Chamados

**Files:**
- Create: `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketLinkCreationDto.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketLinkDto.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketLinkMapper.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/repository/TicketLinkRepository.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/service/TicketLinkService.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/controller/TicketController.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/service/TicketLinkServiceTest.java`

**Interfaces:**
- Produces: `TicketLinkService.findLinks(UUID ticketId, TicketStatusGroup statusGroup)`.
- Produces: `TicketLinkService.create(UUID ticketId, TicketLinkCreationDto dto)`.
- Produces: `TicketLinkService.delete(UUID ticketId, UUID linkedTicketId)`.
- Produces: endpoints `GET`, `POST`, `DELETE /api/v1/tickets/{id}/links`.

- [ ] **Step 1: Write service tests**

Test canonical order:

```java
UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");

service.create(second, new TicketLinkCreationDto(first));

verify(ticketLinkRepository).save(argThat(link ->
		link.getSourceTicket().getId().equals(first)
				&& link.getTargetTicket().getId().equals(second)
));
```

Test self-link throws `ConflictException("Chamado nao pode ser vinculado a si mesmo.")`.

Test duplicate throws `ConflictException("Vinculo entre chamados ja existe.")`.

- [ ] **Step 2: Run test and verify failure**

Run: `cd backend && ./gradlew test --tests br.org.fadex.helpdesk.service.TicketLinkServiceTest`

Expected: FAIL because service/DTOs do not exist.

- [ ] **Step 3: Create DTOs**

`TicketLinkCreationDto`:

```java
public record TicketLinkCreationDto(@NotNull UUID linkedTicketId) {
}
```

`TicketLinkDto`:

```java
public record TicketLinkDto(TicketMinDto ticket, LocalDateTime createdAt) {
}
```

- [ ] **Step 4: Implement service**

Use `ticketService.findEntityById` for both tickets.

Canonicalize pair by UUID string comparison before saving.

Use `authenticatedUserService.getUserId()` and `userService.findEntityById` for `createdBy`.

For listing, load links where current ticket appears as source or target and return the opposite side, applying `TicketStatusGroup`.

- [ ] **Step 5: Add repository methods**

```java
List<TicketLink> findBySourceTicketIdOrTargetTicketId(UUID sourceTicketId, UUID targetTicketId);
void deleteBySourceTicketIdAndTargetTicketId(UUID sourceTicketId, UUID targetTicketId);
```

- [ ] **Step 6: Add controller endpoints**

```java
@GetMapping("/{id}/links")
public ResponseEntity<List<TicketLinkDto>> findLinks(
		@PathVariable UUID id,
		@RequestParam(defaultValue = "all") TicketStatusGroup statusGroup
)

@PostMapping("/{id}/links")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<TicketLinkDto> createLink(
		@PathVariable UUID id,
		@Valid @RequestBody TicketLinkCreationDto dto
)

@DeleteMapping("/{id}/links/{linkedTicketId}")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<Void> deleteLink(
		@PathVariable UUID id,
		@PathVariable UUID linkedTicketId
)
```

- [ ] **Step 7: Run tests**

Run: `cd backend && ./gradlew test --tests br.org.fadex.helpdesk.service.TicketLinkServiceTest`

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketLinkCreationDto.java backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketLinkDto.java backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketLinkMapper.java backend/src/main/java/br/org/fadex/helpdesk/repository/TicketLinkRepository.java backend/src/main/java/br/org/fadex/helpdesk/service/TicketLinkService.java backend/src/main/java/br/org/fadex/helpdesk/controller/TicketController.java backend/src/test/java/br/org/fadex/helpdesk/service/TicketLinkServiceTest.java
git commit -m "feat(backend): gerencia chamados relacionados"
```

### Task 10: Contratos, Documentacao e Verificacao Final

**Files:**
- Modify: `docs/backend/api.md`
- Modify: `docs/configuracao/env.md`
- Modify: `docs/projeto/acompanhamento-desenvolvimento.md`
- Modify: `README.md`
- Test: full backend suite and build.

**Interfaces:**
- Produces: documentation for endpoints, env vars, Ollama, pgvector, fallback, temporary worktree stacks.

- [ ] **Step 1: Update API docs**

In `docs/backend/api.md`, update ticket response examples with:

```json
"classificationJustification": "Classificacao automatica por fallback deterministico."
```

Add sections for:

```text
PATCH /api/v1/tickets/{id}/classification
GET /api/v1/tickets/{id}/similar
GET /api/v1/tickets/{id}/links
POST /api/v1/tickets/{id}/links
DELETE /api/v1/tickets/{id}/links/{linkedTicketId}
GET /api/v1/admin/ai-jobs
GET /api/v1/admin/ai-jobs/summary
GET /api/v1/admin/ai-jobs/{id}
POST /api/v1/admin/ai-jobs/{id}/retry
```

- [ ] **Step 2: Update environment docs**

In `docs/configuracao/env.md`, document:

```bash
COMPOSE_PROJECT_NAME=fadex-triagem POSTGRES_PORT=15432 BACKEND_PORT=18080 OLLAMA_PORT=11435 docker compose up -d postgres ollama ollama-models backend
```

Document that model preparation is declared in Compose:

```bash
docker compose up ollama-models
```

Explain that first execution downloads model weights into the `ollama-data` volume and later executions reuse the volume.

- [ ] **Step 3: Update project tracking**

In `docs/projeto/acompanhamento-desenvolvimento.md`, mark `IA` as `Concluido` after `make backend-test` and `make backend-build` pass, mark `Deteccao de duplicados/similares` as `Concluido`, and note that similar tickets use pgvector with embeddings generated asynchronously.

- [ ] **Step 4: Update README**

Add a concise section:

```markdown
## Triagem por IA local

A triagem usa Ollama local via Docker quando `AI_TRIAGE_ENABLED=true`. A criacao do chamado nao depende da IA: o backend salva jobs em `ai_jobs` e um worker Quartz processa classificacao e embeddings de forma assincrona. Se Ollama estiver indisponivel, a classificacao usa fallback deterministico por palavras-chave e registra a justificativa.
```

- [ ] **Step 5: Run backend tests**

Run: `make backend-test`

Expected: PASS.

- [ ] **Step 6: Run backend build**

Run: `make backend-build`

Expected: PASS.

- [ ] **Step 7: Validate Compose config**

Run: `docker compose config`

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add docs/backend/api.md docs/configuracao/env.md docs/projeto/acompanhamento-desenvolvimento.md README.md
git commit -m "docs(backend): documenta triagem ia e similares"
```

## Self-Review Checklist

- Spec coverage: plano cobre boundary de IA, Ollama, fallback, jobs persistidos, Quartz, pgvector, similares, links, controller ADMIN de jobs, Docker por worktree, docs e verificacao.
- TDD: cada tarefa de codigo inicia com teste ou assercao de falha antes da implementacao.
- Type consistency: `TicketClassification`, `TicketEmbedding`, `AiJobType`, `AiJobStatus`, `TicketStatusGroup`, `TicketSimilarityDto`, `TicketLinkDto` e endpoints usam os mesmos nomes em todas as tarefas.
- H2 compatibility: Flyway placeholders substituem extensao pgvector, tipo `vector(384)` e indice HNSW no profile `test`.
- Scope control: RabbitMQ, Kafka, APIs externas, entidade de incidente e bloqueio por duplicidade permanecem fora do plano.

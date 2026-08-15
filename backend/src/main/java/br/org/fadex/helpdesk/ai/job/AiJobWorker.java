package br.org.fadex.helpdesk.ai.job;

import br.org.fadex.helpdesk.ai.AiIntegrationException;
import lombok.extern.slf4j.Slf4j;
import br.org.fadex.helpdesk.ai.client.AiEmbeddingClient;
import br.org.fadex.helpdesk.ai.classification.TicketAiSuggestionService;
import br.org.fadex.helpdesk.ai.client.AiTriageClient;
import br.org.fadex.helpdesk.ai.duplicate.DuplicateDetectionService;
import br.org.fadex.helpdesk.ai.model.TicketClassification;
import br.org.fadex.helpdesk.ai.model.TicketEmbedding;
import br.org.fadex.helpdesk.ai.triage.FallbackTicketClassifier;
import br.org.fadex.helpdesk.model.enums.ClassificationOrigin;
import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.model.ticket.Ticket;
import br.org.fadex.helpdesk.repository.TicketEmbeddingRepository;
import br.org.fadex.helpdesk.service.TicketService;
import br.org.fadex.helpdesk.sse.model.NotificationAudience;
import br.org.fadex.helpdesk.sse.model.NotificationEventName;
import br.org.fadex.helpdesk.sse.model.NotificationMessage;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@DisallowConcurrentExecution
public class AiJobWorker implements Job {

	private static final String EMBEDDINGS_DISABLED_MESSAGE = "Triagem IA desabilitada para embeddings.";

	private final AiJobService aiJobService;
	private final AiJobRepository aiJobRepository;
	private final AiTriageClient aiTriageClient;
	private final AiEmbeddingClient aiEmbeddingClient;
	private final FallbackTicketClassifier fallbackTicketClassifier;
	private final TicketEmbeddingRepository ticketEmbeddingRepository;
	private final DuplicateDetectionService duplicateDetectionService;
	private final TicketService ticketService;
	private final TicketAiSuggestionService ticketAiSuggestionService;
	private final ApplicationEventPublisher applicationEventPublisher;
	private final boolean triageEnabled;
	private final boolean workerEnabled;
	private final int batchSize;
	private final int maxAttempts;

	public AiJobWorker(
			AiJobService aiJobService,
			AiJobRepository aiJobRepository,
			AiTriageClient aiTriageClient,
			AiEmbeddingClient aiEmbeddingClient,
			FallbackTicketClassifier fallbackTicketClassifier,
			TicketEmbeddingRepository ticketEmbeddingRepository,
			DuplicateDetectionService duplicateDetectionService,
			TicketService ticketService,
			TicketAiSuggestionService ticketAiSuggestionService,
			ApplicationEventPublisher applicationEventPublisher,
			@Value("${app.ai.triage.enabled}") boolean triageEnabled,
			@Value("${app.ai.worker.enabled}") boolean workerEnabled,
			@Value("${app.ai.worker.batch-size}") int batchSize,
			@Value("${app.ai.worker.max-attempts}") int maxAttempts
	) {
		this.aiJobService = aiJobService;
		this.aiJobRepository = aiJobRepository;
		this.aiTriageClient = aiTriageClient;
		this.aiEmbeddingClient = aiEmbeddingClient;
		this.fallbackTicketClassifier = fallbackTicketClassifier;
		this.ticketEmbeddingRepository = ticketEmbeddingRepository;
		this.duplicateDetectionService = duplicateDetectionService;
		this.ticketService = ticketService;
		this.ticketAiSuggestionService = ticketAiSuggestionService;
		this.applicationEventPublisher = applicationEventPublisher;
		this.triageEnabled = triageEnabled;
		this.workerEnabled = workerEnabled;
		this.batchSize = batchSize;
		this.maxAttempts = maxAttempts;
	}

	@Override
	@Transactional
	public void execute(JobExecutionContext context) {
		processDueJobs();
	}

	void processDueJobs() {
		if (!workerEnabled) {
			return;
		}

		LocalDateTime now = LocalDateTime.now();
		List<AiJob> jobs = aiJobService.findDueJobs(now, batchSize);
		for (AiJob job : jobs) {
			process(job, now);
		}
	}

	private void process(AiJob job, LocalDateTime now) {
		job.markProcessing();
		if (job.getType() == AiJobType.EMBEDDING && !triageEnabled) {
			job.markFailed(EMBEDDINGS_DISABLED_MESSAGE, now);
			aiJobRepository.save(job);
			return;
		}

		try {
			if (job.getType() == AiJobType.CLASSIFICATION) {
				processClassification(job);
			} else if (job.getType() == AiJobType.EMBEDDING) {
				processEmbedding(job, now);
			}
			job.markDone();
		} catch (RuntimeException exception) {
			handleFailure(job, exception);
		}
		aiJobRepository.save(job);
	}

	private void processClassification(AiJob job) {
		Ticket ticket = job.getTicket();
		UUID ticketId = ticket.getId();
		TicketClassification classification = triageEnabled
				? classifyWithFallback(ticket)
				: fallbackTicketClassifier.classify(ticket.getTitle(), ticket.getDescription());

		// Auditoria da sugestao primeiro, classificacao vigente depois: sao colunas disjuntas, e a
		// ordem deixa a sugestao gravada mesmo que a seam recuse a escrita por regra de negocio.
		UUID requesterId = ticketAiSuggestionService.recordSuggestion(
				ticketId,
				classification.category(),
				classification.priority(),
				classification.confidence()
		);

		// Unica porta de escrita de categoria, prioridade e origem. O worker nao muta o chamado.
		ticketService.applyClassification(
				ticketId,
				classification.category(),
				classification.priority(),
				ClassificationOrigin.IA,
				classification.justification()
		);

		publishClassificationDone(ticketId, requesterId, classification);
	}

	private void publishClassificationDone(
			UUID ticketId,
			UUID requesterId,
			TicketClassification classification
	) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("ticketId", ticketId);
		payload.put("category", classification.category().name());
		payload.put("priority", classification.priority().name());
		payload.put("confidence", classification.confidence());

		// NotificationAudience nao tem variante que combine usuario e papel, entao a mesma audiencia
		// logica sai em duas mensagens.
		if (requesterId != null) {
			applicationEventPublisher.publishEvent(NotificationMessage.of(
					NotificationEventName.CLASSIFICACAO_CONCLUIDA,
					payload,
					new NotificationAudience.Users(Set.of(requesterId))
			));
		}

		applicationEventPublisher.publishEvent(NotificationMessage.of(
				NotificationEventName.CLASSIFICACAO_CONCLUIDA,
				payload,
				new NotificationAudience.Roles(Set.of(Role.ADMIN))
		));
		applicationEventPublisher.publishEvent(NotificationMessage.of(
				NotificationEventName.INDICADORES_ATUALIZADOS,
				indicatorsPayload("CLASSIFICACAO_CONCLUIDA", ticketId),
				new NotificationAudience.Roles(Set.of(Role.ADMIN))
		));
	}

	private Map<String, Object> indicatorsPayload(String reason, UUID ticketId) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("reason", reason);
		payload.put("ticketId", ticketId);
		payload.put("occurredAt", LocalDateTime.now());

		return payload;
	}

	private TicketClassification classifyWithFallback(Ticket ticket) {
		try {
			return aiTriageClient.classify(ticket.getTitle(), ticket.getDescription());
		} catch (AiIntegrationException exception) {
			// Registrado em WARN de proposito: por meses a queda para a heuristica foi silenciosa, e
			// o sistema parecia estar classificando por IA quando nao estava. Falha de modelo pode
			// degradar o resultado, nunca esconder que degradou.
			log.warn(
					"Classificacao por IA falhou para o chamado {}; usando fallback heuristico: {}",
					ticket.getId(),
					exception.getMessage()
			);
			return fallbackTicketClassifier.classify(ticket.getTitle(), ticket.getDescription());
		}
	}

	private void processEmbedding(AiJob job, LocalDateTime now) {
		Ticket ticket = job.getTicket();
		TicketEmbedding embedding = aiEmbeddingClient.embed(ticket.getTitle() + "\n\n" + ticket.getDescription());
		ticketEmbeddingRepository.updateEmbedding(
				ticket.getId(),
				embedding.toPgVectorLiteral(),
				embedding.model(),
				now
		);

		// Deteccao roda dentro do try de process(...): se falhar, o job ja e marcado como falho e
		// reagendado pelo caminho de erro existente. Duplicado e sinal, nao regra — nunca altera o
		// chamado nem bloqueia nada.
		duplicateDetectionService.detect(ticket.getId());
	}

	private void handleFailure(AiJob job, RuntimeException exception) {
		int nextDelay = job.getAttempts() + 1;
		LocalDateTime nextAttemptAt = LocalDateTime.now().plusMinutes(nextDelay);
		String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();

		job.markFailed(message, nextAttemptAt);
		if (job.getAttempts() < maxAttempts) {
			job.scheduleRetry();
			return;
		}

		// So notifica no esgotamento das tentativas: markFailed ja incrementou attempts, entao o
		// evento sai uma unica vez. Notificar cada tentativa transformaria uma falha transitoria de
		// rede em tres alertas para o ADMIN.
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("jobId", job.getId());
		payload.put("ticketId", job.getTicketId());
		payload.put("type", job.getType().name());
		payload.put("attempts", job.getAttempts());
		payload.put("lastError", message);

		applicationEventPublisher.publishEvent(NotificationMessage.of(
				NotificationEventName.JOB_IA_FALHOU,
				payload,
				new NotificationAudience.Roles(Set.of(Role.ADMIN))
		));
	}
}

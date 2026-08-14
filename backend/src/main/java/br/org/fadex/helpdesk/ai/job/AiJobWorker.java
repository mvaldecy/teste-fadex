package br.org.fadex.helpdesk.ai.job;

import br.org.fadex.helpdesk.ai.AiIntegrationException;
import br.org.fadex.helpdesk.ai.client.AiEmbeddingClient;
import br.org.fadex.helpdesk.ai.client.AiTriageClient;
import br.org.fadex.helpdesk.ai.duplicate.DuplicateDetectionService;
import br.org.fadex.helpdesk.ai.model.TicketClassification;
import br.org.fadex.helpdesk.ai.model.TicketEmbedding;
import br.org.fadex.helpdesk.ai.triage.FallbackTicketClassifier;
import br.org.fadex.helpdesk.model.ticket.Ticket;
import br.org.fadex.helpdesk.repository.TicketEmbeddingRepository;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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
		TicketClassification classification = triageEnabled
				? classifyWithFallback(ticket)
				: fallbackTicketClassifier.classify(ticket.getTitle(), ticket.getDescription());

		ticket.applyAutomaticClassification(
				classification.category(),
				classification.priority(),
				classification.justification()
		);
	}

	private TicketClassification classifyWithFallback(Ticket ticket) {
		try {
			return aiTriageClient.classify(ticket.getTitle(), ticket.getDescription());
		} catch (AiIntegrationException exception) {
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
		}
	}
}

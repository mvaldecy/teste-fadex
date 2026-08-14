package br.org.fadex.helpdesk.ai.job;

import br.org.fadex.helpdesk.ai.AiIntegrationException;
import br.org.fadex.helpdesk.ai.client.AiEmbeddingClient;
import br.org.fadex.helpdesk.ai.client.AiTriageClient;
import br.org.fadex.helpdesk.ai.duplicate.DuplicateDetectionService;
import br.org.fadex.helpdesk.ai.model.TicketClassification;
import br.org.fadex.helpdesk.ai.model.TicketEmbedding;
import br.org.fadex.helpdesk.ai.triage.FallbackTicketClassifier;
import br.org.fadex.helpdesk.model.enums.ClassificationOrigin;
import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import br.org.fadex.helpdesk.model.ticket.Ticket;
import br.org.fadex.helpdesk.model.user.User;
import br.org.fadex.helpdesk.repository.TicketEmbeddingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiJobWorkerTest {

	private static final int BATCH_SIZE = 5;
	private static final int MAX_ATTEMPTS = 3;

	@Mock
	private AiJobService aiJobService;

	@Mock
	private AiJobRepository aiJobRepository;

	@Mock
	private AiTriageClient aiTriageClient;

	@Mock
	private AiEmbeddingClient aiEmbeddingClient;

	@Mock
	private FallbackTicketClassifier fallbackTicketClassifier;

	@Mock
	private TicketEmbeddingRepository ticketEmbeddingRepository;

	@Mock
	private DuplicateDetectionService duplicateDetectionService;

	@Test
	void deveClassificarChamadoComClienteLocalEFinalizarJob() {
		Ticket ticket = ticket();
		AiJob job = job(ticket, AiJobType.CLASSIFICATION);
		TicketClassification classification = new TicketClassification(
				TicketCategory.SISTEMAS,
				TicketPriority.ALTA,
				0.9,
				"Erro recorrente no sistema interno."
		);
		AiJobWorker worker = worker(true, true);

		when(aiJobService.findDueJobs(any(LocalDateTime.class), eq(BATCH_SIZE))).thenReturn(List.of(job));
		when(aiTriageClient.classify(ticket.getTitle(), ticket.getDescription())).thenReturn(classification);

		worker.processDueJobs();

		assertThat(ticket.getCategory()).isEqualTo(TicketCategory.SISTEMAS);
		assertThat(ticket.getPriority()).isEqualTo(TicketPriority.ALTA);
		assertThat(ticket.getClassificationOrigin()).isEqualTo(ClassificationOrigin.IA);
		assertThat(ticket.getClassificationJustification()).isEqualTo("Erro recorrente no sistema interno.");
		verify(aiJobRepository).save(job);
		assertThat(job.getStatus()).isEqualTo(AiJobStatus.DONE);
	}

	@Test
	void deveUsarFallbackQuandoClienteLocalFalha() {
		Ticket ticket = ticket();
		AiJob job = job(ticket, AiJobType.CLASSIFICATION);
		TicketClassification classification = new TicketClassification(
				TicketCategory.ACESSO,
				TicketPriority.MEDIA,
				0.6,
				"Classificacao por fallback."
		);
		AiJobWorker worker = worker(true, true);

		when(aiJobService.findDueJobs(any(LocalDateTime.class), eq(BATCH_SIZE))).thenReturn(List.of(job));
		when(aiTriageClient.classify(anyString(), anyString()))
				.thenThrow(new AiIntegrationException("Falha IA"));
		when(fallbackTicketClassifier.classify(ticket.getTitle(), ticket.getDescription())).thenReturn(classification);

		worker.processDueJobs();

		assertThat(ticket.getCategory()).isEqualTo(TicketCategory.ACESSO);
		assertThat(ticket.getClassificationOrigin()).isEqualTo(ClassificationOrigin.IA);
		verify(fallbackTicketClassifier).classify(ticket.getTitle(), ticket.getDescription());
		assertThat(job.getStatus()).isEqualTo(AiJobStatus.DONE);
	}

	@Test
	void devePersistirEmbeddingEFinalizarJob() {
		Ticket ticket = ticket();
		AiJob job = job(ticket, AiJobType.EMBEDDING);
		TicketEmbedding embedding = new TicketEmbedding(List.of(0.1, 0.2, 0.3), "all-minilm");
		AiJobWorker worker = worker(true, true);
		ArgumentCaptor<LocalDateTime> updatedAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

		when(aiJobService.findDueJobs(any(LocalDateTime.class), eq(BATCH_SIZE))).thenReturn(List.of(job));
		when(aiEmbeddingClient.embed("Erro\n\nDescricao")).thenReturn(embedding);

		worker.processDueJobs();

		verify(ticketEmbeddingRepository).updateEmbedding(
				eq(ticket.getId()),
				eq("[0.1,0.2,0.3]"),
				eq("all-minilm"),
				updatedAtCaptor.capture()
		);
		assertThat(updatedAtCaptor.getValue()).isNotNull();
		assertThat(job.getStatus()).isEqualTo(AiJobStatus.DONE);
	}

	@Test
	void deveDetectarDuplicadosDepoisDeGravarOEmbedding() {
		Ticket ticket = ticket();
		AiJob job = job(ticket, AiJobType.EMBEDDING);
		AiJobWorker worker = worker(true, true);

		when(aiJobService.findDueJobs(any(LocalDateTime.class), eq(BATCH_SIZE))).thenReturn(List.of(job));
		when(aiEmbeddingClient.embed(anyString()))
				.thenReturn(new TicketEmbedding(List.of(0.1, 0.2, 0.3), "all-minilm"));

		worker.processDueJobs();

		verify(duplicateDetectionService).detect(ticket.getId());
	}

	private AiJobWorker worker(boolean triageEnabled, boolean workerEnabled) {
		return new AiJobWorker(
				aiJobService,
				aiJobRepository,
				aiTriageClient,
				aiEmbeddingClient,
				fallbackTicketClassifier,
				ticketEmbeddingRepository,
				duplicateDetectionService,
				triageEnabled,
				workerEnabled,
				BATCH_SIZE,
				MAX_ATTEMPTS
		);
	}

	private Ticket ticket() {
		Ticket ticket = new Ticket(
				"Erro",
				"Descricao",
				TicketCategory.OUTROS,
				TicketPriority.MEDIA,
				ClassificationOrigin.PENDENTE,
				org.mockito.Mockito.mock(User.class)
		);
		ReflectionTestUtils.setField(ticket, "id", UUID.randomUUID());

		return ticket;
	}

	private AiJob job(Ticket ticket, AiJobType type) {
		AiJob job = new AiJob(ticket.getId(), type, LocalDateTime.now());
		ReflectionTestUtils.setField(job, "ticket", ticket);

		return job;
	}
}

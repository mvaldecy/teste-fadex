package br.org.fadex.helpdesk.ai.job;

import br.org.fadex.helpdesk.ai.AiIntegrationException;
import br.org.fadex.helpdesk.ai.classification.TicketAiSuggestionService;
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
import br.org.fadex.helpdesk.service.TicketService;
import br.org.fadex.helpdesk.sse.model.NotificationEventName;
import br.org.fadex.helpdesk.sse.model.NotificationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
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

	@Mock
	private TicketService ticketService;

	@Mock
	private TicketAiSuggestionService ticketAiSuggestionService;

	@Mock
	private ApplicationEventPublisher applicationEventPublisher;

	@Test
	void deveClassificarChamadoPelaSeamEFinalizarJob() {
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

		verify(ticketService).applyClassification(
				ticket.getId(),
				TicketCategory.SISTEMAS,
				TicketPriority.ALTA,
				ClassificationOrigin.IA,
				"Erro recorrente no sistema interno."
		);
		verify(aiJobRepository).save(job);
		assertThat(job.getStatus()).isEqualTo(AiJobStatus.DONE);
	}

	@Test
	void naoDeveMutarOChamadoDiretamenteAoClassificar() {
		Ticket ticket = ticket();
		AiJob job = job(ticket, AiJobType.CLASSIFICATION);
		AiJobWorker worker = worker(true, true);

		when(aiJobService.findDueJobs(any(LocalDateTime.class), eq(BATCH_SIZE))).thenReturn(List.of(job));
		when(aiTriageClient.classify(anyString(), anyString())).thenReturn(new TicketClassification(
				TicketCategory.SISTEMAS,
				TicketPriority.ALTA,
				0.9,
				"Erro recorrente no sistema interno."
		));

		worker.processDueJobs();

		assertThat(ticket.getCategory()).isEqualTo(TicketCategory.OUTROS);
		assertThat(ticket.getPriority()).isEqualTo(TicketPriority.MEDIA);
		assertThat(ticket.getClassificationOrigin()).isEqualTo(ClassificationOrigin.PENDENTE);
	}

	@Test
	void deveGravarSugestaoEConfiancaDaIa() {
		Ticket ticket = ticket();
		AiJob job = job(ticket, AiJobType.CLASSIFICATION);
		AiJobWorker worker = worker(true, true);

		when(aiJobService.findDueJobs(any(LocalDateTime.class), eq(BATCH_SIZE))).thenReturn(List.of(job));
		when(aiTriageClient.classify(anyString(), anyString())).thenReturn(new TicketClassification(
				TicketCategory.INFRAESTRUTURA,
				TicketPriority.ALTA,
				0.87,
				"Mencao a rede e servidor."
		));

		worker.processDueJobs();

		verify(ticketAiSuggestionService).recordSuggestion(
				ticket.getId(),
				TicketCategory.INFRAESTRUTURA,
				TicketPriority.ALTA,
				0.87
		);
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

		verify(fallbackTicketClassifier).classify(ticket.getTitle(), ticket.getDescription());
		verify(ticketService).applyClassification(
				ticket.getId(),
				TicketCategory.ACESSO,
				TicketPriority.MEDIA,
				ClassificationOrigin.IA,
				"Classificacao por fallback."
		);
		assertThat(job.getStatus()).isEqualTo(AiJobStatus.DONE);
	}

	@Test
	void devePublicarClassificacaoConcluidaEIndicadoresAtualizados() {
		Ticket ticket = ticket();
		AiJob job = job(ticket, AiJobType.CLASSIFICATION);
		AiJobWorker worker = worker(true, true);

		when(aiJobService.findDueJobs(any(LocalDateTime.class), eq(BATCH_SIZE))).thenReturn(List.of(job));
		when(aiTriageClient.classify(anyString(), anyString())).thenReturn(new TicketClassification(
				TicketCategory.INFRAESTRUTURA,
				TicketPriority.ALTA,
				0.87,
				"Mencao a rede e servidor."
		));
		when(ticketAiSuggestionService.recordSuggestion(any(), any(), any(), any()))
				.thenReturn(UUID.randomUUID());

		worker.processDueJobs();

		ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
		verify(applicationEventPublisher, atLeastOnce()).publishEvent(captor.capture());

		assertThat(captor.getAllValues())
				.extracting(NotificationMessage::eventName)
				.contains(
						NotificationEventName.CLASSIFICACAO_CONCLUIDA,
						NotificationEventName.INDICADORES_ATUALIZADOS
				);
	}

	@Test
	void devePublicarJobIaFalhouSomenteAoEsgotarTentativas() {
		Ticket ticket = ticket();
		AiJob job = job(ticket, AiJobType.CLASSIFICATION);
		ReflectionTestUtils.setField(job, "attempts", MAX_ATTEMPTS - 1);
		AiJobWorker worker = worker(true, true);

		when(aiJobService.findDueJobs(any(LocalDateTime.class), eq(BATCH_SIZE))).thenReturn(List.of(job));
		when(aiTriageClient.classify(anyString(), anyString()))
				.thenThrow(new IllegalStateException("modelo local indisponivel"));

		worker.processDueJobs();

		ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
		verify(applicationEventPublisher, atLeastOnce()).publishEvent(captor.capture());

		assertThat(captor.getAllValues())
				.extracting(NotificationMessage::eventName)
				.contains(NotificationEventName.JOB_IA_FALHOU);
		assertThat(job.getStatus()).isEqualTo(AiJobStatus.FAILED);
	}

	@Test
	void naoDevePublicarJobIaFalhouQuandoAindaHaTentativas() {
		Ticket ticket = ticket();
		AiJob job = job(ticket, AiJobType.CLASSIFICATION);
		AiJobWorker worker = worker(true, true);

		when(aiJobService.findDueJobs(any(LocalDateTime.class), eq(BATCH_SIZE))).thenReturn(List.of(job));
		when(aiTriageClient.classify(anyString(), anyString()))
				.thenThrow(new IllegalStateException("modelo local indisponivel"));

		worker.processDueJobs();

		verify(applicationEventPublisher, never()).publishEvent(any(Object.class));
		assertThat(job.getStatus()).isEqualTo(AiJobStatus.PENDING);
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
				ticketService,
				ticketAiSuggestionService,
				applicationEventPublisher,
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

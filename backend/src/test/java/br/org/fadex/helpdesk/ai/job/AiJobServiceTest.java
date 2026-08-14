package br.org.fadex.helpdesk.ai.job;

import br.org.fadex.helpdesk.exception.ConflictException;
import br.org.fadex.helpdesk.exception.NotFoundException;
import br.org.fadex.helpdesk.model.ticket.Ticket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.mockito.ArgumentMatchers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiJobServiceTest {

	@Mock
	private AiJobRepository aiJobRepository;

	@Test
	void deveCriarJobsDeClassificacaoEEmbeddingParaChamado() {
		UUID ticketId = UUID.fromString("71e9c3d9-53b2-4c4e-9803-c504754dbb45");
		Ticket ticket = mock(Ticket.class);
		AiJobService service = new AiJobService(aiJobRepository);

		when(ticket.getId()).thenReturn(ticketId);

		service.enqueueTicketJobs(ticket);

		verify(aiJobRepository).save(argThat(job ->
				job.getTicketId().equals(ticketId) && job.getType() == AiJobType.CLASSIFICATION
		));
		verify(aiJobRepository).save(argThat(job ->
				job.getTicketId().equals(ticketId) && job.getType() == AiJobType.EMBEDDING
		));
	}

	@Test
	void deveBuscarJobsPendentesDevidosComLimiteInformado() {
		LocalDateTime now = LocalDateTime.of(2026, 8, 14, 10, 30);
		int limit = 5;
		AiJob job = new AiJob(UUID.randomUUID(), AiJobType.CLASSIFICATION, now);
		AiJobService service = new AiJobService(aiJobRepository);

		when(aiJobRepository.findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
				AiJobStatus.PENDING,
				now,
				PageRequest.of(0, limit)
		)).thenReturn(List.of(job));

		List<AiJob> response = service.findDueJobs(now, limit);

		assertThat(response).containsExactly(job);
		verify(aiJobRepository).findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
				eq(AiJobStatus.PENDING),
				eq(now),
				eq(PageRequest.of(0, limit))
		);
	}

	@Test
	void deveRetentarJobComFalha() {
		UUID jobId = UUID.fromString("d95862fb-9b74-4945-89bc-159376233656");
		AiJob job = new AiJob(UUID.randomUUID(), AiJobType.CLASSIFICATION, LocalDateTime.now());
		AiJobService service = new AiJobService(aiJobRepository);

		job.markFailed("timeout", LocalDateTime.now());
		when(aiJobRepository.findById(jobId)).thenReturn(Optional.of(job));
		when(aiJobRepository.save(job)).thenReturn(job);

		AiJobDto response = service.retry(jobId);

		assertThat(response.status()).isEqualTo(AiJobStatus.PENDING);
		assertThat(response.lastError()).isNull();
		verify(aiJobRepository).save(job);
	}

	@Test
	void deveImpedirRetentativaDeJobSemFalha() {
		UUID jobId = UUID.fromString("d95862fb-9b74-4945-89bc-159376233656");
		AiJob job = new AiJob(UUID.randomUUID(), AiJobType.CLASSIFICATION, LocalDateTime.now());
		AiJobService service = new AiJobService(aiJobRepository);

		when(aiJobRepository.findById(jobId)).thenReturn(Optional.of(job));

		assertThatThrownBy(() -> service.retry(jobId))
				.isInstanceOf(ConflictException.class)
				.hasMessage("Apenas jobs com falha podem ser retentados.");
	}

	@Test
	void deveInformarQuandoJobParaRetentativaNaoExiste() {
		UUID jobId = UUID.fromString("d95862fb-9b74-4945-89bc-159376233656");
		AiJobService service = new AiJobService(aiJobRepository);

		when(aiJobRepository.findById(jobId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.retry(jobId))
				.isInstanceOf(NotFoundException.class)
				.hasMessage("Job de IA nao encontrado.");
	}

	@Test
	void deveListarJobsPaginadosAplicandoFiltro() {
		Pageable pageable = PageRequest.of(0, 10);
		AiJob job = new AiJob(UUID.randomUUID(), AiJobType.CLASSIFICATION, LocalDateTime.now());
		Page<AiJob> page = new PageImpl<>(List.of(job), pageable, 1);
		AiJobService service = new AiJobService(aiJobRepository);

		when(aiJobRepository.findAll(ArgumentMatchers.<Specification<AiJob>>any(), eq(pageable)))
				.thenReturn(page);

		Page<AiJobDto> response = service.findAll(new AiJobFilter(AiJobStatus.FAILED, null, null), pageable);

		assertThat(response.getTotalElements()).isEqualTo(1);
		assertThat(response.getContent().getFirst().type()).isEqualTo(AiJobType.CLASSIFICATION);
	}

	@Test
	void deveMapearItensDaListagemParaDto() {
		Pageable pageable = PageRequest.of(0, 10);
		UUID ticketId = UUID.randomUUID();
		AiJob job = new AiJob(ticketId, AiJobType.EMBEDDING, LocalDateTime.of(2026, 8, 14, 9, 0));
		AiJobService service = new AiJobService(aiJobRepository);

		when(aiJobRepository.findAll(ArgumentMatchers.<Specification<AiJob>>any(), eq(pageable)))
				.thenReturn(new PageImpl<>(List.of(job), pageable, 1));

		AiJobDto dto = service.findAll(new AiJobFilter(null, null, null), pageable).getContent().getFirst();

		assertThat(dto.ticketId()).isEqualTo(ticketId);
		assertThat(dto.status()).isEqualTo(AiJobStatus.PENDING);
		assertThat(dto.attempts()).isZero();
	}
}

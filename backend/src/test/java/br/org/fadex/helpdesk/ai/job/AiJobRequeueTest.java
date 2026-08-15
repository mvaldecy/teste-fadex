package br.org.fadex.helpdesk.ai.job;

import br.org.fadex.helpdesk.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guarda contra job duplicado no reenfileiramento manual.
 *
 * A guarda e por tipo, e nao por chamado: o job de embedding e o mais lento dos dois, e uma guarda
 * por chamado deixaria um embedding ainda PENDING bloqueando a reclassificacao inteira — que e
 * justamente o que o ADMIN quer refazer.
 */
@ExtendWith(MockitoExtension.class)
class AiJobRequeueTest {

	private static final UUID TICKET_ID = UUID.randomUUID();

	@Mock
	private AiJobRepository aiJobRepository;

	@InjectMocks
	private AiJobService aiJobService;

	@Test
	void deveEnfileirarOsDoisTiposQuandoNaoHaJobAtivo() {
		when(aiJobRepository.existsByTicketIdAndTypeAndStatusIn(eq(TICKET_ID), any(), any()))
				.thenReturn(false);
		when(aiJobRepository.save(any(AiJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

		List<AiJobDto> jobs = aiJobService.requeueTicketJobs(TICKET_ID);

		assertThat(jobs).hasSize(2);
		assertThat(jobs).extracting(AiJobDto::type)
				.containsExactlyInAnyOrder(AiJobType.CLASSIFICATION, AiJobType.EMBEDDING);
	}

	@Test
	void naoDeveEmpilharJobDoTipoQueJaEstaAtivo() {
		when(aiJobRepository.existsByTicketIdAndTypeAndStatusIn(
				eq(TICKET_ID), eq(AiJobType.CLASSIFICATION), any())).thenReturn(true);
		when(aiJobRepository.existsByTicketIdAndTypeAndStatusIn(
				eq(TICKET_ID), eq(AiJobType.EMBEDDING), any())).thenReturn(false);
		when(aiJobRepository.save(any(AiJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

		List<AiJobDto> jobs = aiJobService.requeueTicketJobs(TICKET_ID);

		ArgumentCaptor<AiJob> captor = ArgumentCaptor.forClass(AiJob.class);
		verify(aiJobRepository).save(captor.capture());

		assertThat(jobs).hasSize(1);
		assertThat(captor.getValue().getType()).isEqualTo(AiJobType.EMBEDDING);
	}

	@Test
	void deveRecusarComConflitoQuandoTodosOsTiposJaEstaoAtivos() {
		when(aiJobRepository.existsByTicketIdAndTypeAndStatusIn(eq(TICKET_ID), any(), any()))
				.thenReturn(true);

		assertThatThrownBy(() -> aiJobService.requeueTicketJobs(TICKET_ID))
				.isInstanceOf(ConflictException.class);

		verify(aiJobRepository, never()).save(any(AiJob.class));
	}

	@Test
	void deveConsiderarApenasPendingEProcessingComoAtivos() {
		when(aiJobRepository.existsByTicketIdAndTypeAndStatusIn(eq(TICKET_ID), any(), any()))
				.thenReturn(false);
		when(aiJobRepository.save(any(AiJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

		aiJobService.requeueTicketJobs(TICKET_ID);

		ArgumentCaptor<List<AiJobStatus>> captor = ArgumentCaptor.captor();
		verify(aiJobRepository, org.mockito.Mockito.atLeastOnce())
				.existsByTicketIdAndTypeAndStatusIn(eq(TICKET_ID), any(), captor.capture());

		// FAILED e DONE nao bloqueiam: job com falha ja tem o proprio caminho de retry, e job
		// concluido e exatamente o caso em que o ADMIN quer reclassificar.
		assertThat(captor.getValue())
				.containsExactlyInAnyOrder(AiJobStatus.PENDING, AiJobStatus.PROCESSING);
	}
}

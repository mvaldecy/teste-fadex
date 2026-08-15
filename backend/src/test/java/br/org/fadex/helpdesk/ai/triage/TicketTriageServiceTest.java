package br.org.fadex.helpdesk.ai.triage;

import br.org.fadex.helpdesk.ai.job.AiJobDto;
import br.org.fadex.helpdesk.ai.job.AiJobService;
import br.org.fadex.helpdesk.ai.job.AiJobStatus;
import br.org.fadex.helpdesk.ai.job.AiJobType;
import br.org.fadex.helpdesk.exception.ConflictException;
import br.org.fadex.helpdesk.exception.ForbiddenException;
import br.org.fadex.helpdesk.exception.NotFoundException;
import br.org.fadex.helpdesk.model.ticket.Ticket;
import br.org.fadex.helpdesk.security.AccessControlService;
import br.org.fadex.helpdesk.service.TicketService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketTriageServiceTest {

	private static final UUID TICKET_ID = UUID.randomUUID();

	@Mock
	private AiJobService aiJobService;

	@Mock
	private TicketService ticketService;

	@Mock
	private AccessControlService accessControlService;

	@InjectMocks
	private TicketTriageService ticketTriageService;

	@Test
	void deveReenfileirarOsDoisTiposQuandoNaoHaJobAtivo() {
		when(ticketService.findEntityById(TICKET_ID)).thenReturn(mock(Ticket.class));
		when(aiJobService.requeueTicketJobs(TICKET_ID)).thenReturn(List.of(
				job(AiJobType.CLASSIFICATION),
				job(AiJobType.EMBEDDING)
		));

		List<AiJobDto> jobs = ticketTriageService.requestTriage(TICKET_ID);

		assertThat(jobs).extracting(AiJobDto::type)
				.containsExactlyInAnyOrder(AiJobType.CLASSIFICATION, AiJobType.EMBEDDING);
		verify(aiJobService).requeueTicketJobs(TICKET_ID);
	}

	@Test
	void devePropagarConflitoQuandoJaHaJobAtivoParaTodosOsTipos() {
		when(ticketService.findEntityById(TICKET_ID)).thenReturn(mock(Ticket.class));
		when(aiJobService.requeueTicketJobs(TICKET_ID))
				.thenThrow(new ConflictException("Ja existe triagem em andamento para este chamado."));

		assertThatThrownBy(() -> ticketTriageService.requestTriage(TICKET_ID))
				.isInstanceOf(ConflictException.class);
	}

	@Test
	void deveNegarSolicitacaoParaSolicitante() {
		doThrow(new ForbiddenException("Acesso negado ao recurso solicitado."))
				.when(accessControlService).assertAdmin();

		assertThatThrownBy(() -> ticketTriageService.requestTriage(TICKET_ID))
				.isInstanceOf(ForbiddenException.class);

		verify(aiJobService, never()).requeueTicketJobs(any());
	}

	@Test
	void devePropagarNaoEncontradoQuandoChamadoNaoExiste() {
		when(ticketService.findEntityById(TICKET_ID))
				.thenThrow(new NotFoundException("Chamado nao encontrado."));

		assertThatThrownBy(() -> ticketTriageService.requestTriage(TICKET_ID))
				.isInstanceOf(NotFoundException.class);

		verify(aiJobService, never()).requeueTicketJobs(any());
	}

	private AiJobDto job(AiJobType type) {
		return new AiJobDto(
				UUID.randomUUID(),
				TICKET_ID,
				type,
				AiJobStatus.PENDING,
				0,
				LocalDateTime.of(2026, 8, 14, 18, 0),
				null,
				LocalDateTime.of(2026, 8, 14, 18, 0),
				LocalDateTime.of(2026, 8, 14, 18, 0)
		);
	}
}

package br.org.fadex.helpdesk.service;

import br.org.fadex.helpdesk.ai.job.AiJobService;
import br.org.fadex.helpdesk.model.enums.ClassificationOrigin;
import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import br.org.fadex.helpdesk.model.enums.TicketStatus;
import br.org.fadex.helpdesk.model.ticket.Ticket;
import br.org.fadex.helpdesk.model.ticket.TicketCreationDto;
import br.org.fadex.helpdesk.model.ticket.TicketDto;
import br.org.fadex.helpdesk.model.user.User;
import br.org.fadex.helpdesk.repository.TicketRepository;
import br.org.fadex.helpdesk.security.AuthenticatedUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

	@Mock
	private TicketRepository ticketRepository;

	@Mock
	private UserService userService;

	@Mock
	private AuthenticatedUserService authenticatedUserService;

	@Mock
	private AiJobService aiJobService;

	@InjectMocks
	private TicketService ticketService;

	@Test
	void deveCriarChamadoComSolicitanteDoUsuarioAutenticado() {
		UUID authenticatedUserId = UUID.fromString("71e9c3d9-53b2-4c4e-9803-c504754dbb45");
		User requester = new User(
				"Maria Solicitante",
				"maria@fadex.org.br",
				"senha-com-hash",
				Role.SOLICITANTE
		);
		TicketCreationDto ticketCreationDto = new TicketCreationDto(
				"Erro ao acessar sistema",
				"Nao consigo acessar o sistema interno."
		);
		ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);

		when(authenticatedUserService.getUserId()).thenReturn(authenticatedUserId);
		when(userService.findEntityById(authenticatedUserId)).thenReturn(requester);
		when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

		TicketDto response = ticketService.create(ticketCreationDto);

		verify(ticketRepository).save(ticketCaptor.capture());
		Ticket ticketToSave = ticketCaptor.getValue();

		assertThat(ticketToSave.getTitle()).isEqualTo("Erro ao acessar sistema");
		assertThat(ticketToSave.getDescription()).isEqualTo("Nao consigo acessar o sistema interno.");
		assertThat(ticketToSave.getRequester()).isEqualTo(requester);
		assertThat(ticketToSave.getStatus()).isEqualTo(TicketStatus.ABERTO);
		assertThat(ticketToSave.getCategory()).isEqualTo(TicketCategory.OUTROS);
		assertThat(ticketToSave.getPriority()).isEqualTo(TicketPriority.MEDIA);
		assertThat(ticketToSave.getClassificationOrigin()).isEqualTo(ClassificationOrigin.PENDENTE);
		assertThat(response.requester().name()).isEqualTo("Maria Solicitante");
		verify(aiJobService).enqueueTicketJobs(ticketToSave);
	}
}

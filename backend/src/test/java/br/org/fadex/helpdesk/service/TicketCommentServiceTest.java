package br.org.fadex.helpdesk.service;

import br.org.fadex.helpdesk.model.comment.TicketComment;
import br.org.fadex.helpdesk.model.comment.TicketCommentCreationDto;
import br.org.fadex.helpdesk.model.comment.TicketCommentDto;
import br.org.fadex.helpdesk.model.comment.TicketCommentFilter;
import br.org.fadex.helpdesk.model.enums.ClassificationOrigin;
import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import br.org.fadex.helpdesk.model.ticket.Ticket;
import br.org.fadex.helpdesk.model.user.User;
import br.org.fadex.helpdesk.repository.TicketCommentRepository;
import br.org.fadex.helpdesk.security.AuthenticatedUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketCommentServiceTest {

	@Mock
	private TicketCommentRepository ticketCommentRepository;

	@Mock
	private TicketService ticketService;

	@Mock
	private UserService userService;

	@Mock
	private AuthenticatedUserService authenticatedUserService;

	@InjectMocks
	private TicketCommentService ticketCommentService;

	@Test
	void deveCriarComentarioComAutorDoUsuarioAutenticado() {
		UUID ticketId = UUID.fromString("e05968eb-a518-4ff9-8aa2-2d7a53497e45");
		UUID authenticatedUserId = UUID.fromString("71e9c3d9-53b2-4c4e-9803-c504754dbb45");
		User requester = new User(
				"Maria Solicitante",
				"maria@fadex.org.br",
				"senha-com-hash",
				Role.SOLICITANTE
		);
		Ticket ticket = new Ticket(
				"Erro ao acessar sistema",
				"Nao consigo acessar o sistema interno.",
				TicketCategory.OUTROS,
				TicketPriority.MEDIA,
				ClassificationOrigin.PENDENTE,
				requester
		);
		TicketCommentCreationDto creationDto = new TicketCommentCreationDto("Consegui reproduzir o erro.");
		ArgumentCaptor<TicketComment> commentCaptor = ArgumentCaptor.forClass(TicketComment.class);

		when(ticketService.findEntityById(ticketId)).thenReturn(ticket);
		when(authenticatedUserService.getUserId()).thenReturn(authenticatedUserId);
		when(userService.findEntityById(authenticatedUserId)).thenReturn(requester);
		when(ticketCommentRepository.save(any(TicketComment.class))).thenAnswer(invocation -> invocation.getArgument(0));

		TicketCommentDto response = ticketCommentService.create(ticketId, creationDto);

		verify(ticketCommentRepository).save(commentCaptor.capture());
		TicketComment commentToSave = commentCaptor.getValue();

		assertThat(commentToSave.getTicket()).isEqualTo(ticket);
		assertThat(commentToSave.getAuthor()).isEqualTo(requester);
		assertThat(commentToSave.getText()).isEqualTo("Consegui reproduzir o erro.");
		assertThat(response.text()).isEqualTo("Consegui reproduzir o erro.");
		assertThat(response.author().name()).isEqualTo("Maria Solicitante");
	}

	@Test
	void deveValidarExistenciaDoChamadoAntesDeListarComentarios() {
		UUID ticketId = UUID.fromString("e05968eb-a518-4ff9-8aa2-2d7a53497e45");
		User requester = new User(
				"Maria Solicitante",
				"maria@fadex.org.br",
				"senha-com-hash",
				Role.SOLICITANTE
		);
		Ticket ticket = new Ticket(
				"Erro ao acessar sistema",
				"Nao consigo acessar o sistema interno.",
				TicketCategory.OUTROS,
				TicketPriority.MEDIA,
				ClassificationOrigin.PENDENTE,
				requester
		);
		TicketComment comment = new TicketComment(ticket, requester, "Chamado criado.");
		PageRequest pageable = PageRequest.of(0, 10);

		when(ticketService.findEntityById(ticketId)).thenReturn(ticket);
		when(ticketCommentRepository.findAll(anyCommentSpecification(), eq(pageable)))
				.thenReturn(new PageImpl<>(List.of(comment), pageable, 1));

		Page<?> response = ticketCommentService.findAll(ticketId, new TicketCommentFilter(null, null, null), pageable);

		verify(ticketService).findEntityById(ticketId);
		assertThat(response.getContent()).hasSize(1);
	}

	private Specification<TicketComment> anyCommentSpecification() {
		return any();
	}
}

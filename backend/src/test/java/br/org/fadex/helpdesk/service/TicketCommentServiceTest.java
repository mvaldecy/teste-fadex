package br.org.fadex.helpdesk.service;

import br.org.fadex.helpdesk.exception.ForbiddenException;
import br.org.fadex.helpdesk.model.comment.TicketComment;
import br.org.fadex.helpdesk.model.comment.TicketCommentCreationDto;
import br.org.fadex.helpdesk.model.comment.TicketCommentDto;
import br.org.fadex.helpdesk.model.comment.TicketCommentFilter;
import br.org.fadex.helpdesk.model.enums.ClassificationOrigin;
import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketEventType;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import br.org.fadex.helpdesk.model.ticket.Ticket;
import br.org.fadex.helpdesk.model.user.User;
import br.org.fadex.helpdesk.repository.TicketCommentRepository;
import br.org.fadex.helpdesk.security.AccessControlService;
import br.org.fadex.helpdesk.security.AuthenticatedUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import br.org.fadex.helpdesk.notification.event.TicketNotificationEvent;
import br.org.fadex.helpdesk.notification.event.TicketNotificationType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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

	@Mock
	private TicketEventService ticketEventService;

	@Mock
	private ApplicationEventPublisher applicationEventPublisher;

	private AccessControlService accessControlService;

	private TicketCommentService ticketCommentService;

	@BeforeEach
	void setUp() {
		accessControlService = new AccessControlService(authenticatedUserService);
		ticketCommentService = new TicketCommentService(
				ticketCommentRepository,
				ticketService,
				userService,
				accessControlService,
				ticketEventService,
				applicationEventPublisher
		);
	}

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

		ReflectionTestUtils.setField(requester, "id", authenticatedUserId);
		when(ticketService.findEntityById(ticketId)).thenReturn(ticket);
		when(authenticatedUserService.getRole()).thenReturn(Role.SOLICITANTE);
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
	void devePublicarEventoDeNotificacaoAoCriarComentario() {
		UUID ticketId = UUID.fromString("e05968eb-a518-4ff9-8aa2-2d7a53497e45");
		UUID authenticatedUserId = UUID.fromString("71e9c3d9-53b2-4c4e-9803-c504754dbb45");
		User requester = newUser("maria@fadex.org.br", Role.SOLICITANTE);
		Ticket ticket = newTicket(requester);
		TicketCommentCreationDto creationDto = new TicketCommentCreationDto("Continua com erro.");
		ArgumentCaptor<TicketNotificationEvent> captor = ArgumentCaptor.forClass(TicketNotificationEvent.class);

		ReflectionTestUtils.setField(requester, "id", authenticatedUserId);
		ReflectionTestUtils.setField(ticket, "id", ticketId);
		when(ticketService.findEntityById(ticketId)).thenReturn(ticket);
		when(authenticatedUserService.getRole()).thenReturn(Role.SOLICITANTE);
		when(authenticatedUserService.getUserId()).thenReturn(authenticatedUserId);
		when(userService.findEntityById(authenticatedUserId)).thenReturn(requester);
		when(ticketCommentRepository.save(any(TicketComment.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ticketCommentService.create(ticketId, creationDto);

		verify(applicationEventPublisher).publishEvent(captor.capture());
		TicketNotificationEvent event = captor.getValue();

		assertThat(event.type()).isEqualTo(TicketNotificationType.COMENTARIO_ADICIONADO);
		assertThat(event.actorId()).isEqualTo(authenticatedUserId);
		assertThat(event.detail()).isEqualTo("Continua com erro.");
		assertThat(event.requester().email()).isEqualTo("maria@fadex.org.br");
	}

	@Test
	void deveGravarEventoAoCriarComentario() {
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

		ReflectionTestUtils.setField(requester, "id", authenticatedUserId);
		when(ticketService.findEntityById(ticketId)).thenReturn(ticket);
		when(authenticatedUserService.getRole()).thenReturn(Role.SOLICITANTE);
		when(authenticatedUserService.getUserId()).thenReturn(authenticatedUserId);
		when(userService.findEntityById(authenticatedUserId)).thenReturn(requester);
		when(ticketCommentRepository.save(any(TicketComment.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ticketCommentService.create(ticketId, creationDto);

		verify(ticketEventService).record(
				ticket,
				requester,
				TicketEventType.COMENTARIO_ADICIONADO,
				"Comentario adicionado."
		);
	}

	@Test
	void deveNegarCriacaoDeComentarioEmChamadoDeOutroSolicitante() {
		UUID ticketId = UUID.fromString("e05968eb-a518-4ff9-8aa2-2d7a53497e45");
		UUID authenticatedUserId = UUID.fromString("71e9c3d9-53b2-4c4e-9803-c504754dbb45");
		UUID requesterId = UUID.fromString("b9ff9b29-e32b-4a51-a586-0119beeb0cd5");
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

		ReflectionTestUtils.setField(requester, "id", requesterId);
		when(ticketService.findEntityById(ticketId)).thenReturn(ticket);
		when(authenticatedUserService.getRole()).thenReturn(Role.SOLICITANTE);
		when(authenticatedUserService.getUserId()).thenReturn(authenticatedUserId);

		assertThatThrownBy(() -> ticketCommentService.create(ticketId, creationDto))
				.isInstanceOf(ForbiddenException.class)
				.hasMessage("Acesso negado ao recurso solicitado.");

		verify(userService, never()).findEntityById(any(UUID.class));
		verify(ticketCommentRepository, never()).save(any(TicketComment.class));
	}

	@Test
	void deveValidarExistenciaDoChamadoAntesDeListarComentarios() {
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
		TicketComment comment = new TicketComment(ticket, requester, "Chamado criado.");
		PageRequest pageable = PageRequest.of(0, 10);

		ReflectionTestUtils.setField(requester, "id", authenticatedUserId);
		when(ticketService.findEntityById(ticketId)).thenReturn(ticket);
		when(authenticatedUserService.getRole()).thenReturn(Role.SOLICITANTE);
		when(authenticatedUserService.getUserId()).thenReturn(authenticatedUserId);
		when(ticketCommentRepository.findAll(anyCommentSpecification(), eq(pageable)))
				.thenReturn(new PageImpl<>(List.of(comment), pageable, 1));

		Page<?> response = ticketCommentService.findAll(ticketId, new TicketCommentFilter(null, null, null), pageable);

		verify(ticketService).findEntityById(ticketId);
		assertThat(response.getContent()).hasSize(1);
	}

	@Test
	void deveNegarListagemDeComentariosDeChamadoDeOutroSolicitante() {
		UUID ticketId = UUID.fromString("e05968eb-a518-4ff9-8aa2-2d7a53497e45");
		UUID authenticatedUserId = UUID.fromString("71e9c3d9-53b2-4c4e-9803-c504754dbb45");
		UUID requesterId = UUID.fromString("b9ff9b29-e32b-4a51-a586-0119beeb0cd5");
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
		PageRequest pageable = PageRequest.of(0, 10);

		ReflectionTestUtils.setField(requester, "id", requesterId);
		when(ticketService.findEntityById(ticketId)).thenReturn(ticket);
		when(authenticatedUserService.getRole()).thenReturn(Role.SOLICITANTE);
		when(authenticatedUserService.getUserId()).thenReturn(authenticatedUserId);

		assertThatThrownBy(() -> ticketCommentService.findAll(ticketId, new TicketCommentFilter(null, null, null), pageable))
				.isInstanceOf(ForbiddenException.class)
				.hasMessage("Acesso negado ao recurso solicitado.");

		verify(ticketCommentRepository, never()).findAll(anyCommentSpecification(), eq(pageable));
	}

	private Specification<TicketComment> anyCommentSpecification() {
		return any();
	}

	@Test
	void createDevePreencherPrimeiraRespostaQuandoAutorForAdmin() {
		UUID ticketId = UUID.randomUUID();
		UUID authorId = UUID.randomUUID();
		User requester = newUser("solicitante@fadex.org.br", Role.SOLICITANTE);
		User admin = newUser("admin@fadex.org.br", Role.ADMIN);
		Ticket ticket = newTicket(requester);

		when(ticketService.findEntityById(ticketId)).thenReturn(ticket);
		when(authenticatedUserService.getRole()).thenReturn(Role.ADMIN);
		when(authenticatedUserService.getUserId()).thenReturn(authorId);
		when(userService.findEntityById(authorId)).thenReturn(admin);
		when(ticketCommentRepository.save(any(TicketComment.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		ticketCommentService.create(ticketId, new TicketCommentCreationDto("Estamos analisando."));

		assertThat(ticket.getFirstResponseAt()).isNotNull();
	}

	@Test
	void createNaoDevePreencherPrimeiraRespostaQuandoAutorForSolicitante() {
		UUID ticketId = UUID.randomUUID();
		UUID authorId = UUID.randomUUID();
		User requester = newUser("solicitante@fadex.org.br", Role.SOLICITANTE);
		Ticket ticket = newTicket(requester);
		ReflectionTestUtils.setField(requester, "id", authorId);

		when(ticketService.findEntityById(ticketId)).thenReturn(ticket);
		when(authenticatedUserService.getRole()).thenReturn(Role.SOLICITANTE);
		when(authenticatedUserService.getUserId()).thenReturn(authorId);
		when(userService.findEntityById(authorId)).thenReturn(requester);
		when(ticketCommentRepository.save(any(TicketComment.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		ticketCommentService.create(ticketId, new TicketCommentCreationDto("Alguma novidade?"));

		assertThat(ticket.getFirstResponseAt()).isNull();
	}

	@Test
	void createNaoDeveSobrescreverPrimeiraRespostaJaRegistrada() {
		UUID ticketId = UUID.randomUUID();
		UUID authorId = UUID.randomUUID();
		User requester = newUser("solicitante@fadex.org.br", Role.SOLICITANTE);
		User admin = newUser("admin@fadex.org.br", Role.ADMIN);
		Ticket ticket = newTicket(requester);
		LocalDateTime primeiraResposta = LocalDateTime.of(2026, 1, 1, 0, 0);
		ticket.markFirstResponse(primeiraResposta);

		when(ticketService.findEntityById(ticketId)).thenReturn(ticket);
		when(authenticatedUserService.getRole()).thenReturn(Role.ADMIN);
		when(authenticatedUserService.getUserId()).thenReturn(authorId);
		when(userService.findEntityById(authorId)).thenReturn(admin);
		when(ticketCommentRepository.save(any(TicketComment.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		ticketCommentService.create(ticketId, new TicketCommentCreationDto("Mais uma atualizacao."));

		assertThat(ticket.getFirstResponseAt()).isEqualTo(primeiraResposta);
	}

	private User newUser(String email, Role role) {
		User user = new User("Usuario", email, "hash", role, false);
		ReflectionTestUtils.setField(user, "id", UUID.randomUUID());

		return user;
	}

	private Ticket newTicket(User requester) {
		return new Ticket(
				"Chamado",
				"Descricao do chamado.",
				TicketCategory.OUTROS,
				TicketPriority.MEDIA,
				ClassificationOrigin.PENDENTE,
				requester
		);
	}
}

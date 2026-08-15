package br.org.fadex.helpdesk.service;

import br.org.fadex.helpdesk.ai.job.AiJobService;
import br.org.fadex.helpdesk.model.enums.ClassificationOrigin;
import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketEventType;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import br.org.fadex.helpdesk.model.enums.TicketStatus;
import br.org.fadex.helpdesk.model.ticket.Ticket;
import br.org.fadex.helpdesk.model.ticket.TicketCreationDto;
import br.org.fadex.helpdesk.model.ticket.TicketDto;
import br.org.fadex.helpdesk.model.ticket.TicketFields;
import br.org.fadex.helpdesk.model.ticket.TicketAssigneeUpdateDto;
import br.org.fadex.helpdesk.model.ticket.TicketFilter;
import br.org.fadex.helpdesk.model.ticket.TicketStatusUpdateDto;
import br.org.fadex.helpdesk.model.user.User;
import br.org.fadex.helpdesk.exception.ForbiddenException;
import br.org.fadex.helpdesk.exception.ConflictException;
import br.org.fadex.helpdesk.exception.NotFoundException;
import br.org.fadex.helpdesk.exception.UnauthorizedException;
import br.org.fadex.helpdesk.notification.event.TicketNotificationEvent;
import br.org.fadex.helpdesk.notification.event.TicketNotificationType;
import br.org.fadex.helpdesk.repository.TicketRepository;
import br.org.fadex.helpdesk.security.AccessControlService;
import br.org.fadex.helpdesk.security.AuthenticatedUserService;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

	@Mock
	private TicketRepository ticketRepository;

	@Mock
	private UserService userService;

	@Mock
	private AuthenticatedUserService authenticatedUserService;

	@Mock
	private TicketEventService ticketEventService;

	@Mock
	private AiJobService aiJobService;

	@Mock
	private ApplicationEventPublisher applicationEventPublisher;

	private AccessControlService accessControlService;

	private TicketService ticketService;

	@BeforeEach
	void setUp() {
		accessControlService = new AccessControlService(authenticatedUserService);
		ticketService = new TicketService(
				ticketRepository,
				userService,
				accessControlService,
				ticketEventService,
				aiJobService,
				applicationEventPublisher
		);
	}

	@Test
	void deveCriarChamadoComSolicitanteDoUsuarioAutenticado() {
		UUID authenticatedUserId = UUID.fromString("71e9c3d9-53b2-4c4e-9803-c504754dbb45");
		User requester = new User(
				"Maria Solicitante",
				"maria@fadex.org.br",
				"senha-com-hash",
				Role.SOLICITANTE
		);
		ReflectionTestUtils.setField(requester, "id", UUID.randomUUID());
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

	@Test
	void deveGravarEventoAoCriarChamado() {
		UUID authenticatedUserId = UUID.fromString("71e9c3d9-53b2-4c4e-9803-c504754dbb45");
		User requester = new User("Maria", "maria@fadex.org.br", "hash", Role.SOLICITANTE, false);
		ReflectionTestUtils.setField(requester, "id", UUID.randomUUID());
		TicketCreationDto dto = new TicketCreationDto(
				"Erro ao acessar sistema",
				"Nao consigo acessar o sistema interno."
		);

		when(authenticatedUserService.getUserId()).thenReturn(authenticatedUserId);
		when(userService.findEntityById(authenticatedUserId)).thenReturn(requester);
		when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ticketService.create(dto);

		verify(ticketEventService).record(
				any(Ticket.class),
				eq(requester),
				eq(TicketEventType.CHAMADO_CRIADO),
				eq("Chamado criado.")
		);
	}

	@Test
	void deveNotificarSolicitanteEAdminsAoCriarChamado() {
		UUID authenticatedUserId = UUID.fromString("71e9c3d9-53b2-4c4e-9803-c504754dbb45");
		Ticket savedTicket = newTicket(TicketPriority.MEDIA);
		TicketCreationDto dto = new TicketCreationDto("Chamado", "Descricao do chamado.");
		ArgumentCaptor<TicketNotificationEvent> captor = ArgumentCaptor.forClass(TicketNotificationEvent.class);

		when(authenticatedUserService.getUserId()).thenReturn(authenticatedUserId);
		when(userService.findEntityById(authenticatedUserId)).thenReturn(savedTicket.getRequester());
		when(ticketRepository.save(any(Ticket.class))).thenReturn(savedTicket);

		ticketService.create(dto);

		verify(applicationEventPublisher).publishEvent(captor.capture());
		TicketNotificationEvent event = captor.getValue();

		assertThat(event.type()).isEqualTo(TicketNotificationType.CHAMADO_CRIADO);
		assertThat(event.ticket().id()).isEqualTo(savedTicket.getId());
		assertThat(event.requester().id()).isEqualTo(savedTicket.getRequester().getId());
		assertThat(event.assignee()).isNull();
		assertThat(event.previousPriority()).isNull();
		assertThat(event.becameHighPriority()).isFalse();
	}
	@Test
	void deveAlertarAdminsQuandoChamadoNasceComPrioridadeAlta() {
		UUID authenticatedUserId = UUID.fromString("71e9c3d9-53b2-4c4e-9803-c504754dbb45");
		Ticket savedTicket = newTicket(TicketPriority.ALTA);
		TicketCreationDto dto = new TicketCreationDto("Chamado", "Descricao do chamado.");
		ArgumentCaptor<TicketNotificationEvent> captor = ArgumentCaptor.forClass(TicketNotificationEvent.class);

		when(authenticatedUserService.getUserId()).thenReturn(authenticatedUserId);
		when(userService.findEntityById(authenticatedUserId)).thenReturn(savedTicket.getRequester());
		when(ticketRepository.save(any(Ticket.class))).thenReturn(savedTicket);

		ticketService.create(dto);

		verify(applicationEventPublisher).publishEvent(captor.capture());
		TicketNotificationEvent event = captor.getValue();

		assertThat(event.type()).isEqualTo(TicketNotificationType.CHAMADO_CRIADO);
		assertThat(event.becameHighPriority()).isTrue();
	}
	@Test
	void naoDeveAlertarPrioridadeAltaQuandoChamadoNasceComPrioridadeMedia() {
		UUID authenticatedUserId = UUID.fromString("71e9c3d9-53b2-4c4e-9803-c504754dbb45");
		Ticket savedTicket = newTicket(TicketPriority.MEDIA);
		TicketCreationDto dto = new TicketCreationDto("Chamado", "Descricao do chamado.");
		ArgumentCaptor<TicketNotificationEvent> captor = ArgumentCaptor.forClass(TicketNotificationEvent.class);

		when(authenticatedUserService.getUserId()).thenReturn(authenticatedUserId);
		when(userService.findEntityById(authenticatedUserId)).thenReturn(savedTicket.getRequester());
		when(ticketRepository.save(any(Ticket.class))).thenReturn(savedTicket);

		ticketService.create(dto);

		verify(applicationEventPublisher).publishEvent(captor.capture());

		assertThat(captor.getValue().becameHighPriority()).isFalse();
	}
	@Test
	void deveForcarSolicitanteAutenticadoNoFiltroDeChamados() {
		UUID authenticatedUserId = UUID.fromString("71e9c3d9-53b2-4c4e-9803-c504754dbb45");
		UUID requestedRequesterId = UUID.fromString("b9ff9b29-e32b-4a51-a586-0119beeb0cd5");
		PageRequest pageable = PageRequest.of(0, 10);
		ArgumentCaptor<Specification<Ticket>> specificationCaptor = ArgumentCaptor.forClass(Specification.class);

		when(authenticatedUserService.getRole()).thenReturn(Role.SOLICITANTE);
		when(authenticatedUserService.getUserId()).thenReturn(authenticatedUserId);
		when(ticketRepository.findAll(anyTicketSpecification(), eq(pageable))).thenReturn(Page.empty(pageable));

		ticketService.findAll(new TicketFilter(null, null, null, requestedRequesterId, null, null), pageable);

		verify(ticketRepository).findAll(specificationCaptor.capture(), eq(pageable));
		assertTicketSpecificationFiltersRequester(specificationCaptor.getValue(), authenticatedUserId, requestedRequesterId);
	}

	@Test
	void deveManterFiltroInformadoParaAdminAoListarChamados() {
		UUID requestedRequesterId = UUID.fromString("b9ff9b29-e32b-4a51-a586-0119beeb0cd5");
		PageRequest pageable = PageRequest.of(0, 10);
		ArgumentCaptor<Specification<Ticket>> specificationCaptor = ArgumentCaptor.forClass(Specification.class);

		when(authenticatedUserService.getRole()).thenReturn(Role.ADMIN);
		when(ticketRepository.findAll(anyTicketSpecification(), eq(pageable))).thenReturn(Page.empty(pageable));

		ticketService.findAll(new TicketFilter(null, null, null, requestedRequesterId, null, null), pageable);

		verify(ticketRepository).findAll(specificationCaptor.capture(), eq(pageable));
		assertTicketSpecificationFiltersRequester(specificationCaptor.getValue(), requestedRequesterId, null);
		verify(authenticatedUserService, never()).getUserId();
	}

	@Test
	void deveNegarDetalheDeChamadoDeOutroSolicitante() {
		UUID ticketId = UUID.fromString("e05968eb-a518-4ff9-8aa2-2d7a53497e45");
		UUID authenticatedUserId = UUID.fromString("71e9c3d9-53b2-4c4e-9803-c504754dbb45");
		UUID requesterId = UUID.fromString("b9ff9b29-e32b-4a51-a586-0119beeb0cd5");
		User requester = new User("Joao", "joao@fadex.org.br", "hash", Role.SOLICITANTE, false);
		Ticket ticket = new Ticket(
				"Titulo",
				"Descricao",
				TicketCategory.OUTROS,
				TicketPriority.MEDIA,
				ClassificationOrigin.PENDENTE,
				requester
		);

		ReflectionTestUtils.setField(requester, "id", requesterId);
		when(ticketRepository.findById(ticketId)).thenReturn(java.util.Optional.of(ticket));
		when(authenticatedUserService.getRole()).thenReturn(Role.SOLICITANTE);
		when(authenticatedUserService.getUserId()).thenReturn(authenticatedUserId);

		assertThatThrownBy(() -> ticketService.findById(ticketId))
				.isInstanceOf(ForbiddenException.class)
				.hasMessage("Acesso negado ao recurso solicitado.");
	}

	@Test
	void devePermitirDetalheDeChamadoParaAdmin() {
		UUID ticketId = UUID.fromString("e05968eb-a518-4ff9-8aa2-2d7a53497e45");
		UUID requesterId = UUID.fromString("b9ff9b29-e32b-4a51-a586-0119beeb0cd5");
		User requester = new User("Joao", "joao@fadex.org.br", "hash", Role.SOLICITANTE, false);
		Ticket ticket = new Ticket(
				"Titulo",
				"Descricao",
				TicketCategory.OUTROS,
				TicketPriority.MEDIA,
				ClassificationOrigin.PENDENTE,
				requester
		);

		ReflectionTestUtils.setField(requester, "id", requesterId);
		when(ticketRepository.findById(ticketId)).thenReturn(java.util.Optional.of(ticket));
		when(authenticatedUserService.getRole()).thenReturn(Role.ADMIN);

		TicketDto response = ticketService.findById(ticketId);

		assertThat(response.title()).isEqualTo("Titulo");
		assertThat(response.requester().id()).isEqualTo(requesterId);
		verify(authenticatedUserService, never()).getUserId();
	}

	private Specification<Ticket> anyTicketSpecification() {
		return any();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private void assertTicketSpecificationFiltersRequester(
			Specification<Ticket> specification,
			UUID expectedRequesterId,
			UUID forbiddenRequesterId
	) {
		Root<Ticket> root = mock(Root.class);
		CriteriaQuery<?> query = mock(CriteriaQuery.class);
		CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
		Path<Object> requesterPath = mock(Path.class);
		Path<Object> requesterIdPath = mock(Path.class);
		Predicate requesterPredicate = mock(Predicate.class);
		Predicate combinedPredicate = mock(Predicate.class);

		when(root.get(TicketFields.REQUESTER)).thenReturn((Path) requesterPath);
		when(requesterPath.get(TicketFields.ID)).thenReturn((Path) requesterIdPath);
		when(criteriaBuilder.equal(requesterIdPath, expectedRequesterId)).thenReturn(requesterPredicate);
		when(criteriaBuilder.and(any(Predicate[].class))).thenReturn(combinedPredicate);

		specification.toPredicate(root, query, criteriaBuilder);

		verify(criteriaBuilder).equal(requesterIdPath, expectedRequesterId);
		if (forbiddenRequesterId != null) {
			verify(criteriaBuilder, never()).equal(requesterIdPath, forbiddenRequesterId);
		}
	}

	@Test
	void applyClassificationDeveAplicarClassificacaoERegistrarEvento() {
		Ticket ticket = newTicket(TicketPriority.MEDIA);

		when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
		when(ticketRepository.save(ticket)).thenReturn(ticket);
		when(authenticatedUserService.getUserId()).thenThrow(new UnauthorizedException("Autenticação necessária."));

		ticketService.applyClassification(
				ticket.getId(),
				TicketCategory.ACESSO,
				TicketPriority.MEDIA,
				ClassificationOrigin.IA,
				"Justificativa da IA."
		);

		assertThat(ticket.getCategory()).isEqualTo(TicketCategory.ACESSO);
		assertThat(ticket.getClassificationOrigin()).isEqualTo(ClassificationOrigin.IA);
		assertThat(ticket.getClassificationJustification()).isEqualTo("Justificativa da IA.");
		verify(ticketEventService).record(
				eq(ticket), isNull(), eq(TicketEventType.CLASSIFICACAO_ATUALIZADA), anyString()
		);
	}

	@Test
	void applyClassificationNaoDeveExigirUsuarioAutenticado() {
		Ticket ticket = newTicket(TicketPriority.MEDIA);

		when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
		when(ticketRepository.save(ticket)).thenReturn(ticket);
		when(authenticatedUserService.getUserId()).thenThrow(new UnauthorizedException("Autenticação necessária."));

		ticketService.applyClassification(
				ticket.getId(), TicketCategory.RH, TicketPriority.BAIXA, ClassificationOrigin.IA, null
		);

		verify(authenticatedUserService, never()).getRole();
	}

	@Test
	void applyClassificationDevePublicarAlertaQuandoPrioridadeViraAlta() {
		Ticket ticket = newTicket(TicketPriority.BAIXA);
		ArgumentCaptor<TicketNotificationEvent> captor = ArgumentCaptor.forClass(TicketNotificationEvent.class);

		when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
		when(ticketRepository.save(ticket)).thenReturn(ticket);
		when(authenticatedUserService.getUserId()).thenThrow(new UnauthorizedException("Autenticação necessária."));

		ticketService.applyClassification(
				ticket.getId(), TicketCategory.ACESSO, TicketPriority.ALTA, ClassificationOrigin.IA, null
		);

		verify(applicationEventPublisher).publishEvent(captor.capture());
		TicketNotificationEvent event = captor.getValue();

		assertThat(event.type()).isEqualTo(TicketNotificationType.CLASSIFICACAO_ATUALIZADA);
		assertThat(event.previousPriority()).isEqualTo(TicketPriority.BAIXA);
		assertThat(event.becameHighPriority()).isTrue();
		assertThat(event.actorId()).isNull();
	}
	@Test
	void applyClassificationNaoDeveRepetirAlertaQuandoPrioridadeJaEraAlta() {
		Ticket ticket = newTicket(TicketPriority.ALTA);
		ArgumentCaptor<TicketNotificationEvent> captor = ArgumentCaptor.forClass(TicketNotificationEvent.class);

		when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
		when(ticketRepository.save(ticket)).thenReturn(ticket);
		when(authenticatedUserService.getUserId()).thenThrow(new UnauthorizedException("Autenticação necessária."));

		ticketService.applyClassification(
				ticket.getId(), TicketCategory.ACESSO, TicketPriority.ALTA, ClassificationOrigin.IA, null
		);

		verify(applicationEventPublisher).publishEvent(captor.capture());

		assertThat(captor.getValue().becameHighPriority()).isFalse();
	}
	@Test
	void applyClassificationDeveNotificarSolicitanteEResponsavel() {
		Ticket ticket = newTicket(TicketPriority.MEDIA);
		User assignee = newAdmin();
		ticket.assignTo(assignee);
		ArgumentCaptor<TicketNotificationEvent> captor = ArgumentCaptor.forClass(TicketNotificationEvent.class);

		when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
		when(ticketRepository.save(ticket)).thenReturn(ticket);
		when(authenticatedUserService.getUserId()).thenThrow(new UnauthorizedException("Autenticação necessária."));

		ticketService.applyClassification(
				ticket.getId(), TicketCategory.ACESSO, TicketPriority.MEDIA, ClassificationOrigin.IA, null
		);

		verify(applicationEventPublisher).publishEvent(captor.capture());
		TicketNotificationEvent event = captor.getValue();

		assertThat(event.requester().id()).isEqualTo(ticket.getRequester().getId());
		assertThat(event.assignee().id()).isEqualTo(assignee.getId());
		assertThat(event.assignee().email()).isEqualTo(assignee.getEmail());
	}
	@Test
	void applyClassificationDeveLancarNotFoundQuandoChamadoNaoExistir() {
		UUID ticketId = UUID.fromString("2a5b0a5e-8f6d-4c1b-9a3e-7d4c2b1a0f9e");

		when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> ticketService.applyClassification(
				ticketId, TicketCategory.RH, TicketPriority.BAIXA, ClassificationOrigin.IA, null
		)).isInstanceOf(NotFoundException.class);
	}

	private Ticket newTicket(TicketPriority priority) {
		User requester = new User("Maria", "maria@fadex.org.br", "hash", Role.SOLICITANTE, false);
		ReflectionTestUtils.setField(requester, "id", UUID.randomUUID());

		Ticket ticket = new Ticket(
				"Chamado",
				"Descricao do chamado.",
				TicketCategory.OUTROS,
				priority,
				ClassificationOrigin.PENDENTE,
				requester
		);
		ReflectionTestUtils.setField(ticket, "id", UUID.randomUUID());

		return ticket;
	}

	private User newAdmin() {
		User admin = new User("Admin", "admin@fadex.org.br", "hash", Role.ADMIN, false);
		ReflectionTestUtils.setField(admin, "id", UUID.randomUUID());

		return admin;
	}


	@Test
	void updateStatusDeveExigirAdmin() {
		Ticket ticket = newTicket(TicketPriority.MEDIA);

		when(authenticatedUserService.getRole()).thenReturn(Role.SOLICITANTE);

		assertThatThrownBy(() -> ticketService.updateStatus(
				ticket.getId(), new TicketStatusUpdateDto(TicketStatus.EM_ANDAMENTO)
		)).isInstanceOf(ForbiddenException.class);

		verify(ticketRepository, never()).save(any(Ticket.class));
	}

	@Test
	void updateStatusDeveAlterarStatusERegistrarEvento() {
		Ticket ticket = newTicket(TicketPriority.MEDIA);
		User admin = newAdmin();
		stubAdminUpdate(ticket, admin);

		ticketService.updateStatus(ticket.getId(), new TicketStatusUpdateDto(TicketStatus.EM_ANDAMENTO));

		assertThat(ticket.getStatus()).isEqualTo(TicketStatus.EM_ANDAMENTO);
		verify(ticketEventService).record(
				eq(ticket), eq(admin), eq(TicketEventType.STATUS_ALTERADO), anyString()
		);
	}

	@Test
	void updateStatusDeveCarimbarResolvedAtSemFecharChamado() {
		Ticket ticket = newTicket(TicketPriority.MEDIA);
		stubAdminUpdate(ticket, newAdmin());

		ticketService.updateStatus(ticket.getId(), new TicketStatusUpdateDto(TicketStatus.RESOLVIDO));

		assertThat(ticket.getResolvedAt()).isNotNull();
		assertThat(ticket.getClosedAt()).isNull();
	}

	@Test
	void updateStatusDeveCarimbarResolvedAtAoFecharChamadoNuncaResolvido() {
		Ticket ticket = newTicket(TicketPriority.MEDIA);
		stubAdminUpdate(ticket, newAdmin());

		ticketService.updateStatus(ticket.getId(), new TicketStatusUpdateDto(TicketStatus.FECHADO));

		assertThat(ticket.getClosedAt()).isNotNull();
		assertThat(ticket.getResolvedAt()).isEqualTo(ticket.getClosedAt());
	}

	@Test
	void updateStatusDevePreservarResolvedAtOriginalAoFecharChamadoJaResolvido() {
		Ticket ticket = newTicket(TicketPriority.MEDIA);
		LocalDateTime originalResolvedAt = LocalDateTime.of(2026, 1, 1, 0, 0);
		ticket.changeStatus(TicketStatus.RESOLVIDO);
		ticket.markResolved(originalResolvedAt);
		stubAdminUpdate(ticket, newAdmin());

		ticketService.updateStatus(ticket.getId(), new TicketStatusUpdateDto(TicketStatus.FECHADO));

		assertThat(ticket.getResolvedAt()).isEqualTo(originalResolvedAt);
		assertThat(ticket.getClosedAt()).isNotNull();
	}

	@Test
	void updateStatusDeveSobrescreverResolvedAtNaSegundaResolucao() {
		Ticket ticket = newTicket(TicketPriority.MEDIA);
		LocalDateTime primeiraResolucao = LocalDateTime.of(2026, 1, 1, 0, 0);
		ticket.changeStatus(TicketStatus.RESOLVIDO);
		ticket.markResolved(primeiraResolucao);
		stubAdminUpdate(ticket, newAdmin());

		ticketService.updateStatus(ticket.getId(), new TicketStatusUpdateDto(TicketStatus.EM_ANDAMENTO));
		ticketService.updateStatus(ticket.getId(), new TicketStatusUpdateDto(TicketStatus.RESOLVIDO));

		assertThat(ticket.getResolvedAt()).isAfter(primeiraResolucao);
	}

	@Test
	void updateStatusDeveRecusarChamadoFechado() {
		Ticket ticket = newTicket(TicketPriority.MEDIA);
		ticket.changeStatus(TicketStatus.FECHADO);

		when(authenticatedUserService.getRole()).thenReturn(Role.ADMIN);
		when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

		assertThatThrownBy(() -> ticketService.updateStatus(
				ticket.getId(), new TicketStatusUpdateDto(TicketStatus.EM_ANDAMENTO)
		)).isInstanceOf(ConflictException.class);
	}

	@Test
	void updateStatusDeveRecusarTransicaoParaOMesmoStatus() {
		Ticket ticket = newTicket(TicketPriority.MEDIA);

		when(authenticatedUserService.getRole()).thenReturn(Role.ADMIN);
		when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

		assertThatThrownBy(() -> ticketService.updateStatus(
				ticket.getId(), new TicketStatusUpdateDto(TicketStatus.ABERTO)
		)).isInstanceOf(ConflictException.class);
	}

	@Test
	void updateStatusDeveRecusarTransicaoDeResolvidoParaAberto() {
		Ticket ticket = newTicket(TicketPriority.MEDIA);
		ticket.changeStatus(TicketStatus.RESOLVIDO);

		when(authenticatedUserService.getRole()).thenReturn(Role.ADMIN);
		when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

		assertThatThrownBy(() -> ticketService.updateStatus(
				ticket.getId(), new TicketStatusUpdateDto(TicketStatus.ABERTO)
		)).isInstanceOf(ConflictException.class);
	}

	@Test
	void cancelDeveCancelarChamadoAbertoQuandoAdmin() {
		Ticket ticket = newTicket(TicketPriority.MEDIA);
		User admin = newAdmin();
		stubAdminUpdate(ticket, admin);

		TicketDto response = ticketService.cancel(ticket.getId());

		assertThat(response.status()).isEqualTo(TicketStatus.CANCELADO);
		assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CANCELADO);
		verify(ticketEventService).record(
				eq(ticket), eq(admin), eq(TicketEventType.CHAMADO_CANCELADO), anyString()
		);
	}

	@Test
	void cancelDeveCancelarChamadoEmAndamentoQuandoAdmin() {
		Ticket ticket = newTicket(TicketPriority.MEDIA);
		ticket.changeStatus(TicketStatus.EM_ANDAMENTO);
		stubAdminUpdate(ticket, newAdmin());

		ticketService.cancel(ticket.getId());

		assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CANCELADO);
	}

	/**
	 * Carimbo nenhum: e o que mantem o cancelado fora da media de fechamento e dos contadores de
	 * chamado fechado, sem nenhum codigo defensivo nos indicadores.
	 */
	@Test
	void cancelDeveManterResolvedAtEClosedAtNulos() {
		Ticket ticket = newTicket(TicketPriority.MEDIA);
		stubAdminUpdate(ticket, newAdmin());

		ticketService.cancel(ticket.getId());

		assertThat(ticket.getResolvedAt()).isNull();
		assertThat(ticket.getClosedAt()).isNull();
	}

	@Test
	void cancelDevePublicarNotificacaoDeMudancaDeStatus() {
		Ticket ticket = newTicket(TicketPriority.MEDIA);
		stubAdminUpdate(ticket, newAdmin());
		ArgumentCaptor<TicketNotificationEvent> eventCaptor =
				ArgumentCaptor.forClass(TicketNotificationEvent.class);

		ticketService.cancel(ticket.getId());

		verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
		assertThat(eventCaptor.getValue().type()).isEqualTo(TicketNotificationType.STATUS_ALTERADO);
		assertThat(eventCaptor.getValue().ticket().status()).isEqualTo(TicketStatus.CANCELADO);
	}

	@Test
	void cancelDevePermitirQueSolicitanteCanceleOProprioChamadoAberto() {
		Ticket ticket = newTicket(TicketPriority.MEDIA);
		User requester = ticket.getRequester();

		when(authenticatedUserService.getRole()).thenReturn(Role.SOLICITANTE);
		when(authenticatedUserService.getUserId()).thenReturn(requester.getId());
		when(userService.findEntityById(requester.getId())).thenReturn(requester);
		when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
		when(ticketRepository.save(ticket)).thenReturn(ticket);

		ticketService.cancel(ticket.getId());

		assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CANCELADO);
		verify(ticketEventService).record(
				eq(ticket), eq(requester), eq(TicketEventType.CHAMADO_CANCELADO), anyString()
		);
	}

	@Test
	void cancelDeveNegarChamadoDeOutroSolicitante() {
		Ticket ticket = newTicket(TicketPriority.MEDIA);

		when(authenticatedUserService.getRole()).thenReturn(Role.SOLICITANTE);
		when(authenticatedUserService.getUserId()).thenReturn(UUID.randomUUID());
		when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

		assertThatThrownBy(() -> ticketService.cancel(ticket.getId()))
				.isInstanceOf(ForbiddenException.class);

		verify(ticketRepository, never()).save(any(Ticket.class));
	}

	/**
	 * A partir de EM_ANDAMENTO existe trabalho de outra pessoa em curso: quem pede o cancelamento
	 * comenta, e o ADMIN cancela.
	 */
	@Test
	void cancelDeveRecusarSolicitanteEmChamadoJaEmAtendimento() {
		Ticket ticket = newTicket(TicketPriority.MEDIA);
		ticket.changeStatus(TicketStatus.EM_ANDAMENTO);
		User requester = ticket.getRequester();

		when(authenticatedUserService.getRole()).thenReturn(Role.SOLICITANTE);
		when(authenticatedUserService.getUserId()).thenReturn(requester.getId());
		when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

		assertThatThrownBy(() -> ticketService.cancel(ticket.getId()))
				.isInstanceOf(ConflictException.class);

		verify(ticketRepository, never()).save(any(Ticket.class));
	}

	@Test
	void cancelDeveRecusarChamadoJaCancelado() {
		Ticket ticket = newTicket(TicketPriority.MEDIA);
		ticket.changeStatus(TicketStatus.CANCELADO);

		when(authenticatedUserService.getRole()).thenReturn(Role.ADMIN);
		when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

		assertThatThrownBy(() -> ticketService.cancel(ticket.getId()))
				.isInstanceOf(ConflictException.class);
	}

	@Test
	void cancelDeveRecusarChamadoResolvidoOuFechado() {
		Ticket resolvido = newTicket(TicketPriority.MEDIA);
		resolvido.changeStatus(TicketStatus.RESOLVIDO);
		Ticket fechado = newTicket(TicketPriority.MEDIA);
		fechado.changeStatus(TicketStatus.FECHADO);

		when(authenticatedUserService.getRole()).thenReturn(Role.ADMIN);
		when(ticketRepository.findById(resolvido.getId())).thenReturn(Optional.of(resolvido));
		when(ticketRepository.findById(fechado.getId())).thenReturn(Optional.of(fechado));

		assertThatThrownBy(() -> ticketService.cancel(resolvido.getId()))
				.isInstanceOf(ConflictException.class);
		assertThatThrownBy(() -> ticketService.cancel(fechado.getId()))
				.isInstanceOf(ConflictException.class);
	}

	@Test
	void updateStatusDeveCancelarChamadoQuandoAdminUsaAMatriz() {
		Ticket ticket = newTicket(TicketPriority.MEDIA);
		stubAdminUpdate(ticket, newAdmin());

		ticketService.updateStatus(ticket.getId(), new TicketStatusUpdateDto(TicketStatus.CANCELADO));

		assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CANCELADO);
		assertThat(ticket.getClosedAt()).isNull();
	}

	@Test
	void updateAssigneeDeveRecusarChamadoCancelado() {
		Ticket ticket = newTicket(TicketPriority.MEDIA);
		ticket.changeStatus(TicketStatus.CANCELADO);

		when(authenticatedUserService.getRole()).thenReturn(Role.ADMIN);
		when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

		assertThatThrownBy(() -> ticketService.updateAssignee(
				ticket.getId(), new TicketAssigneeUpdateDto(UUID.randomUUID())
		)).isInstanceOf(ConflictException.class);
	}

	@Test
	void removeAssigneeDeveRecusarChamadoCancelado() {
		Ticket ticket = newTicket(TicketPriority.MEDIA);
		ticket.assignTo(newAdmin());
		ticket.changeStatus(TicketStatus.CANCELADO);

		when(authenticatedUserService.getRole()).thenReturn(Role.ADMIN);
		when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

		assertThatThrownBy(() -> ticketService.removeAssignee(ticket.getId()))
				.isInstanceOf(ConflictException.class);
	}

	@Test
	void updateAssigneeDeveExigirAdmin() {
		Ticket ticket = newTicket(TicketPriority.MEDIA);

		when(authenticatedUserService.getRole()).thenReturn(Role.SOLICITANTE);

		assertThatThrownBy(() -> ticketService.updateAssignee(
				ticket.getId(), new TicketAssigneeUpdateDto(UUID.randomUUID())
		)).isInstanceOf(ForbiddenException.class);
	}

	@Test
	void updateAssigneeDeveAtribuirECarimbarAssignedAt() {
		Ticket ticket = newTicket(TicketPriority.MEDIA);
		User admin = newAdmin();

		when(authenticatedUserService.getRole()).thenReturn(Role.ADMIN);
		when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
		when(ticketRepository.save(ticket)).thenReturn(ticket);
		when(userService.findEntityById(admin.getId())).thenReturn(admin);

		ticketService.updateAssignee(ticket.getId(), new TicketAssigneeUpdateDto(admin.getId()));

		assertThat(ticket.getAssignee()).isEqualTo(admin);
		assertThat(ticket.getAssignedAt()).isNotNull();
		verify(ticketEventService).record(
				eq(ticket), eq(admin), eq(TicketEventType.RESPONSAVEL_ATRIBUIDO), anyString()
		);
	}

	@Test
	void updateAssigneeDeveRecusarChamadoQueJaTemResponsavel() {
		Ticket ticket = newTicket(TicketPriority.MEDIA);
		User admin = newAdmin();
		ticket.assignTo(admin);

		when(authenticatedUserService.getRole()).thenReturn(Role.ADMIN);
		when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

		assertThatThrownBy(() -> ticketService.updateAssignee(
				ticket.getId(), new TicketAssigneeUpdateDto(admin.getId())
		)).isInstanceOf(ConflictException.class);
	}

	@Test
	void updateAssigneeDeveRecusarResponsavelSemPapelAdmin() {
		Ticket ticket = newTicket(TicketPriority.MEDIA);
		User solicitante = new User("Outro", "outro@fadex.org.br", "hash", Role.SOLICITANTE, false);
		ReflectionTestUtils.setField(solicitante, "id", UUID.randomUUID());

		when(authenticatedUserService.getRole()).thenReturn(Role.ADMIN);
		when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
		when(userService.findEntityById(solicitante.getId())).thenReturn(solicitante);

		assertThatThrownBy(() -> ticketService.updateAssignee(
				ticket.getId(), new TicketAssigneeUpdateDto(solicitante.getId())
		)).isInstanceOf(ConflictException.class);
	}

	@Test
	void updateAssigneeDeveRecusarChamadoFechado() {
		Ticket ticket = newTicket(TicketPriority.MEDIA);
		ticket.changeStatus(TicketStatus.FECHADO);

		when(authenticatedUserService.getRole()).thenReturn(Role.ADMIN);
		when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

		assertThatThrownBy(() -> ticketService.updateAssignee(
				ticket.getId(), new TicketAssigneeUpdateDto(UUID.randomUUID())
		)).isInstanceOf(ConflictException.class);
	}

	@Test
	void removeAssigneeDeveRemoverResponsavelEPreservarAssignedAt() {
		Ticket ticket = newTicket(TicketPriority.MEDIA);
		User admin = newAdmin();
		LocalDateTime originalAssignedAt = LocalDateTime.of(2026, 1, 1, 0, 0);
		ticket.assignTo(admin);
		ticket.markAssigned(originalAssignedAt);

		when(authenticatedUserService.getRole()).thenReturn(Role.ADMIN);
		when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
		when(ticketRepository.save(ticket)).thenReturn(ticket);

		ticketService.removeAssignee(ticket.getId());

		assertThat(ticket.getAssignee()).isNull();
		assertThat(ticket.getAssignedAt()).isEqualTo(originalAssignedAt);
		verify(ticketEventService).record(
				eq(ticket), eq(admin), eq(TicketEventType.RESPONSAVEL_REMOVIDO), anyString()
		);
	}

	@Test
	void removeAssigneeDeveRecusarChamadoSemResponsavel() {
		Ticket ticket = newTicket(TicketPriority.MEDIA);

		when(authenticatedUserService.getRole()).thenReturn(Role.ADMIN);
		when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

		assertThatThrownBy(() -> ticketService.removeAssignee(ticket.getId()))
				.isInstanceOf(ConflictException.class);
	}

	private void stubAdminUpdate(Ticket ticket, User admin) {
		when(authenticatedUserService.getRole()).thenReturn(Role.ADMIN);
		when(authenticatedUserService.getUserId()).thenReturn(admin.getId());
		when(userService.findEntityById(admin.getId())).thenReturn(admin);
		when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
		when(ticketRepository.save(ticket)).thenReturn(ticket);
	}
}

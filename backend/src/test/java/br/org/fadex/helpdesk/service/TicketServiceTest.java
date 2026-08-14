package br.org.fadex.helpdesk.service;

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
import br.org.fadex.helpdesk.model.ticket.TicketFilter;
import br.org.fadex.helpdesk.model.user.User;
import br.org.fadex.helpdesk.exception.ForbiddenException;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
	private TicketEventService ticketEventService;

	private AccessControlService accessControlService;

	private TicketService ticketService;

	@BeforeEach
	void setUp() {
		accessControlService = new AccessControlService(authenticatedUserService);
		ticketService = new TicketService(ticketRepository, userService, accessControlService, ticketEventService);
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
	}

	@Test
	void deveGravarEventoAoCriarChamado() {
		UUID authenticatedUserId = UUID.fromString("71e9c3d9-53b2-4c4e-9803-c504754dbb45");
		User requester = new User("Maria", "maria@fadex.org.br", "hash", Role.SOLICITANTE, false);
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
}

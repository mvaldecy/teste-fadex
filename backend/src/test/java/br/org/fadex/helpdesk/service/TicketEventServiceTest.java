package br.org.fadex.helpdesk.service;

import br.org.fadex.helpdesk.exception.ForbiddenException;
import br.org.fadex.helpdesk.exception.NotFoundException;
import br.org.fadex.helpdesk.model.enums.ClassificationOrigin;
import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketEventType;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import br.org.fadex.helpdesk.model.event.TicketEvent;
import br.org.fadex.helpdesk.model.event.TicketEventFields;
import br.org.fadex.helpdesk.model.event.TicketEventFilter;
import br.org.fadex.helpdesk.model.event.TicketEventMinDto;
import br.org.fadex.helpdesk.model.ticket.Ticket;
import br.org.fadex.helpdesk.model.ticket.TicketFields;
import br.org.fadex.helpdesk.model.user.User;
import br.org.fadex.helpdesk.repository.TicketEventRepository;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
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
class TicketEventServiceTest {

	@Mock
	private TicketEventRepository ticketEventRepository;

	@Mock
	private TicketRepository ticketRepository;

	@Mock
	private AuthenticatedUserService authenticatedUserService;

	private AccessControlService accessControlService;

	private TicketEventService ticketEventService;

	@BeforeEach
	void setUp() {
		accessControlService = new AccessControlService(authenticatedUserService);
		ticketEventService = new TicketEventService(ticketEventRepository, ticketRepository, accessControlService);
	}

	@Test
	void deveGravarEventoComDadosInformados() {
		User actor = new User("Maria", "maria@fadex.org.br", "hash", Role.SOLICITANTE, false);
		Ticket ticket = buildTicket(actor);
		ArgumentCaptor<TicketEvent> eventCaptor = ArgumentCaptor.forClass(TicketEvent.class);

		ticketEventService.record(ticket, actor, TicketEventType.CHAMADO_CRIADO, "Chamado criado.");

		verify(ticketEventRepository).save(eventCaptor.capture());
		TicketEvent eventToSave = eventCaptor.getValue();

		assertThat(eventToSave.getTicket()).isEqualTo(ticket);
		assertThat(eventToSave.getActor()).isEqualTo(actor);
		assertThat(eventToSave.getType()).isEqualTo(TicketEventType.CHAMADO_CRIADO);
		assertThat(eventToSave.getDescription()).isEqualTo("Chamado criado.");
		assertThat(eventToSave.getMetadata()).isNull();
	}

	@Test
	void deveListarEventosDoChamadoComFiltroDoPath() {
		UUID ticketId = UUID.fromString("e05968eb-a518-4ff9-8aa2-2d7a53497e45");
		UUID requesterId = UUID.fromString("71e9c3d9-53b2-4c4e-9803-c504754dbb45");
		User requester = new User("Maria", "maria@fadex.org.br", "hash", Role.SOLICITANTE, false);
		Ticket ticket = buildTicket(requester);
		TicketEvent event = new TicketEvent(
				ticket,
				requester,
				TicketEventType.COMENTARIO_ADICIONADO,
				"Comentario adicionado.",
				null
		);
		PageRequest pageable = PageRequest.of(0, 10);
		ArgumentCaptor<Specification<TicketEvent>> specificationCaptor = ArgumentCaptor.forClass(Specification.class);

		ReflectionTestUtils.setField(requester, "id", requesterId);
		when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
		when(authenticatedUserService.getRole()).thenReturn(Role.SOLICITANTE);
		when(authenticatedUserService.getUserId()).thenReturn(requesterId);
		when(ticketEventRepository.findAll(anyEventSpecification(), eq(pageable)))
				.thenReturn(new PageImpl<>(List.of(event), pageable, 1));

		Page<TicketEventMinDto> response = ticketEventService.findAll(
				ticketId,
				new TicketEventFilter(null, null, TicketEventType.COMENTARIO_ADICIONADO, null),
				pageable
		);

		verify(ticketRepository).findById(ticketId);
		verify(ticketEventRepository).findAll(specificationCaptor.capture(), eq(pageable));
		assertTicketEventSpecificationFiltersTicketAndType(
				specificationCaptor.getValue(),
				ticketId,
				TicketEventType.COMENTARIO_ADICIONADO
		);
		assertThat(response.getContent()).hasSize(1);
		assertThat(response.getContent().get(0).type()).isEqualTo(TicketEventType.COMENTARIO_ADICIONADO);
		assertThat(response.getContent().get(0).description()).isEqualTo("Comentario adicionado.");
	}

	@Test
	void deveLancarNotFoundAoListarEventosDeChamadoInexistente() {
		UUID ticketId = UUID.fromString("e05968eb-a518-4ff9-8aa2-2d7a53497e45");
		PageRequest pageable = PageRequest.of(0, 10);

		when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> ticketEventService.findAll(
				ticketId,
				new TicketEventFilter(null, null, null, null),
				pageable
		))
				.isInstanceOf(NotFoundException.class)
				.hasMessage("Chamado nao encontrado.");

		verify(ticketEventRepository, never()).findAll(anyEventSpecification(), eq(pageable));
	}

	@Test
	void deveNegarListagemDeEventosDeChamadoDeOutroSolicitante() {
		UUID ticketId = UUID.fromString("e05968eb-a518-4ff9-8aa2-2d7a53497e45");
		UUID authenticatedUserId = UUID.fromString("71e9c3d9-53b2-4c4e-9803-c504754dbb45");
		UUID requesterId = UUID.fromString("b9ff9b29-e32b-4a51-a586-0119beeb0cd5");
		User requester = new User("Joao", "joao@fadex.org.br", "hash", Role.SOLICITANTE, false);
		Ticket ticket = buildTicket(requester);
		PageRequest pageable = PageRequest.of(0, 10);

		ReflectionTestUtils.setField(requester, "id", requesterId);
		when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
		when(authenticatedUserService.getRole()).thenReturn(Role.SOLICITANTE);
		when(authenticatedUserService.getUserId()).thenReturn(authenticatedUserId);

		assertThatThrownBy(() -> ticketEventService.findAll(
				ticketId,
				new TicketEventFilter(null, null, null, null),
				pageable
		))
				.isInstanceOf(ForbiddenException.class)
				.hasMessage("Acesso negado ao recurso solicitado.");

		verify(ticketEventRepository, never()).findAll(anyEventSpecification(), eq(pageable));
	}

	private Ticket buildTicket(User requester) {
		return new Ticket(
				"Erro ao acessar sistema",
				"Nao consigo acessar o sistema interno.",
				TicketCategory.OUTROS,
				TicketPriority.MEDIA,
				ClassificationOrigin.PENDENTE,
				requester
		);
	}

	private Specification<TicketEvent> anyEventSpecification() {
		return any();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private void assertTicketEventSpecificationFiltersTicketAndType(
			Specification<TicketEvent> specification,
			UUID expectedTicketId,
			TicketEventType expectedType
	) {
		Root<TicketEvent> root = mock(Root.class);
		CriteriaQuery<?> query = mock(CriteriaQuery.class);
		CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
		Path<Object> ticketPath = mock(Path.class);
		Path<Object> ticketIdPath = mock(Path.class);
		Path<Object> typePath = mock(Path.class);
		Predicate ticketPredicate = mock(Predicate.class);
		Predicate typePredicate = mock(Predicate.class);
		Predicate combinedPredicate = mock(Predicate.class);

		when(root.get(TicketEventFields.TICKET)).thenReturn((Path) ticketPath);
		when(ticketPath.get(TicketFields.ID)).thenReturn((Path) ticketIdPath);
		when(root.get(TicketEventFields.TYPE)).thenReturn((Path) typePath);
		when(criteriaBuilder.equal(ticketIdPath, expectedTicketId)).thenReturn(ticketPredicate);
		when(criteriaBuilder.equal(typePath, expectedType)).thenReturn(typePredicate);
		when(criteriaBuilder.and(any(Predicate[].class))).thenReturn(combinedPredicate);

		specification.toPredicate(root, query, criteriaBuilder);

		verify(criteriaBuilder).equal(ticketIdPath, expectedTicketId);
		verify(criteriaBuilder).equal(typePath, expectedType);
	}
}

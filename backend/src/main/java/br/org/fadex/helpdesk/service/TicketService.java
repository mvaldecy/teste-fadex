package br.org.fadex.helpdesk.service;

import br.org.fadex.helpdesk.ai.job.AiJobService;
import br.org.fadex.helpdesk.exception.ConflictException;
import br.org.fadex.helpdesk.exception.NotFoundException;
import br.org.fadex.helpdesk.model.enums.ClassificationOrigin;
import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketEventType;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import br.org.fadex.helpdesk.model.enums.TicketStatus;
import br.org.fadex.helpdesk.model.ticket.Ticket;
import br.org.fadex.helpdesk.model.ticket.TicketCreationDto;
import br.org.fadex.helpdesk.model.ticket.TicketDto;
import br.org.fadex.helpdesk.model.ticket.TicketAssigneeUpdateDto;
import br.org.fadex.helpdesk.model.ticket.TicketFilter;
import br.org.fadex.helpdesk.model.ticket.TicketStatusTransition;
import br.org.fadex.helpdesk.model.ticket.TicketStatusUpdateDto;
import br.org.fadex.helpdesk.model.ticket.TicketMapper;
import br.org.fadex.helpdesk.model.ticket.TicketMinDto;
import br.org.fadex.helpdesk.model.user.User;
import br.org.fadex.helpdesk.repository.TicketRepository;
import br.org.fadex.helpdesk.repository.TicketSpecification;
import br.org.fadex.helpdesk.security.AccessControlService;
import br.org.fadex.helpdesk.sse.model.NotificationAudience;
import br.org.fadex.helpdesk.sse.model.NotificationEventName;
import br.org.fadex.helpdesk.sse.model.NotificationMessage;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class TicketService {

	private final TicketRepository ticketRepository;
	private final UserService userService;
	private final AccessControlService accessControlService;
	private final TicketEventService ticketEventService;
	private final AiJobService aiJobService;
	private final ApplicationEventPublisher applicationEventPublisher;

	public TicketService(
			TicketRepository ticketRepository,
			UserService userService,
			AccessControlService accessControlService,
			TicketEventService ticketEventService,
			AiJobService aiJobService,
			ApplicationEventPublisher applicationEventPublisher
	) {
		this.ticketRepository = ticketRepository;
		this.userService = userService;
		this.accessControlService = accessControlService;
		this.ticketEventService = ticketEventService;
		this.aiJobService = aiJobService;
		this.applicationEventPublisher = applicationEventPublisher;
	}

	@Transactional(readOnly = true)
	public Page<TicketMinDto> findAll(TicketFilter filter, Pageable pageable) {
		TicketFilter resolvedFilter = resolveFilterByRole(filter);
		Specification<Ticket> spec = TicketSpecification.createSpecification(resolvedFilter);
		Page<Ticket> tickets = ticketRepository.findAll(spec, pageable);
		Page<TicketMinDto> response = tickets.map(TicketMapper::toMinDto);

		return response;
	}

	@Transactional(readOnly = true)
	public TicketDto findById(UUID id) {
		Ticket ticket = findEntityById(id);
		accessControlService.assertCanAccessTicket(ticket);
		TicketDto response = TicketMapper.toResponseDto(ticket);

		return response;
	}

	@Transactional(readOnly = true)
	public Ticket findEntityById(UUID id) {
		return ticketRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("Chamado não encontrado."));
	}

	@Transactional
	public TicketDto create(TicketCreationDto ticketCreationDto) {
		UUID authenticatedUserId = accessControlService.getAuthenticatedUserId();
		User requester = userService.findEntityById(authenticatedUserId);
		Ticket ticket = TicketMapper.toEntity(ticketCreationDto, requester);
		Ticket savedTicket = ticketRepository.save(ticket);
		ticketEventService.record(savedTicket, requester, TicketEventType.CHAMADO_CRIADO, "Chamado criado.");
		aiJobService.enqueueTicketJobs(savedTicket);
		TicketDto response = TicketMapper.toResponseDto(savedTicket);

		return response;
	}

	@Transactional
	public TicketDto updateStatus(UUID id, TicketStatusUpdateDto ticketStatusUpdateDto) {
		accessControlService.assertAdmin();

		Ticket ticket = findEntityById(id);
		TicketStatus currentStatus = ticket.getStatus();
		TicketStatus newStatus = ticketStatusUpdateDto.status();

		assertTransitionAllowed(currentStatus, newStatus);

		LocalDateTime now = LocalDateTime.now();

		ticket.changeStatus(newStatus);

		if (newStatus == TicketStatus.RESOLVIDO) {
			ticket.markResolved(now);
		}

		if (newStatus == TicketStatus.FECHADO) {
			ticket.markClosed(now);

			if (ticket.getResolvedAt() == null) {
				ticket.markResolved(now);
			}
		}

		Ticket savedTicket = ticketRepository.save(ticket);
		User actor = resolveActor();
		String description = "Status alterado de " + currentStatus.getLabel()
				+ " para " + newStatus.getLabel() + ".";

		ticketEventService.record(savedTicket, actor, TicketEventType.STATUS_ALTERADO, description);
		publishTicketUpdated(savedTicket);

		TicketDto response = TicketMapper.toResponseDto(savedTicket);

		return response;
	}

	@Transactional
	public TicketDto updateAssignee(UUID id, TicketAssigneeUpdateDto ticketAssigneeUpdateDto) {
		accessControlService.assertAdmin();

		Ticket ticket = findEntityById(id);

		assertTicketIsNotClosed(ticket);

		if (ticket.getAssignee() != null) {
			throw new ConflictException(
					"O chamado ja possui responsavel. Remova a atribuicao atual antes de atribuir outro."
			);
		}

		User assignee = userService.findEntityById(ticketAssigneeUpdateDto.assigneeId());

		if (assignee.getRole() != Role.ADMIN) {
			throw new ConflictException("O responsavel pelo chamado precisa ter papel de administrador.");
		}

		ticket.assignTo(assignee);

		if (ticket.getAssignedAt() == null) {
			ticket.markAssigned(LocalDateTime.now());
		}

		Ticket savedTicket = ticketRepository.save(ticket);
		String description = "Responsavel atribuido: " + assignee.getName() + ".";

		ticketEventService.record(
				savedTicket, assignee, TicketEventType.RESPONSAVEL_ATRIBUIDO, description
		);
		publishTicketUpdated(savedTicket);

		TicketDto response = TicketMapper.toResponseDto(savedTicket);

		return response;
	}

	@Transactional
	public TicketDto removeAssignee(UUID id) {
		accessControlService.assertAdmin();

		Ticket ticket = findEntityById(id);

		assertTicketIsNotClosed(ticket);

		User previousAssignee = ticket.getAssignee();

		if (previousAssignee == null) {
			throw new ConflictException("O chamado nao possui responsavel atribuido.");
		}

		ticket.unassign();

		Ticket savedTicket = ticketRepository.save(ticket);
		String description = "Atribuicao removida de " + previousAssignee.getName() + ".";

		ticketEventService.record(
				savedTicket, previousAssignee, TicketEventType.RESPONSAVEL_REMOVIDO, description
		);
		publishTicketUpdated(savedTicket);

		TicketDto response = TicketMapper.toResponseDto(savedTicket);

		return response;
	}

	private void assertTransitionAllowed(TicketStatus currentStatus, TicketStatus newStatus) {
		if (currentStatus == TicketStatus.FECHADO) {
			throw new ConflictException("Chamado fechado nao pode ser reaberto.");
		}

		if (currentStatus == newStatus) {
			throw new ConflictException("O chamado ja esta com o status " + newStatus.getLabel() + ".");
		}

		if (!TicketStatusTransition.isAllowed(currentStatus, newStatus)) {
			throw new ConflictException(
					"Transicao de " + currentStatus.getLabel() + " para " + newStatus.getLabel()
							+ " nao e permitida."
			);
		}
	}

	private void assertTicketIsNotClosed(Ticket ticket) {
		if (ticket.getStatus() == TicketStatus.FECHADO) {
			throw new ConflictException("Chamado fechado nao pode ser alterado.");
		}
	}

	/**
	 * Unica porta de escrita de classificacao no chamado.
	 *
	 * Nao chama {@code assertAdmin()} de proposito: roda tambem no worker de IA, sem usuario
	 * autenticado no contexto. A autorizacao do endpoint de revisao e responsabilidade da camada
	 * que o expoe.
	 */
	@Transactional
	public void applyClassification(
			UUID ticketId,
			TicketCategory category,
			TicketPriority priority,
			ClassificationOrigin origin,
			String justification
	) {
		Ticket ticket = findEntityById(ticketId);
		TicketPriority previousPriority = ticket.getPriority();

		ticket.applyClassification(category, priority, origin, justification);

		Ticket savedTicket = ticketRepository.save(ticket);
		User actor = resolveActor();
		String description = "Classificacao atualizada para " + category.getLabel()
				+ " / " + priority.getLabel() + " (" + origin.getLabel() + ").";

		ticketEventService.record(savedTicket, actor, TicketEventType.CLASSIFICACAO_ATUALIZADA, description);
		publishTicketUpdated(savedTicket);
		publishHighPriorityAlertIfNeeded(savedTicket, previousPriority);
	}

	private User resolveActor() {
		Optional<UUID> authenticatedUserId = accessControlService.findAuthenticatedUserId();

		return authenticatedUserId.map(userService::findEntityById).orElse(null);
	}

	private void publishTicketUpdated(Ticket ticket) {
		Set<UUID> userIds = new HashSet<>();
		userIds.add(ticket.getRequester().getId());

		User assignee = ticket.getAssignee();

		if (assignee != null) {
			userIds.add(assignee.getId());
		}

		NotificationMessage message = NotificationMessage.of(
				NotificationEventName.CHAMADO_ATUALIZADO,
				TicketMapper.toMinDto(ticket),
				new NotificationAudience.Users(userIds)
		);

		applicationEventPublisher.publishEvent(message);
	}

	private void publishHighPriorityAlertIfNeeded(Ticket ticket, TicketPriority previousPriority) {
		boolean becameHighPriority = ticket.getPriority() == TicketPriority.ALTA
				&& previousPriority != TicketPriority.ALTA;

		if (!becameHighPriority) {
			return;
		}

		NotificationMessage message = NotificationMessage.of(
				NotificationEventName.CHAMADO_ALTA_PRIORIDADE,
				TicketMapper.toMinDto(ticket),
				new NotificationAudience.Roles(Set.of(Role.ADMIN))
		);

		applicationEventPublisher.publishEvent(message);
	}

	private TicketFilter resolveFilterByRole(TicketFilter filter) {
		if (accessControlService.isAdmin()) {
			return filter;
		}

		return new TicketFilter(
				filter.status(),
				filter.priority(),
				filter.category(),
				accessControlService.getAuthenticatedUserId(),
				filter.assigneeId(),
				filter.search()
		);
	}
}

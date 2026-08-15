package br.org.fadex.helpdesk.service;

import br.org.fadex.helpdesk.ai.job.AiJobService;
import br.org.fadex.helpdesk.exception.ConflictException;
import br.org.fadex.helpdesk.exception.ForbiddenException;
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
import br.org.fadex.helpdesk.notification.event.NotificationRecipient;
import br.org.fadex.helpdesk.notification.event.TicketNotificationEvent;
import br.org.fadex.helpdesk.notification.event.TicketNotificationType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
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
		publishTicketNotification(savedTicket, TicketNotificationType.CHAMADO_CRIADO, null, "Chamado criado.");
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

		String description = "Status alterado de " + currentStatus.getLabel()
				+ " para " + newStatus.getLabel() + ".";

		return applyStatusChange(ticket, newStatus, TicketEventType.STATUS_ALTERADO, description);
	}

	/**
	 * Cancelamento de chamado: exclusao logica.
	 *
	 * O chamado sai do fluxo e continua na base — historico, comentarios e o que a IA classificou
	 * ficam. Nao ha remocao fisica em lugar nenhum: o valor deste sistema e o rastro.
	 *
	 * ADMIN cancela qualquer chamado; SOLICITANTE cancela o proprio e apenas enquanto ABERTO, porque
	 * a partir de EM_ANDAMENTO existe trabalho de outra pessoa em curso. Papel indevido e 403;
	 * estado que nao aceita cancelamento e 409, e quem decide isso e a matriz.
	 */
	@Transactional
	public TicketDto cancel(UUID id) {
		Ticket ticket = findEntityById(id);

		assertCanCancel(ticket);

		// A mensagem generica de chamado fechado fala em reabertura, que responde a uma acao que nao
		// e a de quem clicou em cancelar.
		if (ticket.getStatus() == TicketStatus.FECHADO) {
			throw new ConflictException("Chamado fechado nao pode ser cancelado.");
		}

		assertTransitionAllowed(ticket.getStatus(), TicketStatus.CANCELADO);

		return applyStatusChange(
				ticket,
				TicketStatus.CANCELADO,
				TicketEventType.CHAMADO_CANCELADO,
				"Chamado cancelado."
		);
	}

	/**
	 * Caminho unico de mudanca de status: carimbo, gravacao, evento de historico e notificacao.
	 *
	 * Cancelamento nao carimba nada de proposito. {@code resolvedAt} e {@code closedAt} nulos sao o
	 * que mantem o chamado cancelado fora da media de fechamento e dos contadores de chamado
	 * fechado, sem nenhum tratamento defensivo nos indicadores.
	 */
	private TicketDto applyStatusChange(
			Ticket ticket,
			TicketStatus newStatus,
			TicketEventType eventType,
			String description
	) {
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

		ticketEventService.record(savedTicket, actor, eventType, description);
		publishTicketNotification(
				savedTicket, TicketNotificationType.STATUS_ALTERADO, savedTicket.getPriority(), description
		);

		TicketDto response = TicketMapper.toResponseDto(savedTicket);

		return response;
	}

	private void assertCanCancel(Ticket ticket) {
		if (accessControlService.isAdmin()) {
			return;
		}

		accessControlService.assertCanAccessTicket(ticket);

		if (ticket.getStatus() != TicketStatus.ABERTO) {
			throw new ConflictException(
					"Chamado ja em atendimento so pode ser cancelado por um administrador."
			);
		}
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
		publishTicketNotification(
				savedTicket, TicketNotificationType.RESPONSAVEL_ATRIBUIDO, savedTicket.getPriority(), description
		);

		TicketDto response = TicketMapper.toResponseDto(savedTicket);

		return response;
	}

	/**
	 * Recusa da atribuicao pelo proprio responsavel.
	 *
	 * Atribuir e ato de gestao e sai de qualquer ADMIN; desatribuir e recusa de trabalho e so faz
	 * sentido vinda de quem esta com o chamado. Enquanto qualquer ADMIN podia remover qualquer
	 * responsavel, um administrador tirava o chamado da fila do colega sem deixar decisao dele no
	 * meio — o evento gravado dizia "atribuicao removida de Fulano" e escondia quem removeu.
	 *
	 * Nao ha excecao de gestao: o ADMIN que precisa trocar o responsavel continua tendo caminho, a
	 * remocao seguida de nova atribuicao pelo proprio responsavel, e um atalho aqui devolveria
	 * exatamente o comportamento que esta regra existe para tirar. Se a operacao pedir a troca a
	 * quente, o lugar dela e um endpoint proprio de reatribuicao, com evento proprio.
	 *
	 * A ordem das guardas e deliberada: chamado sem responsavel responde 409, e nao 403, porque
	 * nesse estado nao existe responsavel de quem o solicitante da chamada pudesse ser diferente —
	 * o problema e o estado do chamado, nao quem pediu.
	 */
	@Transactional
	public TicketDto removeAssignee(UUID id) {
		accessControlService.assertAdmin();

		Ticket ticket = findEntityById(id);

		assertTicketIsNotClosed(ticket);

		User previousAssignee = ticket.getAssignee();

		if (previousAssignee == null) {
			throw new ConflictException("O chamado nao possui responsavel atribuido.");
		}

		if (!previousAssignee.getId().equals(accessControlService.getAuthenticatedUserId())) {
			throw new ForbiddenException("Apenas o responsavel pelo chamado pode recusar a atribuicao.");
		}

		ticket.unassign();

		Ticket savedTicket = ticketRepository.save(ticket);
		String description = "Atribuicao removida de " + previousAssignee.getName() + ".";

		ticketEventService.record(
				savedTicket, previousAssignee, TicketEventType.RESPONSAVEL_REMOVIDO, description
		);
		publishTicketNotification(
				savedTicket, TicketNotificationType.RESPONSAVEL_REMOVIDO, savedTicket.getPriority(), description
		);

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

	/**
	 * Estado terminal nao aceita mais mexer em responsavel.
	 *
	 * Deriva da matriz em vez de listar FECHADO e CANCELADO: um terceiro estado terminal futuro ja
	 * entra coberto, e a regra continua morando num lugar so.
	 */
	private void assertTicketIsNotClosed(Ticket ticket) {
		if (TicketStatusTransition.allowedFrom(ticket.getStatus()).isEmpty()) {
			throw new ConflictException(
					"Chamado " + ticket.getStatus().getLabel().toLowerCase() + " nao pode ser alterado."
			);
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
		publishTicketNotification(
				savedTicket, TicketNotificationType.CLASSIFICACAO_ATUALIZADA, previousPriority, description
		);
	}

	private User resolveActor() {
		Optional<UUID> authenticatedUserId = accessControlService.findAuthenticatedUserId();

		return authenticatedUserId.map(userService::findEntityById).orElse(null);
	}

	/**
	 * Unico ponto de notificacao do chamado: e-mail e SSE sao derivados deste evento por listeners
	 * pos-commit, em vez de cada mutacao falar com os dois transportes.
	 *
	 * O retrato vai pronto no evento porque os listeners rodam depois do commit, em outra thread e
	 * sem sessao JPA aberta.
	 *
	 * {@code previousPriority} nulo significa chamado recem-criado: chamado que ja nasce ALTA conta
	 * como chamado que passou a ser ALTA e dispara o alerta.
	 */
	private void publishTicketNotification(
			Ticket ticket,
			TicketNotificationType type,
			TicketPriority previousPriority,
			String detail
	) {
		TicketNotificationEvent event = new TicketNotificationEvent(
				type,
				TicketMapper.toMinDto(ticket),
				NotificationRecipient.of(ticket.getRequester()),
				NotificationRecipient.of(ticket.getAssignee()),
				accessControlService.findAuthenticatedUserId().orElse(null),
				previousPriority,
				detail
		);

		applicationEventPublisher.publishEvent(event);
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

package br.org.fadex.helpdesk.service;

import br.org.fadex.helpdesk.model.comment.TicketComment;
import br.org.fadex.helpdesk.model.comment.TicketCommentCreationDto;
import br.org.fadex.helpdesk.model.comment.TicketCommentDto;
import br.org.fadex.helpdesk.model.comment.TicketCommentFilter;
import br.org.fadex.helpdesk.model.comment.TicketCommentMapper;
import br.org.fadex.helpdesk.model.comment.TicketCommentMinDto;
import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.model.enums.TicketEventType;
import br.org.fadex.helpdesk.model.ticket.Ticket;
import br.org.fadex.helpdesk.model.ticket.TicketMapper;
import br.org.fadex.helpdesk.model.user.User;
import br.org.fadex.helpdesk.notification.event.NotificationRecipient;
import br.org.fadex.helpdesk.notification.event.TicketNotificationEvent;
import br.org.fadex.helpdesk.notification.event.TicketNotificationType;
import br.org.fadex.helpdesk.repository.TicketCommentRepository;
import br.org.fadex.helpdesk.repository.TicketCommentSpecification;
import br.org.fadex.helpdesk.security.AccessControlService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class TicketCommentService {

	private final TicketCommentRepository ticketCommentRepository;
	private final TicketService ticketService;
	private final UserService userService;
	private final AccessControlService accessControlService;
	private final TicketEventService ticketEventService;
	private final ApplicationEventPublisher applicationEventPublisher;

	public TicketCommentService(
			TicketCommentRepository ticketCommentRepository,
			TicketService ticketService,
			UserService userService,
			AccessControlService accessControlService,
			TicketEventService ticketEventService,
			ApplicationEventPublisher applicationEventPublisher
	) {
		this.ticketCommentRepository = ticketCommentRepository;
		this.ticketService = ticketService;
		this.userService = userService;
		this.accessControlService = accessControlService;
		this.ticketEventService = ticketEventService;
		this.applicationEventPublisher = applicationEventPublisher;
	}

	@Transactional(readOnly = true)
	public Page<TicketCommentMinDto> findAll(UUID ticketId, TicketCommentFilter filter, Pageable pageable) {
		Ticket ticket = ticketService.findEntityById(ticketId);
		accessControlService.assertCanAccessTicket(ticket);

		TicketCommentFilter resolvedFilter = new TicketCommentFilter(ticketId, filter.authorId(), filter.search());
		Specification<TicketComment> spec = TicketCommentSpecification.createSpecification(resolvedFilter);
		Page<TicketComment> comments = ticketCommentRepository.findAll(spec, pageable);
		Page<TicketCommentMinDto> response = comments.map(TicketCommentMapper::toMinDto);

		return response;
	}

	@Transactional
	public TicketCommentDto create(UUID ticketId, TicketCommentCreationDto ticketCommentCreationDto) {
		Ticket ticket = ticketService.findEntityById(ticketId);
		accessControlService.assertCanAccessTicket(ticket);
		UUID authenticatedUserId = accessControlService.getAuthenticatedUserId();
		User author = userService.findEntityById(authenticatedUserId);
		TicketComment ticketComment = TicketCommentMapper.toEntity(ticketCommentCreationDto, ticket, author);
		TicketComment savedComment = ticketCommentRepository.save(ticketComment);

		// Primeira resposta e a do atendimento: comentario do proprio solicitante nao conta.
		// O chamado e entidade gerenciada, entao o dirty checking persiste a mudanca.
		boolean isFirstAdminResponse = author.getRole() == Role.ADMIN && ticket.getFirstResponseAt() == null;

		if (isFirstAdminResponse) {
			ticket.markFirstResponse(LocalDateTime.now());
		}

		ticketEventService.record(ticket, author, TicketEventType.COMENTARIO_ADICIONADO, "Comentario adicionado.");
		publishCommentNotification(ticket, author, savedComment.getText());
		TicketCommentDto response = TicketCommentMapper.toResponseDto(savedComment);

		return response;
	}

	/**
	 * O e-mail do comentario vai para a contraparte, e quem escreveu nunca recebe copia. A escolha
	 * do destinatario fica no listener, que e quem conhece a matriz de notificacao.
	 */
	private void publishCommentNotification(Ticket ticket, User author, String text) {
		TicketNotificationEvent event = new TicketNotificationEvent(
				TicketNotificationType.COMENTARIO_ADICIONADO,
				TicketMapper.toMinDto(ticket),
				NotificationRecipient.of(ticket.getRequester()),
				NotificationRecipient.of(ticket.getAssignee()),
				author.getId(),
				ticket.getPriority(),
				text
		);

		applicationEventPublisher.publishEvent(event);
	}
}

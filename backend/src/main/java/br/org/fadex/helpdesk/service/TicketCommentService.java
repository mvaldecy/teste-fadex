package br.org.fadex.helpdesk.service;

import br.org.fadex.helpdesk.model.comment.TicketComment;
import br.org.fadex.helpdesk.model.comment.TicketCommentCreationDto;
import br.org.fadex.helpdesk.model.comment.TicketCommentDto;
import br.org.fadex.helpdesk.model.comment.TicketCommentFilter;
import br.org.fadex.helpdesk.model.comment.TicketCommentMapper;
import br.org.fadex.helpdesk.model.comment.TicketCommentMinDto;
import br.org.fadex.helpdesk.model.ticket.Ticket;
import br.org.fadex.helpdesk.model.user.User;
import br.org.fadex.helpdesk.repository.TicketCommentRepository;
import br.org.fadex.helpdesk.repository.TicketCommentSpecification;
import br.org.fadex.helpdesk.security.AuthenticatedUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TicketCommentService {

	private final TicketCommentRepository ticketCommentRepository;
	private final TicketService ticketService;
	private final UserService userService;
	private final AuthenticatedUserService authenticatedUserService;

	public TicketCommentService(
			TicketCommentRepository ticketCommentRepository,
			TicketService ticketService,
			UserService userService,
			AuthenticatedUserService authenticatedUserService
	) {
		this.ticketCommentRepository = ticketCommentRepository;
		this.ticketService = ticketService;
		this.userService = userService;
		this.authenticatedUserService = authenticatedUserService;
	}

	@Transactional(readOnly = true)
	public Page<TicketCommentMinDto> findAll(UUID ticketId, TicketCommentFilter filter, Pageable pageable) {
		ticketService.findEntityById(ticketId);

		TicketCommentFilter resolvedFilter = new TicketCommentFilter(ticketId, filter.authorId(), filter.search());
		Specification<TicketComment> spec = TicketCommentSpecification.createSpecification(resolvedFilter);
		Page<TicketComment> comments = ticketCommentRepository.findAll(spec, pageable);
		Page<TicketCommentMinDto> response = comments.map(TicketCommentMapper::toMinDto);

		return response;
	}

	@Transactional
	public TicketCommentDto create(UUID ticketId, TicketCommentCreationDto ticketCommentCreationDto) {
		Ticket ticket = ticketService.findEntityById(ticketId);
		UUID authenticatedUserId = authenticatedUserService.getUserId();
		User author = userService.findEntityById(authenticatedUserId);
		TicketComment ticketComment = TicketCommentMapper.toEntity(ticketCommentCreationDto, ticket, author);
		TicketComment savedComment = ticketCommentRepository.save(ticketComment);
		TicketCommentDto response = TicketCommentMapper.toResponseDto(savedComment);

		return response;
	}
}

package br.org.fadex.helpdesk.service;

import br.org.fadex.helpdesk.exception.NotFoundException;
import br.org.fadex.helpdesk.model.enums.TicketEventType;
import br.org.fadex.helpdesk.model.ticket.Ticket;
import br.org.fadex.helpdesk.model.ticket.TicketCreationDto;
import br.org.fadex.helpdesk.model.ticket.TicketDto;
import br.org.fadex.helpdesk.model.ticket.TicketFilter;
import br.org.fadex.helpdesk.model.ticket.TicketMapper;
import br.org.fadex.helpdesk.model.ticket.TicketMinDto;
import br.org.fadex.helpdesk.model.user.User;
import br.org.fadex.helpdesk.repository.TicketRepository;
import br.org.fadex.helpdesk.repository.TicketSpecification;
import br.org.fadex.helpdesk.security.AccessControlService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TicketService {

	private final TicketRepository ticketRepository;
	private final UserService userService;
	private final AccessControlService accessControlService;
	private final TicketEventService ticketEventService;

	public TicketService(
			TicketRepository ticketRepository,
			UserService userService,
			AccessControlService accessControlService,
			TicketEventService ticketEventService
	) {
		this.ticketRepository = ticketRepository;
		this.userService = userService;
		this.accessControlService = accessControlService;
		this.ticketEventService = ticketEventService;
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
		TicketDto response = TicketMapper.toResponseDto(savedTicket);

		return response;
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

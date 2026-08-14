package br.org.fadex.helpdesk.service;

import br.org.fadex.helpdesk.exception.NotFoundException;
import br.org.fadex.helpdesk.model.enums.TicketEventType;
import br.org.fadex.helpdesk.model.event.TicketEvent;
import br.org.fadex.helpdesk.model.event.TicketEventFilter;
import br.org.fadex.helpdesk.model.event.TicketEventMapper;
import br.org.fadex.helpdesk.model.event.TicketEventMinDto;
import br.org.fadex.helpdesk.model.ticket.Ticket;
import br.org.fadex.helpdesk.model.user.User;
import br.org.fadex.helpdesk.repository.TicketEventRepository;
import br.org.fadex.helpdesk.repository.TicketEventSpecification;
import br.org.fadex.helpdesk.repository.TicketRepository;
import br.org.fadex.helpdesk.security.AccessControlService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TicketEventService {

	private final TicketEventRepository ticketEventRepository;
	private final TicketRepository ticketRepository;
	private final AccessControlService accessControlService;

	public TicketEventService(
			TicketEventRepository ticketEventRepository,
			TicketRepository ticketRepository,
			AccessControlService accessControlService
	) {
		this.ticketEventRepository = ticketEventRepository;
		this.ticketRepository = ticketRepository;
		this.accessControlService = accessControlService;
	}

	@Transactional
	public void record(Ticket ticket, User actor, TicketEventType type, String description) {
		TicketEvent event = new TicketEvent(ticket, actor, type, description, null);

		ticketEventRepository.save(event);
	}

	@Transactional(readOnly = true)
	public Page<TicketEventMinDto> findAll(UUID ticketId, TicketEventFilter filter, Pageable pageable) {
		Ticket ticket = ticketRepository.findById(ticketId)
				.orElseThrow(() -> new NotFoundException("Chamado nao encontrado."));
		accessControlService.assertCanAccessTicket(ticket);

		TicketEventFilter resolvedFilter = new TicketEventFilter(
				ticketId,
				filter.actorId(),
				filter.type(),
				filter.search()
		);
		Specification<TicketEvent> spec = TicketEventSpecification.createSpecification(resolvedFilter);
		Page<TicketEvent> events = ticketEventRepository.findAll(spec, pageable);
		Page<TicketEventMinDto> response = events.map(TicketEventMapper::toMinDto);

		return response;
	}
}

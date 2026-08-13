package br.org.fadex.helpdesk.service;

import br.org.fadex.helpdesk.exception.NotFoundException;
import br.org.fadex.helpdesk.model.ticket.Ticket;
import br.org.fadex.helpdesk.model.ticket.TicketDto;
import br.org.fadex.helpdesk.model.ticket.TicketFilter;
import br.org.fadex.helpdesk.model.ticket.TicketMapper;
import br.org.fadex.helpdesk.model.ticket.TicketMinDto;
import br.org.fadex.helpdesk.repository.TicketRepository;
import br.org.fadex.helpdesk.repository.TicketSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TicketService {

	private final TicketRepository ticketRepository;

	public TicketService(TicketRepository ticketRepository) {
		this.ticketRepository = ticketRepository;
	}

	public Page<TicketMinDto> findAll(TicketFilter filter, Pageable pageable) {
		Specification<Ticket> spec = TicketSpecification.createSpecification(filter);
		Page<Ticket> tickets = ticketRepository.findAll(spec, pageable);
		Page<TicketMinDto> response = tickets.map(TicketMapper::toMinDto);

		return response;
	}

	public TicketDto findById(UUID id) {
		Ticket ticket = ticketRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("Chamado não encontrado."));
		TicketDto response = TicketMapper.toResponseDto(ticket);

		return response;
	}
}

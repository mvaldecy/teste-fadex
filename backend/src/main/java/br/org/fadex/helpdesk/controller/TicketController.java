package br.org.fadex.helpdesk.controller;

import br.org.fadex.helpdesk.model.ticket.TicketFilter;
import br.org.fadex.helpdesk.model.ticket.TicketDto;
import br.org.fadex.helpdesk.model.ticket.TicketFields;
import br.org.fadex.helpdesk.model.ticket.TicketMinDto;
import br.org.fadex.helpdesk.service.TicketService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {

	private final TicketService ticketService;

	public TicketController(TicketService ticketService) {
		this.ticketService = ticketService;
	}

	@GetMapping
	public ResponseEntity<Page<TicketMinDto>> findAll(
			@ModelAttribute TicketFilter filter,
			@PageableDefault(size = 10, sort = TicketFields.CREATED_AT, direction = Sort.Direction.DESC) Pageable pageable
	) {
		Page<TicketMinDto> tickets = ticketService.findAll(filter, pageable);

		return ResponseEntity.ok(tickets);
	}

	@GetMapping("/{id}")
	public ResponseEntity<TicketDto> findById(@PathVariable UUID id) {
		TicketDto ticket = ticketService.findById(id);

		return ResponseEntity.ok(ticket);
	}
}

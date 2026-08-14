package br.org.fadex.helpdesk.controller;

import br.org.fadex.helpdesk.model.event.TicketEventFields;
import br.org.fadex.helpdesk.model.event.TicketEventFilter;
import br.org.fadex.helpdesk.model.event.TicketEventMinDto;
import br.org.fadex.helpdesk.service.TicketEventService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tickets/{ticketId}/events")
public class TicketEventController {

	private final TicketEventService ticketEventService;

	public TicketEventController(TicketEventService ticketEventService) {
		this.ticketEventService = ticketEventService;
	}

	@GetMapping
	public ResponseEntity<Page<TicketEventMinDto>> findAll(
			@PathVariable UUID ticketId,
			@ModelAttribute TicketEventFilter filter,
			@PageableDefault(size = 10, sort = TicketEventFields.CREATED_AT, direction = Sort.Direction.DESC) Pageable pageable
	) {
		Page<TicketEventMinDto> events = ticketEventService.findAll(ticketId, filter, pageable);

		return ResponseEntity.ok(events);
	}
}

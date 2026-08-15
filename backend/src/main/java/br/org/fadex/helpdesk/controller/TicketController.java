package br.org.fadex.helpdesk.controller;

import br.org.fadex.helpdesk.model.ticket.TicketAssigneeUpdateDto;
import br.org.fadex.helpdesk.model.ticket.TicketCreationDto;
import br.org.fadex.helpdesk.model.ticket.TicketStatusUpdateDto;
import br.org.fadex.helpdesk.model.ticket.TicketFilter;
import br.org.fadex.helpdesk.model.ticket.TicketDto;
import br.org.fadex.helpdesk.model.ticket.TicketFields;
import br.org.fadex.helpdesk.model.ticket.TicketMinDto;
import br.org.fadex.helpdesk.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

	@PostMapping
	public ResponseEntity<TicketDto> create(@Valid @RequestBody TicketCreationDto ticketCreationDto) {
		TicketDto ticket = ticketService.create(ticketCreationDto);

		return ResponseEntity.status(HttpStatus.CREATED).body(ticket);
	}

	@PatchMapping("/{id}/status")
	public ResponseEntity<TicketDto> updateStatus(
			@PathVariable UUID id,
			@Valid @RequestBody TicketStatusUpdateDto ticketStatusUpdateDto
	) {
		TicketDto ticket = ticketService.updateStatus(id, ticketStatusUpdateDto);

		return ResponseEntity.ok(ticket);
	}

	@PatchMapping("/{id}/assignee")
	public ResponseEntity<TicketDto> updateAssignee(
			@PathVariable UUID id,
			@Valid @RequestBody TicketAssigneeUpdateDto ticketAssigneeUpdateDto
	) {
		TicketDto ticket = ticketService.updateAssignee(id, ticketAssigneeUpdateDto);

		return ResponseEntity.ok(ticket);
	}

	/**
	 * Exclusao logica do chamado: cancela e devolve o retrato novo.
	 *
	 * {@code DELETE} porque e onde o cliente procura "remover o chamado", e {@code 200} com corpo
	 * porque o chamado continua existindo em CANCELADO — um {@code 204} mudo sugeriria que o
	 * registro sumiu, e ele nao some: historico, comentarios e metricas ficam.
	 *
	 * A regra de quem pode cancelar e o que o estado atual permite ficam no service, nao aqui.
	 */
	@DeleteMapping("/{id}")
	public ResponseEntity<TicketDto> cancel(@PathVariable UUID id) {
		TicketDto ticket = ticketService.cancel(id);

		return ResponseEntity.ok(ticket);
	}

	@DeleteMapping("/{id}/assignee")
	public ResponseEntity<TicketDto> removeAssignee(@PathVariable UUID id) {
		TicketDto ticket = ticketService.removeAssignee(id);

		return ResponseEntity.ok(ticket);
	}
}

package br.org.fadex.helpdesk.controller;

import br.org.fadex.helpdesk.model.comment.TicketCommentCreationDto;
import br.org.fadex.helpdesk.model.comment.TicketCommentDto;
import br.org.fadex.helpdesk.model.comment.TicketCommentFields;
import br.org.fadex.helpdesk.model.comment.TicketCommentFilter;
import br.org.fadex.helpdesk.model.comment.TicketCommentMinDto;
import br.org.fadex.helpdesk.service.TicketCommentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tickets/{ticketId}/comments")
public class TicketCommentController {

	private final TicketCommentService ticketCommentService;

	public TicketCommentController(TicketCommentService ticketCommentService) {
		this.ticketCommentService = ticketCommentService;
	}

	@GetMapping
	public ResponseEntity<Page<TicketCommentMinDto>> findAll(
			@PathVariable UUID ticketId,
			@ModelAttribute TicketCommentFilter filter,
			@PageableDefault(size = 10, sort = TicketCommentFields.CREATED_AT, direction = Sort.Direction.DESC) Pageable pageable
	) {
		Page<TicketCommentMinDto> comments = ticketCommentService.findAll(ticketId, filter, pageable);

		return ResponseEntity.ok(comments);
	}

	@PostMapping
	public ResponseEntity<TicketCommentDto> create(
			@PathVariable UUID ticketId,
			@Valid @RequestBody TicketCommentCreationDto ticketCommentCreationDto
	) {
		TicketCommentDto comment = ticketCommentService.create(ticketId, ticketCommentCreationDto);

		return ResponseEntity.status(HttpStatus.CREATED).body(comment);
	}
}

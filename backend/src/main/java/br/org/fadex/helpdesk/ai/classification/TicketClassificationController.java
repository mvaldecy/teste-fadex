package br.org.fadex.helpdesk.ai.classification;

import br.org.fadex.helpdesk.model.ticket.TicketDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Revisao da classificacao pelo ADMIN.
 *
 * Compartilha o base path com {@code TicketController} sem colidir: aquele controller nao declara
 * {@code PATCH /{id}/classification}, e o Spring so rejeita duplicidade exata de (path, metodo).
 */
@RestController
@RequestMapping("/api/v1/tickets")
public class TicketClassificationController {

	private final TicketClassificationReviewService ticketClassificationReviewService;

	public TicketClassificationController(TicketClassificationReviewService ticketClassificationReviewService) {
		this.ticketClassificationReviewService = ticketClassificationReviewService;
	}

	@PatchMapping("/{id}/classification")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<TicketDto> review(
			@PathVariable UUID id,
			@Valid @RequestBody TicketClassificationUpdateDto ticketClassificationUpdateDto
	) {
		TicketDto ticket = ticketClassificationReviewService.review(id, ticketClassificationUpdateDto);

		return ResponseEntity.ok(ticket);
	}
}

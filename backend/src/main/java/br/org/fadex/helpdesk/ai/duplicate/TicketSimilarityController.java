package br.org.fadex.helpdesk.ai.duplicate;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Chamados semelhantes detectados por embedding.
 *
 * Compartilha o base path com {@code TicketController} sem colidir: aquele controller nao declara
 * {@code GET /{id}/similar}.
 */
@RestController
@RequestMapping("/api/v1/tickets")
public class TicketSimilarityController {

	private final TicketSimilarityService ticketSimilarityService;

	public TicketSimilarityController(TicketSimilarityService ticketSimilarityService) {
		this.ticketSimilarityService = ticketSimilarityService;
	}

	@GetMapping("/{id}/similar")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<List<SimilarTicketDto>> findSimilar(@PathVariable UUID id) {
		List<SimilarTicketDto> similarTickets = ticketSimilarityService.findSimilar(id);

		return ResponseEntity.ok(similarTickets);
	}
}

package br.org.fadex.helpdesk.ai.triage;

import br.org.fadex.helpdesk.ai.job.AiJobDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Solicitacao manual de triagem por IA.
 *
 * Responde {@code 202 Accepted}, e nao {@code 200}: o trabalho foi aceito para processamento
 * assincrono pelo worker, e nao concluido durante a requisicao.
 */
@RestController
@RequestMapping("/api/v1/tickets")
public class TicketTriageController {

	private final TicketTriageService ticketTriageService;

	public TicketTriageController(TicketTriageService ticketTriageService) {
		this.ticketTriageService = ticketTriageService;
	}

	@PostMapping("/{id}/ai-triage")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<List<AiJobDto>> requestTriage(@PathVariable UUID id) {
		List<AiJobDto> jobs = ticketTriageService.requestTriage(id);

		return ResponseEntity.status(HttpStatus.ACCEPTED).body(jobs);
	}
}

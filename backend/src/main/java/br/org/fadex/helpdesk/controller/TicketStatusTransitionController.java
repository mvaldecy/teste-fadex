package br.org.fadex.helpdesk.controller;

import br.org.fadex.helpdesk.model.enums.TicketStatus;
import br.org.fadex.helpdesk.model.ticket.TicketStatusTransition;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Publica a matriz de transicoes de status do chamado.
 *
 * Endpoint proprio, e nao dentro de {@code /choices}: aquele agrega rotulos de enum e e publico,
 * enquanto isto e regra de fluxo do dominio e so interessa a quem ja esta autenticado.
 *
 * Existe para que o cliente habilite apenas transicoes validas em vez de duplicar a matriz e sair
 * de sincronia. A fonte continua sendo {@link TicketStatusTransition}, a mesma que o service usa
 * para recusar a mudanca invalida — publicar aqui nao afrouxa a validacao do servidor.
 */
@RestController
@RequestMapping("/api/v1/ticket-status-transitions")
public class TicketStatusTransitionController {

	@GetMapping
	public ResponseEntity<Map<String, List<String>>> findAll() {
		Map<String, List<String>> transitions = new LinkedHashMap<>();

		for (TicketStatus status : TicketStatus.values()) {
			transitions.put(
					status.name(),
					TicketStatusTransition.allowedFrom(status).stream()
							.map(TicketStatus::name)
							.sorted()
							.toList()
			);
		}

		return ResponseEntity.ok(transitions);
	}
}

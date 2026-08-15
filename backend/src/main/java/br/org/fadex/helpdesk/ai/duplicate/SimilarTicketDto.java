package br.org.fadex.helpdesk.ai.duplicate;

import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import br.org.fadex.helpdesk.model.enums.TicketStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Chamado semelhante ao consultado, com a similaridade do par quando disponivel.
 *
 * {@code similarity} e nulavel: vinculos gravados antes da V6 nao registraram o valor, e nao ha
 * backfill possivel porque o embedding pode ter mudado desde a deteccao. Quem renderiza precisa
 * tratar a ausencia em vez de assumir um numero.
 */
public record SimilarTicketDto(
		UUID id,
		String title,
		TicketStatus status,
		TicketPriority priority,
		TicketCategory category,
		Double similarity,
		LocalDateTime createdAt
) {
}

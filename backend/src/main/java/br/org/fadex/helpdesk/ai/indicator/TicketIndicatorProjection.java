package br.org.fadex.helpdesk.ai.indicator;

import br.org.fadex.helpdesk.model.enums.ClassificationOrigin;
import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import br.org.fadex.helpdesk.model.enums.TicketStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Linha enxuta de chamado usada pelos indicadores.
 *
 * Nao carrega titulo, descricao nem embedding: sao os campos pesados da tabela e nenhum indicador os
 * usa. Toda a agregacao acontece em Java sobre esta projecao (decisao D5 do design), o que mantem
 * mediana e p90 testaveis em H2, onde {@code percentile_cont} nao existe.
 */
public record TicketIndicatorProjection(
		UUID ticketId,
		TicketStatus status,
		TicketPriority priority,
		TicketCategory category,
		ClassificationOrigin classificationOrigin,
		TicketCategory aiSuggestedCategory,
		TicketPriority aiSuggestedPriority,
		Double aiConfidence,
		UUID requesterId,
		String requesterName,
		UUID assigneeId,
		String assigneeName,
		LocalDateTime createdAt,
		LocalDateTime assignedAt,
		LocalDateTime firstResponseAt,
		LocalDateTime closedAt,
		LocalDateTime classificationReviewedAt
) {

	public boolean isOpen() {
		return status == TicketStatus.ABERTO || status == TicketStatus.EM_ANDAMENTO;
	}

	public boolean isClosed() {
		return closedAt != null;
	}

	public boolean hasSuggestion() {
		return aiSuggestedCategory != null && aiSuggestedPriority != null;
	}

	public boolean isReviewed() {
		return classificationReviewedAt != null;
	}

	public boolean agreesWithSuggestion() {
		return hasSuggestion() && category == aiSuggestedCategory && priority == aiSuggestedPriority;
	}
}

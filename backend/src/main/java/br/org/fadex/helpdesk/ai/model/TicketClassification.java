package br.org.fadex.helpdesk.ai.model;

import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;

import java.util.Objects;

public record TicketClassification(
		TicketCategory category,
		TicketPriority priority,
		double confidence,
		String justification
) {

	public TicketClassification {
		Objects.requireNonNull(category, "category nao pode ser nula");
		Objects.requireNonNull(priority, "priority nao pode ser nula");
		if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
			throw new IllegalArgumentException("confidence deve estar entre 0.0 e 1.0");
		}
		if (justification == null || justification.isBlank()) {
			throw new IllegalArgumentException("justification nao pode estar em branco");
		}
	}
}

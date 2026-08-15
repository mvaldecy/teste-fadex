package br.org.fadex.helpdesk.ai.classification;

import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TicketClassificationUpdateDto(
		@NotNull(message = "A categoria e obrigatoria.")
		TicketCategory category,

		@NotNull(message = "A prioridade e obrigatoria.")
		TicketPriority priority,

		@Size(max = 2000, message = "A justificativa deve ter no maximo 2000 caracteres.")
		String justification
) {
}

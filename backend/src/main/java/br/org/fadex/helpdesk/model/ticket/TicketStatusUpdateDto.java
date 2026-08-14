package br.org.fadex.helpdesk.model.ticket;

import br.org.fadex.helpdesk.model.enums.TicketStatus;
import jakarta.validation.constraints.NotNull;

public record TicketStatusUpdateDto(
		@NotNull TicketStatus status
) {
}

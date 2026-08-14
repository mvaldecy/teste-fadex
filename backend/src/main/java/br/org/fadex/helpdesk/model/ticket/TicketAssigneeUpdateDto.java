package br.org.fadex.helpdesk.model.ticket;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TicketAssigneeUpdateDto(
		@NotNull UUID assigneeId
) {
}

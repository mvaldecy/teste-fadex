package br.org.fadex.helpdesk.model.event;

import br.org.fadex.helpdesk.model.enums.TicketEventType;
import br.org.fadex.helpdesk.model.user.UserMinDto;

import java.time.LocalDateTime;
import java.util.UUID;

public record TicketEventMinDto(
		UUID id,
		UserMinDto actor,
		TicketEventType type,
		String description,
		LocalDateTime createdAt
) {
}

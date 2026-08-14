package br.org.fadex.helpdesk.model.event;

import br.org.fadex.helpdesk.model.enums.TicketEventType;
import br.org.fadex.helpdesk.model.ticket.TicketMinDto;
import br.org.fadex.helpdesk.model.user.UserMinDto;

import java.time.LocalDateTime;
import java.util.UUID;

public record TicketEventDto(
		UUID id,
		TicketMinDto ticket,
		UserMinDto actor,
		TicketEventType type,
		String description,
		String metadata,
		LocalDateTime createdAt
) {
}

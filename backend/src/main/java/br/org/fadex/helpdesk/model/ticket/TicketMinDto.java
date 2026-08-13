package br.org.fadex.helpdesk.model.ticket;

import br.org.fadex.helpdesk.model.enums.ClassificationOrigin;
import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import br.org.fadex.helpdesk.model.enums.TicketStatus;
import br.org.fadex.helpdesk.model.user.UserMinDto;

import java.time.LocalDateTime;
import java.util.UUID;

public record TicketMinDto(
		UUID id,
		String title,
		TicketCategory category,
		TicketPriority priority,
		TicketStatus status,
		ClassificationOrigin classificationOrigin,
		UserMinDto requester,
		UserMinDto assignee,
		LocalDateTime createdAt
) {
}

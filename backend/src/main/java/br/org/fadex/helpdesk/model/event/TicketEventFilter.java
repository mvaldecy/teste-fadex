package br.org.fadex.helpdesk.model.event;

import br.org.fadex.helpdesk.model.enums.TicketEventType;
import org.springframework.util.StringUtils;

import java.util.UUID;

public record TicketEventFilter(
		UUID ticketId,
		UUID actorId,
		TicketEventType type,
		String search
) {

	public boolean hasTicketId() {
		return ticketId != null;
	}

	public boolean hasActorId() {
		return actorId != null;
	}

	public boolean hasType() {
		return type != null;
	}

	public boolean hasSearch() {
		return StringUtils.hasText(search);
	}
}

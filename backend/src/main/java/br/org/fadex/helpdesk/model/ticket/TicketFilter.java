package br.org.fadex.helpdesk.model.ticket;

import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import br.org.fadex.helpdesk.model.enums.TicketStatus;
import org.springframework.util.StringUtils;

import java.util.UUID;

public record TicketFilter(
		TicketStatus status,
		TicketPriority priority,
		TicketCategory category,
		UUID requesterId,
		UUID assigneeId,
		String search
) {

	public boolean hasStatus() {
		return status != null;
	}

	public boolean hasPriority() {
		return priority != null;
	}

	public boolean hasCategory() {
		return category != null;
	}

	public boolean hasRequesterId() {
		return requesterId != null;
	}

	public boolean hasAssigneeId() {
		return assigneeId != null;
	}

	public boolean hasSearch() {
		return StringUtils.hasText(search);
	}
}

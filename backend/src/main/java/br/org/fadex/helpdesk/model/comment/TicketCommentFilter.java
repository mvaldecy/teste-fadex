package br.org.fadex.helpdesk.model.comment;

import org.springframework.util.StringUtils;

import java.util.UUID;

public record TicketCommentFilter(
		UUID ticketId,
		UUID authorId,
		String search
) {

	public boolean hasTicketId() {
		return ticketId != null;
	}

	public boolean hasAuthorId() {
		return authorId != null;
	}

	public boolean hasSearch() {
		return StringUtils.hasText(search);
	}
}

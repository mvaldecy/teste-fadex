package br.org.fadex.helpdesk.model.comment;

import br.org.fadex.helpdesk.model.user.UserMinDto;

import java.time.LocalDateTime;
import java.util.UUID;

public record TicketCommentMinDto(
		UUID id,
		UserMinDto author,
		String text,
		LocalDateTime createdAt
) {
}

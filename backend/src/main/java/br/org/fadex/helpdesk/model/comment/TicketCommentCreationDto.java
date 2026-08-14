package br.org.fadex.helpdesk.model.comment;

import jakarta.validation.constraints.NotBlank;

public record TicketCommentCreationDto(
		@NotBlank
		String text
) {
}

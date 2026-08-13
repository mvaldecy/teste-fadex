package br.org.fadex.helpdesk.model.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TicketCreationDto(
		@NotBlank
		@Size(max = 160)
		String title,

		@NotBlank
		String description
) {
}

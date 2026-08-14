package br.org.fadex.helpdesk.model.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TicketCreationDto(
		@NotBlank(message = "Titulo e obrigatorio.")
		@Size(max = 160, message = "Titulo deve ter no maximo 160 caracteres.")
		String title,

		@NotBlank(message = "Descricao e obrigatoria.")
		String description
) {
}

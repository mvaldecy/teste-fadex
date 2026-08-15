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
		LocalDateTime assignedAt,
		LocalDateTime createdAt,
		/**
		 * Quantos chamados semelhantes a deteccao de duplicados vinculou a este.
		 *
		 * {@code null} quando o numero nao foi apurado no contexto que montou o DTO — e o caso do
		 * payload das notificacoes, que descreve a mudanca de um chamado e nao a listagem. Zero e
		 * "nao tem semelhante"; nulo e "nao perguntei". Distinguir os dois evita a listagem
		 * apagar um selo por causa de um evento que nunca soube dele.
		 */
		Integer similarCount
) {
}

package br.org.fadex.helpdesk.model.ticket;

import br.org.fadex.helpdesk.model.enums.ClassificationOrigin;
import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import br.org.fadex.helpdesk.model.enums.TicketStatus;
import br.org.fadex.helpdesk.model.user.UserMinDto;

import java.time.LocalDateTime;
import java.util.UUID;

public record TicketDto(
		UUID id,
		String title,
		String description,
		TicketCategory category,
		TicketPriority priority,
		TicketStatus status,
		ClassificationOrigin classificationOrigin,
		String classificationJustification,
		UserMinDto requester,
		UserMinDto assignee,
		LocalDateTime assignedAt,
		LocalDateTime firstResponseAt,
		LocalDateTime resolvedAt,
		LocalDateTime closedAt,
		LocalDateTime createdAt,
		LocalDateTime updatedAt,
		TicketCategory aiSuggestedCategory,
		TicketPriority aiSuggestedPriority,
		Double confidence,
		/**
		 * Quando um ADMIN revisou a classificacao. Nulo enquanto a sugestao da IA nunca passou por
		 * pessoa nenhuma.
		 *
		 * A origem sozinha nao responde isso: o ADMIN que revisa e concorda com a IA deixa a origem
		 * em {@code IA}, entao "IA" cobre tanto "ninguem olhou" quanto "olharam e aprovaram". Sao
		 * situacoes opostas para quem precisa saber o que ainda exige atencao.
		 */
		LocalDateTime classificationReviewedAt
) {
}

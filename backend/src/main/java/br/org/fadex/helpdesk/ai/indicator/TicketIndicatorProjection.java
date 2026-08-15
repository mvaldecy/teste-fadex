package br.org.fadex.helpdesk.ai.indicator;

import br.org.fadex.helpdesk.model.enums.ClassificationOrigin;
import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import br.org.fadex.helpdesk.model.enums.TicketStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Linha enxuta de chamado usada pelos indicadores.
 *
 * Nao carrega titulo, descricao nem embedding: sao os campos pesados da tabela e nenhum indicador os
 * usa. Toda a agregacao acontece em Java sobre esta projecao (decisao D5 do design), o que mantem
 * mediana e p90 testaveis em H2, onde {@code percentile_cont} nao existe.
 */
public record TicketIndicatorProjection(
		UUID ticketId,
		TicketStatus status,
		TicketPriority priority,
		TicketCategory category,
		ClassificationOrigin classificationOrigin,
		TicketCategory aiSuggestedCategory,
		TicketPriority aiSuggestedPriority,
		Double aiConfidence,
		UUID requesterId,
		String requesterName,
		UUID assigneeId,
		String assigneeName,
		LocalDateTime createdAt,
		LocalDateTime assignedAt,
		LocalDateTime firstResponseAt,
		LocalDateTime resolvedAt,
		LocalDateTime closedAt,
		LocalDateTime classificationReviewedAt
) {

	public boolean isOpen() {
		return status == TicketStatus.ABERTO || status == TicketStatus.EM_ANDAMENTO;
	}

	public boolean isClosed() {
		return closedAt != null;
	}

	/**
	 * Chamado cancelado: nao esta aberto, nao foi resolvido e nao foi fechado.
	 *
	 * Fica explicito em vez de emergir de {@code isOpen()} porque as metricas que precisam exclui-lo
	 * sao mais de uma, e cada uma exclui por um motivo diferente.
	 */
	public boolean isCanceled() {
		return status == TicketStatus.CANCELADO;
	}

	/**
	 * Instante em que o atendimento parou de correr, ou {@code null} se ainda esta correndo.
	 *
	 * Chamado RESOLVIDO tem o trabalho concluido mesmo sem ninguem ter clicado em fechar. Medir SLA
	 * ate agora nesse caso transformaria toda pendencia de fechamento em violacao permanente, o que
	 * mede burocracia e nao atendimento.
	 *
	 * Nao vale para chamado CANCELADO, que nao entra no SLA de forma alguma — ver
	 * {@code IndicatorService.buildSla}.
	 */
	public LocalDateTime settledAt() {
		if (closedAt != null) {
			return closedAt;
		}

		return isOpen() ? null : resolvedAt;
	}

	public boolean isSettled() {
		return settledAt() != null;
	}

	public boolean hasSuggestion() {
		return aiSuggestedCategory != null && aiSuggestedPriority != null;
	}

	public boolean isReviewed() {
		return classificationReviewedAt != null;
	}

	public boolean agreesWithSuggestion() {
		return hasSuggestion() && category == aiSuggestedCategory && priority == aiSuggestedPriority;
	}
}

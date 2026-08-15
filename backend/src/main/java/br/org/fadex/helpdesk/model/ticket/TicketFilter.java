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
		Boolean unassigned,
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

	/**
	 * Filtra os chamados que ainda nao tem responsavel — a fila de quem vai pegar trabalho.
	 *
	 * Tem precedencia sobre {@code assigneeId}: os dois juntos descrevem um conjunto vazio
	 * ("sem responsavel e com este responsavel"), e devolver vazio calado esconderia o erro de
	 * quem montou a consulta. Aqui o pedido mais forte vence e o resultado e explicavel.
	 */
	public boolean hasUnassigned() {
		return Boolean.TRUE.equals(unassigned);
	}

	public boolean hasSearch() {
		return StringUtils.hasText(search);
	}
}

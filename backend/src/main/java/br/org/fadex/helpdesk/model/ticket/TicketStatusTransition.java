package br.org.fadex.helpdesk.model.ticket;

import br.org.fadex.helpdesk.model.enums.TicketStatus;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Matriz de transicoes de status do chamado.
 *
 * Fica em estrutura de dados consultavel, e nao espalhada em ifs no service, porque a regra e lida
 * por mais de uma frente: os indicadores usam a matriz para aging e o front para habilitar botoes.
 *
 * {@code FECHADO} mapeia para conjunto vazio: o estado terminal e um dado, nao um caso especial.
 */
public final class TicketStatusTransition {

	private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED = buildAllowed();

	private TicketStatusTransition() {
	}

	public static boolean isAllowed(TicketStatus from, TicketStatus to) {
		return allowedFrom(from).contains(to);
	}

	public static Set<TicketStatus> allowedFrom(TicketStatus from) {
		return ALLOWED.getOrDefault(from, Set.of());
	}

	private static Map<TicketStatus, Set<TicketStatus>> buildAllowed() {
		Map<TicketStatus, Set<TicketStatus>> allowed = new EnumMap<>(TicketStatus.class);

		allowed.put(TicketStatus.ABERTO, Set.of(
				TicketStatus.EM_ANDAMENTO,
				TicketStatus.RESOLVIDO,
				TicketStatus.FECHADO
		));
		allowed.put(TicketStatus.EM_ANDAMENTO, Set.of(
				TicketStatus.ABERTO,
				TicketStatus.RESOLVIDO,
				TicketStatus.FECHADO
		));
		allowed.put(TicketStatus.RESOLVIDO, Set.of(
				TicketStatus.EM_ANDAMENTO,
				TicketStatus.FECHADO
		));
		allowed.put(TicketStatus.FECHADO, Set.of());

		return Map.copyOf(allowed);
	}
}

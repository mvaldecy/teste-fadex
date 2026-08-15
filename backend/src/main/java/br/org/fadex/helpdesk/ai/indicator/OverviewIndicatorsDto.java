package br.org.fadex.helpdesk.ai.indicator;

import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import br.org.fadex.helpdesk.model.enums.TicketStatus;

import java.util.Map;

public record OverviewIndicatorsDto(
		long total,
		Map<TicketStatus, Long> byStatus,
		Map<TicketPriority, Long> byPriority,
		Map<TicketCategory, Long> byCategory,
		long openedToday,
		long closedToday,
		long openedThisWeek,
		long closedThisWeek,
		long openHighPriority
) {
}

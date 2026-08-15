package br.org.fadex.helpdesk.ai.indicator;

import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;

import java.util.Map;

public record DurationGroupDto(
		DurationStatsDto overall,
		Map<TicketPriority, DurationStatsDto> byPriority,
		Map<TicketCategory, DurationStatsDto> byCategory
) {
}

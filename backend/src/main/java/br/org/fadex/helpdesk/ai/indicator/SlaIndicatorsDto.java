package br.org.fadex.helpdesk.ai.indicator;

import br.org.fadex.helpdesk.model.enums.TicketPriority;

import java.util.Map;

public record SlaIndicatorsDto(SlaSliceDto overall, Map<TicketPriority, SlaSliceDto> byPriority) {
}

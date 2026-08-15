package br.org.fadex.helpdesk.ai.indicator;

public record DurationIndicatorsDto(
		DurationGroupDto closure,
		DurationGroupDto firstResponse,
		DurationGroupDto assignment,
		BacklogAgingDto backlogAging,
		Double oldestOpenTicketHours,
		SlaIndicatorsDto sla
) {
}

package br.org.fadex.helpdesk.ai.indicator;

import java.time.LocalDateTime;

public record IndicatorsDto(
		LocalDateTime generatedAt,
		OverviewIndicatorsDto overview,
		DurationIndicatorsDto durations,
		AiIndicatorsDto ai,
		WorkloadIndicatorsDto workload
) {
}

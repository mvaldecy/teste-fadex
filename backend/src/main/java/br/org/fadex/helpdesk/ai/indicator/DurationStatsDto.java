package br.org.fadex.helpdesk.ai.indicator;

public record DurationStatsDto(
		int sampleSize,
		Double averageHours,
		Double medianHours,
		Double p90Hours
) {

	public static DurationStatsDto empty() {
		return new DurationStatsDto(0, null, null, null);
	}
}

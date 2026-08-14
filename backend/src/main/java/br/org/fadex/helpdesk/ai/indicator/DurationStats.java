package br.org.fadex.helpdesk.ai.indicator;

import java.time.Duration;
import java.util.List;

/**
 * Estatistica descritiva de uma amostra de duracoes.
 *
 * Media, mediana e p90 sao calculadas em Java, e nao em SQL, porque o H2 usado nos testes nao tem
 * {@code percentile_cont} (decisao D5 do design). Amostra vazia devolve nulos, nunca zeros: zero e
 * um valor medido e ausencia de dado nao e.
 */
public abstract class DurationStats {

	private static final double SECONDS_PER_HOUR = 3600.0;
	private static final double P90 = 0.9;

	private DurationStats() {
	}

	public static DurationStatsDto of(List<Duration> durations) {
		if (durations == null || durations.isEmpty()) {
			return DurationStatsDto.empty();
		}

		List<Double> hours = durations.stream()
				.map(duration -> duration.toSeconds() / SECONDS_PER_HOUR)
				.sorted()
				.toList();

		double average = hours.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
		double median = median(hours);
		double p90 = percentile(hours, P90);

		return new DurationStatsDto(hours.size(), round(average), round(median), round(p90));
	}

	private static double median(List<Double> sortedHours) {
		int size = sortedHours.size();
		int middle = size / 2;

		if (size % 2 == 1) {
			return sortedHours.get(middle);
		}

		return (sortedHours.get(middle - 1) + sortedHours.get(middle)) / 2.0;
	}

	private static double percentile(List<Double> sortedHours, double percentile) {
		int rank = (int) Math.ceil(percentile * sortedHours.size());
		int index = Math.max(0, Math.min(rank - 1, sortedHours.size() - 1));

		return sortedHours.get(index);
	}

	private static double round(double value) {
		return Math.round(value * 10.0) / 10.0;
	}
}

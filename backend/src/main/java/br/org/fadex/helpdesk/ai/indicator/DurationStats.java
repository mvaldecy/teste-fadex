package br.org.fadex.helpdesk.ai.indicator;

import java.time.Duration;
import java.util.ArrayList;
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

	/**
	 * Limites inferiores das faixas do histograma, em horas.
	 *
	 * Sao fixos de proposito. Faixa calculada a partir da amostra mudaria de largura a cada
	 * execucao, e dois graficos do painel — ou o mesmo grafico antes e depois de fechar um chamado —
	 * deixariam de ser comparaveis. Faixa fixa custa um grafico menos ajustado e paga com leitura
	 * estavel.
	 *
	 * A largura cresce porque a distribuicao e assimetrica: parede em zero, massa nas primeiras
	 * horas e cauda longa a direita. Faixa de largura constante gastaria metade do eixo com barras
	 * vazias.
	 */
	private static final int[] BUCKET_LOWER_BOUNDS = {0, 4, 8, 24, 48, 96};

	private DurationStats() {
	}

	public static DurationStatsDto of(List<Duration> durations) {
		return of(durations, false);
	}

	/**
	 * Mesma estatistica, acrescida do histograma por faixa fixa.
	 *
	 * Existe como chamada separada porque o histograma so vale no {@code overall}: nos recortes por
	 * prioridade e por categoria a amostra e pequena demais para uma distribuicao significar algo, e
	 * seis faixas com uma barra de altura 1 sugeririam precisao que o dado nao tem.
	 */
	public static DurationStatsDto withHistogram(List<Duration> durations) {
		return of(durations, true);
	}

	private static DurationStatsDto of(List<Duration> durations, boolean withHistogram) {
		if (durations == null || durations.isEmpty()) {
			// Amostra vazia ainda devolve as faixas zeradas quando o histograma foi pedido: o grafico
			// precisa dos mesmos eixos de sempre para mostrar que nao ha dado, em vez de sumir.
			return withHistogram
					? new DurationStatsDto(0, null, null, null, histogram(List.of()))
					: DurationStatsDto.empty();
		}

		List<Double> hours = durations.stream()
				.map(duration -> duration.toSeconds() / SECONDS_PER_HOUR)
				.sorted()
				.toList();

		double average = hours.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
		double median = median(hours);
		double p90 = percentile(hours, P90);

		return new DurationStatsDto(
				hours.size(),
				round(average),
				round(median),
				round(p90),
				withHistogram ? histogram(hours) : null
		);
	}

	/**
	 * Distribui as duracoes nas faixas fixas.
	 *
	 * Faixa fechada no inicio e aberta no fim, entao a soma das contagens e sempre o
	 * {@code sampleSize} — nenhuma duracao cai em duas faixas nem fica de fora. Duracao negativa nao
	 * deveria existir (o fim vem sempre depois da criacao), mas se existir entra na primeira faixa
	 * em vez de desaparecer da soma.
	 */
	private static List<DurationBucketDto> histogram(List<Double> hours) {
		List<DurationBucketDto> buckets = new ArrayList<>();

		for (int index = 0; index < BUCKET_LOWER_BOUNDS.length; index++) {
			int from = BUCKET_LOWER_BOUNDS[index];
			boolean isLast = index == BUCKET_LOWER_BOUNDS.length - 1;
			Integer to = isLast ? null : BUCKET_LOWER_BOUNDS[index + 1];
			boolean isFirst = index == 0;

			long count = hours.stream()
					.filter(value -> (isFirst || value >= from) && (isLast || value < to))
					.count();

			buckets.add(new DurationBucketDto(from, to, count));
		}

		return List.copyOf(buckets);
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

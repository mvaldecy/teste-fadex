package br.org.fadex.helpdesk.ai.indicator;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DurationStatsTest {

	@Test
	void deveDevolverNulosParaAmostraVazia() {
		DurationStatsDto stats = DurationStats.of(List.of());

		assertThat(stats.sampleSize()).isZero();
		assertThat(stats.averageHours()).isNull();
		assertThat(stats.medianHours()).isNull();
		assertThat(stats.p90Hours()).isNull();
	}

	@Test
	void deveCalcularComUmUnicoElemento() {
		DurationStatsDto stats = DurationStats.of(List.of(Duration.ofHours(4)));

		assertThat(stats.sampleSize()).isEqualTo(1);
		assertThat(stats.averageHours()).isEqualTo(4.0);
		assertThat(stats.medianHours()).isEqualTo(4.0);
		assertThat(stats.p90Hours()).isEqualTo(4.0);
	}

	@Test
	void deveUsarMediaDosDoisCentraisQuandoAmostraEPar() {
		DurationStatsDto stats = DurationStats.of(List.of(
				Duration.ofHours(2),
				Duration.ofHours(4),
				Duration.ofHours(6),
				Duration.ofHours(8)
		));

		assertThat(stats.medianHours()).isEqualTo(5.0);
		assertThat(stats.averageHours()).isEqualTo(5.0);
	}

	@Test
	void deveUsarOCentralQuandoAmostraEImpar() {
		DurationStatsDto stats = DurationStats.of(List.of(
				Duration.ofHours(1),
				Duration.ofHours(2),
				Duration.ofHours(30)
		));

		assertThat(stats.medianHours()).isEqualTo(2.0);
		assertThat(stats.averageHours()).isEqualTo(11.0);
	}

	@Test
	void deveCalcularP90PorRankMaisProximo() {
		List<Duration> durations = new ArrayList<>();
		for (int hour = 1; hour <= 10; hour++) {
			durations.add(Duration.ofHours(hour));
		}

		DurationStatsDto stats = DurationStats.of(durations);

		assertThat(stats.p90Hours()).isEqualTo(9.0);
	}

	@Test
	void deveOrdenarAmostraDesordenada() {
		DurationStatsDto stats = DurationStats.of(List.of(
				Duration.ofHours(8),
				Duration.ofHours(2),
				Duration.ofHours(6),
				Duration.ofHours(4)
		));

		assertThat(stats.medianHours()).isEqualTo(5.0);
	}

	@Test
	void deveConverterMinutosEmFracaoDeHora() {
		DurationStatsDto stats = DurationStats.of(List.of(Duration.ofMinutes(90)));

		assertThat(stats.averageHours()).isEqualTo(1.5);
	}

	@Test
	void deveArredondarParaUmaCasaDecimal() {
		DurationStatsDto stats = DurationStats.of(List.of(
				Duration.ofHours(1),
				Duration.ofHours(2)
		));

		assertThat(DurationStats.of(List.of(Duration.ofMinutes(50))).averageHours()).isEqualTo(0.8);
		assertThat(stats.averageHours()).isEqualTo(1.5);
	}

	@Test
	void deveDevolverAmostraVaziaParaListaNula() {
		DurationStatsDto stats = DurationStats.of(null);

		assertThat(stats.sampleSize()).isZero();
		assertThat(stats.averageHours()).isNull();
	}
}

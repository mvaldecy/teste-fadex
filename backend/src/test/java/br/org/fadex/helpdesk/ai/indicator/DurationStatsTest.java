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

	/**
	 * O histograma acompanha apenas o recorte geral. Nos demais a chave nao existe, e nao vem
	 * vazia — o front precisa distinguir "aqui nao ha histograma" de "histograma sem chamado".
	 */
	@Test
	void naoDeveMontarHistogramaForaDoRecorteGeral() {
		DurationStatsDto stats = DurationStats.of(List.of(Duration.ofHours(4)));

		assertThat(stats.histogram()).isNull();
	}

	@Test
	void devePublicarAsSeisFaixasFixasNaOrdem() {
		DurationStatsDto stats = DurationStats.withHistogram(List.of(Duration.ofHours(1)));

		assertThat(stats.histogram()).extracting(DurationBucketDto::fromHours)
				.containsExactly(0, 4, 8, 24, 48, 96);
		assertThat(stats.histogram()).extracting(DurationBucketDto::toHours)
				.containsExactly(4, 8, 24, 48, 96, null);
	}

	/**
	 * Faixa fechada no inicio e aberta no fim: 4h exatas pertencem a 4–8, nao a 0–4. Sem essa regra
	 * a duracao de fronteira contaria duas vezes ou nenhuma.
	 */
	@Test
	void deveContarADuracaoDeFronteiraNaFaixaSeguinte() {
		DurationStatsDto stats = DurationStats.withHistogram(List.of(
				Duration.ofHours(4),
				Duration.ofHours(8),
				Duration.ofHours(24),
				Duration.ofHours(48),
				Duration.ofHours(96)
		));

		assertThat(counts(stats)).containsExactly(0L, 1L, 1L, 1L, 1L, 1L);
	}

	@Test
	void deveAcumularACaudaLongaNaUltimaFaixa() {
		DurationStatsDto stats = DurationStats.withHistogram(List.of(
				Duration.ofHours(96),
				Duration.ofHours(500),
				Duration.ofHours(5000)
		));

		assertThat(counts(stats)).containsExactly(0L, 0L, 0L, 0L, 0L, 3L);
	}

	/**
	 * A soma das faixas e o tamanho da amostra. E o que garante que o grafico e o numero ao lado
	 * dele contam a mesma historia — nenhuma duracao cai em duas faixas nem fica de fora.
	 */
	@Test
	void deveDistribuirTodaAmostraEntreAsFaixas() {
		List<Duration> durations = List.of(
				Duration.ofMinutes(30),
				Duration.ofHours(3),
				Duration.ofHours(5),
				Duration.ofHours(12),
				Duration.ofHours(30),
				Duration.ofHours(70),
				Duration.ofHours(200)
		);

		DurationStatsDto stats = DurationStats.withHistogram(durations);

		assertThat(counts(stats)).containsExactly(2L, 1L, 1L, 1L, 1L, 1L);
		assertThat(counts(stats).stream().mapToLong(Long::longValue).sum())
				.isEqualTo(stats.sampleSize());
	}

	/**
	 * Amostra vazia devolve as faixas zeradas, e nao histograma nulo: o grafico precisa dos mesmos
	 * eixos de sempre para mostrar que nao ha dado, em vez de sumir da tela.
	 */
	@Test
	void deveDevolverFaixasZeradasParaAmostraVazia() {
		DurationStatsDto stats = DurationStats.withHistogram(List.of());

		assertThat(stats.sampleSize()).isZero();
		assertThat(stats.averageHours()).isNull();
		assertThat(counts(stats)).containsExactly(0L, 0L, 0L, 0L, 0L, 0L);
	}

	private List<Long> counts(DurationStatsDto stats) {
		return stats.histogram().stream().map(DurationBucketDto::count).toList();
	}
}

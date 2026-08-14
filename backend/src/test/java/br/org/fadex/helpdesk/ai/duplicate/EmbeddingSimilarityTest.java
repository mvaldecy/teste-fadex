package br.org.fadex.helpdesk.ai.duplicate;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class EmbeddingSimilarityTest {

	@Test
	void deveFazerParseDoLiteralPgvector() {
		List<Double> values = EmbeddingSimilarity.parse("[0.1,0.2,0.3]");

		assertThat(values).containsExactly(0.1, 0.2, 0.3);
	}

	@Test
	void deveFazerParseDeLiteralComEspacos() {
		List<Double> values = EmbeddingSimilarity.parse("[0.1, 0.2, 0.3]");

		assertThat(values).containsExactly(0.1, 0.2, 0.3);
	}

	@Test
	void deveDevolverListaVaziaParaLiteralNulo() {
		assertThat(EmbeddingSimilarity.parse(null)).isEmpty();
	}

	@Test
	void deveDevolverListaVaziaParaLiteralEmBranco() {
		assertThat(EmbeddingSimilarity.parse("   ")).isEmpty();
	}

	@Test
	void deveDevolverListaVaziaParaColchetesVazios() {
		assertThat(EmbeddingSimilarity.parse("[]")).isEmpty();
	}

	@Test
	void vetoresIdenticosTemCossenoUm() {
		List<Double> vector = List.of(1.0, 2.0, 3.0);

		assertThat(EmbeddingSimilarity.cosine(vector, vector)).isCloseTo(1.0, within(1e-9));
	}

	@Test
	void vetoresOrtogonaisTemCossenoZero() {
		assertThat(EmbeddingSimilarity.cosine(List.of(1.0, 0.0), List.of(0.0, 1.0)))
				.isCloseTo(0.0, within(1e-9));
	}

	@Test
	void vetoresOpostosTemCossenoMenosUm() {
		assertThat(EmbeddingSimilarity.cosine(List.of(1.0, 0.0), List.of(-1.0, 0.0)))
				.isCloseTo(-1.0, within(1e-9));
	}

	@Test
	void vetorNuloTemCossenoZero() {
		assertThat(EmbeddingSimilarity.cosine(List.of(0.0, 0.0), List.of(1.0, 1.0))).isZero();
	}

	@Test
	void deveIgnorarEscalaDoVetor() {
		double similarity = EmbeddingSimilarity.cosine(List.of(1.0, 1.0), List.of(10.0, 10.0));

		assertThat(similarity).isCloseTo(1.0, within(1e-9));
	}

	@Test
	void deveRejeitarVetoresDeTamanhosDiferentes() {
		assertThatThrownBy(() -> EmbeddingSimilarity.cosine(List.of(1.0), List.of(1.0, 2.0)))
				.isInstanceOf(IllegalArgumentException.class);
	}
}

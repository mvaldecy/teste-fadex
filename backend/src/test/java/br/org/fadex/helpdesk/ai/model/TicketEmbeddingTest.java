package br.org.fadex.helpdesk.ai.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class TicketEmbeddingTest {

	@Test
	void deveConverterValoresParaLiteralPgVector() {
		TicketEmbedding embedding = new TicketEmbedding(List.of(0.1, -0.2, 1.0), "modelo-customizado");

		assertThat(embedding.toPgVectorLiteral()).isEqualTo("[0.1,-0.2,1.0]");
	}

	@Test
	void deveRejeitarValoresVaziosNaoFinitosOuModeloEmBranco() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new TicketEmbedding(List.of(), "modelo"));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new TicketEmbedding(List.of(Double.NaN), "modelo"));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new TicketEmbedding(List.of(0.1), " "));
	}
}

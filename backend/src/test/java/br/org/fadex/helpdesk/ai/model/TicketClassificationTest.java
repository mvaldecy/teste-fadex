package br.org.fadex.helpdesk.ai.model;

import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class TicketClassificationTest {

	@Test
	void deveRejeitarConfidenceNaoFinita() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new TicketClassification(
						TicketCategory.ACESSO,
						TicketPriority.MEDIA,
						Double.NaN,
						"Classificacao valida"
				));
	}
}

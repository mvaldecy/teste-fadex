package br.org.fadex.helpdesk.ai.triage;

import br.org.fadex.helpdesk.ai.model.TicketClassification;
import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FallbackTicketClassifierTest {

	private final FallbackTicketClassifier classifier = new FallbackTicketClassifier();

	@Test
	void deveClassificarAcessoComPrioridadeAltaQuandoTextoIndicarBloqueioDeLogin() {
		TicketClassification classification = classifier.classify(
				"Login bloqueado",
				"Usuario nao consegue acessar o sistema e precisa desbloquear senha com urgencia."
		);

		assertThat(classification.category()).isEqualTo(TicketCategory.ACESSO);
		assertThat(classification.priority()).isEqualTo(TicketPriority.ALTA);
		assertThat(classification.justification()).contains("fallback");
	}

	@Test
	void deveClassificarFinanceiroComPrioridadeMedia() {
		TicketClassification classification = classifier.classify(
				"Problema com nota fiscal",
				"Preciso corrigir informacoes de pagamento e financeiro."
		);

		assertThat(classification.category()).isEqualTo(TicketCategory.FINANCEIRO);
		assertThat(classification.priority()).isEqualTo(TicketPriority.MEDIA);
	}
}

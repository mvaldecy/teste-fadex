package br.org.fadex.helpdesk.ai.client;

import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalAiTriageClientPromptTest {

	/**
	 * O prompt precisa listar os valores aceitos: sem eles o modelo responde rotulos livres, o
	 * parse estoura no valueOf e toda classificacao cai no fallback heuristico.
	 */
	@Test
	void devePublicarTodosOsValoresDeCategoriaEPrioridadeNoPrompt() {
		String prompt = LocalAiTriageClient.systemPrompt();

		for (TicketCategory categoria : TicketCategory.values()) {
			assertThat(prompt).contains(categoria.name());
		}

		for (TicketPriority prioridade : TicketPriority.values()) {
			assertThat(prompt).contains(prioridade.name());
		}
	}
}

package br.org.fadex.helpdesk.ai.client;

import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

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

	/**
	 * O schema enviado no campo {@code format} precisa restringir categoria e prioridade por
	 * {@code enum}: e isso que impede o modelo de devolver rotulo inexistente, e vale mais que o
	 * mesmo pedido feito em linguagem natural no prompt. Os valores saem dos enums para que um
	 * valor novo nao fique de fora sem ninguem perceber.
	 */
	@Test
	void devePublicarOsValoresDosEnumsNoSchemaDaResposta() {
		Map<String, Object> schema = LocalAiTriageClient.responseSchema();

		assertThat(schema).containsEntry("type", "object");
		assertThat(schema.get("required")).isEqualTo(
				List.of("category", "priority", "confidence", "justification")
		);
		assertThat(enumValues(schema, "category"))
				.containsExactlyElementsOf(names(TicketCategory.values()));
		assertThat(enumValues(schema, "priority"))
				.containsExactlyElementsOf(names(TicketPriority.values()));
	}

	@Test
	void deveDeclararCategoriaEPrioridadeComoTextoNoSchema() {
		Map<String, Object> schema = LocalAiTriageClient.responseSchema();

		assertThat(property(schema, "category")).containsEntry("type", "string");
		assertThat(property(schema, "priority")).containsEntry("type", "string");
		assertThat(property(schema, "confidence")).containsEntry("type", "number");
		assertThat(property(schema, "justification")).containsEntry("type", "string");
	}

	private List<String> names(Enum<?>[] values) {
		return Arrays.stream(values).map(Enum::name).toList();
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> property(Map<String, Object> schema, String field) {
		Map<String, Object> properties = (Map<String, Object>) schema.get("properties");

		return (Map<String, Object>) properties.get(field);
	}

	@SuppressWarnings("unchecked")
	private List<String> enumValues(Map<String, Object> schema, String field) {
		return (List<String>) property(schema, field).get("enum");
	}
}

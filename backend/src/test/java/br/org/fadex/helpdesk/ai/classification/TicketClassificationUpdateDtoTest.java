package br.org.fadex.helpdesk.ai.classification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TicketClassificationUpdateDtoTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void deveAceitarJustificationComoNomeCanonico() throws Exception {
		String json = """
				{"category":"ACESSO","priority":"MEDIA","justification":"Texto do admin."}
				""";

		TicketClassificationUpdateDto dto = objectMapper.readValue(json, TicketClassificationUpdateDto.class);

		assertThat(dto.justification()).isEqualTo("Texto do admin.");
	}

	@Test
	void deveAceitarClassificationJustificationEnviadoPeloFrontend() throws Exception {
		String json = """
				{"category":"ACESSO","priority":"MEDIA","classificationJustification":"Texto do admin."}
				""";

		TicketClassificationUpdateDto dto = objectMapper.readValue(json, TicketClassificationUpdateDto.class);

		assertThat(dto.justification()).isEqualTo("Texto do admin.");
	}
}

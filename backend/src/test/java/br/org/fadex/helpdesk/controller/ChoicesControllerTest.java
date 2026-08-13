package br.org.fadex.helpdesk.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChoicesControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void deveEntregarChoicesAgregadosSemAutenticacao() throws Exception {
		mockMvc.perform(get("/api/v1/choices"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roles[0].value").value("ADMIN"))
				.andExpect(jsonPath("$.roles[0].label").value("Administrador"))
				.andExpect(jsonPath("$.roles[1].value").value("SOLICITANTE"))
				.andExpect(jsonPath("$.roles[1].label").value("Solicitante"))
				.andExpect(jsonPath("$.ticketStatuses[1].value").value("EM_ANDAMENTO"))
				.andExpect(jsonPath("$.ticketStatuses[1].label").value("Em andamento"))
				.andExpect(jsonPath("$.ticketPriorities[1].value").value("MEDIA"))
				.andExpect(jsonPath("$.ticketPriorities[1].label").value("Media"))
				.andExpect(jsonPath("$.ticketCategories[6].value").value("OUTROS"))
				.andExpect(jsonPath("$.ticketCategories[6].label").value("Outros"))
				.andExpect(jsonPath("$.classificationOrigins[2].value").value("PENDENTE"))
				.andExpect(jsonPath("$.classificationOrigins[2].label").value("Pendente"));
	}
}

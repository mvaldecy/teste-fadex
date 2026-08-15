package br.org.fadex.helpdesk.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TicketStatusTransitionControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void deveEntregarAsTransicoesPermitidasPorStatus() throws Exception {
		mockMvc.perform(get("/api/v1/ticket-status-transitions").with(jwt()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ABERTO").isArray())
				.andExpect(jsonPath("$.ABERTO[0]").value("EM_ANDAMENTO"))
				.andExpect(jsonPath("$.RESOLVIDO[0]").value("EM_ANDAMENTO"))
				.andExpect(jsonPath("$.RESOLVIDO[1]").value("FECHADO"))
				.andExpect(jsonPath("$.FECHADO").isEmpty());
	}

	@Test
	void deveExigirAutenticacao() throws Exception {
		mockMvc.perform(get("/api/v1/ticket-status-transitions"))
				.andExpect(status().isUnauthorized());
	}
}

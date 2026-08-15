package br.org.fadex.helpdesk.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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

	/**
	 * As listas sao ordenadas alfabeticamente pelo controller, entao a assercao e por conteudo e nao
	 * por posicao: acrescentar um destino novo a matriz nao pode quebrar um teste de contrato.
	 */
	@Test
	void deveEntregarAsTransicoesPermitidasPorStatus() throws Exception {
		mockMvc.perform(get("/api/v1/ticket-status-transitions").with(jwt()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ABERTO").isArray())
				.andExpect(jsonPath("$.ABERTO", containsInAnyOrder(
						"EM_ANDAMENTO", "RESOLVIDO", "FECHADO", "CANCELADO")))
				.andExpect(jsonPath("$.RESOLVIDO", containsInAnyOrder("EM_ANDAMENTO", "FECHADO")))
				.andExpect(jsonPath("$.FECHADO").isEmpty());
	}

	/**
	 * A matriz publicada e a do dominio e independe de papel: quem pode cancelar e camada de cima,
	 * aplicada pelo cliente sobre esta resposta e reconferida pelo servidor.
	 */
	@Test
	void deveEntregarCancelamentoComoDestinoETerminal() throws Exception {
		mockMvc.perform(get("/api/v1/ticket-status-transitions").with(jwt()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ABERTO", hasItem("CANCELADO")))
				.andExpect(jsonPath("$.EM_ANDAMENTO", hasItem("CANCELADO")))
				.andExpect(jsonPath("$.RESOLVIDO", not(hasItem("CANCELADO"))))
				.andExpect(jsonPath("$.CANCELADO").isEmpty());
	}

	@Test
	void deveExigirAutenticacao() throws Exception {
		mockMvc.perform(get("/api/v1/ticket-status-transitions"))
				.andExpect(status().isUnauthorized());
	}
}

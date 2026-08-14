package br.org.fadex.helpdesk.sse.controller;

import br.org.fadex.helpdesk.sse.service.NotificationEmitterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private NotificationEmitterRegistry registry;

	@Test
	void deveAbrirStreamParaUsuarioAutenticado() throws Exception {
		int conexoesAntes = registry.countConnections();

		mockMvc.perform(get("/api/v1/notifications/stream")
						.accept(MediaType.TEXT_EVENT_STREAM)
						.with(jwt().jwt(builder -> builder
								.claim("userId", "71e9c3d9-53b2-4c4e-9803-c504754dbb45")
								.claim("role", "ADMIN"))))
				.andExpect(status().isOk())
				.andExpect(request().asyncStarted());

		assertThat(registry.countConnections()).isGreaterThan(conexoesAntes);
	}

	@Test
	void deveRecusarStreamSemAutenticacao() throws Exception {
		mockMvc.perform(get("/api/v1/notifications/stream").accept(MediaType.TEXT_EVENT_STREAM))
				.andExpect(status().isUnauthorized());
	}
}

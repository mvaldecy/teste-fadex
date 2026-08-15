package br.org.fadex.helpdesk.controller;

import br.org.fadex.helpdesk.exception.ConflictException;
import br.org.fadex.helpdesk.exception.NotFoundException;
import br.org.fadex.helpdesk.model.enums.ClassificationOrigin;
import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import br.org.fadex.helpdesk.model.enums.TicketStatus;
import br.org.fadex.helpdesk.model.ticket.Ticket;
import br.org.fadex.helpdesk.model.ticket.TicketAssigneeUpdateDto;
import br.org.fadex.helpdesk.model.ticket.TicketDto;
import br.org.fadex.helpdesk.model.ticket.TicketMapper;
import br.org.fadex.helpdesk.model.ticket.TicketStatusUpdateDto;
import br.org.fadex.helpdesk.model.user.User;
import br.org.fadex.helpdesk.security.PasswordChangeRequiredFilter;
import br.org.fadex.helpdesk.security.RestAccessDeniedHandler;
import br.org.fadex.helpdesk.security.RestAuthenticationEntryPoint;
import br.org.fadex.helpdesk.security.SecurityConfig;
import br.org.fadex.helpdesk.service.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fatia web dos endpoints de ciclo de vida do chamado.
 *
 * Importa a {@link SecurityConfig} de producao para exercitar a cadeia real — CSRF desabilitado e
 * resource server JWT — e nao a cadeia default do slice. O {@code JwtDecoder} e mockado porque a
 * autenticacao dos testes entra pelo pos-processador {@code jwt()}, sem token real.
 */
@WebMvcTest(TicketController.class)
@Import({
		SecurityConfig.class,
		RestAuthenticationEntryPoint.class,
		RestAccessDeniedHandler.class,
		PasswordChangeRequiredFilter.class
})
@TestPropertySource(properties = "security.cors.allowed-origins=http://localhost:3000")
class TicketControllerTest {

	private static final UUID TICKET_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
	private static final UUID ASSIGNEE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TicketService ticketService;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@Test
	void devePermitirTrocaDeStatus() throws Exception {
		when(ticketService.updateStatus(eq(TICKET_ID), any(TicketStatusUpdateDto.class)))
				.thenReturn(ticketDto(TicketStatus.EM_ANDAMENTO));

		mockMvc.perform(patch("/api/v1/tickets/{id}/status", TICKET_ID)
						.with(jwt())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"EM_ANDAMENTO\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("EM_ANDAMENTO"));
	}

	@Test
	void deveRecusarTrocaDeStatusSemAutenticacao() throws Exception {
		mockMvc.perform(patch("/api/v1/tickets/{id}/status", TICKET_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"EM_ANDAMENTO\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void deveRecusarTrocaDeStatusSemCampoObrigatorio() throws Exception {
		mockMvc.perform(patch("/api/v1/tickets/{id}/status", TICKET_ID)
						.with(jwt())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fields[0].field").value("status"));
	}

	@Test
	void deveMapearConflitoDeTransicaoParaStatus409() throws Exception {
		doThrow(new ConflictException("Chamado fechado nao pode ser reaberto."))
				.when(ticketService).updateStatus(eq(TICKET_ID), any(TicketStatusUpdateDto.class));

		mockMvc.perform(patch("/api/v1/tickets/{id}/status", TICKET_ID)
						.with(jwt())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"ABERTO\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Chamado fechado nao pode ser reaberto."));
	}

	@Test
	void deveAtribuirResponsavel() throws Exception {
		when(ticketService.updateAssignee(eq(TICKET_ID), any(TicketAssigneeUpdateDto.class)))
				.thenReturn(ticketDto(TicketStatus.EM_ANDAMENTO));

		mockMvc.perform(patch("/api/v1/tickets/{id}/assignee", TICKET_ID)
						.with(jwt())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"assigneeId\":\"" + ASSIGNEE_ID + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(TICKET_ID.toString()));
	}

	@Test
	void deveRecusarAtribuicaoSemResponsavel() throws Exception {
		mockMvc.perform(patch("/api/v1/tickets/{id}/assignee", TICKET_ID)
						.with(jwt())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields[0].field").value("assigneeId"));
	}

	@Test
	void deveMapearConflitoDeAtribuicaoParaStatus409() throws Exception {
		doThrow(new ConflictException("O chamado ja possui responsavel."))
				.when(ticketService).updateAssignee(eq(TICKET_ID), any(TicketAssigneeUpdateDto.class));

		mockMvc.perform(patch("/api/v1/tickets/{id}/assignee", TICKET_ID)
						.with(jwt())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"assigneeId\":\"" + ASSIGNEE_ID + "\"}"))
				.andExpect(status().isConflict());
	}

	@Test
	void deveRemoverResponsavel() throws Exception {
		when(ticketService.removeAssignee(TICKET_ID)).thenReturn(ticketDto(TicketStatus.ABERTO));

		mockMvc.perform(delete("/api/v1/tickets/{id}/assignee", TICKET_ID).with(jwt()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ABERTO"));
	}

	@Test
	void deveMapearChamadoInexistenteParaStatus404() throws Exception {
		doThrow(new NotFoundException("Chamado não encontrado."))
				.when(ticketService).removeAssignee(TICKET_ID);

		mockMvc.perform(delete("/api/v1/tickets/{id}/assignee", TICKET_ID).with(jwt()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void deveMapearConflitoDeRemocaoParaStatus409() throws Exception {
		doThrow(new ConflictException("O chamado nao possui responsavel atribuido."))
				.when(ticketService).removeAssignee(TICKET_ID);

		mockMvc.perform(delete("/api/v1/tickets/{id}/assignee", TICKET_ID).with(jwt()))
				.andExpect(status().isConflict());
	}

	/**
	 * Exclusao logica: o {@code DELETE} devolve 200 com o chamado em CANCELADO, e nao 204, porque o
	 * chamado continua existindo — quem chamou precisa ver o retrato novo.
	 */
	@Test
	void deveCancelarChamadoERetornarORetratoCancelado() throws Exception {
		when(ticketService.cancel(TICKET_ID)).thenReturn(ticketDto(TicketStatus.CANCELADO));

		mockMvc.perform(delete("/api/v1/tickets/{id}", TICKET_ID).with(jwt()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(TICKET_ID.toString()))
				.andExpect(jsonPath("$.status").value("CANCELADO"));
	}

	@Test
	void deveRecusarCancelamentoSemAutenticacao() throws Exception {
		mockMvc.perform(delete("/api/v1/tickets/{id}", TICKET_ID))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void deveMapearConflitoDeCancelamentoParaStatus409() throws Exception {
		doThrow(new ConflictException("Transicao de Fechado para Cancelado nao e permitida."))
				.when(ticketService).cancel(TICKET_ID);

		mockMvc.perform(delete("/api/v1/tickets/{id}", TICKET_ID).with(jwt()))
				.andExpect(status().isConflict());
	}

	@Test
	void deveMapearCancelamentoDeChamadoInexistenteParaStatus404() throws Exception {
		doThrow(new NotFoundException("Chamado não encontrado."))
				.when(ticketService).cancel(TICKET_ID);

		mockMvc.perform(delete("/api/v1/tickets/{id}", TICKET_ID).with(jwt()))
				.andExpect(status().isNotFound());
	}

	/**
	 * O DTO e montado pelo mapper de producao de proposito: componente novo no record nao quebra
	 * este teste, que se importa com status HTTP e serializacao, nao com a forma do payload.
	 */
	private TicketDto ticketDto(TicketStatus status) {
		User requester = new User("Maria Solicitante", "maria@fadex.org.br", "hash", Role.SOLICITANTE, false);
		User assignee = new User("Ana Admin", "ana@fadex.org.br", "hash", Role.ADMIN, false);
		ReflectionTestUtils.setField(requester, "id", UUID.randomUUID());
		ReflectionTestUtils.setField(assignee, "id", ASSIGNEE_ID);

		Ticket ticket = new Ticket(
				"Erro ao acessar sistema",
				"Nao consigo acessar o sistema interno.",
				TicketCategory.SISTEMAS,
				TicketPriority.MEDIA,
				ClassificationOrigin.PENDENTE,
				requester
		);
		ReflectionTestUtils.setField(ticket, "id", TICKET_ID);
		ticket.assignTo(assignee);
		ticket.changeStatus(status);

		return TicketMapper.toResponseDto(ticket);
	}
}

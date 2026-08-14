package br.org.fadex.helpdesk.model.ticket;

import br.org.fadex.helpdesk.model.enums.ClassificationOrigin;
import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import br.org.fadex.helpdesk.model.enums.TicketStatus;
import br.org.fadex.helpdesk.model.user.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TicketTest {

	@Test
	void deveNascerAberto() {
		assertThat(newTicket().getStatus()).isEqualTo(TicketStatus.ABERTO);
	}

	@Test
	void deveTrocarStatus() {
		Ticket ticket = newTicket();

		ticket.changeStatus(TicketStatus.EM_ANDAMENTO);

		assertThat(ticket.getStatus()).isEqualTo(TicketStatus.EM_ANDAMENTO);
	}

	@Test
	void deveRemoverResponsavel() {
		Ticket ticket = newTicket();
		ticket.assignTo(newUser("responsavel@fadex.org.br", Role.ADMIN));

		ticket.unassign();

		assertThat(ticket.getAssignee()).isNull();
	}

	private Ticket newTicket() {
		return new Ticket(
				"Chamado de teste",
				"Descricao do chamado de teste",
				TicketCategory.ACESSO,
				TicketPriority.MEDIA,
				ClassificationOrigin.PENDENTE,
				newUser("solicitante@teste.org.br", Role.SOLICITANTE)
		);
	}

	private User newUser(String email, Role role) {
		return new User("Usuario de teste", email, "hash", role);
	}
}

package br.org.fadex.helpdesk.model.ticket;

import br.org.fadex.helpdesk.model.enums.ClassificationOrigin;
import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import br.org.fadex.helpdesk.model.user.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TicketMapperTest {

	@Test
	void deveMapearSugestaoEConfiancaDaIa() {
		Ticket ticket = ticket();
		ticket.applyAiSuggestion(TicketCategory.INFRAESTRUTURA, TicketPriority.ALTA, 0.87);

		TicketDto ticketDto = TicketMapper.toResponseDto(ticket);

		assertThat(ticketDto.aiSuggestedCategory()).isEqualTo(TicketCategory.INFRAESTRUTURA);
		assertThat(ticketDto.aiSuggestedPriority()).isEqualTo(TicketPriority.ALTA);
		assertThat(ticketDto.confidence()).isEqualTo(0.87);
	}

	@Test
	void deveMapearSugestaoComoNulaQuandoIaAindaNaoRespondeu() {
		Ticket ticket = ticket();

		TicketDto ticketDto = TicketMapper.toResponseDto(ticket);

		assertThat(ticketDto.aiSuggestedCategory()).isNull();
		assertThat(ticketDto.aiSuggestedPriority()).isNull();
		assertThat(ticketDto.confidence()).isNull();
	}

	private Ticket ticket() {
		User requester = new User(
				"Maria Solicitante",
				"maria@fadex.org.br",
				"hash",
				Role.SOLICITANTE
		);

		return new Ticket(
				"Sem acesso a rede",
				"O predio inteiro esta sem conexao.",
				TicketCategory.OUTROS,
				TicketPriority.MEDIA,
				ClassificationOrigin.PENDENTE,
				requester
		);
	}
}

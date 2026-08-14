package br.org.fadex.helpdesk.model.event;

import br.org.fadex.helpdesk.model.ticket.TicketMapper;
import br.org.fadex.helpdesk.model.user.User;
import br.org.fadex.helpdesk.model.user.UserMapper;

public abstract class TicketEventMapper {

	private TicketEventMapper() {
	}

	public static TicketEventDto toResponseDto(TicketEvent ticketEvent) {
		User actor = ticketEvent.getActor();

		return new TicketEventDto(
				ticketEvent.getId(),
				TicketMapper.toMinDto(ticketEvent.getTicket()),
				actor != null ? UserMapper.toMinDto(actor) : null,
				ticketEvent.getType(),
				ticketEvent.getDescription(),
				ticketEvent.getMetadata(),
				ticketEvent.getCreatedAt()
		);
	}

	public static TicketEventMinDto toMinDto(TicketEvent ticketEvent) {
		User actor = ticketEvent.getActor();

		return new TicketEventMinDto(
				ticketEvent.getId(),
				actor != null ? UserMapper.toMinDto(actor) : null,
				ticketEvent.getType(),
				ticketEvent.getDescription(),
				ticketEvent.getCreatedAt()
		);
	}
}

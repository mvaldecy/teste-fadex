package br.org.fadex.helpdesk.model.comment;

import br.org.fadex.helpdesk.model.ticket.Ticket;
import br.org.fadex.helpdesk.model.user.User;
import br.org.fadex.helpdesk.model.user.UserMapper;

public abstract class TicketCommentMapper {

	private TicketCommentMapper() {
	}

	public static TicketCommentDto toResponseDto(TicketComment ticketComment) {
		return new TicketCommentDto(
				ticketComment.getId(),
				UserMapper.toMinDto(ticketComment.getAuthor()),
				ticketComment.getText(),
				ticketComment.getCreatedAt(),
				ticketComment.getUpdatedAt()
		);
	}

	public static TicketCommentMinDto toMinDto(TicketComment ticketComment) {
		return new TicketCommentMinDto(
				ticketComment.getId(),
				UserMapper.toMinDto(ticketComment.getAuthor()),
				ticketComment.getText(),
				ticketComment.getCreatedAt()
		);
	}

	public static TicketComment toEntity(TicketCommentCreationDto ticketCommentCreationDto, Ticket ticket, User author) {
		return new TicketComment(
				ticket,
				author,
				ticketCommentCreationDto.text()
		);
	}
}

package br.org.fadex.helpdesk.model.ticket;

import br.org.fadex.helpdesk.model.enums.ClassificationOrigin;
import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import br.org.fadex.helpdesk.model.user.User;
import br.org.fadex.helpdesk.model.user.UserMapper;

public abstract class TicketMapper {

	private TicketMapper() {
	}

	public static TicketDto toResponseDto(Ticket ticket) {
		User requester = ticket.getRequester();
		User assignee = ticket.getAssignee();

		return new TicketDto(
				ticket.getId(),
				ticket.getTitle(),
				ticket.getDescription(),
				ticket.getCategory(),
				ticket.getPriority(),
				ticket.getStatus(),
				ticket.getClassificationOrigin(),
				ticket.getClassificationJustification(),
				UserMapper.toMinDto(requester),
				assignee != null ? UserMapper.toMinDto(assignee) : null,
				ticket.getAssignedAt(),
				ticket.getFirstResponseAt(),
				ticket.getResolvedAt(),
				ticket.getClosedAt(),
				ticket.getCreatedAt(),
				ticket.getUpdatedAt()
		);
	}

	public static Ticket toEntity(TicketCreationDto ticketCreationDto, User requester) {
		return toEntity(
				ticketCreationDto,
				requester,
				TicketCategory.OUTROS,
				TicketPriority.MEDIA,
				ClassificationOrigin.PENDENTE
		);
	}

	public static Ticket toEntity(
			TicketCreationDto ticketCreationDto,
			User requester,
			TicketCategory category,
			TicketPriority priority,
			ClassificationOrigin classificationOrigin
	) {
		return new Ticket(
				ticketCreationDto.title(),
				ticketCreationDto.description(),
				category,
				priority,
				classificationOrigin,
				requester
		);
	}

	public static TicketMinDto toMinDto(Ticket ticket) {
		User requester = ticket.getRequester();
		User assignee = ticket.getAssignee();

		return new TicketMinDto(
				ticket.getId(),
				ticket.getTitle(),
				ticket.getCategory(),
				ticket.getPriority(),
				ticket.getStatus(),
				ticket.getClassificationOrigin(),
				UserMapper.toMinDto(requester),
				assignee != null ? UserMapper.toMinDto(assignee) : null,
				ticket.getAssignedAt(),
				ticket.getCreatedAt()
		);
	}
}

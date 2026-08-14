package br.org.fadex.helpdesk.model.event;

import br.org.fadex.helpdesk.model.enums.TicketEventType;
import br.org.fadex.helpdesk.model.ticket.Ticket;
import br.org.fadex.helpdesk.model.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ticket_events")
@EntityListeners(AuditingEntityListener.class)
public class TicketEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "ticket_id", nullable = false)
	private Ticket ticket;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "actor_id")
	private User actor;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private TicketEventType type;

	@Column(nullable = false, length = 255)
	private String description;

	@Column(columnDefinition = "text")
	private String metadata;

	@Column(name = "created_at", nullable = false)
	@CreatedDate
	private LocalDateTime createdAt;

	protected TicketEvent() {
	}

	public TicketEvent(Ticket ticket, User actor, TicketEventType type, String description, String metadata) {
		this.ticket = ticket;
		this.actor = actor;
		this.type = type;
		this.description = description;
		this.metadata = metadata;
	}

	public UUID getId() {
		return id;
	}

	public Ticket getTicket() {
		return ticket;
	}

	public User getActor() {
		return actor;
	}

	public TicketEventType getType() {
		return type;
	}

	public String getDescription() {
		return description;
	}

	public String getMetadata() {
		return metadata;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}

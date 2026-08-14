package br.org.fadex.helpdesk.model.ticket;

import br.org.fadex.helpdesk.model.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
@Table(name = "ticket_links")
@EntityListeners(AuditingEntityListener.class)
public class TicketLink {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "source_ticket_id", nullable = false)
	private Ticket sourceTicket;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "target_ticket_id", nullable = false)
	private Ticket targetTicket;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "created_by", nullable = false)
	private User createdBy;

	@Column(name = "created_at", nullable = false)
	@CreatedDate
	private LocalDateTime createdAt;

	protected TicketLink() {
	}

	public TicketLink(Ticket sourceTicket, Ticket targetTicket, User createdBy) {
		this.sourceTicket = sourceTicket;
		this.targetTicket = targetTicket;
		this.createdBy = createdBy;
	}

	public UUID getId() {
		return id;
	}

	public Ticket getSourceTicket() {
		return sourceTicket;
	}

	public Ticket getTargetTicket() {
		return targetTicket;
	}

	public User getCreatedBy() {
		return createdBy;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}

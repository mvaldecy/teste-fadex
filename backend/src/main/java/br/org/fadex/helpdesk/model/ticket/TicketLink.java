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

	/**
	 * Similaridade de cosseno do par no instante da deteccao.
	 *
	 * Nulavel: vinculos gravados antes da V6 nao tem o valor, e nao ha backfill possivel — o
	 * embedding de origem pode ter mudado desde entao. Quem le precisa tratar a ausencia.
	 */
	@Column(name = "similarity")
	private Double similarity;

	@Column(name = "created_at", nullable = false)
	@CreatedDate
	private LocalDateTime createdAt;

	protected TicketLink() {
	}

	public TicketLink(Ticket sourceTicket, Ticket targetTicket, User createdBy) {
		this(sourceTicket, targetTicket, createdBy, null);
	}

	public TicketLink(Ticket sourceTicket, Ticket targetTicket, User createdBy, Double similarity) {
		this.sourceTicket = sourceTicket;
		this.targetTicket = targetTicket;
		this.createdBy = createdBy;
		this.similarity = similarity;
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

	public Double getSimilarity() {
		return similarity;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}

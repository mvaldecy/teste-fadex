package br.org.fadex.helpdesk.ai.job;

import br.org.fadex.helpdesk.model.ticket.Ticket;
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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_jobs")
@EntityListeners(AuditingEntityListener.class)
public class AiJob {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "ticket_id", nullable = false)
	private UUID ticketId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "ticket_id", insertable = false, updatable = false)
	private Ticket ticket;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private AiJobType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private AiJobStatus status;

	@Column(nullable = false)
	private int attempts;

	@Column(name = "next_attempt_at", nullable = false)
	private LocalDateTime nextAttemptAt;

	@Column(name = "last_error", columnDefinition = "text")
	private String lastError;

	@Column(name = "created_at", nullable = false)
	@CreatedDate
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	@LastModifiedDate
	private LocalDateTime updatedAt;

	protected AiJob() {
	}

	public AiJob(UUID ticketId, AiJobType type, LocalDateTime nextAttemptAt) {
		this.ticketId = ticketId;
		this.type = type;
		this.status = AiJobStatus.PENDING;
		this.attempts = 0;
		this.nextAttemptAt = nextAttemptAt;
	}

	public void markProcessing() {
		this.status = AiJobStatus.PROCESSING;
	}

	public void markDone() {
		this.status = AiJobStatus.DONE;
	}

	public void markFailed(String lastError, LocalDateTime nextAttemptAt) {
		this.status = AiJobStatus.FAILED;
		this.attempts++;
		this.lastError = lastError;
		this.nextAttemptAt = nextAttemptAt;
	}

	public void retry(LocalDateTime nextAttemptAt) {
		this.status = AiJobStatus.PENDING;
		this.nextAttemptAt = nextAttemptAt;
		this.lastError = null;
	}

	public void scheduleRetry() {
		this.status = AiJobStatus.PENDING;
	}

	public UUID getId() {
		return id;
	}

	public UUID getTicketId() {
		return ticketId;
	}

	public Ticket getTicket() {
		return ticket;
	}

	public AiJobType getType() {
		return type;
	}

	public AiJobStatus getStatus() {
		return status;
	}

	public int getAttempts() {
		return attempts;
	}

	public LocalDateTime getNextAttemptAt() {
		return nextAttemptAt;
	}

	public String getLastError() {
		return lastError;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}

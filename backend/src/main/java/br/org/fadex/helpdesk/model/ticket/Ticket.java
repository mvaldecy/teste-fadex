package br.org.fadex.helpdesk.model.ticket;

import br.org.fadex.helpdesk.model.enums.ClassificationOrigin;
import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import br.org.fadex.helpdesk.model.enums.TicketStatus;
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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "tickets")
@EntityListeners(AuditingEntityListener.class)
public class Ticket {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, length = 160)
	private String title;

	@Column(nullable = false, columnDefinition = "text")
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private TicketCategory category;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TicketPriority priority;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private TicketStatus status;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "requester_id", nullable = false)
	private User requester;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "assignee_id")
	private User assignee;

	@Enumerated(EnumType.STRING)
	@Column(name = "classification_origin", nullable = false, length = 30)
	private ClassificationOrigin classificationOrigin;

	@Column(name = "classification_justification", columnDefinition = "text")
	private String classificationJustification;

	@Column(columnDefinition = "text")
	private String embedding;

	@Column(name = "embedding_model", length = 120)
	private String embeddingModel;

	@Column(name = "embedding_updated_at")
	private LocalDateTime embeddingUpdatedAt;

	@Column(name = "created_at", nullable = false)
	@CreatedDate
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	@LastModifiedDate
	private LocalDateTime updatedAt;

	protected Ticket() {
	}

	public Ticket(
			String title,
			String description,
			TicketCategory category,
			TicketPriority priority,
			ClassificationOrigin classificationOrigin,
			User requester
	) {
		this.title = title;
		this.description = description;
		this.category = category;
		this.priority = priority;
		this.status = TicketStatus.ABERTO;
		this.classificationOrigin = classificationOrigin;
		this.requester = requester;
	}

	public void assignTo(User assignee) {
		this.assignee = assignee;
	}

	public void applyAutomaticClassification(
			TicketCategory category,
			TicketPriority priority,
			String classificationJustification
	) {
		this.category = category;
		this.priority = priority;
		this.classificationOrigin = ClassificationOrigin.IA;
		this.classificationJustification = classificationJustification;
	}

	public void applyManualClassification(
			TicketCategory category,
			TicketPriority priority,
			String classificationJustification
	) {
		this.category = category;
		this.priority = priority;
		this.classificationOrigin = ClassificationOrigin.MANUAL;
		this.classificationJustification = classificationJustification;
	}

	public void updateEmbedding(String embedding, String embeddingModel, LocalDateTime embeddingUpdatedAt) {
		this.embedding = embedding;
		this.embeddingModel = embeddingModel;
		this.embeddingUpdatedAt = embeddingUpdatedAt;
	}

	public UUID getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public TicketCategory getCategory() {
		return category;
	}

	public TicketPriority getPriority() {
		return priority;
	}

	public TicketStatus getStatus() {
		return status;
	}

	public User getRequester() {
		return requester;
	}

	public User getAssignee() {
		return assignee;
	}

	public ClassificationOrigin getClassificationOrigin() {
		return classificationOrigin;
	}

	public String getClassificationJustification() {
		return classificationJustification;
	}

	public String getEmbedding() {
		return embedding;
	}

	public String getEmbeddingModel() {
		return embeddingModel;
	}

	public LocalDateTime getEmbeddingUpdatedAt() {
		return embeddingUpdatedAt;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		}
		if (!(object instanceof Ticket ticket)) {
			return false;
		}
		return id != null && Objects.equals(id, ticket.id);
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}
}

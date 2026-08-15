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

	@Column(name = "embedding_model", length = 120)
	private String embeddingModel;

	@Column(name = "embedding_updated_at")
	private LocalDateTime embeddingUpdatedAt;

	@Column(name = "resolved_at")
	private LocalDateTime resolvedAt;

	@Column(name = "closed_at")
	private LocalDateTime closedAt;

	@Column(name = "first_response_at")
	private LocalDateTime firstResponseAt;

	@Column(name = "assigned_at")
	private LocalDateTime assignedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "ai_suggested_category", length = 40)
	private TicketCategory aiSuggestedCategory;

	@Enumerated(EnumType.STRING)
	@Column(name = "ai_suggested_priority", length = 20)
	private TicketPriority aiSuggestedPriority;

	@Column(name = "ai_confidence")
	private Double aiConfidence;

	@Column(name = "classification_reviewed_at")
	private LocalDateTime classificationReviewedAt;

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

	public void unassign() {
		this.assignee = null;
	}

	public void changeStatus(TicketStatus status) {
		this.status = status;
	}

	public void applyClassification(
			TicketCategory category,
			TicketPriority priority,
			ClassificationOrigin classificationOrigin,
			String classificationJustification
	) {
		this.category = category;
		this.priority = priority;
		this.classificationOrigin = classificationOrigin;
		this.classificationJustification = classificationJustification;
	}

	public void applyAutomaticClassification(
			TicketCategory category,
			TicketPriority priority,
			String classificationJustification
	) {
		applyClassification(category, priority, ClassificationOrigin.IA, classificationJustification);
	}

	public void applyManualClassification(
			TicketCategory category,
			TicketPriority priority,
			String classificationJustification
	) {
		applyClassification(category, priority, ClassificationOrigin.MANUAL, classificationJustification);
	}

	/**
	 * Carimba o instante em que o ADMIN revisou a classificacao — aceitando ou corrigindo.
	 *
	 * E este carimbo que separa "sugestao aceita" de "ninguem olhou": sem ele os dois casos ficam
	 * com origem IA e valores iguais aos sugeridos, e a taxa de concordancia admin x IA passaria a
	 * contar chamado nunca revisado como aceite.
	 */
	public void markClassificationReviewed(LocalDateTime classificationReviewedAt) {
		this.classificationReviewedAt = classificationReviewedAt;
	}

	public void applyAiSuggestion(TicketCategory category, TicketPriority priority, Double confidence) {
		this.aiSuggestedCategory = category;
		this.aiSuggestedPriority = priority;
		this.aiConfidence = confidence;
	}

	public void markResolved(LocalDateTime resolvedAt) {
		this.resolvedAt = resolvedAt;
	}

	public void markClosed(LocalDateTime closedAt) {
		this.closedAt = closedAt;
	}

	public void markAssigned(LocalDateTime assignedAt) {
		this.assignedAt = assignedAt;
	}

	public void markFirstResponse(LocalDateTime firstResponseAt) {
		this.firstResponseAt = firstResponseAt;
	}

	public void updateEmbeddingMetadata(String embeddingModel, LocalDateTime embeddingUpdatedAt) {
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

	public String getEmbeddingModel() {
		return embeddingModel;
	}

	public LocalDateTime getEmbeddingUpdatedAt() {
		return embeddingUpdatedAt;
	}

	public LocalDateTime getResolvedAt() {
		return resolvedAt;
	}

	public LocalDateTime getClosedAt() {
		return closedAt;
	}

	public LocalDateTime getFirstResponseAt() {
		return firstResponseAt;
	}

	public LocalDateTime getAssignedAt() {
		return assignedAt;
	}

	public TicketCategory getAiSuggestedCategory() {
		return aiSuggestedCategory;
	}

	public TicketPriority getAiSuggestedPriority() {
		return aiSuggestedPriority;
	}

	public Double getAiConfidence() {
		return aiConfidence;
	}

	public LocalDateTime getClassificationReviewedAt() {
		return classificationReviewedAt;
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

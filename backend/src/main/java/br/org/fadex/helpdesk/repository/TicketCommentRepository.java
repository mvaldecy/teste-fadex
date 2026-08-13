package br.org.fadex.helpdesk.repository;

import br.org.fadex.helpdesk.model.comment.TicketComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketCommentRepository extends JpaRepository<TicketComment, UUID> {

	List<TicketComment> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);
}

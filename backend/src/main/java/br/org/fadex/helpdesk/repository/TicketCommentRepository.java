package br.org.fadex.helpdesk.repository;

import br.org.fadex.helpdesk.model.comment.TicketComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface TicketCommentRepository extends JpaRepository<TicketComment, UUID>, JpaSpecificationExecutor<TicketComment> {

	List<TicketComment> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);
}

package br.org.fadex.helpdesk.repository;

import br.org.fadex.helpdesk.model.ticket.TicketLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketLinkRepository extends JpaRepository<TicketLink, UUID> {

	List<TicketLink> findBySourceTicketId(UUID sourceTicketId);

	Optional<TicketLink> findBySourceTicketIdAndTargetTicketId(UUID sourceTicketId, UUID targetTicketId);

	boolean existsBySourceTicketIdAndTargetTicketId(UUID sourceTicketId, UUID targetTicketId);
}

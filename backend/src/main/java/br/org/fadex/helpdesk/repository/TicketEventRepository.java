package br.org.fadex.helpdesk.repository;

import br.org.fadex.helpdesk.model.event.TicketEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface TicketEventRepository extends JpaRepository<TicketEvent, UUID>, JpaSpecificationExecutor<TicketEvent> {
}

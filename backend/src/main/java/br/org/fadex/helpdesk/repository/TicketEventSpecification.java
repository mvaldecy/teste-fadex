package br.org.fadex.helpdesk.repository;

import br.org.fadex.helpdesk.model.event.TicketEvent;
import br.org.fadex.helpdesk.model.event.TicketEventFields;
import br.org.fadex.helpdesk.model.event.TicketEventFilter;
import br.org.fadex.helpdesk.model.ticket.TicketFields;
import br.org.fadex.helpdesk.model.user.UserFields;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TicketEventSpecification {

	private TicketEventSpecification() {
	}

	public static Specification<TicketEvent> createSpecification(TicketEventFilter filter) {
		return (root, query, criteriaBuilder) -> {
			List<Predicate> predicates = new ArrayList<>();

			if (filter.hasTicketId()) {
				predicates.add(criteriaBuilder.equal(
						root.get(TicketEventFields.TICKET).get(TicketFields.ID),
						filter.ticketId()
				));
			}

			if (filter.hasActorId()) {
				predicates.add(criteriaBuilder.equal(
						root.get(TicketEventFields.ACTOR).get(UserFields.ID),
						filter.actorId()
				));
			}

			if (filter.hasType()) {
				predicates.add(criteriaBuilder.equal(root.get(TicketEventFields.TYPE), filter.type()));
			}

			if (filter.hasSearch()) {
				String search = "%" + filter.search().trim().toLowerCase(Locale.ROOT) + "%";
				predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get(TicketEventFields.DESCRIPTION)), search));
			}

			return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
		};
	}
}

package br.org.fadex.helpdesk.repository;

import br.org.fadex.helpdesk.model.ticket.Ticket;
import br.org.fadex.helpdesk.model.ticket.TicketFields;
import br.org.fadex.helpdesk.model.ticket.TicketFilter;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TicketSpecification {

	private TicketSpecification() {
	}

	public static Specification<Ticket> createSpecification(TicketFilter filter) {
		return (root, query, criteriaBuilder) -> {
			List<Predicate> predicates = new ArrayList<>();

			if (filter.hasStatus()) {
				predicates.add(criteriaBuilder.equal(root.get(TicketFields.STATUS), filter.status()));
			}

			if (filter.hasPriority()) {
				predicates.add(criteriaBuilder.equal(root.get(TicketFields.PRIORITY), filter.priority()));
			}

			if (filter.hasCategory()) {
				predicates.add(criteriaBuilder.equal(root.get(TicketFields.CATEGORY), filter.category()));
			}

			if (filter.hasRequesterId()) {
				predicates.add(criteriaBuilder.equal(
						root.get(TicketFields.REQUESTER).get(TicketFields.ID),
						filter.requesterId()
				));
			}

			if (filter.hasUnassigned()) {
				predicates.add(criteriaBuilder.isNull(root.get(TicketFields.ASSIGNEE)));
			} else if (filter.hasAssigneeId()) {
				predicates.add(criteriaBuilder.equal(
						root.get(TicketFields.ASSIGNEE).get(TicketFields.ID),
						filter.assigneeId()
				));
			}

			if (filter.hasSearch()) {
				String search = "%" + filter.search().trim().toLowerCase(Locale.ROOT) + "%";

				predicates.add(criteriaBuilder.or(
						criteriaBuilder.like(criteriaBuilder.lower(root.get(TicketFields.TITLE)), search),
						criteriaBuilder.like(criteriaBuilder.lower(root.get(TicketFields.DESCRIPTION)), search)
				));
			}

			return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
		};
	}
}

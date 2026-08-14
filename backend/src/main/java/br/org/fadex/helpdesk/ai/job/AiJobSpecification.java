package br.org.fadex.helpdesk.ai.job;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public abstract class AiJobSpecification {

	private AiJobSpecification() {
	}

	public static Specification<AiJob> createSpecification(AiJobFilter filter) {
		return (root, query, criteriaBuilder) -> {
			List<Predicate> predicates = new ArrayList<>();

			if (filter.hasStatus()) {
				predicates.add(criteriaBuilder.equal(root.get(AiJobFields.STATUS), filter.status()));
			}
			if (filter.hasType()) {
				predicates.add(criteriaBuilder.equal(root.get(AiJobFields.TYPE), filter.type()));
			}
			if (filter.hasTicketId()) {
				predicates.add(criteriaBuilder.equal(root.get(AiJobFields.TICKET_ID), filter.ticketId()));
			}

			return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
		};
	}
}

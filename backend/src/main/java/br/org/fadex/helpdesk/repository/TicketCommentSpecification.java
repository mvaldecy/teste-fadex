package br.org.fadex.helpdesk.repository;

import br.org.fadex.helpdesk.model.comment.TicketComment;
import br.org.fadex.helpdesk.model.comment.TicketCommentFields;
import br.org.fadex.helpdesk.model.comment.TicketCommentFilter;
import br.org.fadex.helpdesk.model.ticket.TicketFields;
import br.org.fadex.helpdesk.model.user.UserFields;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TicketCommentSpecification {

	private TicketCommentSpecification() {
	}

	public static Specification<TicketComment> createSpecification(TicketCommentFilter filter) {
		return (root, query, criteriaBuilder) -> {
			List<Predicate> predicates = new ArrayList<>();

			if (filter.hasTicketId()) {
				predicates.add(criteriaBuilder.equal(
						root.get(TicketCommentFields.TICKET).get(TicketFields.ID),
						filter.ticketId()
				));
			}

			if (filter.hasAuthorId()) {
				predicates.add(criteriaBuilder.equal(
						root.get(TicketCommentFields.AUTHOR).get(UserFields.ID),
						filter.authorId()
				));
			}

			if (filter.hasSearch()) {
				String search = "%" + filter.search().trim().toLowerCase(Locale.ROOT) + "%";
				predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get(TicketCommentFields.TEXT)), search));
			}

			return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
		};
	}
}

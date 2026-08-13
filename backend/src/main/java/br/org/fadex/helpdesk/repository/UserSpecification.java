package br.org.fadex.helpdesk.repository;

import br.org.fadex.helpdesk.model.user.User;
import br.org.fadex.helpdesk.model.user.UserFields;
import br.org.fadex.helpdesk.model.user.UserFilter;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class UserSpecification {

	private UserSpecification() {
	}

	public static Specification<User> createSpecification(UserFilter filter) {
		return (root, query, criteriaBuilder) -> {
			List<Predicate> predicates = new ArrayList<>();

			if (filter.hasId()) {
				predicates.add(criteriaBuilder.equal(root.get(UserFields.ID), filter.id()));
			}

			if (filter.hasRole()) {
				predicates.add(criteriaBuilder.equal(root.get(UserFields.ROLE), filter.role()));
			}

			if (filter.hasName()) {
				String name = "%" + filter.name().trim().toLowerCase(Locale.ROOT) + "%";
				predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get(UserFields.NAME)), name));
			}

			if (filter.hasEmail()) {
				String email = "%" + filter.email().trim().toLowerCase(Locale.ROOT) + "%";
				predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get(UserFields.EMAIL)), email));
			}

			if (filter.hasSearch()) {
				String search = "%" + filter.search().trim().toLowerCase(Locale.ROOT) + "%";
				predicates.add(criteriaBuilder.or(
						criteriaBuilder.like(criteriaBuilder.lower(root.get(UserFields.NAME)), search),
						criteriaBuilder.like(criteriaBuilder.lower(root.get(UserFields.EMAIL)), search)
				));
			}

			return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
		};
	}
}

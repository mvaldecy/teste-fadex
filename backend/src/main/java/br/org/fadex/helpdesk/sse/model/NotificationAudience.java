package br.org.fadex.helpdesk.sse.model;

import br.org.fadex.helpdesk.model.enums.Role;

import java.util.Set;
import java.util.UUID;

public sealed interface NotificationAudience {

	boolean includes(UUID userId, Role role);

	record Users(Set<UUID> userIds) implements NotificationAudience {

		public Users {
			userIds = Set.copyOf(userIds);
		}

		@Override
		public boolean includes(UUID userId, Role role) {
			return userIds.contains(userId);
		}
	}

	record Roles(Set<Role> roles) implements NotificationAudience {

		public Roles {
			roles = Set.copyOf(roles);
		}

		@Override
		public boolean includes(UUID userId, Role role) {
			return roles.contains(role);
		}
	}

	/**
	 * Uniao de destinatarios explicitos com papeis inteiros.
	 *
	 * Existe para a criacao de chamado: o solicitante precisa receber, e todo ADMIN enxerga todos
	 * os chamados na listagem. Publicar duas mensagens entregaria frame duplicado ao ADMIN que
	 * tambem e o solicitante.
	 */
	record UsersAndRoles(Set<UUID> userIds, Set<Role> roles) implements NotificationAudience {

		public UsersAndRoles {
			userIds = Set.copyOf(userIds);
			roles = Set.copyOf(roles);
		}

		@Override
		public boolean includes(UUID userId, Role role) {
			return userIds.contains(userId) || roles.contains(role);
		}
	}

	record Everyone() implements NotificationAudience {

		@Override
		public boolean includes(UUID userId, Role role) {
			return true;
		}
	}
}

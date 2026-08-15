package br.org.fadex.helpdesk.notification.event;

import br.org.fadex.helpdesk.model.user.User;

import java.util.UUID;

/**
 * Retrato imutavel de um destinatario.
 *
 * Os listeners de notificacao rodam depois do commit e em outra thread, com
 * {@code spring.jpa.open-in-view=false}. Carregar a entidade {@code User} no evento significaria
 * lazy loading em sessao fechada, entao o evento leva os dados ja resolvidos.
 */
public record NotificationRecipient(
		UUID id,
		String name,
		String email
) {

	public static NotificationRecipient of(User user) {
		if (user == null) {
			return null;
		}

		return new NotificationRecipient(user.getId(), user.getName(), user.getEmail());
	}

	public boolean isNot(UUID userId) {
		return userId == null || !userId.equals(id);
	}
}

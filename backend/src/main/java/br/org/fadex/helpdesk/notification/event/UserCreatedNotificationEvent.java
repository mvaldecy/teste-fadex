package br.org.fadex.helpdesk.notification.event;

import java.util.UUID;

/**
 * Usuario criado com senha provisoria.
 *
 * A senha so existe em memoria neste evento: no banco fica apenas o hash.
 */
public record UserCreatedNotificationEvent(
		UUID userId,
		String name,
		String email,
		String temporaryPassword
) {
}

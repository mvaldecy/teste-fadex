package br.org.fadex.helpdesk.model.notification;

import java.util.UUID;

public record NotificationMessage(
		String eventId,
		String eventName,
		Object data,
		NotificationAudience audience
) {

	public static NotificationMessage of(String eventName, Object data, NotificationAudience audience) {
		return new NotificationMessage(UUID.randomUUID().toString(), eventName, data, audience);
	}
}

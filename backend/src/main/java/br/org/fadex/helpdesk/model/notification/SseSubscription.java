package br.org.fadex.helpdesk.model.notification;

import br.org.fadex.helpdesk.model.enums.Role;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

public record SseSubscription(String connectionId, UUID userId, Role role, SseEmitter emitter) {

	public static SseSubscription create(UUID userId, Role role, SseEmitter emitter) {
		return new SseSubscription(UUID.randomUUID().toString(), userId, role, emitter);
	}
}

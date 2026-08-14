package br.org.fadex.helpdesk.sse.service;

import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.sse.model.NotificationConnectionDto;
import br.org.fadex.helpdesk.sse.model.NotificationMessage;
import br.org.fadex.helpdesk.sse.model.SseSubscription;
import br.org.fadex.helpdesk.security.AuthenticatedUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class NotificationService {

	public static final String CONNECTION_EVENT_NAME = "CONEXAO_ESTABELECIDA";
	private static final String HEARTBEAT_COMMENT = "ping";

	private final NotificationEmitterRegistry registry;
	private final AuthenticatedUserService authenticatedUserService;
	private final long timeout;
	private final long reconnectTime;

	public NotificationService(
			NotificationEmitterRegistry registry,
			AuthenticatedUserService authenticatedUserService,
			@Value("${notifications.sse.timeout}") long timeout,
			@Value("${notifications.sse.reconnect-time}") long reconnectTime
	) {
		this.registry = registry;
		this.authenticatedUserService = authenticatedUserService;
		this.timeout = timeout;
		this.reconnectTime = reconnectTime;
	}

	public SseEmitter subscribe() {
		UUID userId = authenticatedUserService.getUserId();
		Role role = authenticatedUserService.getRole();
		SseEmitter emitter = new SseEmitter(timeout);
		SseSubscription subscription = SseSubscription.create(userId, role, emitter);

		registry.add(subscription);
		emitter.onCompletion(() -> registry.remove(subscription));
		emitter.onTimeout(() -> registry.remove(subscription));
		emitter.onError(throwable -> registry.remove(subscription));

		sendConnectionEvent(subscription);

		return emitter;
	}

	public void dispatch(NotificationMessage message) {
		List<SseSubscription> subscriptions = registry.findAll();

		for (SseSubscription subscription : subscriptions) {
			boolean shouldReceive = message.audience().includes(subscription.userId(), subscription.role());

			if (shouldReceive) {
				send(subscription, message.eventId(), message.eventName(), message.data());
			}
		}
	}

	public void sendHeartbeat() {
		List<SseSubscription> subscriptions = registry.findAll();

		for (SseSubscription subscription : subscriptions) {
			sendComment(subscription);
		}
	}

	private void sendComment(SseSubscription subscription) {
		try {
			subscription.emitter().send(SseEmitter.event().comment(HEARTBEAT_COMMENT));
		} catch (IOException exception) {
			log.debug("Cliente desconectado durante o keep-alive da conexao {}.", subscription.connectionId());
			registry.remove(subscription);
		} catch (IllegalStateException exception) {
			log.warn(
					"Falha ao enviar keep-alive para a conexao {}; encerrando o emitter.",
					subscription.connectionId(),
					exception
			);
			registry.remove(subscription);
			subscription.emitter().completeWithError(exception);
		}
	}

	private void sendConnectionEvent(SseSubscription subscription) {
		NotificationConnectionDto connection = new NotificationConnectionDto(
				subscription.connectionId(),
				LocalDateTime.now()
		);

		send(subscription, subscription.connectionId(), CONNECTION_EVENT_NAME, connection);
	}

	private void send(SseSubscription subscription, String eventId, String eventName, Object data) {
		try {
			subscription.emitter().send(SseEmitter.event()
					.id(eventId)
					.name(eventName)
					.reconnectTime(reconnectTime)
					.data(data, MediaType.APPLICATION_JSON));
		} catch (IOException exception) {
			log.debug("Cliente desconectado ao entregar notificacao na conexao {}.", subscription.connectionId());
			registry.remove(subscription);
		} catch (IllegalStateException exception) {
			log.warn(
					"Falha ao entregar notificacao na conexao {}; encerrando o emitter.",
					subscription.connectionId(),
					exception
			);
			registry.remove(subscription);
			subscription.emitter().completeWithError(exception);
		}
	}
}

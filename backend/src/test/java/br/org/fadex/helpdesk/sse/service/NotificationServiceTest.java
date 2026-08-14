package br.org.fadex.helpdesk.sse.service;

import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.sse.model.SseSubscription;
import br.org.fadex.helpdesk.security.AuthenticatedUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

	private static final UUID USUARIO = UUID.fromString("71e9c3d9-53b2-4c4e-9803-c504754dbb45");
	private static final long TIMEOUT = 1800000L;
	private static final long RECONNECT_TIME = 5000L;

	@Mock
	private NotificationEmitterRegistry registry;

	@Mock
	private AuthenticatedUserService authenticatedUserService;

	@Test
	void deveRegistrarAssinaturaComIdentidadeCapturadaDoToken() {
		NotificationService notificationService = new NotificationService(
				registry,
				authenticatedUserService,
				TIMEOUT,
				RECONNECT_TIME
		);
		ArgumentCaptor<SseSubscription> subscriptionCaptor = ArgumentCaptor.forClass(SseSubscription.class);

		when(authenticatedUserService.getUserId()).thenReturn(USUARIO);
		when(authenticatedUserService.getRole()).thenReturn(Role.ADMIN);

		SseEmitter emitter = notificationService.subscribe();

		verify(registry).add(subscriptionCaptor.capture());
		SseSubscription subscription = subscriptionCaptor.getValue();

		assertThat(subscription.userId()).isEqualTo(USUARIO);
		assertThat(subscription.role()).isEqualTo(Role.ADMIN);
		assertThat(subscription.connectionId()).isNotBlank();
		assertThat(subscription.emitter()).isSameAs(emitter);
		assertThat(emitter.getTimeout()).isEqualTo(TIMEOUT);
	}
}

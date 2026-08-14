package br.org.fadex.helpdesk.sse.service;

import br.org.fadex.helpdesk.sse.model.NotificationAudience;
import br.org.fadex.helpdesk.sse.model.NotificationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

	@Mock
	private NotificationService notificationService;

	@InjectMocks
	private NotificationDispatcher notificationDispatcher;

	@Test
	void deveDelegarMensagemParaOFanout() {
		NotificationMessage message = NotificationMessage.of(
				"CHAMADO_CRIADO",
				"conteudo",
				new NotificationAudience.Everyone()
		);

		notificationDispatcher.onNotificationMessage(message);

		verify(notificationService).dispatch(message);
	}

	@Test
	void deveEntregarSomenteDepoisDoCommit() throws Exception {
		Method listener = NotificationDispatcher.class.getMethod("onNotificationMessage", NotificationMessage.class);
		TransactionalEventListener annotation = AnnotationUtils.findAnnotation(listener, TransactionalEventListener.class);

		assertThat(annotation).isNotNull();
		assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
		assertThat(annotation.fallbackExecution()).isTrue();
	}

	@Test
	void deveRodarNoExecutorDedicadoDeNotificacoes() throws Exception {
		Method listener = NotificationDispatcher.class.getMethod("onNotificationMessage", NotificationMessage.class);
		Async annotation = AnnotationUtils.findAnnotation(listener, Async.class);

		assertThat(annotation).isNotNull();
		assertThat(annotation.value()).isEqualTo("sseNotificationExecutor");
	}
}

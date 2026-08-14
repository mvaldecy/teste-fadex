package br.org.fadex.helpdesk.sse.service;

import br.org.fadex.helpdesk.sse.model.NotificationAudience;
import br.org.fadex.helpdesk.sse.model.NotificationMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class NotificationDispatcherIntegrationTest {

	@Autowired
	private ApplicationEventPublisher applicationEventPublisher;

	@Autowired
	private PlatformTransactionManager platformTransactionManager;

	@MockitoBean
	private NotificationService notificationService;

	@Test
	void naoDeveEntregarAntesDoCommit() {
		TransactionTemplate transactionTemplate = new TransactionTemplate(platformTransactionManager);
		NotificationMessage message = NotificationMessage.of(
				"CHAMADO_CRIADO",
				"conteudo",
				new NotificationAudience.Everyone()
		);

		transactionTemplate.executeWithoutResult(status -> {
			applicationEventPublisher.publishEvent(message);

			// Antes do commit nao ha corrida a considerar: o listener AFTER_COMMIT
			// so eh acionado depois que a transacao commitar, independente do @Async.
			verify(notificationService, never()).dispatch(message);
		});

		// Apos o commit, o fanout roda em thread separada (@Async): usa-se timeout()
		// do Mockito em vez de verify() sincrono para evitar falso negativo por corrida.
		verify(notificationService, timeout(2000)).dispatch(message);
	}

	@Test
	void deveEntregarSemTransacaoAtiva() {
		NotificationMessage message = NotificationMessage.of(
				"CHAMADO_CRIADO",
				"conteudo",
				new NotificationAudience.Everyone()
		);

		applicationEventPublisher.publishEvent(message);

		verify(notificationService, timeout(2000)).dispatch(message);
	}

	@Test
	void deveDespacharForaDaThreadQuePublicouOEvento() {
		String threadDeteste = Thread.currentThread().getName();
		AtomicReference<String> threadDoDispatch = new AtomicReference<>();
		NotificationMessage message = NotificationMessage.of(
				"CHAMADO_CRIADO",
				"conteudo",
				new NotificationAudience.Everyone()
		);

		doAnswer(invocation -> {
			threadDoDispatch.set(Thread.currentThread().getName());

			return null;
		}).when(notificationService).dispatch(message);

		applicationEventPublisher.publishEvent(message);

		verify(notificationService, timeout(2000)).dispatch(message);
		// Prova de que o @Async realmente desviou a execucao para o pool dedicado:
		// se o fanout tivesse rodado sincrono na thread do publicador, o nome
		// registrado seria o da propria thread de teste, e nao teria o prefixo do
		// executor configurado em sse/config/AsyncConfig.java.
		assertThat(threadDoDispatch.get()).isNotNull();
		assertThat(threadDoDispatch.get()).isNotEqualTo(threadDeteste);
		assertThat(threadDoDispatch.get()).startsWith("sse-notification-");
	}
}

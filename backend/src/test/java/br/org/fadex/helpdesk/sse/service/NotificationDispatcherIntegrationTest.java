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

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

			verify(notificationService, never()).dispatch(message);
		});

		verify(notificationService, times(1)).dispatch(message);
	}

	@Test
	void deveEntregarSemTransacaoAtiva() {
		NotificationMessage message = NotificationMessage.of(
				"CHAMADO_CRIADO",
				"conteudo",
				new NotificationAudience.Everyone()
		);

		applicationEventPublisher.publishEvent(message);

		verify(notificationService, times(1)).dispatch(message);
	}
}

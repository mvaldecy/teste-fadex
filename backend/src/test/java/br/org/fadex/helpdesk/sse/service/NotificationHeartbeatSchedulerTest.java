package br.org.fadex.helpdesk.sse.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationHeartbeatSchedulerTest {

	@Mock
	private NotificationService notificationService;

	@InjectMocks
	private NotificationHeartbeatScheduler notificationHeartbeatScheduler;

	@Test
	void deveAcionarKeepAliveDoServico() {
		notificationHeartbeatScheduler.sendHeartbeat();

		verify(notificationService).sendHeartbeat();
	}

	@Test
	void deveUsarIntervaloConfiguravel() throws Exception {
		Method scheduled = NotificationHeartbeatScheduler.class.getMethod("sendHeartbeat");
		Scheduled annotation = AnnotationUtils.findAnnotation(scheduled, Scheduled.class);

		assertThat(annotation).isNotNull();
		assertThat(annotation.fixedRateString()).isEqualTo("${notifications.sse.heartbeat-interval}");
	}
}

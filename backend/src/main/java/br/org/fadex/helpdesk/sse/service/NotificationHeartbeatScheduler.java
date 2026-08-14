package br.org.fadex.helpdesk.sse.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationHeartbeatScheduler {

	private final NotificationService notificationService;

	public NotificationHeartbeatScheduler(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@Scheduled(fixedRateString = "${notifications.sse.heartbeat-interval}")
	public void sendHeartbeat() {
		notificationService.sendHeartbeat();
	}
}

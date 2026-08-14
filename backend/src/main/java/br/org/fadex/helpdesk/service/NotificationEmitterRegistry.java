package br.org.fadex.helpdesk.service;

import br.org.fadex.helpdesk.model.notification.SseSubscription;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NotificationEmitterRegistry {

	private final Map<UUID, Set<SseSubscription>> subscriptionsByUser = new ConcurrentHashMap<>();

	public void add(SseSubscription subscription) {
		subscriptionsByUser.compute(subscription.userId(), (userId, subscriptions) -> {
			Set<SseSubscription> currentSubscriptions = subscriptions == null
					? ConcurrentHashMap.newKeySet()
					: subscriptions;
			currentSubscriptions.add(subscription);

			return currentSubscriptions;
		});
	}

	public void remove(SseSubscription subscription) {
		subscriptionsByUser.computeIfPresent(subscription.userId(), (userId, subscriptions) -> {
			subscriptions.remove(subscription);

			return subscriptions.isEmpty() ? null : subscriptions;
		});
	}

	public List<SseSubscription> findAll() {
		List<SseSubscription> subscriptions = subscriptionsByUser.values().stream()
				.flatMap(Set::stream)
				.toList();

		return subscriptions;
	}

	public int countConnections() {
		return findAll().size();
	}
}

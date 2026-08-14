package br.org.fadex.helpdesk.sse.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

	public static final String SSE_NOTIFICATION_EXECUTOR = "sseNotificationExecutor";

	private static final int CORE_POOL_SIZE = 2;
	private static final int MAX_POOL_SIZE = 4;
	private static final int QUEUE_CAPACITY = 500;
	private static final String THREAD_NAME_PREFIX = "sse-notification-";

	@Bean(SSE_NOTIFICATION_EXECUTOR)
	public Executor sseNotificationExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(CORE_POOL_SIZE);
		executor.setMaxPoolSize(MAX_POOL_SIZE);
		executor.setQueueCapacity(QUEUE_CAPACITY);
		executor.setThreadNamePrefix(THREAD_NAME_PREFIX);
		executor.initialize();

		return executor;
	}
}

package br.org.fadex.helpdesk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Relogio injetavel.
 *
 * Existe para que revisao de classificacao e indicadores nao dependam de {@code LocalDateTime.now()}
 * estatico: os testes de "hoje", "nesta semana", aging e SLA precisam de um instante fixo para nao
 * mudarem de resultado conforme a hora em que a suite roda.
 */
@Configuration
public class ClockConfig {

	@Bean
	public Clock clock() {
		return Clock.systemDefaultZone();
	}
}

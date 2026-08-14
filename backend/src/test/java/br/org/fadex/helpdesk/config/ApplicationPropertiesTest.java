package br.org.fadex.helpdesk.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ApplicationPropertiesTest {

	@Autowired
	private Environment environment;

	@Test
	void deveCarregarProfileDeTesteComBancoEmMemoria() {
		assertThat(environment.getProperty("spring.application.name")).isEqualTo("helpdesk");
		assertThat(environment.getProperty("spring.datasource.url")).startsWith("jdbc:h2:mem:");
		assertThat(environment.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
		assertThat(environment.getProperty("spring.mail.host")).isEqualTo("localhost");
		assertThat(environment.getProperty("spring.mail.port")).isEqualTo("1025");
		assertThat(environment.getProperty("app.mail.from")).isEqualTo("no-reply@fadex.local");
		assertThat(environment.getProperty("app.ai.triage.enabled")).isEqualTo("false");
		assertThat(environment.getProperty("app.ai.base-url")).isEqualTo("http://localhost:11434");
		assertThat(environment.getProperty("app.ai.embedding-dimensions")).isEqualTo("384");
		assertThat(environment.getProperty("app.ai.worker.batch-size")).isEqualTo("1");
		assertThat(environment.getProperty("spring.quartz.properties.org.quartz.threadPool.threadCount")).isEqualTo("1");
	}
}

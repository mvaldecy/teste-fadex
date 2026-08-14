package br.org.fadex.helpdesk.config;

import br.org.fadex.helpdesk.ai.job.AiJobWorker;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiJobQuartzConfig {

	@Bean
	public JobDetail aiJobWorkerDetail() {
		return JobBuilder.newJob(AiJobWorker.class)
				.withIdentity("aiJobWorker")
				.storeDurably()
				.build();
	}

	@Bean
	public Trigger aiJobWorkerTrigger(
			JobDetail aiJobWorkerDetail,
			@Value("${app.ai.worker.interval-millis}") long intervalMillis
	) {
		SimpleScheduleBuilder schedule = SimpleScheduleBuilder.simpleSchedule()
				.withIntervalInMilliseconds(intervalMillis)
				.repeatForever();

		return TriggerBuilder.newTrigger()
				.forJob(aiJobWorkerDetail)
				.withIdentity("aiJobWorkerTrigger")
				.withSchedule(schedule)
				.build();
	}
}

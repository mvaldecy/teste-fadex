package br.org.fadex.helpdesk.ai.indicator;

import br.org.fadex.helpdesk.model.enums.ClassificationOrigin;

import java.util.Map;

public record AiIndicatorsDto(
		AgreementRateDto agreementRate,
		Double averageConfidence,
		Map<ClassificationOrigin, Long> originDistribution,
		JobQueueIndicatorsDto jobQueue,
		long duplicatesDetected
) {
}

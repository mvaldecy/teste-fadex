package br.org.fadex.helpdesk.ai.indicator;

import br.org.fadex.helpdesk.model.user.UserMinDto;

public record AssigneeClosureDto(
		UserMinDto user,
		int sampleSize,
		Double averageHours,
		Double medianHours
) {
}

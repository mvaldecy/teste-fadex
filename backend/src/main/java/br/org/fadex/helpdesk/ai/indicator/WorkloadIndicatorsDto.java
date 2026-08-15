package br.org.fadex.helpdesk.ai.indicator;

import java.util.List;

public record WorkloadIndicatorsDto(
		List<AssigneeLoadDto> openByAssignee,
		List<AssigneeClosureDto> closureTimeByAssignee,
		List<RequesterVolumeDto> topRequesters
) {
}

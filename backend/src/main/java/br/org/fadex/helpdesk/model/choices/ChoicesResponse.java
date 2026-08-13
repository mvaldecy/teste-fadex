package br.org.fadex.helpdesk.model.choices;

import java.util.List;

public record ChoicesResponse(
		List<ChoiceItemResponse> roles,
		List<ChoiceItemResponse> ticketStatuses,
		List<ChoiceItemResponse> ticketPriorities,
		List<ChoiceItemResponse> ticketCategories,
		List<ChoiceItemResponse> classificationOrigins
) {
}

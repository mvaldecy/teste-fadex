package br.org.fadex.helpdesk.service;

import br.org.fadex.helpdesk.model.choices.ChoiceItemResponse;
import br.org.fadex.helpdesk.model.choices.ChoicesResponse;
import br.org.fadex.helpdesk.model.enums.ClassificationOrigin;
import br.org.fadex.helpdesk.model.enums.LabeledEnum;
import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import br.org.fadex.helpdesk.model.enums.TicketStatus;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class ChoiceService {

	public ChoicesResponse getChoices() {
		return new ChoicesResponse(
				toChoices(Role.values()),
				toChoices(TicketStatus.values()),
				toChoices(TicketPriority.values()),
				toChoices(TicketCategory.values()),
				toChoices(ClassificationOrigin.values())
		);
	}

	private List<ChoiceItemResponse> toChoices(LabeledEnum[] values) {
		return Arrays.stream(values)
				.map(ChoiceItemResponse::from)
				.toList();
	}
}

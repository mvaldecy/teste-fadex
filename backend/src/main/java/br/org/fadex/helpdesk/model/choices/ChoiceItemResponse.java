package br.org.fadex.helpdesk.model.choices;

import br.org.fadex.helpdesk.model.enums.LabeledEnum;

public record ChoiceItemResponse(String value, String label) {

	public static ChoiceItemResponse from(LabeledEnum labeledEnum) {
		return new ChoiceItemResponse(labeledEnum.getValue(), labeledEnum.getLabel());
	}
}

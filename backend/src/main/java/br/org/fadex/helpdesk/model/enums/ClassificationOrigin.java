package br.org.fadex.helpdesk.model.enums;

public enum ClassificationOrigin implements LabeledEnum {
	IA("IA"),
	MANUAL("Manual"),
	PENDENTE("Pendente");

	private final String label;

	ClassificationOrigin(String label) {
		this.label = label;
	}

	@Override
	public String getValue() {
		return name();
	}

	@Override
	public String getLabel() {
		return label;
	}
}

package br.org.fadex.helpdesk.model.enums;

public enum Role implements LabeledEnum {
	ADMIN("Administrador"),
	SOLICITANTE("Solicitante");

	private final String label;

	Role(String label) {
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

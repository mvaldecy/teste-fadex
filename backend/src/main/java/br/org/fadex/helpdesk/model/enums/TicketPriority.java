package br.org.fadex.helpdesk.model.enums;

public enum TicketPriority implements LabeledEnum {
	BAIXA("Baixa"),
	MEDIA("Media"),
	ALTA("Alta");

	private final String label;

	TicketPriority(String label) {
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

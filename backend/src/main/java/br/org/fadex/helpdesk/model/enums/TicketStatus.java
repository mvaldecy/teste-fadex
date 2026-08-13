package br.org.fadex.helpdesk.model.enums;

public enum TicketStatus implements LabeledEnum {
	ABERTO("Aberto"),
	EM_ANDAMENTO("Em andamento"),
	RESOLVIDO("Resolvido"),
	FECHADO("Fechado");

	private final String label;

	TicketStatus(String label) {
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

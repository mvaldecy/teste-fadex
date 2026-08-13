package br.org.fadex.helpdesk.model.enums;

public enum TicketCategory implements LabeledEnum {
	ACESSO("Acesso"),
	SISTEMAS("Sistemas"),
	INFRAESTRUTURA("Infraestrutura"),
	EQUIPAMENTOS("Equipamentos"),
	FINANCEIRO("Financeiro"),
	RH("RH"),
	OUTROS("Outros");

	private final String label;

	TicketCategory(String label) {
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

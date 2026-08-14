package br.org.fadex.helpdesk.model.enums;

public enum TicketEventType implements LabeledEnum {
	CHAMADO_CRIADO("Chamado criado"),
	COMENTARIO_ADICIONADO("Comentario adicionado"),
	STATUS_ALTERADO("Status alterado"),
	RESPONSAVEL_ATRIBUIDO("Responsavel atribuido"),
	RESPONSAVEL_REMOVIDO("Responsavel removido"),
	PRIORIDADE_ALTERADA("Prioridade alterada"),
	CATEGORIA_ALTERADA("Categoria alterada"),
	CLASSIFICACAO_ATUALIZADA("Classificacao atualizada");

	private final String label;

	TicketEventType(String label) {
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

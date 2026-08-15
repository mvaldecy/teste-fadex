package br.org.fadex.helpdesk.ai.classification;

import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TicketClassificationUpdateDto(
		@NotNull(message = "A categoria e obrigatoria.")
		TicketCategory category,

		@NotNull(message = "A prioridade e obrigatoria.")
		TicketPriority priority,

		/**
		 * O alias existe porque o frontend ja envia {@code classificationJustification}, nome do
		 * campo equivalente no {@code TicketDto} de resposta. Sem ele a justificativa escrita pelo
		 * ADMIN seria descartada em silencio e substituida pelo texto padrao — falha discreta, do
		 * tipo que so aparece quando alguem estranha o historico. Aceitar os dois nomes custa uma
		 * anotacao e nao exige mudanca em {@code frontend/}.
		 */
		@JsonAlias("classificationJustification")
		@Size(max = 2000, message = "A justificativa deve ter no maximo 2000 caracteres.")
		String justification
) {
}

package br.org.fadex.helpdesk.ai.duplicate;

import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import br.org.fadex.helpdesk.model.enums.TicketStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Chamado mais proximo deste, com a similaridade calculada na hora.
 *
 * Diferente de {@link SimilarTicketDto}, que le vinculos ja gravados, aqui **nada foi filtrado por
 * limiar**: e o ranking cru. Existe porque o limiar esconde a unica informacao capaz de dizer se a
 * ausencia de duplicata e uma resposta ou uma falha do modelo — com o ranking a vista, um humano
 * enxerga em um segundo o que a maquina errou.
 *
 * {@code linked} diz se este par cruzou o limiar e virou vinculo persistido.
 */
public record NearestTicketDto(
		UUID id,
		String title,
		TicketStatus status,
		TicketPriority priority,
		TicketCategory category,
		double similarity,
		boolean linked,
		LocalDateTime createdAt
) {
}

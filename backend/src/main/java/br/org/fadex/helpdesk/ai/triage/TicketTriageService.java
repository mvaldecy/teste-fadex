package br.org.fadex.helpdesk.ai.triage;

import br.org.fadex.helpdesk.ai.job.AiJobDto;
import br.org.fadex.helpdesk.ai.job.AiJobService;
import br.org.fadex.helpdesk.security.AccessControlService;
import br.org.fadex.helpdesk.service.TicketService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Solicitacao manual de triagem por IA, pelo ADMIN.
 *
 * Enfileira e devolve: a requisicao nao espera o modelo local responder. Quem chama recebe os jobs
 * criados e acompanha o resultado pelo evento {@code CLASSIFICACAO_CONCLUIDA} ou por
 * {@code GET /api/v1/ai/jobs}.
 *
 * A guarda contra reprocessamento concorrente vive em {@link AiJobService#requeueTicketJobs}, junto
 * da escrita — separar a checagem da gravacao abriria janela entre uma e outra.
 */
@Service
public class TicketTriageService {

	private final AiJobService aiJobService;
	private final TicketService ticketService;
	private final AccessControlService accessControlService;

	public TicketTriageService(
			AiJobService aiJobService,
			TicketService ticketService,
			AccessControlService accessControlService
	) {
		this.aiJobService = aiJobService;
		this.ticketService = ticketService;
		this.accessControlService = accessControlService;
	}

	@Transactional
	public List<AiJobDto> requestTriage(UUID ticketId) {
		accessControlService.assertAdmin();

		// Existencia antes de enfileirar: sem isso um id inexistente criaria jobs orfaos que o
		// worker so descobriria como falha, em vez de devolver 404 na hora.
		ticketService.findEntityById(ticketId);

		List<AiJobDto> jobs = aiJobService.requeueTicketJobs(ticketId);

		return jobs;
	}
}

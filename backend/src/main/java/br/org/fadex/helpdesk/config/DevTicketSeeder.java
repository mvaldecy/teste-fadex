package br.org.fadex.helpdesk.config;

import br.org.fadex.helpdesk.ai.job.AiJobStatus;
import br.org.fadex.helpdesk.ai.job.AiJobType;
import br.org.fadex.helpdesk.model.enums.ClassificationOrigin;
import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketEventType;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import br.org.fadex.helpdesk.model.enums.TicketStatus;
import br.org.fadex.helpdesk.model.user.User;
import br.org.fadex.helpdesk.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Popula chamados de desenvolvimento com datas retroagidas.
 *
 * A escrita e feita em SQL nativo de proposito: {@code @CreatedDate} sobrescreveria
 * {@code created_at} com o horario atual e a entidade {@code Ticket} nao expoe troca de status,
 * entao nao ha como montar dispersao de tempo nem mistura de status pela API de dominio.
 *
 * A verificacao de existencia e feita por titulo, e nao pela contagem da tabela, para conviver
 * com chamados criados manualmente durante o desenvolvimento.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DevTicketSeeder {

	@Bean
	@Order(2)
	public CommandLineRunner seedTickets(JdbcTemplate jdbcTemplate, UserRepository userRepository) {
		return args -> {
			Map<String, UUID> userIdsByEmail = userRepository.findAll().stream()
					.collect(Collectors.toMap(User::getEmail, User::getId));

			LocalDateTime now = LocalDateTime.now();

			for (TicketSeed seed : ticketSeeds()) {
				if (!ticketExists(jdbcTemplate, seed.title())) {
					insertTicket(jdbcTemplate, userIdsByEmail, now, seed);
				}
			}

			enqueueAiJobs(jdbcTemplate, now);
		};
	}

	/**
	 * Enfileira os jobs de IA dos chamados semeados.
	 *
	 * Sem isto uma instalacao nova nasce sem nenhum embedding: o seed escreve por SQL direto, entao
	 * nao passa por {@code TicketService} e nunca dispara {@code enqueueTicketJobs}. Sem embedding a
	 * deteccao de duplicados nao tem o que comparar e {@code ticket_links} fica permanentemente
	 * vazia — a funcionalidade existe no codigo e some da tela.
	 *
	 * Roda fora do {@code if} de insercao de proposito: uma base ja semeada por uma versao anterior
	 * tem os chamados e nao tem os jobs, e e exatamente essa base que precisa da correcao.
	 *
	 * EMBEDDING vai para todos os chamados semeados. CLASSIFICATION vai apenas para os de origem
	 * PENDENTE: reclassificar um chamado com origem IA ou MANUAL sobrescreveria
	 * {@code ai_suggested_*} com o mesmo valor que passaria a valer no chamado, e a concordancia
	 * admin x IA — que ja tem {@code classification_reviewed_at} carimbado nesses chamados — subiria
	 * para 100% sem medir nada. Os PENDENTE tem sugestao nula e revisao nula, entao a classificacao
	 * so os move para o estado correto de "aguardando revisao do ADMIN".
	 *
	 * A guarda de idempotencia olha qualquer status, e nao apenas PENDING/PROCESSING como
	 * {@code AiJobService.requeueTicketJobs}: aqui o job DONE e o caso de sucesso, e reenfileirar a
	 * cada boot faria o backend reprocessar o seed inteiro toda vez que subisse.
	 */
	private void enqueueAiJobs(JdbcTemplate jdbcTemplate, LocalDateTime now) {
		for (TicketSeed seed : ticketSeeds()) {
			UUID ticketId = findTicketId(jdbcTemplate, seed.title());

			if (ticketId == null) {
				continue;
			}

			enqueueJob(jdbcTemplate, ticketId, AiJobType.EMBEDDING, now);

			if (seed.classificationOrigin() == ClassificationOrigin.PENDENTE) {
				enqueueJob(jdbcTemplate, ticketId, AiJobType.CLASSIFICATION, now);
			}
		}
	}

	private UUID findTicketId(JdbcTemplate jdbcTemplate, String title) {
		List<UUID> ids = jdbcTemplate.queryForList(
				"select id from tickets where title = ?",
				UUID.class,
				title
		);

		return ids.isEmpty() ? null : ids.getFirst();
	}

	private void enqueueJob(JdbcTemplate jdbcTemplate, UUID ticketId, AiJobType type, LocalDateTime now) {
		Integer count = jdbcTemplate.queryForObject(
				"select count(*) from ai_jobs where ticket_id = ? and type = ?",
				Integer.class,
				ticketId,
				type.name()
		);

		if (count != null && count > 0) {
			return;
		}

		jdbcTemplate.update(
				"""
				insert into ai_jobs (
					id, ticket_id, type, status, attempts, next_attempt_at, created_at, updated_at
				) values (?, ?, ?, ?, 0, ?, ?, ?)
				""",
				UUID.randomUUID(),
				ticketId,
				type.name(),
				AiJobStatus.PENDING.name(),
				Timestamp.valueOf(now),
				Timestamp.valueOf(now),
				Timestamp.valueOf(now)
		);
	}

	private boolean ticketExists(JdbcTemplate jdbcTemplate, String title) {
		Integer count = jdbcTemplate.queryForObject(
				"select count(*) from tickets where title = ?",
				Integer.class,
				title
		);

		return count != null && count > 0;
	}

	private void insertTicket(
			JdbcTemplate jdbcTemplate,
			Map<String, UUID> userIdsByEmail,
			LocalDateTime now,
			TicketSeed seed
	) {
		UUID ticketId = UUID.randomUUID();
		UUID requesterId = userIdsByEmail.get(seed.requesterEmail());
		UUID assigneeId = seed.assigneeEmail() == null ? null : userIdsByEmail.get(seed.assigneeEmail());

		LocalDateTime createdAt = now.minusHours(seed.createdHoursAgo());
		LocalDateTime resolvedAt = seed.resolvedAfterHours() == null
				? null
				: createdAt.plusHours(seed.resolvedAfterHours());
		LocalDateTime updatedAt = resolvedAt == null ? createdAt : resolvedAt;
		LocalDateTime assignedAt = assigneeId == null ? null : createdAt.plusHours(1);
		LocalDateTime firstResponseAt = (seed.firstReplyAfterHours() == null || assigneeId == null)
				? null
				: createdAt.plusHours(seed.firstReplyAfterHours());
		LocalDateTime closedAt = seed.status() == TicketStatus.FECHADO ? resolvedAt : null;

		// Carimbo de revisao da classificacao, que e o denominador da concordancia admin x IA.
		// Regra do seed: chamado MANUAL foi necessariamente revisado (alguem corrigiu a sugestao);
		// chamado com origem IA so conta como revisado se tem responsavel, ou seja, alguem trabalhou
		// nele e manteve a classificacao sugerida. Chamado PENDENTE fica nulo — a IA nao respondeu e
		// nao ha o que aceitar. Sem esse carimbo a taxa sairia com denominador zero.
		boolean reviewed = seed.aiSuggestedCategory() != null
				&& (seed.classificationOrigin() == ClassificationOrigin.MANUAL || assigneeId != null);
		LocalDateTime classificationReviewedAt = reviewed ? createdAt.plusHours(2) : null;

		jdbcTemplate.update(
				"""
				insert into tickets (
					id, title, description, category, priority, status, requester_id, assignee_id,
					classification_origin, classification_justification, created_at, updated_at,
					assigned_at, first_response_at, resolved_at, closed_at,
					ai_suggested_category, ai_suggested_priority, ai_confidence,
					classification_reviewed_at
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				ticketId,
				seed.title(),
				seed.description(),
				seed.category().name(),
				seed.priority().name(),
				seed.status().name(),
				requesterId,
				assigneeId,
				seed.classificationOrigin().name(),
				seed.justification(),
				Timestamp.valueOf(createdAt),
				Timestamp.valueOf(updatedAt),
				assignedAt == null ? null : Timestamp.valueOf(assignedAt),
				firstResponseAt == null ? null : Timestamp.valueOf(firstResponseAt),
				resolvedAt == null ? null : Timestamp.valueOf(resolvedAt),
				closedAt == null ? null : Timestamp.valueOf(closedAt),
				seed.aiSuggestedCategory() == null ? null : seed.aiSuggestedCategory().name(),
				seed.aiSuggestedPriority() == null ? null : seed.aiSuggestedPriority().name(),
				seed.aiConfidence(),
				classificationReviewedAt == null ? null : Timestamp.valueOf(classificationReviewedAt)
		);

		insertEvent(
				jdbcTemplate,
				ticketId,
				requesterId,
				TicketEventType.CHAMADO_CRIADO,
				"Chamado aberto pelo solicitante",
				createdAt
		);

		if (assigneeId != null) {
			insertEvent(
					jdbcTemplate,
					ticketId,
					assigneeId,
					TicketEventType.RESPONSAVEL_ATRIBUIDO,
					"Responsavel atribuido ao chamado",
					createdAt.plusHours(1)
			);
		}

		if (seed.firstReplyAfterHours() != null && assigneeId != null) {
			LocalDateTime commentedAt = createdAt.plusHours(seed.firstReplyAfterHours());

			jdbcTemplate.update(
					"""
					insert into ticket_comments (id, ticket_id, author_id, text, created_at, updated_at)
					values (?, ?, ?, ?, ?, ?)
					""",
					UUID.randomUUID(),
					ticketId,
					assigneeId,
					seed.firstReply(),
					Timestamp.valueOf(commentedAt),
					Timestamp.valueOf(commentedAt)
			);

			insertEvent(
					jdbcTemplate,
					ticketId,
					assigneeId,
					TicketEventType.COMENTARIO_ADICIONADO,
					"Comentario adicionado ao chamado",
					commentedAt
			);
		}

		if (resolvedAt != null) {
			insertEvent(
					jdbcTemplate,
					ticketId,
					assigneeId,
					TicketEventType.STATUS_ALTERADO,
					"Status alterado para " + seed.status().getLabel(),
					resolvedAt
			);
		}
	}

	private void insertEvent(
			JdbcTemplate jdbcTemplate,
			UUID ticketId,
			UUID actorId,
			TicketEventType type,
			String description,
			LocalDateTime createdAt
	) {
		jdbcTemplate.update(
				"""
				insert into ticket_events (id, ticket_id, actor_id, type, description, metadata, created_at)
				values (?, ?, ?, ?, ?, null, ?)
				""",
				UUID.randomUUID(),
				ticketId,
				actorId,
				type.name(),
				description,
				Timestamp.valueOf(createdAt)
		);
	}

	private List<TicketSeed> ticketSeeds() {
		return List.of(
				new TicketSeed(
						"Nao consigo acessar o sistema de folha",
						"Ao entrar com meu usuario aparece a mensagem de credencial invalida desde ontem.",
						TicketCategory.ACESSO, TicketPriority.ALTA, TicketStatus.ABERTO,
						"solicitante@fadex.org.br", null,
						ClassificationOrigin.IA,
						"Termos de bloqueio de credencial indicam acesso; impacto imediato eleva a prioridade.",
						6, null, null, null,
						TicketCategory.ACESSO, TicketPriority.ALTA, 0.91
				),
				new TicketSeed(
						"Servidor de arquivos fora do ar",
						"A pasta compartilhada do setor nao abre em nenhuma maquina da sala.",
						TicketCategory.INFRAESTRUTURA, TicketPriority.ALTA, TicketStatus.ABERTO,
						"ana.ribeiro@fadex.org.br", null,
						ClassificationOrigin.IA,
						"Indisponibilidade generalizada de recurso compartilhado caracteriza infraestrutura critica.",
						3, null, null, null,
						TicketCategory.INFRAESTRUTURA, TicketPriority.ALTA, 0.88
				),
				new TicketSeed(
						"Solicitacao de segunda via de cracha",
						"Perdi meu cracha de acesso e preciso de uma segunda via.",
						TicketCategory.EQUIPAMENTOS, TicketPriority.BAIXA, TicketStatus.ABERTO,
						"bruno.carvalho@fadex.org.br", null,
						ClassificationOrigin.PENDENTE, null,
						20, null, null, null,
						null, null, null
				),
				new TicketSeed(
						"Duvida sobre desconto no holerite",
						"Apareceu um desconto que nao reconheco na folha deste mes.",
						TicketCategory.RH, TicketPriority.MEDIA, TicketStatus.ABERTO,
						"solicitante@fadex.org.br", null,
						ClassificationOrigin.PENDENTE, null,
						30, null, null, null,
						null, null, null
				),
				new TicketSeed(
						"Teclado com teclas travando",
						"Algumas teclas param de responder e preciso apertar varias vezes.",
						TicketCategory.EQUIPAMENTOS, TicketPriority.BAIXA, TicketStatus.ABERTO,
						"ana.ribeiro@fadex.org.br", null,
						ClassificationOrigin.PENDENTE, null,
						52, null, null, null,
						null, null, null
				),
				new TicketSeed(
						"Sistema de compras lento no fim do dia",
						"A partir das 17h o sistema de compras demora mais de um minuto por tela.",
						TicketCategory.SISTEMAS, TicketPriority.MEDIA, TicketStatus.ABERTO,
						"bruno.carvalho@fadex.org.br", null,
						ClassificationOrigin.IA,
						"Relato de lentidao recorrente em aplicacao interna, sem indisponibilidade total.",
						96, null, null, null,
						TicketCategory.SISTEMAS, TicketPriority.MEDIA, 0.79
				),
				new TicketSeed(
						"Erro ao emitir relatorio financeiro",
						"O relatorio mensal retorna erro 500 ao ser exportado em PDF.",
						TicketCategory.FINANCEIRO, TicketPriority.ALTA, TicketStatus.EM_ANDAMENTO,
						"solicitante@fadex.org.br", "admin@fadex.org.br",
						ClassificationOrigin.IA,
						"Falha de exportacao em rotina financeira mensal com bloqueio de entrega.",
						28, null, 4, "Reproduzimos o erro e estamos analisando o log do gerador de PDF.",
						TicketCategory.FINANCEIRO, TicketPriority.ALTA, 0.86
				),
				new TicketSeed(
						"Impressora do terceiro andar sem rede",
						"A impressora aparece offline para todos os computadores do andar.",
						TicketCategory.INFRAESTRUTURA, TicketPriority.MEDIA, TicketStatus.EM_ANDAMENTO,
						"ana.ribeiro@fadex.org.br", "carla.menezes@fadex.org.br",
						ClassificationOrigin.IA,
						"Equipamento de rede compartilhado inacessivel por multiplos usuarios.",
						45, null, 6, "Trocamos o cabo de rede e seguimos testando a conexao.",
						TicketCategory.INFRAESTRUTURA, TicketPriority.MEDIA, 0.82
				),
				new TicketSeed(
						"Acesso ao modulo de contratos negado",
						"Fui promovido e preciso de acesso ao modulo de contratos do sistema.",
						TicketCategory.ACESSO, TicketPriority.MEDIA, TicketStatus.EM_ANDAMENTO,
						"bruno.carvalho@fadex.org.br", "mvaldecy11@gmail.com",
						ClassificationOrigin.MANUAL,
						"Reclassificado manualmente: pedido de permissao, nao incidente de sistema.",
						60, null, 12, "Solicitamos aprovacao da chefia imediata para liberar o perfil.",
						TicketCategory.SISTEMAS, TicketPriority.ALTA, 0.44
				),
				new TicketSeed(
						"Notebook nao liga apos atualizacao",
						"Depois da atualizacao automatica o notebook trava na tela da fabricante.",
						TicketCategory.EQUIPAMENTOS, TicketPriority.ALTA, TicketStatus.EM_ANDAMENTO,
						"solicitante@fadex.org.br", "admin@fadex.org.br",
						ClassificationOrigin.IA,
						"Equipamento inoperante impede o trabalho do solicitante.",
						14, null, 2, "Notebook recolhido para diagnostico, emprestamos uma maquina reserva.",
						TicketCategory.EQUIPAMENTOS, TicketPriority.ALTA, 0.90
				),
				new TicketSeed(
						"Solicitacao de VPN para trabalho remoto",
						"Preciso de acesso VPN para trabalhar remotamente nas sextas.",
						TicketCategory.ACESSO, TicketPriority.BAIXA, TicketStatus.EM_ANDAMENTO,
						"ana.ribeiro@fadex.org.br", "carla.menezes@fadex.org.br",
						ClassificationOrigin.PENDENTE, null,
						72, null, 24, "Encaminhado para aprovacao da politica de acesso remoto.",
						null, null, null
				),
				new TicketSeed(
						"Reembolso de diaria nao processado",
						"Enviei a prestacao de contas ha duas semanas e o reembolso nao caiu.",
						TicketCategory.FINANCEIRO, TicketPriority.MEDIA, TicketStatus.RESOLVIDO,
						"bruno.carvalho@fadex.org.br", "mvaldecy11@gmail.com",
						ClassificationOrigin.MANUAL,
						"Reclassificado manualmente de RH para Financeiro apos analise do fluxo.",
						120, 48, 8, "Localizamos a pendencia de documento e reenviamos ao financeiro.",
						TicketCategory.RH, TicketPriority.BAIXA, 0.41
				),
				new TicketSeed(
						"Senha do e-mail expirada",
						"Meu e-mail parou de sincronizar no celular pedindo senha nova.",
						TicketCategory.ACESSO, TicketPriority.BAIXA, TicketStatus.RESOLVIDO,
						"solicitante@fadex.org.br", "admin@fadex.org.br",
						ClassificationOrigin.IA,
						"Expiracao de credencial e um caso rotineiro de acesso, sem impacto coletivo.",
						96, 3, 1, "Senha redefinida e sincronizacao validada com o solicitante.",
						TicketCategory.ACESSO, TicketPriority.BAIXA, 0.93
				),
				new TicketSeed(
						"Monitor com listras verticais",
						"O monitor comecou a mostrar listras verticais na metade da tela.",
						TicketCategory.EQUIPAMENTOS, TicketPriority.MEDIA, TicketStatus.RESOLVIDO,
						"ana.ribeiro@fadex.org.br", "carla.menezes@fadex.org.br",
						ClassificationOrigin.IA,
						"Defeito fisico de periferico com substituicao prevista em estoque.",
						168, 30, 5, "Monitor substituido por unidade do estoque.",
						TicketCategory.EQUIPAMENTOS, TicketPriority.MEDIA, 0.84
				),
				new TicketSeed(
						"Cadastro de fornecedor duplicado",
						"O mesmo fornecedor aparece duas vezes na listagem do sistema de compras.",
						TicketCategory.SISTEMAS, TicketPriority.BAIXA, TicketStatus.RESOLVIDO,
						"bruno.carvalho@fadex.org.br", "mvaldecy11@gmail.com",
						ClassificationOrigin.PENDENTE, null,
						200, 72, 20, "Registros consolidados e duplicidade removida da base.",
						null, null, null
				),
				new TicketSeed(
						"Falha de conexao no sistema de ponto",
						"O relogio de ponto nao registra as batidas desde a manha de segunda.",
						TicketCategory.SISTEMAS, TicketPriority.ALTA, TicketStatus.RESOLVIDO,
						"solicitante@fadex.org.br", "admin@fadex.org.br",
						ClassificationOrigin.IA,
						"Sistema de registro obrigatorio indisponivel afeta toda a equipe.",
						80, 5, 1, "Servico do integrador reiniciado e batidas recuperadas.",
						TicketCategory.SISTEMAS, TicketPriority.ALTA, 0.89
				),
				new TicketSeed(
						"Atualizacao de dados bancarios",
						"Troquei de banco e preciso atualizar a conta de recebimento.",
						TicketCategory.RH, TicketPriority.BAIXA, TicketStatus.FECHADO,
						"ana.ribeiro@fadex.org.br", "carla.menezes@fadex.org.br",
						ClassificationOrigin.MANUAL,
						"Reclassificado manualmente para RH por envolver cadastro funcional.",
						300, 96, 36, "Dados atualizados no cadastro e confirmados com a servidora.",
						TicketCategory.FINANCEIRO, TicketPriority.MEDIA, 0.38
				),
				new TicketSeed(
						"Instalacao de pacote estatistico",
						"Preciso do pacote estatistico instalado na maquina do laboratorio.",
						TicketCategory.SISTEMAS, TicketPriority.BAIXA, TicketStatus.FECHADO,
						"bruno.carvalho@fadex.org.br", "mvaldecy11@gmail.com",
						ClassificationOrigin.PENDENTE, null,
						400, 120, 48, "Pacote instalado e licenca registrada no inventario.",
						null, null, null
				),
				new TicketSeed(
						"Queda de energia derrubou o rack",
						"Depois da queda de energia os servicos do rack principal ficaram indisponiveis.",
						TicketCategory.INFRAESTRUTURA, TicketPriority.ALTA, TicketStatus.FECHADO,
						"solicitante@fadex.org.br", "admin@fadex.org.br",
						ClassificationOrigin.IA,
						"Interrupcao de servicos centrais por falha eletrica exige resposta imediata.",
						240, 6, 1, "Nobreak substituido e servicos restabelecidos.",
						TicketCategory.INFRAESTRUTURA, TicketPriority.ALTA, 0.94
				),
				new TicketSeed(
						"Solicitacao de treinamento no novo sistema",
						"A equipe precisa de treinamento para o novo modulo de prestacao de contas.",
						TicketCategory.OUTROS, TicketPriority.MEDIA, TicketStatus.FECHADO,
						"ana.ribeiro@fadex.org.br", "carla.menezes@fadex.org.br",
						ClassificationOrigin.MANUAL,
						"Reclassificado manualmente como demanda administrativa, nao incidente tecnico.",
						500, 168, 72, "Treinamento realizado com a equipe e material compartilhado.",
						TicketCategory.SISTEMAS, TicketPriority.MEDIA, 0.52
				),

				// Cobaias de triagem: recem-abertas, sem classificacao, sem sugestao da IA e sem
				// responsavel. Existem para o avaliador clicar em "solicitar triagem" e ver a IA
				// classificar ao vivo. Ficam com `ai_suggested_*` nulos de proposito — assim nao
				// entram no denominador da concordancia e nao mexem nas metricas que os 20 chamados
				// anteriores sustentam.
				new TicketSeed(
						"Impressora do setor financeiro parou de responder",
						"A impressora da sala do financeiro nao aparece mais na lista de dispositivos desde hoje de manha.",
						TicketCategory.OUTROS, TicketPriority.MEDIA, TicketStatus.ABERTO,
						"solicitante@fadex.org.br", null,
						ClassificationOrigin.PENDENTE, null,
						2, null, null, null,
						null, null, null
				),
				new TicketSeed(
						"Esqueci a senha do portal de prestacao de contas",
						"Tentei redefinir pelo proprio portal mas o e-mail de recuperacao nao chega.",
						TicketCategory.OUTROS, TicketPriority.MEDIA, TicketStatus.ABERTO,
						"ana.ribeiro@fadex.org.br", null,
						ClassificationOrigin.PENDENTE, null,
						1, null, null, null,
						null, null, null
				),
				new TicketSeed(
						"Internet do terceiro andar oscilando desde cedo",
						"A conexao cai por alguns segundos a cada poucos minutos e derruba as reunioes.",
						TicketCategory.OUTROS, TicketPriority.MEDIA, TicketStatus.ABERTO,
						"bruno.carvalho@fadex.org.br", null,
						ClassificationOrigin.PENDENTE, null,
						3, null, null, null,
						null, null, null
				),
				new TicketSeed(
						"Duvida sobre o periodo aquisitivo de ferias",
						"Preciso saber a partir de quando posso agendar minhas ferias deste ciclo.",
						TicketCategory.OUTROS, TicketPriority.MEDIA, TicketStatus.ABERTO,
						"solicitante@fadex.org.br", null,
						ClassificationOrigin.PENDENTE, null,
						5, null, null, null,
						null, null, null
				),

				// Par de duplicados: o mesmo incidente relatado por duas pessoas, com palavras
				// diferentes. Existe porque sem ele a deteccao de duplicados nao tem o que achar —
				// o par mais proximo do resto do seed marca 0,76, abaixo do limiar de 0,80, e a aba
				// de similares nasce vazia num sistema que funciona.
				//
				// Medido com all-minilm pelo endpoint de embeddings: os dois marcam 0,850 entre si,
				// e o vizinho mais proximo de qualquer um deles no restante do seed fica em 0,54.
				// Ou seja, o par entra com folga acima do limiar e nao arrasta ninguem junto.
				new TicketSeed(
						"Sistema de protocolo fora do ar para todo o setor",
						"Ninguem do setor consegue abrir o sistema de protocolo desde as 8h da manha; a pagina fica carregando e nao entra.",
						TicketCategory.OUTROS, TicketPriority.MEDIA, TicketStatus.ABERTO,
						"ana.ribeiro@fadex.org.br", null,
						ClassificationOrigin.PENDENTE, null,
						4, null, null, null,
						null, null, null
				),
				new TicketSeed(
						"Nao consigo entrar no sistema de protocolo desde a manha",
						"Desde as 8h o sistema de protocolo fica carregando e nao entra; ninguem da minha sala consegue abrir.",
						TicketCategory.OUTROS, TicketPriority.MEDIA, TicketStatus.ABERTO,
						"bruno.carvalho@fadex.org.br", null,
						ClassificationOrigin.PENDENTE, null,
						3, null, null, null,
						null, null, null
				)
		);
	}

	private record TicketSeed(
			String title,
			String description,
			TicketCategory category,
			TicketPriority priority,
			TicketStatus status,
			String requesterEmail,
			String assigneeEmail,
			ClassificationOrigin classificationOrigin,
			String justification,
			int createdHoursAgo,
			Integer resolvedAfterHours,
			Integer firstReplyAfterHours,
			String firstReply,
			TicketCategory aiSuggestedCategory,
			TicketPriority aiSuggestedPriority,
			Double aiConfidence
	) {
	}
}

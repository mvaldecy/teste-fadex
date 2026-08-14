package br.org.fadex.helpdesk.service;

import br.org.fadex.helpdesk.ai.job.AiJobService;
import br.org.fadex.helpdesk.exception.NotFoundException;
import br.org.fadex.helpdesk.model.ticket.Ticket;
import br.org.fadex.helpdesk.model.ticket.TicketCreationDto;
import br.org.fadex.helpdesk.model.ticket.TicketDto;
import br.org.fadex.helpdesk.model.ticket.TicketFilter;
import br.org.fadex.helpdesk.model.ticket.TicketMapper;
import br.org.fadex.helpdesk.model.ticket.TicketMinDto;
import br.org.fadex.helpdesk.model.user.User;
import br.org.fadex.helpdesk.repository.TicketRepository;
import br.org.fadex.helpdesk.repository.TicketSpecification;
import br.org.fadex.helpdesk.security.AuthenticatedUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TicketService {

	private final TicketRepository ticketRepository;
	private final UserService userService;
	private final AuthenticatedUserService authenticatedUserService;
	private final AiJobService aiJobService;

	public TicketService(
			TicketRepository ticketRepository,
			UserService userService,
			AuthenticatedUserService authenticatedUserService,
			AiJobService aiJobService
	) {
		this.ticketRepository = ticketRepository;
		this.userService = userService;
		this.authenticatedUserService = authenticatedUserService;
		this.aiJobService = aiJobService;
	}

	@Transactional(readOnly = true)
	public Page<TicketMinDto> findAll(TicketFilter filter, Pageable pageable) {
		Specification<Ticket> spec = TicketSpecification.createSpecification(filter);
		Page<Ticket> tickets = ticketRepository.findAll(spec, pageable);
		Page<TicketMinDto> response = tickets.map(TicketMapper::toMinDto);

		return response;
	}

	@Transactional(readOnly = true)
	public TicketDto findById(UUID id) {
		Ticket ticket = findEntityById(id);
		TicketDto response = TicketMapper.toResponseDto(ticket);

		return response;
	}

	@Transactional(readOnly = true)
	public Ticket findEntityById(UUID id) {
		return ticketRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("Chamado não encontrado."));
	}

	@Transactional
	public TicketDto create(TicketCreationDto ticketCreationDto) {
		UUID authenticatedUserId = authenticatedUserService.getUserId();
		User requester = userService.findEntityById(authenticatedUserId);
		Ticket ticket = TicketMapper.toEntity(ticketCreationDto, requester);
		Ticket savedTicket = ticketRepository.save(ticket);
		aiJobService.enqueueTicketJobs(savedTicket);
		TicketDto response = TicketMapper.toResponseDto(savedTicket);

		return response;
	}
}

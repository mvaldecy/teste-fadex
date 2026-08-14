package br.org.fadex.helpdesk.security;

import br.org.fadex.helpdesk.exception.ForbiddenException;
import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.model.ticket.Ticket;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AccessControlService {

	private static final String FORBIDDEN_MESSAGE = "Acesso negado ao recurso solicitado.";

	private final AuthenticatedUserService authenticatedUserService;

	public AccessControlService(AuthenticatedUserService authenticatedUserService) {
		this.authenticatedUserService = authenticatedUserService;
	}

	public boolean isAdmin() {
		return authenticatedUserService.getRole() == Role.ADMIN;
	}

	public void assertAdmin() {
		if (!isAdmin()) {
			throw new ForbiddenException(FORBIDDEN_MESSAGE);
		}
	}

	public UUID getAuthenticatedUserId() {
		return authenticatedUserService.getUserId();
	}

	public void assertCanAccessUser(UUID userId) {
		if (!isAdmin() && !getAuthenticatedUserId().equals(userId)) {
			throw new ForbiddenException(FORBIDDEN_MESSAGE);
		}
	}

	public void assertCanAccessTicket(Ticket ticket) {
		if (isAdmin()) {
			return;
		}

		UUID requesterId = ticket.getRequester().getId();

		if (!getAuthenticatedUserId().equals(requesterId)) {
			throw new ForbiddenException(FORBIDDEN_MESSAGE);
		}
	}
}

package br.org.fadex.helpdesk.repository;

import br.org.fadex.helpdesk.model.ticket.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID>, JpaSpecificationExecutor<Ticket> {

	/**
	 * Traz solicitante e responsavel na mesma consulta da pagina.
	 *
	 * As duas associacoes sao {@code LAZY} — o que esta certo para quem carrega um chamado
	 * isolado — mas a listagem le o nome dos dois em toda linha. Sem este grafo era uma consulta
	 * por associacao nao repetida: uma pagina de dez chamados de usuarios distintos disparava
	 * ate vinte e uma consultas onde uma basta.
	 *
	 * O {@code countQuery} da paginacao ignora o grafo, entao a contagem continua barata.
	 */
	@Override
	@EntityGraph(attributePaths = {"requester", "assignee"})
	Page<Ticket> findAll(Specification<Ticket> specification, Pageable pageable);
}

import type {
  TicketCategoryValue,
  TicketPriorityValue,
  TicketStatusValue
} from "./choice";

/**
 * Item de `GET /api/v1/tickets/{id}/similar`.
 *
 * `similarity` e nulavel de proposito: vinculos gravados antes da migration V6
 * nao registraram o valor e nao ha backfill possivel, porque o embedding de
 * origem pode ter mudado desde a deteccao. A tela precisa renderizar a
 * ausencia em vez de assumir zero — que seria "nada parecido", o oposto do que
 * o vinculo significa.
 */
export type SimilarTicketDto = {
  id: string;
  title: string;
  status: TicketStatusValue;
  priority: TicketPriorityValue;
  category: TicketCategoryValue;
  similarity: number | null;
  createdAt: string;
};

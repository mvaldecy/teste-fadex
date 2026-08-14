import type { PageParams } from "./pagination";
import type { UserSummary } from "./user";

/**
 * Tipos publicados em `docs/backend/api.md`, secao
 * `GET /api/v1/tickets/{ticketId}/events`.
 */
export const ticketEventTypes = [
  "CHAMADO_CRIADO",
  "COMENTARIO_ADICIONADO",
  "STATUS_ALTERADO",
  "RESPONSAVEL_ATRIBUIDO",
  "PRIORIDADE_ALTERADA",
  "CATEGORIA_ALTERADA",
  "CLASSIFICACAO_ATUALIZADA"
] as const;

export type TicketEventType = (typeof ticketEventTypes)[number];

/**
 * `actor` anulavel e `type` aberto de proposito: eventos gerados pela IA ou
 * pelo seed podem nao ter ator, e um tipo novo no backend nao pode quebrar a
 * renderizacao da linha do tempo.
 */
export type TicketEventDto = {
  id: string;
  actor: UserSummary | null;
  type: TicketEventType | string;
  description: string;
  createdAt: string;
};

export type TicketEventFilters = PageParams & {
  actorId?: string;
  type?: TicketEventType;
  search?: string;
};

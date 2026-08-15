import type {
  ClassificationOriginValue,
  TicketCategoryValue,
  TicketPriorityValue,
  TicketStatusValue
} from "./choice";
import type { PageParams } from "./pagination";
import type { UserSummary } from "./user";

export type TicketSummary = {
  id: string;
  title: string;
  category: TicketCategoryValue;
  priority: TicketPriorityValue;
  status: TicketStatusValue;
  classificationOrigin: ClassificationOriginValue;
  requester: UserSummary;
  assignee: UserSummary | null;
  createdAt: string;
  /**
   * Quantos semelhantes a detecção de duplicados vinculou a este chamado.
   * Ausente quando o número não foi apurado — é o caso do payload das
   * notificações, que descreve a mudança e não a listagem.
   */
  similarCount?: number | null;
};

/**
 * `classificationJustification` já existe no `TicketDto` desde a triagem por
 * IA. Os campos `aiSuggested*` e `confidence` dependem da frente IA expor no
 * DTO: por ora vem sempre ausentes, e o bloco de sugestao não renderiza.
 */
export type TicketDto = TicketSummary & {
  description: string;
  updatedAt: string;
  classificationJustification?: string | null;
  aiSuggestedCategory?: TicketCategoryValue | null;
  aiSuggestedPriority?: TicketPriorityValue | null;
  confidence?: number | null;
};

export type CreateTicketRequest = {
  title: string;
  description: string;
};

export type UpdateTicketStatusRequest = {
  status: TicketStatusValue;
};

export type AssignTicketRequest = {
  assigneeId: string;
};

export type UpdateTicketClassificationRequest = {
  category: TicketCategoryValue;
  priority: TicketPriorityValue;
  classificationJustification?: string;
};

export type TicketFilters = PageParams & {
  status?: TicketStatusValue;
  priority?: TicketPriorityValue;
  category?: TicketCategoryValue;
  requesterId?: string;
  assigneeId?: string;
  /** `true` traz so os chamados sem responsável. Tem precedencia sobre `assigneeId` na API. */
  unassigned?: boolean;
  search?: string;
};

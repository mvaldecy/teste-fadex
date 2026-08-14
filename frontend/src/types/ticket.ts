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
};

export type TicketDto = TicketSummary & {
  description: string;
  updatedAt: string;
};

export type TicketFilters = PageParams & {
  status?: TicketStatusValue;
  priority?: TicketPriorityValue;
  category?: TicketCategoryValue;
  requesterId?: string;
  assigneeId?: string;
  search?: string;
};

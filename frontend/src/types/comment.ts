import type { PageParams } from "./pagination";
import type { UserSummary } from "./user";

export type TicketCommentSummary = {
  id: string;
  author: UserSummary;
  text: string;
  createdAt: string;
};

export type TicketCommentDto = TicketCommentSummary & {
  updatedAt: string;
};

export type TicketCommentFilters = PageParams & {
  authorId?: string;
  search?: string;
};

export type CreateTicketCommentRequest = {
  text: string;
};

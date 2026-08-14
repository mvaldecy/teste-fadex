import type {
  CreateTicketCommentRequest,
  PageResponse,
  TicketCommentDto,
  TicketCommentFilters,
  TicketCommentSummary
} from "@/src/types/api";
import { api } from "./api";

async function list(ticketId: string, filters?: TicketCommentFilters) {
  const response = await api.get<PageResponse<TicketCommentSummary>>(
    `/tickets/${ticketId}/comments`,
    {
      params: filters
    }
  );

  return response.data;
}

async function create(ticketId: string, payload: CreateTicketCommentRequest) {
  const response = await api.post<TicketCommentDto>(
    `/tickets/${ticketId}/comments`,
    payload
  );

  return response.data;
}

export const ticketCommentsService = {
  list,
  create
};

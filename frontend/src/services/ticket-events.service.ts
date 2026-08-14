import type {
  PageResponse,
  TicketEventDto,
  TicketEventFilters
} from "@/src/types/api";
import { api } from "./api";

async function list(ticketId: string, filters?: TicketEventFilters) {
  const response = await api.get<PageResponse<TicketEventDto>>(
    `/tickets/${ticketId}/events`,
    { params: filters }
  );

  return response.data;
}

export const ticketEventsService = {
  list
};

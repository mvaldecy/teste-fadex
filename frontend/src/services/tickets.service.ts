import type {
  PageResponse,
  TicketDto,
  TicketFilters,
  TicketSummary
} from "@/src/types/api";
import { api } from "./api";

async function list(filters?: TicketFilters) {
  const response = await api.get<PageResponse<TicketSummary>>("/tickets", {
    params: filters
  });

  return response.data;
}

async function getById(id: string) {
  const response = await api.get<TicketDto>(`/tickets/${id}`);
  return response.data;
}

export const ticketsService = {
  list,
  getById
};

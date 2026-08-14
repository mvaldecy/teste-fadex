import type {
  AssignTicketRequest,
  CreateTicketRequest,
  PageResponse,
  TicketDto,
  TicketFilters,
  TicketSummary,
  UpdateTicketClassificationRequest,
  UpdateTicketStatusRequest
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

async function create(payload: CreateTicketRequest) {
  const response = await api.post<TicketDto>("/tickets", payload);
  return response.data;
}

/**
 * As quatro mutacoes abaixo usam os caminhos fixados em
 * `docs/projeto/2026-08-14-frentes-de-trabalho.md`. Os corpos seguem
 * provisorios ate a frente API publicar o delta do `api.md`.
 *
 * Sem fallback para dado fixo, ao contrario das leituras: fingir sucesso de
 * uma escrita que nao aconteceu e pior do que mostrar o erro.
 */
async function updateStatus(id: string, payload: UpdateTicketStatusRequest) {
  const response = await api.patch<TicketDto>(`/tickets/${id}/status`, payload);
  return response.data;
}

async function assign(id: string, payload: AssignTicketRequest) {
  const response = await api.patch<TicketDto>(`/tickets/${id}/assignee`, payload);
  return response.data;
}

async function unassign(id: string) {
  const response = await api.delete<TicketDto>(`/tickets/${id}/assignee`);
  return response.data;
}

async function updateClassification(
  id: string,
  payload: UpdateTicketClassificationRequest
) {
  const response = await api.patch<TicketDto>(
    `/tickets/${id}/classification`,
    payload
  );

  return response.data;
}

export const ticketsService = {
  list,
  getById,
  create,
  updateStatus,
  assign,
  unassign,
  updateClassification
};

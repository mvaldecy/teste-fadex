import type {
  AiJobDto,
  AssignTicketRequest,
  CreateTicketRequest,
  PageResponse,
  NearestTicketDto,
  SimilarTicketDto,
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
 * provisorios até a frente API publicar o delta do `api.md`.
 *
 * Sem fallback para dado fixo, ao contrario das leituras: fingir sucesso de
 * uma escrita que não aconteceu e pior do que mostrar o erro.
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

/**
 * Cancelamento e exclusao logica: o backend responde `200` com o chamado já em
 * `CANCELADO`, e não `204`. O chamado continua existindo — histórico,
 * comentários e métricas ficam.
 *
 * ADMIN cancela qualquer chamado; SOLICITANTE cancela o próprio enquanto
 * `ABERTO`. Papel indevido responde `403`, estado que não aceita cancelamento
 * responde `409`.
 */
async function cancel(id: string) {
  const response = await api.delete<TicketDto>(`/tickets/${id}`);
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

/**
 * Chamados semelhantes já detectados por embedding. Não dispara deteccao: le os
 * vinculos gravados pelo worker, nas duas direcoes.
 *
 * Restrito a ADMIN no backend. Quem chama precisa esconder a aba para os demais
 * papeis em vez de tomar 403 — o resultado expoe título de chamado alheio.
 */
async function listSimilar(id: string) {
  const response = await api.get<SimilarTicketDto[]>(`/tickets/${id}/similar`);
  return response.data;
}

/** Ranking dos mais próximos, sem filtro de limiar. */
async function listNearest(id: string, limit = 5) {
  const response = await api.get<NearestTicketDto[]>(`/tickets/${id}/nearest`, {
    params: { limit }
  });
  return response.data;
}

/**
 * Reenfileira a triagem por IA. Responde `202`: enfileira e devolve, sem
 * esperar o modelo local. O resultado chega depois por SSE
 * (`CLASSIFICACAO_CONCLUIDA`) ou pela tela de jobs.
 */
async function requestAiTriage(id: string) {
  const response = await api.post<AiJobDto[]>(`/tickets/${id}/ai-triage`);
  return response.data;
}

export const ticketsService = {
  list,
  getById,
  create,
  updateStatus,
  assign,
  unassign,
  cancel,
  updateClassification,
  listSimilar,
  listNearest,
  requestAiTriage
};

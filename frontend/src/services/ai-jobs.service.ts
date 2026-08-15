import type { AiJobDto, AiJobFilters, PageResponse } from "@/src/types/api";
import { api } from "./api";

/**
 * `GET /api/v1/ai/jobs` e `POST /api/v1/ai/jobs/{id}/retry` estao publicados e
 * foram verificados contra o backend rodando. Por isso a listagem perdeu o
 * fallback para dado fixo: manter o fallback num endpoint publicado faria um
 * erro real da API virar tela de exemplo, escondendo a falha do operador.
 */
async function list(filters?: AiJobFilters) {
  const response = await api.get<PageResponse<AiJobDto>>("/ai/jobs", {
    params: filters
  });

  return response.data;
}

/**
 * Sem fallback: e mutacao. Fingir um reprocessamento que não aconteceu seria
 * pior do que mostrar o erro.
 */
async function retry(id: string) {
  await api.post(`/ai/jobs/${id}/retry`);
}

export const aiJobsService = {
  list,
  retry
};

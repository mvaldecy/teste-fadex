import type { AiJobDto, AiJobFilters, PageResponse } from "@/src/types/api";
import { aiJobsFixture } from "./ai-jobs.fixture";
import { api } from "./api";
import { toFixtureReason } from "./endpoint-fallback";

export type AiJobsResult = {
  data: AiJobDto[];
  isFixture: boolean;
  fixtureReason: string | null;
};

const path = "/api/v1/ai/jobs";

async function list(filters?: AiJobFilters): Promise<AiJobsResult> {
  try {
    const response = await api.get<PageResponse<AiJobDto>>("/ai/jobs", {
      params: filters
    });

    return {
      data: response.data.content,
      isFixture: false,
      fixtureReason: null
    };
  } catch (error) {
    const fixtureReason = toFixtureReason(error, path);

    if (fixtureReason) {
      return { data: aiJobsFixture, isFixture: true, fixtureReason };
    }

    throw error;
  }
}

/**
 * Sem fallback: e mutacao. Fingir um reprocessamento que nao aconteceu seria
 * pior do que mostrar o erro.
 */
async function retry(id: string) {
  await api.post(`/ai/jobs/${id}/retry`);
}

export const aiJobsService = {
  list,
  retry
};

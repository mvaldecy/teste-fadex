import type { IndicatorsResponse } from "@/src/types/api";
import { api } from "./api";
import { toFixtureReason } from "./endpoint-fallback";
import { indicatorsFixture } from "./indicators.fixture";

export type IndicatorsResult = {
  data: IndicatorsResponse;
  isFixture: boolean;
  fixtureReason: string | null;
};

const path = "/api/v1/indicators";

async function get(): Promise<IndicatorsResult> {
  try {
    const response = await api.get<IndicatorsResponse>("/indicators");
    return { data: response.data, isFixture: false, fixtureReason: null };
  } catch (error) {
    const fixtureReason = toFixtureReason(error, path);

    if (fixtureReason) {
      return { data: indicatorsFixture, isFixture: true, fixtureReason };
    }

    throw error;
  }
}

export const indicatorsService = {
  get
};

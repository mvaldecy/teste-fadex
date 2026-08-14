import type { ChoicesResponse } from "@/src/types/api";
import { api } from "./api";

async function getChoices() {
  const response = await api.get<ChoicesResponse>("/choices");
  return response.data;
}

export const choicesService = {
  getChoices
};

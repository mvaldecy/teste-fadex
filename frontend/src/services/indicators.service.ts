import type { IndicatorsResponse } from "@/src/types/api";
import { api } from "./api";

/**
 * `GET /api/v1/indicators` esta publicado e restrito a ADMIN. O fallback para
 * dado fixo saiu junto com o ultimo endpoint pendente: com o contrato real no
 * ar, um erro da API precisa aparecer como erro, e não como tela de exemplo.
 */
async function get() {
  const response = await api.get<IndicatorsResponse>("/indicators");

  return response.data;
}

export const indicatorsService = {
  get
};

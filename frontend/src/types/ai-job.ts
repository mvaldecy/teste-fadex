import type { PageParams } from "./pagination";

/**
 * Valores lidos de `ai/job/AiJobType.java`, `ai/job/AiJobStatus.java` e do
 * check constraint da migration V3. Em ingles, ao contrario dos enums de
 * dominio do chamado, que sao em portugues. O frontend segue o backend nos
 * dois casos, sem traduzir os valores.
 */
export const aiJobTypes = ["CLASSIFICATION", "EMBEDDING"] as const;
export const aiJobStatuses = [
  "PENDING",
  "PROCESSING",
  "DONE",
  "FAILED"
] as const;

export type AiJobType = (typeof aiJobTypes)[number];
export type AiJobStatus = (typeof aiJobStatuses)[number];

/**
 * `lastError` so existe no `AiJobDto`; o `AiJobSummaryDto` da listagem traz
 * `nextAttemptAt` sem o erro. Por isso ambos sao opcionais.
 */
export type AiJobDto = {
  id: string;
  ticketId: string | null;
  type: AiJobType | string;
  status: AiJobStatus | string;
  attempts: number;
  nextAttemptAt?: string | null;
  lastError?: string | null;
  createdAt: string;
  updatedAt?: string | null;
};

export type AiJobFilters = PageParams & {
  status?: AiJobStatus;
};

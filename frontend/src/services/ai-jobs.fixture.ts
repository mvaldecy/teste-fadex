import type { AiJobDto } from "@/src/types/api";

/**
 * Dado fixo usado apenas enquanto `GET /api/v1/ai/jobs` responder 404.
 * Nunca e exibido sem o aviso de "dados de exemplo" na pagina.
 */
export const aiJobsFixture: AiJobDto[] = [
  {
    id: "3f6f5a10-0b1c-4d2e-9a3b-1c2d3e4f5a6b",
    ticketId: null,
    type: "CLASSIFICATION",
    status: "FAILED",
    attempts: 3,
    nextAttemptAt: null,
    lastError: "Timeout ao chamar o modelo local de classificacao.",
    createdAt: "2026-08-14T10:00:00",
    updatedAt: "2026-08-14T10:05:00"
  },
  {
    id: "8c7d6e5f-4a3b-2c1d-0e9f-8a7b6c5d4e3f",
    ticketId: null,
    type: "EMBEDDING",
    status: "PENDING",
    attempts: 0,
    nextAttemptAt: "2026-08-14T10:20:00",
    lastError: null,
    createdAt: "2026-08-14T10:15:00",
    updatedAt: null
  },
  {
    id: "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
    ticketId: null,
    type: "CLASSIFICATION",
    status: "DONE",
    attempts: 1,
    nextAttemptAt: null,
    lastError: null,
    createdAt: "2026-08-14T09:30:00",
    updatedAt: "2026-08-14T09:31:00"
  }
];

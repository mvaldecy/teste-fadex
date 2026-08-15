import { isAxiosError } from "axios";
import type { ApiErrorResponse } from "@/src/types/api";

const fallbackMessage = "Não foi possível processar a solicitação.";

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

export function isApiErrorResponse(value: unknown): value is ApiErrorResponse {
  if (!isRecord(value)) {
    return false;
  }

  return (
    typeof value.code === "string" &&
    typeof value.message === "string" &&
    typeof value.status === "number" &&
    typeof value.path === "string" &&
    typeof value.timestamp === "string"
  );
}

export function toApiErrorMessage(error: unknown) {
  if (isAxiosError(error)) {
    const responseData = error.response?.data;

    if (isApiErrorResponse(responseData)) {
      return responseData.message;
    }

    if (!error.response) {
      return "Não foi possível conectar com a API.";
    }
  }

  if (error instanceof Error && error.message) {
    return error.message;
  }

  return fallbackMessage;
}

import type { ApiErrorBody } from "@/src/types/api";

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export type ApiClientConfig = {
  apiBaseUrl: string;
};

export function createApiClient(config: ApiClientConfig) {
  const baseUrl = config.apiBaseUrl.replace(/\/+$/, "");

  async function parseError(response: Response): Promise<string> {
    const contentType = response.headers.get("content-type") ?? "";

    if (!contentType.includes("application/json")) {
      return response.statusText || "Erro ao comunicar com a API";
    }

    const body = (await response.json()) as ApiErrorBody;
    return body.message ?? body.error ?? "Erro ao comunicar com a API";
  }

  return {
    async get<T>(path: string): Promise<T> {
      const normalizedPath = path.startsWith("/") ? path : `/${path}`;
      const response = await fetch(`${baseUrl}${normalizedPath}`, {
        headers: { Accept: "application/json" }
      });

      if (!response.ok) {
        throw new ApiError(await parseError(response), response.status);
      }

      return (await response.json()) as T;
    }
  };
}

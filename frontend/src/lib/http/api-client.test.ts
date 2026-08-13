import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, createApiClient } from "./api-client";

describe("createApiClient", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("faz GET usando a URL base configurada", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: { "content-type": "application/json" }
      })
    );

    const client = createApiClient({
      apiBaseUrl: "http://localhost:8080/api/v1"
    });
    const result = await client.get<{ ok: boolean }>("/choices");

    expect(result).toEqual({ ok: true });
    expect(fetch).toHaveBeenCalledWith("http://localhost:8080/api/v1/choices", {
      headers: { Accept: "application/json" }
    });
  });

  it("normaliza erro HTTP", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ message: "Nao autorizado" }), {
        status: 401,
        headers: { "content-type": "application/json" }
      })
    );

    const client = createApiClient({
      apiBaseUrl: "http://localhost:8080/api/v1"
    });

    await expect(client.get("/tickets")).rejects.toMatchObject({
      name: "ApiError",
      status: 401,
      message: "Nao autorizado"
    } satisfies Partial<ApiError>);
  });
});

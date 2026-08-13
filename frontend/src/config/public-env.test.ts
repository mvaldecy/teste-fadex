import { describe, expect, it } from "vitest";
import { getPublicEnv } from "./public-env";

describe("getPublicEnv", () => {
  it("normaliza a URL base removendo barra final", () => {
    const env = getPublicEnv({
      NEXT_PUBLIC_APP_NAME: "Fadex Helpdesk",
      NEXT_PUBLIC_API_BASE_URL: "http://localhost:8080/api/v1/"
    });

    expect(env.appName).toBe("Fadex Helpdesk");
    expect(env.apiBaseUrl).toBe("http://localhost:8080/api/v1");
  });

  it("rejeita URL invalida da API", () => {
    expect(() =>
      getPublicEnv({
        NEXT_PUBLIC_APP_NAME: "Fadex Helpdesk",
        NEXT_PUBLIC_API_BASE_URL: "localhost:8080"
      })
    ).toThrow("Configuracao publica invalida");
  });
});

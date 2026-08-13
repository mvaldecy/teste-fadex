import { describe, expect, it } from "vitest";
import { loginFormSchema } from "./auth.schemas";

describe("loginFormSchema", () => {
  it("aceita credenciais validas", () => {
    const result = loginFormSchema.safeParse({
      email: "admin@fadex.org.br",
      password: "123456"
    });

    expect(result.success).toBe(true);
  });

  it("rejeita email invalido e senha vazia", () => {
    const result = loginFormSchema.safeParse({
      email: "admin",
      password: ""
    });

    expect(result.success).toBe(false);
  });
});

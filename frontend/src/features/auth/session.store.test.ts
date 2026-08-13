import { beforeEach, describe, expect, it } from "vitest";
import { useSessionStore } from "./session.store";

describe("useSessionStore", () => {
  beforeEach(() => {
    useSessionStore.getState().logout();
  });

  it("inicia sem usuario autenticado", () => {
    expect(useSessionStore.getState().user).toBeNull();
    expect(useSessionStore.getState().isAuthenticated).toBe(false);
  });

  it("simula login sem persistir token real", () => {
    useSessionStore.getState().simulateLogin({
      email: "admin@fadex.org.br",
      password: "123456"
    });

    expect(useSessionStore.getState().user).toEqual({
      email: "admin@fadex.org.br",
      name: "admin",
      role: "ADMIN"
    });
    expect(useSessionStore.getState().isAuthenticated).toBe(true);
  });

  it("limpa a sessao ao sair", () => {
    useSessionStore.getState().simulateLogin({
      email: "user@fadex.org.br",
      password: "123456"
    });

    useSessionStore.getState().logout();

    expect(useSessionStore.getState().user).toBeNull();
    expect(useSessionStore.getState().isAuthenticated).toBe(false);
  });
});

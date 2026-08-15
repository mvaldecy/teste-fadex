"use client";

import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import { loginDefaultValues } from "@/src/features/auth/login-default-values";
import { loginFormSchema } from "@/src/schemas/auth.schema";
import { homeRouteForRole } from "@/src/routes/routes";
import { useSessionStore } from "@/src/stores/session.store";

type FieldErrors = Partial<Record<"email" | "password", string>>;

export function LoginForm() {
  const router = useRouter();
  const login = useSessionStore((state) => state.login);
  const apiError = useSessionStore((state) => state.error);
  const isLoading = useSessionStore((state) => state.isLoading);
  const clearSessionError = useSessionStore((state) => state.clearError);
  const [errors, setErrors] = useState<FieldErrors>({});

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    clearSessionError();

    const formData = new FormData(event.currentTarget);
    const parsed = loginFormSchema.safeParse({
      email: String(formData.get("email") ?? ""),
      password: String(formData.get("password") ?? "")
    });

    if (!parsed.success) {
      const fieldErrors = parsed.error.flatten().fieldErrors;
      setErrors({
        email: fieldErrors.email?.[0],
        password: fieldErrors.password?.[0]
      });
      return;
    }

    setErrors({});
    const didLogin = await login(parsed.data);

    if (didLogin) {
      // O papel so existe depois do login, por isso a leitura e feita aqui e
      // nao no topo do componente.
      router.push(homeRouteForRole(useSessionStore.getState().role));
    }
  }

  return (
    <form className="grid gap-5" onSubmit={handleSubmit}>
      <div className="grid gap-2">
        <label className="text-sm font-medium text-slate-800" htmlFor="email">
          E-mail
        </label>
        <input
          className="h-11 rounded-md border border-slate-300 bg-white px-3 text-base text-slate-950 outline-none transition focus:border-emerald-700 focus:ring-2 focus:ring-emerald-100"
          defaultValue={loginDefaultValues.email}
          id="email"
          name="email"
          type="email"
        />
        {errors.email ? (
          <p className="text-sm font-medium text-red-700">{errors.email}</p>
        ) : null}
      </div>

      <div className="grid gap-2">
        <label className="text-sm font-medium text-slate-800" htmlFor="password">
          Senha
        </label>
        <input
          className="h-11 rounded-md border border-slate-300 bg-white px-3 text-base text-slate-950 outline-none transition focus:border-emerald-700 focus:ring-2 focus:ring-emerald-100"
          defaultValue={loginDefaultValues.password}
          id="password"
          name="password"
          type="password"
        />
        {errors.password ? (
          <p className="text-sm font-medium text-red-700">{errors.password}</p>
        ) : null}
      </div>

      {apiError ? (
        <p className="text-sm font-medium text-red-700" role="alert">
          {apiError}
        </p>
      ) : null}

      <button
        className="h-11 rounded-md bg-emerald-700 px-4 text-sm font-semibold text-white transition hover:bg-emerald-800 focus:outline-none focus:ring-2 focus:ring-emerald-700 focus:ring-offset-2 disabled:cursor-not-allowed disabled:bg-slate-400"
        disabled={isLoading}
        type="submit"
      >
        {isLoading ? "Entrando..." : "Entrar"}
      </button>
    </form>
  );
}

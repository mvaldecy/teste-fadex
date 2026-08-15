"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { FormEvent, useState } from "react";
import { getPublicEnv } from "@/src/config/public-env";
import { demoCredentials } from "@/src/features/auth/demo-credentials";
import { loginFormSchema } from "@/src/schemas/auth.schema";
import {
  changePasswordRouteWithRedirect,
  homeRouteForRole,
  redirectParamName,
  sanitizeRedirect
} from "@/src/routes/routes";
import { useSessionStore } from "@/src/stores/session.store";

type FieldErrors = Partial<Record<"email" | "password", string>>;

export function LoginForm() {
  const router = useRouter();
  // Destino pretendido, guardado pela guarda de rota quando ela mandou o
  // usuário para ca. Sem isto, quem abre o link de um chamado pelo e-mail
  // autentica e cai no dashboard, perdendo o chamado que queria ver.
  const searchParams = useSearchParams();
  const redirectTo = sanitizeRedirect(searchParams.get(redirectParamName));
  const login = useSessionStore((state) => state.login);
  const apiError = useSessionStore((state) => state.error);
  const isLoading = useSessionStore((state) => state.isLoading);
  const clearSessionError = useSessionStore((state) => state.clearError);
  const [errors, setErrors] = useState<FieldErrors>({});
  // Campos controlados porque os atalhos de demonstracao precisam escrever
  // neles. Comecam vazios: preencher a senha do administrador por padrao
  // deixava a credencial na tela mesmo onde o seed não roda.
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const { isDemoLogin } = getPublicEnv();

  function fillCredentials(nextEmail: string, nextPassword: string) {
    clearSessionError();
    setErrors({});
    setEmail(nextEmail);
    setPassword(nextPassword);
  }

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

    if (!didLogin) {
      return;
    }

    // O papel e o `mustChangePassword` so existem depois do login, por isso a
    // leitura e feita aqui e não no topo do componente.
    const { mustChangePassword, role } = useSessionStore.getState();

    if (mustChangePassword) {
      // A senha provisoria não abre o resto do sistema: o token do login e
      // limitado ao endpoint de troca. O destino pretendido segue junto e e
      // aplicado depois da troca.
      router.replace(changePasswordRouteWithRedirect(redirectTo));
      return;
    }

    router.replace(redirectTo ?? homeRouteForRole(role));
  }

  return (
    <form className="grid gap-5" onSubmit={handleSubmit}>
      <div className="grid gap-2">
        <label className="text-sm font-medium text-slate-800" htmlFor="email">
          E-mail
        </label>
        <input
          className="h-11 rounded-md border border-slate-300 bg-white px-3 text-base text-slate-950 outline-none transition focus:border-emerald-700 focus:ring-2 focus:ring-emerald-100"
          id="email"
          name="email"
          type="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
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
          id="password"
          name="password"
          type="password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
        />
        {errors.password ? (
          <p className="text-sm font-medium text-red-700">{errors.password}</p>
        ) : null}
      </div>

      {isDemoLogin ? (
        <div className="grid gap-3 rounded-md border border-dashed border-slate-300 bg-slate-50 p-3">
          <p className="text-xs font-medium text-slate-600">
            Contas criadas pelo seed — clique para preencher o formulário
          </p>

          {(["ADMIN", "SOLICITANTE"] as const).map((role) => (
            <div className="grid gap-1.5" key={role}>
              <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-400">
                {role === "ADMIN" ? "Administradores" : "Solicitantes"}
              </p>
              {demoCredentials
                .filter((credential) => credential.role === role)
                .map((credential) => (
                  <button
                    className="rounded-md border border-slate-300 bg-white px-3 py-2 text-left transition hover:border-emerald-700 hover:bg-emerald-50"
                    key={credential.email}
                    type="button"
                    onClick={() =>
                      fillCredentials(credential.email, credential.password)
                    }
                  >
                    <span className="flex flex-wrap items-baseline gap-x-2">
                      <span className="text-sm font-semibold text-slate-900">
                        {credential.label}
                      </span>
                      <span className="text-xs text-slate-500">
                        {credential.email}
                      </span>
                    </span>
                    <span className="block text-xs leading-4 text-slate-500">
                      {credential.description}
                    </span>
                  </button>
                ))}
            </div>
          ))}
        </div>
      ) : null}

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

"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { FormEvent, useEffect, useState } from "react";
import { toast } from "sonner";
import { changePasswordFormSchema } from "@/src/schemas/auth.schema";
import {
  homeRouteForRole,
  redirectParamName,
  routes,
  sanitizeRedirect
} from "@/src/routes/routes";
import { useSessionStore } from "@/src/stores/session.store";

type FieldName = "currentPassword" | "newPassword" | "confirmPassword";
type FieldErrors = Partial<Record<FieldName, string>>;

const fields: { name: FieldName; label: string }[] = [
  { name: "currentPassword", label: "Senha provisoria" },
  { name: "newPassword", label: "Nova senha" },
  { name: "confirmPassword", label: "Confirme a nova senha" }
];

/**
 * Troca de senha obrigatoria do primeiro acesso.
 *
 * Fica fora do grupo de rotas do shell de proposito: o token do login com
 * `mustChangePassword` so abre `POST /auth/change-password`, e montar o shell
 * aqui dispararia indicadores e stream de notificacoes que responderiam `403`.
 */
export function ChangePasswordForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const redirectTo = sanitizeRedirect(searchParams.get(redirectParamName));
  const changePassword = useSessionStore((state) => state.changePassword);
  const clearSessionError = useSessionStore((state) => state.clearError);
  const apiError = useSessionStore((state) => state.error);
  const isLoading = useSessionStore((state) => state.isLoading);
  const isHydrated = useSessionStore((state) => state.isHydrated);
  const isAuthenticated = useSessionStore((state) => state.isAuthenticated);
  const [errors, setErrors] = useState<FieldErrors>({});

  // Sem sessao nao ha token limitado para gastar: quem chega aqui direto pela
  // URL volta para o login.
  useEffect(() => {
    if (isHydrated && !isAuthenticated) {
      router.replace(routes.login);
    }
  }, [isAuthenticated, isHydrated, router]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    clearSessionError();

    const formData = new FormData(event.currentTarget);
    const parsed = changePasswordFormSchema.safeParse({
      currentPassword: String(formData.get("currentPassword") ?? ""),
      newPassword: String(formData.get("newPassword") ?? ""),
      confirmPassword: String(formData.get("confirmPassword") ?? "")
    });

    if (!parsed.success) {
      const fieldErrors = parsed.error.flatten().fieldErrors;
      setErrors({
        currentPassword: fieldErrors.currentPassword?.[0],
        newPassword: fieldErrors.newPassword?.[0],
        confirmPassword: fieldErrors.confirmPassword?.[0]
      });
      return;
    }

    setErrors({});
    const didChange = await changePassword(parsed.data);

    if (!didChange) {
      return;
    }

    toast.success("Senha alterada.");
    router.replace(
      redirectTo ?? homeRouteForRole(useSessionStore.getState().role)
    );
  }

  return (
    <form className="grid gap-5" onSubmit={handleSubmit}>
      {fields.map((field) => (
        <div className="grid gap-2" key={field.name}>
          <label
            className="text-sm font-medium text-slate-800"
            htmlFor={field.name}
          >
            {field.label}
          </label>
          <input
            autoComplete={
              field.name === "currentPassword"
                ? "current-password"
                : "new-password"
            }
            className="h-11 rounded-md border border-slate-300 bg-white px-3 text-base text-slate-950 outline-none transition focus:border-emerald-700 focus:ring-2 focus:ring-emerald-100"
            id={field.name}
            name={field.name}
            type="password"
          />
          {errors[field.name] ? (
            <p className="text-sm font-medium text-red-700">
              {errors[field.name]}
            </p>
          ) : null}
        </div>
      ))}

      <p className="text-xs leading-5 text-slate-500">
        A nova senha precisa ter entre 8 e 72 caracteres.
      </p>

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
        {isLoading ? "Salvando..." : "Salvar nova senha"}
      </button>
    </form>
  );
}

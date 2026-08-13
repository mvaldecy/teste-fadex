"use client";

import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import { loginFormSchema } from "@/src/schemas/auth.schema";
import { useSessionStore } from "@/src/stores/session.store";
import { routes } from "@/src/routes/routes";

type FieldErrors = Partial<Record<"email" | "password", string>>;

const mockCredentials = {
  email: "admin@fadex.org.br",
  password: "123456"
};

export function LoginForm() {
  const router = useRouter();
  const simulateLogin = useSessionStore((state) => state.simulateLogin);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [isSubmitting, setIsSubmitting] = useState(false);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsSubmitting(true);

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
      setIsSubmitting(false);
      return;
    }

    setErrors({});
    simulateLogin(parsed.data);
    router.push(routes.home);
  }

  return (
    <form className="grid gap-5" onSubmit={handleSubmit}>
      <div className="grid gap-2">
        <label className="text-sm font-medium text-slate-800" htmlFor="email">
          E-mail
        </label>
        <input
          className="h-11 rounded-md border border-slate-300 bg-white px-3 text-base text-slate-950 outline-none transition focus:border-emerald-700 focus:ring-2 focus:ring-emerald-100"
          defaultValue={mockCredentials.email}
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
          defaultValue={mockCredentials.password}
          id="password"
          name="password"
          type="password"
        />
        {errors.password ? (
          <p className="text-sm font-medium text-red-700">{errors.password}</p>
        ) : null}
      </div>

      <button
        className="h-11 rounded-md bg-emerald-700 px-4 text-sm font-semibold text-white transition hover:bg-emerald-800 focus:outline-none focus:ring-2 focus:ring-emerald-700 focus:ring-offset-2 disabled:cursor-not-allowed disabled:bg-slate-400"
        disabled={isSubmitting}
        type="submit"
      >
        Entrar
      </button>
    </form>
  );
}

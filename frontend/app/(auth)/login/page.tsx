import { LoginForm } from "@/src/features/auth/login-form";

export default function LoginPage() {
  return (
    <main className="min-h-screen bg-slate-50 px-6 py-10 text-slate-950">
      <section className="mx-auto grid min-h-[calc(100vh-5rem)] max-w-5xl items-center gap-10 lg:grid-cols-[1fr_420px]">
        <div>
          <p className="text-sm font-semibold uppercase tracking-[0.12em] text-emerald-700">
            Central interna
          </p>
          <h1 className="mt-3 text-4xl font-semibold tracking-normal sm:text-5xl">
            Fadex Helpdesk
          </h1>
          <p className="mt-4 max-w-xl text-base leading-7 text-slate-600">
            Acesso mockado para preparar a integracao com a API de autenticacao.
          </p>
        </div>

        <section className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
          <div className="mb-6">
            <h2 className="text-xl font-semibold text-slate-950">Login</h2>
            <p className="mt-2 text-sm leading-6 text-slate-600">
              Use os dados preenchidos para entrar na home inicial.
            </p>
          </div>
          <LoginForm />
        </section>
      </section>
    </main>
  );
}

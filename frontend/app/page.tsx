import Link from "next/link";

export default function Page() {
  return (
    <main className="min-h-screen bg-slate-50 px-6 py-10 text-slate-950">
      <section className="mx-auto flex min-h-[calc(100vh-5rem)] max-w-5xl flex-col justify-center gap-8">
        <div className="max-w-2xl">
          <p className="text-sm font-semibold uppercase tracking-[0.12em] text-emerald-700">
            Central interna
          </p>
          <h1 className="mt-3 text-4xl font-semibold tracking-normal sm:text-5xl">
            Fadex Helpdesk
          </h1>
          <p className="mt-4 max-w-xl text-base leading-7 text-slate-600">
            Acompanhe solicitacoes, priorize atendimentos e prepare a triagem
            inteligente em uma interface direta para operacao diaria.
          </p>
        </div>
        <Link
          href="/login"
          className="inline-flex w-fit items-center justify-center rounded-md bg-emerald-700 px-5 py-3 text-sm font-semibold text-white transition hover:bg-emerald-800 focus:outline-none focus:ring-2 focus:ring-emerald-700 focus:ring-offset-2"
        >
          Acessar central
        </Link>
      </section>
    </main>
  );
}

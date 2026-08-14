import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle
} from "@/src/components/ui/card";

export default function HomePage() {
  return (
    <div className="mx-auto grid max-w-7xl gap-6">
      <header>
        <p className="text-sm font-semibold uppercase tracking-[0.12em] text-emerald-700">
          Home
        </p>
        <h1 className="mt-2 text-3xl font-semibold tracking-normal">
          Dashboard
        </h1>
        <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-600">
          Area reservada para indicadores e acompanhamento operacional.
        </p>
      </header>

      <section className="grid gap-4 md:grid-cols-3">
        <Card>
          <CardHeader>
            <CardTitle>Chamados abertos</CardTitle>
            <CardDescription>Indicador em preparacao.</CardDescription>
          </CardHeader>
          <CardContent>
            <p className="text-3xl font-semibold text-slate-400">--</p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Prioridade alta</CardTitle>
            <CardDescription>Indicador em preparacao.</CardDescription>
          </CardHeader>
          <CardContent>
            <p className="text-3xl font-semibold text-slate-400">--</p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Atualizados hoje</CardTitle>
            <CardDescription>Indicador em preparacao.</CardDescription>
          </CardHeader>
          <CardContent>
            <p className="text-3xl font-semibold text-slate-400">--</p>
          </CardContent>
        </Card>
      </section>
    </div>
  );
}

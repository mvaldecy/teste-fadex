import {
  Card,
  CardContent,
  CardHeader,
  CardTitle
} from "@/src/components/ui/card";

type IndicatorBreakdownProps = {
  // Os mapas do contrato omitem grupos sem ocorrencia, entao o valor por chave
  // e opcional — `Record<string, number>` mentiria sobre o que chega.
  data?: Partial<Record<string, number>>;
  labels?: Map<string, string>;
  title: string;
};

/**
 * Barras proporcionais em CSS puro. Nao vale uma biblioteca de grafico para
 * exibir um punhado de contagens.
 */
export function IndicatorBreakdown({
  data,
  labels,
  title
}: IndicatorBreakdownProps) {
  const entries = Object.entries(data ?? {}).map(
    ([key, value]) => [key, value ?? 0] as const
  );
  const total = entries.reduce((sum, [, value]) => sum + value, 0);

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{title}</CardTitle>
      </CardHeader>
      <CardContent>
        {entries.length === 0 ? (
          <p className="text-sm text-slate-500">Sem dados disponiveis.</p>
        ) : (
          <ul className="grid gap-3">
            {entries.map(([key, value]) => {
              const percent = total > 0 ? Math.round((value / total) * 100) : 0;

              return (
                <li key={key}>
                  <div className="flex items-baseline justify-between gap-3 text-sm">
                    <span className="truncate text-slate-700">
                      {labels?.get(key) ?? key}
                    </span>
                    <span className="shrink-0 tabular-nums font-medium text-slate-950">
                      {value}
                      <span className="ml-1 text-xs font-normal text-slate-500">
                        ({percent}%)
                      </span>
                    </span>
                  </div>
                  <div className="mt-1.5 h-2 overflow-hidden rounded-full bg-slate-100">
                    <div
                      className="h-full rounded-full bg-emerald-700"
                      style={{ width: `${percent}%` }}
                    />
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}

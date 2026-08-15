import {
  Card,
  CardContent,
  CardHeader,
  CardTitle
} from "@/src/components/ui/card";
import type { IndicatorSlaSlice } from "@/src/types/api";

type IndicatorSlaCardProps = {
  byPriority?: Partial<Record<string, IndicatorSlaSlice>>;
  labels?: Map<string, string>;
};

/**
 * SLA por prioridade.
 *
 * O contrato publica a fatia de **todas** as prioridades e o painel so
 * mostrava a de ALTA. Prioridade sem chamado avaliado não aparece: o mapa a
 * omite, e inventar 100% para amostra vazia seria mentir para o gestor.
 */
export function IndicatorSlaCard({ byPriority, labels }: IndicatorSlaCardProps) {
  const entries = Object.entries(byPriority ?? {}).filter(
    (entry): entry is [string, IndicatorSlaSlice] => Boolean(entry[1])
  );

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">SLA por prioridade</CardTitle>
      </CardHeader>
      <CardContent>
        {entries.length === 0 ? (
          <p className="text-sm text-slate-500">
            Nenhum chamado avaliado até agora.
          </p>
        ) : (
          <ul className="grid gap-3">
            {entries.map(([key, slice]) => {
              const percent = slice.percentage;
              const hasPercent =
                typeof percent === "number" && Number.isFinite(percent);

              return (
                <li key={key}>
                  <div className="flex items-baseline justify-between gap-3 text-sm">
                    <span className="text-slate-700">
                      {labels?.get(key) ?? key}
                    </span>
                    <span className="shrink-0 tabular-nums font-medium text-slate-950">
                      {hasPercent ? `${percent.toFixed(1)}%` : "--"}
                      <span className="ml-1 text-xs font-normal text-slate-500">
                        ({slice.withinTarget} de {slice.evaluated})
                      </span>
                    </span>
                  </div>
                  <div className="mt-1.5 h-2 overflow-hidden rounded-full bg-slate-100">
                    <div
                      className="h-full rounded-full bg-emerald-700"
                      style={{ width: `${hasPercent ? percent : 0}%` }}
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

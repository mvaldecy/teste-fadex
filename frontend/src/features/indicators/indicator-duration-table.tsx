import {
  Card,
  CardContent,
  CardHeader,
  CardTitle
} from "@/src/components/ui/card";
import type { IndicatorDurationStats } from "@/src/types/api";

type IndicatorDurationTableProps = {
  // O contrato omite o grupo sem ocorrencia, entao o valor por chave e
  // opcional e o cartao pode chegar vazio.
  groups?: Partial<Record<string, IndicatorDurationStats>>;
  labels?: Map<string, string>;
  title: string;
};

function formatHours(value?: number | null) {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    return "--";
  }

  if (value < 1) {
    return `${Math.round(value * 60)} min`;
  }

  return `${value.toFixed(1)} h`;
}

/**
 * Quebra de uma estatistica de duracao por prioridade ou categoria.
 *
 * O `GET /indicators` ja publicava estes mapas e o painel so exibia o total.
 * Media e mediana continuam lado a lado, e o tamanho da amostra fica visivel:
 * mediana de um chamado nao e mediana.
 */
export function IndicatorDurationTable({
  groups,
  labels,
  title
}: IndicatorDurationTableProps) {
  const entries = Object.entries(groups ?? {}).filter(
    (entry): entry is [string, IndicatorDurationStats] => Boolean(entry[1])
  );

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{title}</CardTitle>
      </CardHeader>
      <CardContent>
        {entries.length === 0 ? (
          <p className="text-sm text-slate-500">Sem dados disponiveis.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-xs uppercase tracking-[0.06em] text-slate-500">
                  <th className="pb-2 text-left font-medium">Grupo</th>
                  <th className="pb-2 text-right font-medium">Media</th>
                  <th className="pb-2 text-right font-medium">Mediana</th>
                  <th className="pb-2 text-right font-medium">p90</th>
                  <th className="pb-2 text-right font-medium">Amostra</th>
                </tr>
              </thead>
              <tbody>
                {entries.map(([key, stats]) => (
                  <tr className="border-t border-slate-100" key={key}>
                    <td className="py-2 text-slate-700">
                      {labels?.get(key) ?? key}
                    </td>
                    <td className="py-2 text-right tabular-nums text-slate-950">
                      {formatHours(stats.averageHours)}
                    </td>
                    <td className="py-2 text-right tabular-nums text-slate-950">
                      {formatHours(stats.medianHours)}
                    </td>
                    <td className="py-2 text-right tabular-nums text-slate-950">
                      {formatHours(stats.p90Hours)}
                    </td>
                    <td className="py-2 text-right tabular-nums text-slate-500">
                      {stats.sampleSize}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

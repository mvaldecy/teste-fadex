import {
  Card,
  CardContent,
  CardHeader,
  CardTitle
} from "@/src/components/ui/card";
import type { IndicatorDurationStats } from "@/src/types/api";

type IndicatorDurationCardProps = {
  duration?: IndicatorDurationStats;
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
 * Media e mediana sempre lado a lado. Com o volume de dados do projeto, media
 * isolada e enganosa — a nota esta no documento de frentes.
 *
 * O `sampleSize` aparece junto porque o contrato zera as horas quando a
 * amostra e vazia: sem o tamanho da amostra, "--" e "0 h" seriam
 * indistinguiveis de estatistica confiavel.
 */
export function IndicatorDurationCard({
  duration,
  title
}: IndicatorDurationCardProps) {
  const sampleSize = duration?.sampleSize ?? 0;

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{title}</CardTitle>
      </CardHeader>
      <CardContent>
        <dl className="grid grid-cols-3 gap-3 text-center">
          <div>
            <dt className="text-xs uppercase tracking-[0.08em] text-slate-500">
              Media
            </dt>
            <dd className="mt-1 text-lg font-semibold tabular-nums text-slate-950">
              {formatHours(duration?.averageHours)}
            </dd>
          </div>
          <div>
            <dt className="text-xs uppercase tracking-[0.08em] text-slate-500">
              Mediana
            </dt>
            <dd className="mt-1 text-lg font-semibold tabular-nums text-slate-950">
              {formatHours(duration?.medianHours)}
            </dd>
          </div>
          <div>
            <dt className="text-xs uppercase tracking-[0.08em] text-slate-500">
              p90
            </dt>
            <dd className="mt-1 text-lg font-semibold tabular-nums text-slate-950">
              {formatHours(duration?.p90Hours)}
            </dd>
          </div>
        </dl>

        <p className="mt-3 text-xs text-slate-500">
          {sampleSize === 1
            ? "1 chamado na amostra"
            : `${sampleSize} chamados na amostra`}
        </p>
      </CardContent>
    </Card>
  );
}

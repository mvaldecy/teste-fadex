import {
  Card,
  CardContent,
  CardHeader,
  CardTitle
} from "@/src/components/ui/card";
import type { IndicatorDuration } from "@/src/types/api";

type IndicatorDurationCardProps = {
  duration?: IndicatorDuration;
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
 */
export function IndicatorDurationCard({
  duration,
  title
}: IndicatorDurationCardProps) {
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
              {formatHours(duration?.media)}
            </dd>
          </div>
          <div>
            <dt className="text-xs uppercase tracking-[0.08em] text-slate-500">
              Mediana
            </dt>
            <dd className="mt-1 text-lg font-semibold tabular-nums text-slate-950">
              {formatHours(duration?.mediana)}
            </dd>
          </div>
          <div>
            <dt className="text-xs uppercase tracking-[0.08em] text-slate-500">
              p90
            </dt>
            <dd className="mt-1 text-lg font-semibold tabular-nums text-slate-950">
              {formatHours(duration?.p90)}
            </dd>
          </div>
        </dl>
      </CardContent>
    </Card>
  );
}

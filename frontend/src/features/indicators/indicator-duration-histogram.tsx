import { cn } from "@/src/lib/utils";
import type { IndicatorHistogramBin } from "@/src/types/api";

type IndicatorDurationHistogramProps = {
  bins: IndicatorHistogramBin[];
  medianHours: number | null;
  p90Hours: number | null;
};

function formatBinLabel(bin: IndicatorHistogramBin) {
  if (bin.toHours === null) {
    return `${bin.fromHours}h+`;
  }

  return `${bin.fromHours}-${bin.toHours}h`;
}

/**
 * Posicao horizontal de um valor em horas, em porcentagem da largura.
 *
 * As faixas nao tem largura igual em horas (4h, depois 16h, depois 48h), mas
 * sao desenhadas com largura igual — e o formato usual de histograma de faixas
 * fixas. Por isso a conversao e feita em duas etapas: acha a faixa e
 * interpola **dentro** dela. Marcar a mediana proporcionalmente ao eixo de
 * horas a colocaria no lugar errado em relacao as barras.
 */
function toOffsetPercent(value: number, bins: IndicatorHistogramBin[]) {
  const binWidth = 100 / bins.length;

  for (const [index, bin] of bins.entries()) {
    const isLast = index === bins.length - 1;
    const upper = bin.toHours;

    if (upper === null || value < upper || (isLast && value >= bin.fromHours)) {
      const span = upper === null ? null : upper - bin.fromHours;
      const ratio =
        span && span > 0
          ? Math.min(Math.max((value - bin.fromHours) / span, 0), 1)
          : 0.5;

      return (index + ratio) * binWidth;
    }
  }

  return 100;
}

function formatHours(value: number) {
  return value < 1 ? `${Math.round(value * 60)} min` : `${value.toFixed(1)} h`;
}

/**
 * Distribuicao das duracoes por faixa.
 *
 * Barras e nao curva: tempo de atendimento e assimetrico — parede em zero e
 * cauda longa a direita. As barras mostram a assimetria sozinhas, e a area a
 * direita do p90 aparece pequena mas presente, que e o que da sentido ao
 * numero.
 */
export function IndicatorDurationHistogram({
  bins,
  medianHours,
  p90Hours
}: IndicatorDurationHistogramProps) {
  const maxCount = Math.max(...bins.map((bin) => bin.count), 1);

  type Marker = { label: string; value: number | null; tone: string };

  const markers: Marker[] = [
    { label: "Mediana", value: medianHours, tone: "bg-slate-700" },
    { label: "p90", value: p90Hours, tone: "bg-amber-600" }
  ].filter(
    (marker) =>
      typeof marker.value === "number" && Number.isFinite(marker.value)
  );

  return (
    <figure className="mt-6 grid gap-2">
      {/*
        O espaco no topo e reservado para os rotulos dos marcadores. Eles ficam
        em alturas diferentes porque mediana e p90 podem cair quase no mesmo
        ponto — no tempo de atribuicao os dois dao 1h — e, empilhados na mesma
        linha, um texto escrevia por cima do outro.
      */}
      <div className="relative mt-8 h-28">
        <div className="flex h-full items-end gap-1">
          {bins.map((bin) => (
            <div
              className="flex h-full flex-1 items-end"
              key={`${bin.fromHours}-${bin.toHours ?? "mais"}`}
              title={`${formatBinLabel(bin)}: ${bin.count}`}
            >
              <div
                className={cn(
                  "w-full rounded-t bg-emerald-600/80",
                  bin.count === 0 && "bg-slate-100"
                )}
                style={{
                  height: `${Math.max((bin.count / maxCount) * 100, bin.count > 0 ? 4 : 2)}%`
                }}
              />
            </div>
          ))}
        </div>

        {markers.map((marker, index) => {
          const offset = toOffsetPercent(marker.value ?? 0, bins);
          // Perto da borda direita o rotulo sairia do grafico: ancora pela
          // direita da linha em vez de pela esquerda.
          const isNearRightEdge = offset > 70;

          return (
            <div
              className="pointer-events-none absolute inset-y-0"
              key={marker.label}
              style={{ left: `${offset}%` }}
            >
              <div className={cn("h-full w-px", marker.tone)} />
              <span
                className={cn(
                  "absolute whitespace-nowrap text-[10px] font-medium text-slate-600",
                  index === 0 ? "-top-8" : "-top-4",
                  isNearRightEdge ? "right-1" : "left-1"
                )}
              >
                {marker.label} {formatHours(marker.value ?? 0)}
              </span>
            </div>
          );
        })}
      </div>

      <div className="flex gap-1">
        {bins.map((bin) => (
          <span
            className="flex-1 text-center text-[10px] text-slate-500"
            key={`rotulo-${bin.fromHours}-${bin.toHours ?? "mais"}`}
          >
            {formatBinLabel(bin)}
          </span>
        ))}
      </div>

      <figcaption className="text-xs text-slate-500">
        Chamados por faixa de duracao, com mediana e p90 marcados.
      </figcaption>
    </figure>
  );
}

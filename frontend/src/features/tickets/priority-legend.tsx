import { cn } from "@/src/lib/utils";
import { resolvePriorityTone } from "./priority-tone";

type PriorityLegendProps = {
  /**
   * Rotulos vindos do backend, na ordem publicada em `GET /choices`. Sem eles
   * a legenda não renderiza — inventar rotulo de enum no frontend não e uma
   * opção.
   */
  priorities: Map<string, string> | undefined;
};

export function PriorityLegend({ priorities }: PriorityLegendProps) {
  if (!priorities || priorities.size === 0) {
    return null;
  }

  return (
    <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
      <span className="text-xs font-medium uppercase tracking-[0.06em] text-slate-500">
        Prioridade
      </span>

      {[...priorities.entries()].map(([value, label]) => (
        <span
          className="inline-flex items-center gap-2 text-xs text-slate-600"
          key={value}
        >
          <span
            aria-hidden="true"
            className={cn(
              "h-2.5 w-2.5 rounded-full",
              resolvePriorityTone(value).dot
            )}
          />
          {label}
        </span>
      ))}
    </div>
  );
}

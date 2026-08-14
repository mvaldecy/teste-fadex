import { cn } from "@/src/lib/utils";

type IndicatorCardProps = {
  label: string;
  note?: string;
  tone?: "default" | "alert";
  value?: number | null;
  formatValue?: (value: number) => string;
};

export function IndicatorCard({
  label,
  note,
  tone = "default",
  value,
  formatValue
}: IndicatorCardProps) {
  const hasValue = typeof value === "number" && Number.isFinite(value);
  const isAlert = tone === "alert" && hasValue && value > 0;

  return (
    <div
      className={cn(
        "rounded-lg border bg-white p-4 shadow-sm",
        isAlert ? "border-amber-300 bg-amber-50" : "border-slate-200"
      )}
    >
      <p className="text-xs font-medium uppercase tracking-[0.08em] text-slate-500">
        {label}
      </p>
      <p
        className={cn(
          "mt-2 text-3xl font-semibold tabular-nums",
          hasValue ? "text-slate-950" : "text-slate-300",
          isAlert && "text-amber-800"
        )}
      >
        {hasValue
          ? formatValue
            ? formatValue(value)
            : String(value)
          : "--"}
      </p>
      {note ? <p className="mt-1 text-xs text-slate-500">{note}</p> : null}
    </div>
  );
}

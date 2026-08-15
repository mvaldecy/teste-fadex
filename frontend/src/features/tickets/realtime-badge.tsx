import { cn } from "@/src/lib/utils";

type RealtimeBadgeProps = {
  className?: string;
  updatedAt: Date | null;
};

function formatTime(value: Date) {
  return new Intl.DateTimeFormat("pt-BR", {
    timeStyle: "medium"
  }).format(value);
}

/**
 * Selo de tempo real.
 *
 * Serve para responder a pergunta que a tela nao respondia: "isso atualiza
 * sozinho?". Enquanto nada chega, ele diz que a tela esta ouvindo o stream;
 * quando um evento chega, mostra o horario da ultima atualizacao.
 */
export function RealtimeBadge({ className, updatedAt }: RealtimeBadgeProps) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-medium transition-colors",
        updatedAt
          ? "border-emerald-300 bg-emerald-50 text-emerald-800"
          : "border-slate-200 bg-white text-slate-500",
        className
      )}
    >
      <span
        aria-hidden="true"
        className={cn(
          "h-2 w-2 rounded-full",
          updatedAt ? "bg-emerald-600" : "bg-slate-400"
        )}
      />
      {updatedAt
        ? `Atualizado em tempo real as ${formatTime(updatedAt)}`
        : "Ouvindo atualizacoes em tempo real"}
    </span>
  );
}

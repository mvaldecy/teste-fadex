import Link from "next/link";
import { routes } from "@/src/routes/routes";
import { cn } from "@/src/lib/utils";
import type { NearestTicketDto } from "@/src/types/api";

type TicketNearestListProps = {
  nearestTickets: NearestTicketDto[];
};

/**
 * Ranking dos chamados mais próximos, **sem filtro de limiar**.
 *
 * Existe porque a lista de vínculos sozinha não distingue "não há duplicata"
 * de "o modelo não achou". Com o ranking à vista, quem lê decide em um segundo
 * o que o modelo errou — e a barra torna a distância entre o primeiro e o
 * limiar visível sem precisar interpretar o número.
 */
export function TicketNearestList({ nearestTickets }: TicketNearestListProps) {
  if (nearestTickets.length === 0) {
    return null;
  }

  return (
    <div className="grid gap-3">
      <div>
        <h3 className="text-sm font-semibold text-slate-900">
          Chamados mais próximos
        </h3>
        <p className="mt-1 text-xs text-slate-500">
          Ordenados por similaridade, sem corte. Marcados em verde os que
          passaram do limiar e viraram vínculo.
        </p>
      </div>

      <ol className="grid gap-2">
        {nearestTickets.map((nearest, index) => {
          const percentual = Math.round(nearest.similarity * 100);

          return (
            <li key={nearest.id}>
              <Link
                className={cn(
                  "flex items-center gap-3 rounded-md border px-3 py-2 transition-colors",
                  nearest.linked
                    ? "border-emerald-300 bg-emerald-50 hover:border-emerald-500"
                    : "border-slate-200 hover:border-slate-400"
                )}
                href={routes.ticketDetails(nearest.id)}
              >
                <span className="w-4 shrink-0 text-xs tabular-nums text-slate-400">
                  {index + 1}
                </span>

                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm text-slate-900">
                    {nearest.title}
                  </span>

                  {/* A barra usa a escala inteira 0–100%: encolher para o
                      intervalo observado exageraria diferenças de dois pontos. */}
                  <span className="mt-1 block h-1 w-full overflow-hidden rounded-full bg-slate-100">
                    <span
                      className={cn(
                        "block h-full rounded-full",
                        nearest.linked ? "bg-emerald-600" : "bg-slate-400"
                      )}
                      style={{ width: `${percentual}%` }}
                    />
                  </span>
                </span>

                <span
                  className={cn(
                    "shrink-0 text-xs font-medium tabular-nums",
                    nearest.linked ? "text-emerald-700" : "text-slate-500"
                  )}
                >
                  {percentual}%
                </span>
              </Link>
            </li>
          );
        })}
      </ol>
    </div>
  );
}

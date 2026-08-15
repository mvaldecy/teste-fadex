import { CopyCheck } from "lucide-react";
import Link from "next/link";
import { routes } from "@/src/routes/routes";
import { cn } from "@/src/lib/utils";

type SimilarBadgeProps = {
  className?: string;
  count?: number | null;
  ticketId: string;
};

/**
 * Selo de chamado com semelhantes, na listagem.
 *
 * Leva direto para a aba de semelhantes do detalhe, e nao para o resumo: quem
 * clica aqui ja sabe o que quer ver. Sem este selo a deteccao de duplicados so
 * aparecia para quem abrisse o chamado certo por acaso — a funcionalidade
 * existia e era invisivel.
 */
export function SimilarBadge({ className, count, ticketId }: SimilarBadgeProps) {
  if (!count) {
    return null;
  }

  return (
    <Link
      className={cn(
        "mt-1 inline-flex items-center gap-1 rounded-full border border-amber-300 bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-800 transition-colors hover:bg-amber-100",
        className
      )}
      href={`${routes.ticketDetails(ticketId)}?aba=semelhantes`}
      title="Ver os chamados semelhantes"
    >
      <CopyCheck className="h-3 w-3 shrink-0" />
      {count === 1 ? "1 semelhante" : `${count} semelhantes`}
    </Link>
  );
}

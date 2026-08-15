"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/src/components/ui/button";

type PaginationBarProps = {
  isDisabled?: boolean;
  page: number;
  totalElements: number;
  totalPages: number;
  onPageChange: (page: number) => void;
};

/**
 * Barra de paginacao das listagens. O backend devolve `Page` do Spring, entao
 * `page` e zero-based aqui e so vira 1-based na exibicao.
 */
export function PaginationBar({
  isDisabled = false,
  page,
  totalElements,
  totalPages,
  onPageChange
}: PaginationBarProps) {
  if (totalPages <= 1) {
    return null;
  }

  const isFirst = page <= 0;
  const isLast = page >= totalPages - 1;

  return (
    <div className="flex flex-wrap items-center justify-between gap-3 pt-4">
      <p className="text-xs text-slate-500">
        Página {page + 1} de {totalPages} | {totalElements} registros
      </p>

      <div className="flex items-center gap-2">
        <Button
          disabled={isDisabled || isFirst}
          size="sm"
          type="button"
          variant="outline"
          onClick={() => onPageChange(page - 1)}
        >
          <ChevronLeft className="h-4 w-4" />
          Anterior
        </Button>

        <Button
          disabled={isDisabled || isLast}
          size="sm"
          type="button"
          variant="outline"
          onClick={() => onPageChange(page + 1)}
        >
          Próxima
          <ChevronRight className="h-4 w-4" />
        </Button>
      </div>
    </div>
  );
}

"use client";

import { Loader2, Search } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Button } from "@/src/components/ui/button";
import { Input } from "@/src/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/src/components/ui/select";
import { useSessionStore } from "@/src/stores/session.store";
import type {
  ChoicesResponse,
  TicketCategoryValue,
  TicketFilters,
  TicketPriorityValue,
  TicketStatusValue
} from "@/src/types/api";

const allValue = "ALL";

/**
 * Filtro por atribuicao. As tres opcoes sao excludentes de proposito: "sem
 * responsavel" e "meus chamados" descrevem conjuntos que nao se cruzam, entao
 * um unico select diz mais do que duas caixas que podem ser marcadas juntas
 * para nao devolver nada.
 */
const assignmentAll = "ALL";
const assignmentUnassigned = "SEM_RESPONSAVEL";
const assignmentMine = "MEUS";

/**
 * Espera antes de buscar pelo texto digitado.
 *
 * Sem ela seria uma requisicao por tecla. A protecao contra resposta atrasada
 * fica no `useTicketList`, que descarta resposta de busca antiga; aqui o
 * objetivo e nao gerar a requisicao inutil.
 */
const searchDebounceMs = 400;

type TicketFilterBarProps = {
  choices: ChoicesResponse | null;
  filters: TicketFilters;
  isRefreshing: boolean;
  onChangeFilters: (filters: TicketFilters) => void;
};

function toOptionalValue(value: string) {
  return value === allValue ? undefined : value;
}

export function TicketFilterBar({
  choices,
  filters,
  isRefreshing,
  onChangeFilters
}: TicketFilterBarProps) {
  const [search, setSearch] = useState(filters.search ?? "");
  const [status, setStatus] = useState<string>(filters.status ?? allValue);
  const [priority, setPriority] = useState<string>(
    filters.priority ?? allValue
  );
  const [category, setCategory] = useState<string>(
    filters.category ?? allValue
  );
  const [assignment, setAssignment] = useState<string>(assignmentAll);
  const currentUserId = useSessionStore((state) => state.user?.id);

  const appliedSearch = filters.search ?? "";
  const hasActiveFilters =
    appliedSearch !== "" ||
    status !== allValue ||
    priority !== allValue ||
    category !== allValue ||
    assignment !== assignmentAll;

  const applyFilters = useCallback(
    (
      overrides: Partial<
        Record<"search" | "status" | "priority" | "category" | "assignment", string>
      >
    ) => {
      const next = { search, status, priority, category, assignment, ...overrides };

      onChangeFilters({
        search: next.search.trim() || undefined,
        unassigned: next.assignment === assignmentUnassigned ? true : undefined,
        assigneeId:
          next.assignment === assignmentMine ? currentUserId : undefined,
        status: toOptionalValue(next.status) as TicketStatusValue | undefined,
        priority: toOptionalValue(next.priority) as
          | TicketPriorityValue
          | undefined,
        category: toOptionalValue(next.category) as
          | TicketCategoryValue
          | undefined
      });
    },
    [assignment, category, currentUserId, onChangeFilters, priority, search, status]
  );

  /**
   * Select aplica na hora: a escolha e discreta e intencional, e esperar um
   * botao so acrescenta um passo.
   */
  function handleSelect(
    setValue: (value: string) => void,
    field: "status" | "priority" | "category" | "assignment"
  ) {
    return (value: string) => {
      setValue(value);
      applyFilters({ [field]: value });
    };
  }

  // A busca aplica sozinha depois da pausa na digitacao. A comparacao com o
  // filtro ja aplicado evita disparar no primeiro render e ao receber o
  // proprio filtro de volta pelo `filters`.
  useEffect(() => {
    if (search.trim() === appliedSearch) {
      return;
    }

    const timer = setTimeout(() => applyFilters({ search }), searchDebounceMs);

    return () => clearTimeout(timer);
  }, [appliedSearch, applyFilters, search]);

  // Reflete filtro alterado por fora (por exemplo, ao limpar).
  useEffect(() => {
    setStatus(filters.status ?? allValue);
    setPriority(filters.priority ?? allValue);
    setCategory(filters.category ?? allValue);
  }, [filters.category, filters.priority, filters.status]);

  function handleClear() {
    setSearch("");
    setStatus(allValue);
    setPriority(allValue);
    setCategory(allValue);
    setAssignment(assignmentAll);
    onChangeFilters({});
  }

  return (
    <div className="grid gap-3 rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
      <div className="grid gap-3 lg:grid-cols-[minmax(200px,1fr)_160px_160px_180px_190px]">
        <div className="relative">
          <Search className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
          <Input
            aria-label="Buscar chamados"
            className="pl-9"
            placeholder="Buscar por titulo ou descricao"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </div>

        <Select value={status} onValueChange={handleSelect(setStatus, "status")}>
          <SelectTrigger aria-label="Status">
            <SelectValue placeholder="Status" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={allValue}>Todos os status</SelectItem>
            {choices?.ticketStatuses.map((choice) => (
              <SelectItem key={choice.value} value={choice.value}>
                {choice.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select
          value={priority}
          onValueChange={handleSelect(setPriority, "priority")}
        >
          <SelectTrigger aria-label="Prioridade">
            <SelectValue placeholder="Prioridade" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={allValue}>Todas as prioridades</SelectItem>
            {choices?.ticketPriorities.map((choice) => (
              <SelectItem key={choice.value} value={choice.value}>
                {choice.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select
          value={category}
          onValueChange={handleSelect(setCategory, "category")}
        >
          <SelectTrigger aria-label="Categoria">
            <SelectValue placeholder="Categoria" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={allValue}>Todas as categorias</SelectItem>
            {choices?.ticketCategories.map((choice) => (
              <SelectItem key={choice.value} value={choice.value}>
                {choice.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select
          value={assignment}
          onValueChange={handleSelect(setAssignment, "assignment")}
        >
          <SelectTrigger aria-label="Atribuicao">
            <SelectValue placeholder="Atribuicao" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={assignmentAll}>Qualquer atribuicao</SelectItem>
            <SelectItem value={assignmentUnassigned}>
              Sem responsavel
            </SelectItem>
            <SelectItem disabled={!currentUserId} value={assignmentMine}>
              Meus chamados
            </SelectItem>
          </SelectContent>
        </Select>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="flex items-center gap-2 text-xs text-slate-500">
          {isRefreshing ? (
            <>
              <Loader2 className="h-3.5 w-3.5 animate-spin" />
              Atualizando resultados...
            </>
          ) : (
            "Os filtros sao aplicados automaticamente."
          )}
        </p>

        <Button
          disabled={!hasActiveFilters}
          size="sm"
          type="button"
          variant="outline"
          onClick={handleClear}
        >
          Limpar filtros
        </Button>
      </div>
    </div>
  );
}

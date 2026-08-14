"use client";

import { Search } from "lucide-react";
import { useEffect, useState } from "react";
import { Button } from "@/src/components/ui/button";
import { Input } from "@/src/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/src/components/ui/select";
import type {
  ChoicesResponse,
  TicketCategoryValue,
  TicketFilters,
  TicketPriorityValue,
  TicketStatusValue
} from "@/src/types/api";

const allValue = "ALL";

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
  const [status, setStatus] = useState(filters.status ?? allValue);
  const [priority, setPriority] = useState(filters.priority ?? allValue);
  const [category, setCategory] = useState(filters.category ?? allValue);

  useEffect(() => {
    setSearch(filters.search ?? "");
    setStatus(filters.status ?? allValue);
    setPriority(filters.priority ?? allValue);
    setCategory(filters.category ?? allValue);
  }, [filters]);

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();

    onChangeFilters({
      search: search.trim() || undefined,
      status: toOptionalValue(status) as TicketStatusValue | undefined,
      priority: toOptionalValue(priority) as TicketPriorityValue | undefined,
      category: toOptionalValue(category) as TicketCategoryValue | undefined
    });
  }

  return (
    <form
      className="grid gap-3 rounded-lg border border-slate-200 bg-white p-4 shadow-sm lg:grid-cols-[minmax(220px,1fr)_170px_170px_190px_auto]"
      onSubmit={handleSubmit}
    >
      <div className="relative">
        <Search className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
        <Input
          className="pl-9"
          placeholder="Buscar por titulo ou descricao"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
      </div>

      <Select value={status} onValueChange={setStatus}>
        <SelectTrigger>
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

      <Select value={priority} onValueChange={setPriority}>
        <SelectTrigger>
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

      <Select value={category} onValueChange={setCategory}>
        <SelectTrigger>
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

      <Button disabled={isRefreshing} type="submit">
        {isRefreshing ? "Atualizando..." : "Filtrar"}
      </Button>
    </form>
  );
}

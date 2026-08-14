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
import type { ChoicesResponse, RoleValue, UserFilters } from "@/src/types/api";

const allValue = "ALL";

type UserFilterBarProps = {
  choices: ChoicesResponse | null;
  filters: UserFilters;
  isRefreshing: boolean;
  onChangeFilters: (filters: UserFilters) => void;
};

export function UserFilterBar({
  choices,
  filters,
  isRefreshing,
  onChangeFilters
}: UserFilterBarProps) {
  const [search, setSearch] = useState(filters.search ?? "");
  const [role, setRole] = useState<string>(filters.role ?? allValue);

  useEffect(() => {
    setSearch(filters.search ?? "");
    setRole(filters.role ?? allValue);
  }, [filters]);

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();

    onChangeFilters({
      search: search.trim() || undefined,
      role: role === allValue ? undefined : (role as RoleValue)
    });
  }

  return (
    <form
      className="grid gap-3 rounded-lg border border-slate-200 bg-white p-4 shadow-sm lg:grid-cols-[minmax(220px,1fr)_190px_auto]"
      onSubmit={handleSubmit}
    >
      <div className="relative">
        <Search className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
        <Input
          className="pl-9"
          placeholder="Buscar por nome ou e-mail"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
      </div>

      <Select value={role} onValueChange={setRole}>
        <SelectTrigger>
          <SelectValue placeholder="Perfil" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value={allValue}>Todos os perfis</SelectItem>
          {choices?.roles.map((choice) => (
            <SelectItem key={choice.value} value={choice.value}>
              {choice.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>

      <Button disabled={isRefreshing} type="submit" variant="outline">
        {isRefreshing ? "Filtrando..." : "Aplicar filtros"}
      </Button>
    </form>
  );
}

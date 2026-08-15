"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { toApiErrorMessage } from "@/src/services/api-error";
import { choicesService } from "@/src/services/choices.service";
import { usersService } from "@/src/services/users.service";
import type {
  ChoicesResponse,
  CreateUserRequest,
  UserFilters,
  UserSummary
} from "@/src/types/api";

export const initialUserFilters: UserFilters = {
  page: 0,
  size: 20,
  sort: "name,asc"
};

export function useUsers() {
  const [choices, setChoices] = useState<ChoicesResponse | null>(null);
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [filters, setFilters] = useState<UserFilters>(initialUserFilters);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [isCreating, setIsCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const roleLabels = useMemo(
    () =>
      choices
        ? new Map(choices.roles.map((role) => [role.value, role.label]))
        : null,
    [choices]
  );

  const loadUsers = useCallback(
    async (nextFilters = filters) => {
      setIsRefreshing(true);
      setError(null);

      try {
        const response = await usersService.list(nextFilters);
        setUsers(response.content);
        setTotalPages(response.totalPages);
        setTotalElements(response.totalElements);
      } catch (loadError) {
        setError(toApiErrorMessage(loadError));
      } finally {
        setIsLoading(false);
        setIsRefreshing(false);
      }
    },
    [filters]
  );

  /**
   * Troca de pagina preserva os filtros: zerar a pagina e comportamento de
   * filtro novo, nao de navegacao.
   */
  const goToPage = useCallback(
    (page: number) => {
      const nextFilters = { ...filters, page };

      setFilters(nextFilters);
      void loadUsers(nextFilters);
    },
    [filters, loadUsers]
  );

  const updateFilters = useCallback(
    (nextFilters: UserFilters) => {
      const normalizedFilters = {
        ...initialUserFilters,
        ...nextFilters,
        page: 0
      };

      setFilters(normalizedFilters);
      void loadUsers(normalizedFilters);
    },
    [loadUsers]
  );

  const createUser = useCallback(
    async (payload: CreateUserRequest) => {
      setIsCreating(true);

      try {
        await usersService.create(payload);
        await loadUsers();
        toast.success("Usuario criado.", {
          description: "A senha provisoria foi enviada por e-mail."
        });

        return true;
      } catch (createError) {
        toast.error("Nao foi possivel criar o usuario.", {
          description: toApiErrorMessage(createError)
        });

        return false;
      } finally {
        setIsCreating(false);
      }
    },
    [loadUsers]
  );

  useEffect(() => {
    async function loadInitialData() {
      setIsLoading(true);
      setError(null);

      try {
        const [choicesResponse, usersResponse] = await Promise.all([
          choicesService.getChoices(),
          usersService.list(initialUserFilters)
        ]);

        setChoices(choicesResponse);
        setUsers(usersResponse.content);
        setTotalPages(usersResponse.totalPages);
        setTotalElements(usersResponse.totalElements);
      } catch (loadError) {
        setError(toApiErrorMessage(loadError));
      } finally {
        setIsLoading(false);
      }
    }

    void loadInitialData();
  }, []);

  return {
    choices,
    roleLabels,
    users,
    filters,
    totalPages,
    totalElements,
    isLoading,
    isRefreshing,
    isCreating,
    error,
    loadUsers,
    goToPage,
    updateFilters,
    createUser
  };
}

"use client";

import { useEffect, useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle
} from "@/src/components/ui/dialog";
import { Badge } from "@/src/components/ui/badge";
import { Skeleton } from "@/src/components/ui/skeleton";
import { toApiErrorMessage } from "@/src/services/api-error";
import { usersService } from "@/src/services/users.service";
import type { UserDto } from "@/src/types/api";

type UserDetailDialogProps = {
  roleLabels: Map<string, string> | null;
  userId: string | null;
  onOpenChange: (isOpen: boolean) => void;
};

function formatDate(value: string) {
  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short"
  }).format(new Date(value));
}

export function UserDetailDialog({
  roleLabels,
  userId,
  onOpenChange
}: UserDetailDialogProps) {
  const [user, setUser] = useState<UserDto | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!userId) {
      setUser(null);
      setError(null);
      return;
    }

    let isActive = true;

    async function loadUser(id: string) {
      setIsLoading(true);
      setError(null);

      try {
        const response = await usersService.getById(id);

        if (isActive) {
          setUser(response);
        }
      } catch (loadError) {
        if (isActive) {
          setError(toApiErrorMessage(loadError));
        }
      } finally {
        if (isActive) {
          setIsLoading(false);
        }
      }
    }

    void loadUser(userId);

    return () => {
      isActive = false;
    };
  }, [userId]);

  return (
    <Dialog open={Boolean(userId)} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Detalhes do usuario</DialogTitle>
          <DialogDescription>
            Dados completos vindos de GET /users/&#123;id&#125;.
          </DialogDescription>
        </DialogHeader>

        {isLoading ? (
          <div className="grid gap-3">
            <Skeleton className="h-5 w-48" />
            <Skeleton className="h-5 w-64" />
            <Skeleton className="h-5 w-40" />
          </div>
        ) : null}

        {error ? (
          <p className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
            {error}
          </p>
        ) : null}

        {user && !isLoading && !error ? (
          <dl className="grid gap-3 text-sm sm:grid-cols-2">
            <div className="sm:col-span-2">
              <dt className="font-medium text-slate-500">Nome</dt>
              <dd className="mt-1 text-slate-950">{user.name}</dd>
            </div>
            <div className="sm:col-span-2">
              <dt className="font-medium text-slate-500">E-mail</dt>
              <dd className="mt-1 break-all text-slate-950">{user.email}</dd>
            </div>
            <div>
              <dt className="font-medium text-slate-500">Perfil</dt>
              <dd className="mt-1">
                <Badge variant="outline">
                  {roleLabels?.get(user.role) ?? user.role}
                </Badge>
              </dd>
            </div>
            <div>
              <dt className="font-medium text-slate-500">Troca de senha</dt>
              <dd className="mt-1 text-slate-950">
                {user.mustChangePassword ? "Pendente" : "Concluida"}
              </dd>
            </div>
            <div>
              <dt className="font-medium text-slate-500">Criado em</dt>
              <dd className="mt-1 text-slate-950">
                {formatDate(user.createdAt)}
              </dd>
            </div>
            <div>
              <dt className="font-medium text-slate-500">Atualizado em</dt>
              <dd className="mt-1 text-slate-950">
                {formatDate(user.updatedAt)}
              </dd>
            </div>
          </dl>
        ) : null}
      </DialogContent>
    </Dialog>
  );
}

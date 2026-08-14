"use client";

import { Eye } from "lucide-react";
import { Button } from "@/src/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle
} from "@/src/components/ui/card";
import { Skeleton } from "@/src/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from "@/src/components/ui/table";
import type { UserSummary } from "@/src/types/api";

type UserListProps = {
  isLoading: boolean;
  users: UserSummary[];
  onSelectUser: (userId: string) => void;
};

export function UserList({ isLoading, users, onSelectUser }: UserListProps) {
  if (isLoading) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Usuarios</CardTitle>
          <CardDescription>Carregando usuarios cadastrados.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-3">
          {Array.from({ length: 4 }).map((_, index) => (
            <Skeleton className="h-14 rounded-md" key={index} />
          ))}
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Usuarios</CardTitle>
        <CardDescription>
          {users.length} usuarios encontrados. A listagem da API devolve apenas
          o nome; abra os detalhes para ver e-mail e perfil.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {users.length === 0 ? (
          <div className="rounded-md border border-dashed border-slate-300 p-6 text-sm text-slate-600">
            Nenhum usuario encontrado para os filtros atuais.
          </div>
        ) : (
          <>
            <div className="hidden rounded-md border border-slate-200 md:block">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Nome</TableHead>
                    <TableHead className="w-32 text-right">Acoes</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {users.map((user) => (
                    <TableRow key={user.id}>
                      <TableCell className="font-medium">{user.name}</TableCell>
                      <TableCell className="text-right">
                        <Button
                          size="sm"
                          type="button"
                          variant="outline"
                          onClick={() => onSelectUser(user.id)}
                        >
                          <Eye className="h-4 w-4" />
                          Detalhes
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>

            <div className="grid gap-3 md:hidden">
              {users.map((user) => (
                <div
                  className="flex items-center justify-between gap-3 rounded-md border border-slate-200 p-4"
                  key={user.id}
                >
                  <p className="min-w-0 truncate text-sm font-medium text-slate-950">
                    {user.name}
                  </p>
                  <Button
                    size="sm"
                    type="button"
                    variant="outline"
                    onClick={() => onSelectUser(user.id)}
                  >
                    <Eye className="h-4 w-4" />
                    Detalhes
                  </Button>
                </div>
              ))}
            </div>
          </>
        )}
      </CardContent>
    </Card>
  );
}

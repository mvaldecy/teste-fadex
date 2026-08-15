"use client";

import { useState } from "react";
import { PaginationBar } from "@/src/components/layout/pagination-bar";
import { UserCreateDialog } from "./user-create-dialog";
import { UserDetailDialog } from "./user-detail-dialog";
import { UserFilterBar } from "./user-filter-bar";
import { UserList } from "./user-list";
import { useUsers } from "./use-users";

export function UsersPage() {
  const users = useUsers();
  const [selectedUserId, setSelectedUserId] = useState<string | null>(null);

  return (
    <div className="mx-auto grid max-w-7xl gap-6">
      <header className="flex flex-col gap-4 border-b border-slate-200 pb-5 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-sm font-semibold uppercase tracking-[0.12em] text-emerald-700">
            Administracao
          </p>
          <h1 className="mt-2 text-3xl font-semibold tracking-normal">
            Usuarios
          </h1>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-600">
            Consulte quem tem acesso ao helpdesk e cadastre novos usuarios.
          </p>
        </div>

        <UserCreateDialog
          choices={users.choices}
          isCreating={users.isCreating}
          onCreateUser={users.createUser}
        />
      </header>

      <UserFilterBar
        choices={users.choices}
        filters={users.filters}
        isRefreshing={users.isRefreshing}
        onChangeFilters={users.updateFilters}
      />

      {users.error ? (
        <p className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
          {users.error}
        </p>
      ) : null}

      <UserList
        isLoading={users.isLoading}
        users={users.users}
        onSelectUser={setSelectedUserId}
      />

      <PaginationBar
        isDisabled={users.isRefreshing}
        page={users.filters.page ?? 0}
        totalElements={users.totalElements}
        totalPages={users.totalPages}
        onPageChange={users.goToPage}
      />

      <UserDetailDialog
        roleLabels={users.roleLabels}
        userId={selectedUserId}
        onOpenChange={(isOpen) => {
          if (!isOpen) {
            setSelectedUserId(null);
          }
        }}
      />
    </div>
  );
}

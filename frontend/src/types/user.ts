import type { PageParams } from "./pagination";
import type { RoleValue } from "./choice";

/**
 * A projecao de `GET /api/v1/users` devolve apenas `id` e `name`. E-mail e
 * papel exigem `GET /api/v1/users/{id}`, por isso a listagem não os exibe.
 */
export type UserSummary = {
  id: string;
  name: string;
};

export type UserDto = UserSummary & {
  email: string;
  role: RoleValue;
  mustChangePassword: boolean;
  createdAt: string;
  updatedAt: string;
};

export type UserFilters = PageParams & {
  id?: string;
  role?: RoleValue;
  name?: string;
  email?: string;
  search?: string;
};

/**
 * Sem senha: o backend gera a provisoria e a envia pelo mecanismo de e-mail
 * configurado, conforme `docs/backend/api.md`.
 */
export type CreateUserRequest = {
  name: string;
  email: string;
  role: RoleValue;
};

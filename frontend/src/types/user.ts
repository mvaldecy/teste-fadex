import type { PageParams } from "./pagination";
import type { RoleValue } from "./choice";

export type UserSummary = {
  id: string;
  name: string;
};

export type UserDto = UserSummary & {
  email: string;
  role: RoleValue;
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

export type CreateUserRequest = {
  name: string;
  email: string;
  password: string;
  role: RoleValue;
};

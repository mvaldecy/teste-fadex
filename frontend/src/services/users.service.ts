import type {
  CreateUserRequest,
  PageResponse,
  UserDto,
  UserFilters,
  UserSummary
} from "@/src/types/api";
import { api } from "./api";

async function list(filters?: UserFilters) {
  const response = await api.get<PageResponse<UserSummary>>("/users", {
    params: filters
  });

  return response.data;
}

async function getById(id: string) {
  const response = await api.get<UserDto>(`/users/${id}`);
  return response.data;
}

async function create(payload: CreateUserRequest) {
  const response = await api.post<UserDto>("/users", payload);
  return response.data;
}

export const usersService = {
  list,
  getById,
  create
};

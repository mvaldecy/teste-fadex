import type { RoleValue } from "./choice";

export type AuthLoginRequest = {
  email: string;
  password: string;
};

export type AuthRefreshRequest = {
  refreshToken: string;
};

export type AuthenticatedUser = {
  id: string;
  name: string;
};

export type AuthLoginResponse = {
  accessToken: string;
  refreshToken: string | null;
  tokenType: "Bearer";
  expiresIn: number;
  mustChangePassword: boolean;
  role: RoleValue;
  user: AuthenticatedUser;
};

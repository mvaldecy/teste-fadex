import type { RoleValue } from "./choice";

export type AuthLoginRequest = {
  email: string;
  password: string;
};

export type AuthenticatedUser = {
  id: string;
  name: string;
};

export type AuthLoginResponse = {
  accessToken: string;
  tokenType: "Bearer";
  expiresIn: number;
  role: RoleValue;
  user: AuthenticatedUser;
};

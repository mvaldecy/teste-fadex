import axios from "axios";
import { getPublicEnv } from "@/src/config/public-env";
import { getApiAccessToken } from "./api-token";

const publicEnv = getPublicEnv();

export const api = axios.create({
  baseURL: publicEnv.apiBaseUrl,
  headers: {
    Accept: "application/json"
  }
});

api.interceptors.request.use((config) => {
  const token = getApiAccessToken();

  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  return config;
});

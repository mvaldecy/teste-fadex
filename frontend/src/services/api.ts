import axios from "axios";
import { getPublicEnv } from "@/src/config/public-env";

const publicEnv = getPublicEnv();

export const api = axios.create({
  baseURL: publicEnv.apiBaseUrl,
  headers: {
    Accept: "application/json"
  }
});

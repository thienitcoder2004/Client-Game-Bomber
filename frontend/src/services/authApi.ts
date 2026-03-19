import type {
  AuthResponse,
  LoginRequest,
  RegisterRequest,
} from "../types/auth";
import { apiFetch } from "./http";

export const authApi = {
  register(payload: RegisterRequest) {
    return apiFetch<AuthResponse>("/api/auth/register", {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },

  login(payload: LoginRequest) {
    return apiFetch<AuthResponse>("/api/auth/login", {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },
};
import type {
  AuthResponse,
  ForgotPasswordRequest,
  LoginRequest,
  MessageResponse,
  RegisterRequest,
  ResetPasswordRequest,
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

  requestForgotPassword(payload: ForgotPasswordRequest) {
    return apiFetch<MessageResponse>("/api/auth/forgot-password/request", {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },

  resetPassword(payload: ResetPasswordRequest) {
    return apiFetch<MessageResponse>("/api/auth/forgot-password/reset", {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },
};
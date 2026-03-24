import type { AdminMatch, AdminUser } from "../types/admin";
import { apiFetch } from "./http";

export const adminApi = {
  getUsers() {
    return apiFetch<AdminUser[]>("/api/admin/users", undefined, true);
  },

  lockUser(userId: string) {
    return apiFetch<AdminUser>(`/api/admin/users/${userId}/lock`, {
      method: "PATCH",
    }, true);
  },

  unlockUser(userId: string) {
    return apiFetch<AdminUser>(`/api/admin/users/${userId}/unlock`, {
      method: "PATCH",
    }, true);
  },

  getMatches() {
    return apiFetch<AdminMatch[]>("/api/admin/matches", undefined, true);
  },
};
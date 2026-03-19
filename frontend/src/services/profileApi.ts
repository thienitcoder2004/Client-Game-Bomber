import type { ProfileResponse, UpdateProfileRequest } from "../types/profile";
import { apiFetch } from "./http";

export const profileApi = {
  getMe() {
    return apiFetch<ProfileResponse>("/api/profile/me", undefined, true);
  },

  updateMe(payload: UpdateProfileRequest) {
    return apiFetch<ProfileResponse>(
      "/api/profile/me",
      {
        method: "PUT",
        body: JSON.stringify(payload),
      },
      true,
    );
  },
};
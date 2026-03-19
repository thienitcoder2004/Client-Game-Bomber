import type { MatchHistoryItem } from "../types/history";
import { apiFetch } from "./http";

export const historyApi = {
  getMyHistory() {
    return apiFetch<MatchHistoryItem[]>("/api/history/me", undefined, true);
  },
};
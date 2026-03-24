import type {
  CreateFriendRequest,
  FriendChatMessage,
  FriendListItem,
  FriendRequestItem,
  FriendSearchItem,
} from "../types/friend";
import type { MessageResponse } from "../types/auth";
import { apiFetch } from "./http";

export const friendApi = {
  search(keyword: string) {
    return apiFetch<FriendSearchItem[]>(
      `/api/friends/search?keyword=${encodeURIComponent(keyword)}`,
      undefined,
      true,
    );
  },

  getFriends() {
    return apiFetch<FriendListItem[]>("/api/friends", undefined, true);
  },

  getIncomingRequests() {
    return apiFetch<FriendRequestItem[]>(
      "/api/friends/requests/incoming",
      undefined,
      true,
    );
  },

  getOutgoingRequests() {
    return apiFetch<FriendRequestItem[]>(
      "/api/friends/requests/outgoing",
      undefined,
      true,
    );
  },

  sendRequest(payload: CreateFriendRequest) {
    return apiFetch<MessageResponse>(
      "/api/friends/requests",
      {
        method: "POST",
        body: JSON.stringify(payload),
      },
      true,
    );
  },

  acceptRequest(requestId: string) {
    return apiFetch<MessageResponse>(
      `/api/friends/requests/${requestId}/accept`,
      {
        method: "POST",
      },
      true,
    );
  },

  deleteRequest(requestId: string) {
    return apiFetch<MessageResponse>(
      `/api/friends/requests/${requestId}`,
      {
        method: "DELETE",
      },
      true,
    );
  },

  removeFriend(friendUserId: string) {
    return apiFetch<MessageResponse>(
      `/api/friends/${friendUserId}`,
      {
        method: "DELETE",
      },
      true,
    );
  },

  getChatHistory(friendUserId: string) {
    return apiFetch<FriendChatMessage[]>(
      `/api/friends/chat/${friendUserId}`,
      undefined,
      true,
    );
  },
};
export type FriendUserSummary = {
  userId: string;
  username: string;
  characterName: string;
  avatarCode: string;
  online: boolean;
};

export type FriendSearchItem = {
  userId: string;
  username: string;
  characterName: string;
  avatarCode: string;
  online: boolean;
  relationshipStatus: "NONE" | "PENDING_IN" | "PENDING_OUT" | "FRIEND";
  requestId: string | null;
};

export type FriendRequestItem = {
  requestId: string;
  direction: "INCOMING" | "OUTGOING";
  user: FriendUserSummary;
  createdAt: string;
};

export type FriendListItem = {
  friendshipId: string;
  user: FriendUserSummary;
  acceptedAt: string | null;
};

export type FriendChatMessage = {
  id: string;
  senderId: string;
  receiverId: string;
  content: string;
  createdAt: string;
  recalled: boolean;
  recalledAt: string | null;
};

export type CreateFriendRequest = {
  targetUserId: string;
};
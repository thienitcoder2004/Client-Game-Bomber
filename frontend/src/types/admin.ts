export type AdminUser = {
  id: string;
  email: string;
  username: string;
  characterName: string;
  gender: string;
  role: string;
  active: boolean;
  createdAt?: string;
};

export type AdminMatch = {
  id: string;
  roomCode: string;
  winnerUserId: string;
  winnerCharacterName: string;
  startedAt?: string;
  endedAt?: string;
  playerCount: number;
  playerNames: string[];
};
export type PlayerMatchResult = {
  userId: string;
  characterName: string;
  bombsPlaced: number;
  kills: number;
  deaths: number;
  livesLeft: number;
  ovr: number;
  winner: boolean;
};

export type MatchHistoryItem = {
  id: string;
  roomCode: string;
  winnerUserId: string;
  winnerCharacterName: string;
  participantUserIds: string[];
  startedAt: string;
  endedAt: string;
  players: PlayerMatchResult[];
};
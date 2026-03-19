import type {
  BombState,
  BoardItem,
  Direction,
  ExplosionState,
  ItemType,
  TileType,
} from "./types";

export type RemotePlayer = {
  id: 1 | 2 | 3 | 4;
  row: number;
  col: number;
  direction: Direction;
  lives: number;
  invulnerableUntil: number;
  inventory: ItemType[];
  maxBombs: number;
  bombRange: number;
  speedLevel: number;
  bombsPlaced: number;
  kills: number;
  deaths: number;
  ovr: number;
  characterName: string;
  gender: string;
  avatarCode: string;
};

export type RemoteGameState = {
  board: TileType[][];
  players: RemotePlayer[];
  bombs: BombState[];
  explosions: ExplosionState[];
  items: BoardItem[];
  gameOver: boolean;
  winnerId: number | null;
  resultMessage: string | null;

  waitingForPlayers: boolean;
  gameStarted: boolean;
  connectedPlayers: number;
  requiredPlayers: number;
  countdownSeconds: number | null;
};

export type InitMessage = {
  type: "init";
  data: {
    playerId: 1 | 2 | 3 | 4;
  };
};

export type StateMessage = {
  type: "state";
  data: RemoteGameState;
};

export type ErrorMessage = {
  type: "error";
  data: string;
};

export type ServerWsMessage = InitMessage | StateMessage | ErrorMessage;

export type MoveClientMessage = {
  type: "move";
  direction: Direction;
};

export type BombClientMessage = {
  type: "bomb";
};

export type UseItemClientMessage = {
  type: "use_item";
  slotIndex: number;
};

export type RestartClientMessage = {
  type: "restart";
};

export type ClientWsMessage =
  | MoveClientMessage
  | BombClientMessage
  | UseItemClientMessage
  | RestartClientMessage;
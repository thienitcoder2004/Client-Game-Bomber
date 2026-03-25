export type TileType = 0 | 1 | 2;
export type Direction = "up" | "down" | "left" | "right";
export type PlayerId = 1 | 2 | 3 | 4;

export type ItemType =
  | "BOMB_UP"
  | "FLAME_UP"
  | "SPEED_UP"
  | "SHIELD"
  | "HEART"
  | "TELEPORT"
  | "RANDOM_BOMB"
  | "FREEZE_BOMB";

export type BoardItem = {
  id: string;
  row: number;
  col: number;
  type: ItemType;
};

export type PickupEffect = {
  id: string;
  x: number;
  y: number;
  text: string;
  createdAt: number;
  duration: number;
};

export type PlayerState = {
  id: PlayerId;
  row: number;
  col: number;
  dir: Direction;
  walkFrame: 0 | 1;
  lives: number;
  invulnerableUntil: number;
  frozenUntil: number;
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

  // ===== thêm để nhận bot từ backend =====
  bot?: boolean;

  // ===== TEAM =====
  // SOLO: mỗi người một team riêng
  // DUO: 2 người cùng team sẽ có cùng teamId
  teamId?: string;
};

export type BombState = {
  id: string;
  ownerId: PlayerId;
  row: number;
  col: number;
  placedAt: number;
  range: number;
  randomPattern: boolean;
  freezeEffect: boolean;
};

export type FlameKind =
  | "center"
  | "horizontal"
  | "vertical"
  | "left_end"
  | "right_end"
  | "top_end"
  | "bottom_end";

export type FlameCell = {
  row: number;
  col: number;
  kind: FlameKind;
};

export type ExplosionState = {
  id: string;
  row: number;
  col: number;
  startedAt: number;
  duration: number;
  cells: FlameCell[];
  randomPattern: boolean;
  freezeEffect: boolean;
};

export type Particle = {
  x: number;
  y: number;
  vx: number;
  vy: number;
  size: number;
  life: number;
  maxLife: number;
};

export type CharacterSprites = {
  downStand: HTMLImageElement | null;
  downWalk: HTMLImageElement | null;
  upStand: HTMLImageElement | null;
  upWalk: HTMLImageElement | null;
  leftStand: HTMLImageElement | null;
  leftWalk: HTMLImageElement | null;
  rightStand: HTMLImageElement | null;
  rightWalk: HTMLImageElement | null;
};

export type Assets = {
  male: CharacterSprites;
  female: CharacterSprites;
  map: {
    hardWall: HTMLImageElement | null;
    softWall: HTMLImageElement | null;
  };
  bomb: {
    cap1: HTMLImageElement | null;
    cap2: HTMLImageElement | null;
    cap3: HTMLImageElement | null;
  };
  items: {
    bombUp: HTMLImageElement | null;
    flameUp: HTMLImageElement | null;
    speedUp: HTMLImageElement | null;
    shield: HTMLImageElement | null;
    heart: HTMLImageElement | null;
    teleport: HTMLImageElement | null;
    randomBomb: HTMLImageElement | null;
    freezeBomb: HTMLImageElement | null;
  };
};
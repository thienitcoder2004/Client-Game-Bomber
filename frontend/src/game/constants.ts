import { GAME_CONFIG } from "../config/gameConfig";

export const TILE = GAME_CONFIG.board.tileSize;
export const ROWS = GAME_CONFIG.board.rows;
export const COLS = GAME_CONFIG.board.cols;
export const HUD_HEIGHT = GAME_CONFIG.board.hudHeight;

export const CANVAS_WIDTH = COLS * TILE;
export const CANVAS_HEIGHT = HUD_HEIGHT + ROWS * TILE;

export const MOVE_INTERVAL_MS =
  GAME_CONFIG.timing.moveCooldownBySpeedLevel[
    GAME_CONFIG.player.startSpeedLevel
  ];
export const KEY_GAP_MAX_MS = GAME_CONFIG.timing.keyGapMaxMs;
export const SHAKE_MS = GAME_CONFIG.timing.shakeMs;
export const BOMB_FUSE_MS = GAME_CONFIG.timing.bombFuseMs;
export const EXPLOSION_MS = GAME_CONFIG.timing.explosionMs;
export const INVULNERABLE_MS = GAME_CONFIG.timing.invulnerableMs;

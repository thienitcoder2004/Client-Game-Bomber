import { COLS, ROWS } from "./constants";
import type { BombState, TileType } from "./types";

export function uid() {
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export function inBounds(row: number, col: number) {
  return row >= 0 && row < ROWS && col >= 0 && col < COLS;
}

export function cloneBoard(board: TileType[][]) {
  return board.map((r) => [...r]) as TileType[][];
}

export function isWalkable(
  board: TileType[][],
  bombs: BombState[],
  row: number,
  col: number,
) {
  if (!inBounds(row, col)) return false;
  if (board[row][col] !== 0) return false;
  if (bombs.some((b) => b.row === row && b.col === col)) return false;
  return true;
}
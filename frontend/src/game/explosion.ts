import { TILE } from "./constants";
import type { FlameCell, Particle, TileType, ExplosionState } from "./types";
import { inBounds } from "./board";

export function buildExplosionCells(
  board: TileType[][],
  row: number,
  col: number,
  range: number = 1
): FlameCell[] {
  const cells: FlameCell[] = [{ row, col, kind: "center" }];

  const dirs = [
    { dr: 0, dc: -1, mid: "horizontal" as const, end: "left_end" as const },
    { dr: 0, dc: 1, mid: "horizontal" as const, end: "right_end" as const },
    { dr: -1, dc: 0, mid: "vertical" as const, end: "top_end" as const },
    { dr: 1, dc: 0, mid: "vertical" as const, end: "bottom_end" as const },
  ];

  for (const dir of dirs) {
    for (let i = 1; i <= range; i++) {
      const nr = row + dir.dr * i;
      const nc = col + dir.dc * i;

      if (!inBounds(nr, nc)) break;
      if (board[nr][nc] === 1) break;

      const isBreakable = board[nr][nc] === 2;
      const isEnd = i === range || isBreakable;

      cells.push({
        row: nr,
        col: nc,
        kind: isEnd ? dir.end : dir.mid,
      });

      if (isBreakable) break;
    }
  }

  return cells;
}

export function spawnExplosionParticles(exp: ExplosionState): Particle[] {
  const particles: Particle[] = [];

  for (const cell of exp.cells) {
    const cx = cell.col * TILE + TILE / 2;
    const cy = cell.row * TILE + TILE / 2;
    const count = cell.kind === "center" ? 10 : 4;

    for (let i = 0; i < count; i++) {
      const angle = (Math.PI * 2 * i) / count + Math.random() * 0.35;
      const speed = 1.4 + Math.random() * 2.6;

      particles.push({
        x: cx,
        y: cy,
        vx: Math.cos(angle) * speed,
        vy: Math.sin(angle) * speed,
        size: 2 + Math.random() * 3,
        life: 180 + Math.random() * 90,
        maxLife: 180 + Math.random() * 90,
      });
    }
  }

  return particles;
}
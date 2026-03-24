import {
  BOMB_FUSE_MS,
  CANVAS_WIDTH,
  COLS,
  HUD_HEIGHT,
  ROWS,
  SHAKE_MS,
  TILE,
} from "./constants";

import type {
  Assets,
  BoardItem,
  BombState,
  ExplosionState,
  FlameKind,
  ItemType,
  Particle,
  PickupEffect,
  PlayerState,
  TileType,
} from "./types";

/**
 * Màu đại diện từng player.
 */
function getPlayerColor(id: number) {
  if (id === 1) return "#ff4d4f";
  if (id === 2) return "#3b82f6";
  if (id === 3) return "#22c55e";
  return "#a855f7";
}

/**
 * Tên hiển thị của player.
 */
function getPlayerName(player: PlayerState) {
  return player.characterName?.trim() || `P${player.id}`;
}

/**
 * Ký hiệu fallback cho item.
 */
function getItemLabel(type: ItemType) {
  if (type === "BOMB_UP") return "B";
  if (type === "FLAME_UP") return "F";
  if (type === "SPEED_UP") return "S";
  if (type === "SHIELD") return "SH";
  if (type === "HEART") return "H";
  if (type === "TELEPORT") return "TP";
  if (type === "RANDOM_BOMB") return "RB";
  return "FB";
}

/**
 * Lấy ảnh item theo loại.
 */
function getItemImage(assets: Assets | null, type: ItemType) {
  if (!assets) return null;
  if (type === "BOMB_UP") return assets.items.bombUp;
  if (type === "FLAME_UP") return assets.items.flameUp;
  if (type === "SPEED_UP") return assets.items.speedUp;
  if (type === "SHIELD") return assets.items.shield;
  if (type === "HEART") return assets.items.heart;
  if (type === "TELEPORT") return assets.items.teleport;
  if (type === "RANDOM_BOMB") return assets.items.randomBomb;
  return assets.items.freezeBomb;
}

/**
 * Vẽ nền đất.
 */
function drawGroundTile(
  ctx: CanvasRenderingContext2D,
  row: number,
  col: number,
) {
  const x = col * TILE;
  const y = row * TILE;

  const base = ctx.createLinearGradient(x, y, x + TILE, y + TILE);
  base.addColorStop(0, (row + col) % 2 === 0 ? "#c98f2f" : "#d9a13e");
  base.addColorStop(1, (row + col) % 2 === 0 ? "#b77d21" : "#c98e2c");
  ctx.fillStyle = base;
  ctx.fillRect(x, y, TILE, TILE);

  ctx.fillStyle = "rgba(255,255,255,0.06)";
  ctx.fillRect(x + 7, y + 7, TILE - 14, TILE - 14);

  ctx.strokeStyle = "rgba(0,0,0,0.08)";
  ctx.strokeRect(x + 1, y + 1, TILE - 2, TILE - 2);
}

/**
 * Vẽ tường cứng.
 */
function drawHardWall(
  ctx: CanvasRenderingContext2D,
  img: HTMLImageElement | null,
  row: number,
  col: number,
) {
  const x = col * TILE;
  const y = row * TILE;

  if (img) {
    ctx.drawImage(img, x + 2, y + 2, TILE - 4, TILE - 4);
    return;
  }

  ctx.fillStyle = "#8d96a0";
  ctx.fillRect(x + 4, y + 4, TILE - 8, TILE - 8);
}

/**
 * Vẽ tường mềm.
 */
function drawSoftWall(
  ctx: CanvasRenderingContext2D,
  img: HTMLImageElement | null,
  row: number,
  col: number,
) {
  const x = col * TILE;
  const y = row * TILE;

  if (img) {
    ctx.drawImage(img, x + 4, y + 4, TILE - 8, TILE - 8);
    return;
  }

  ctx.fillStyle = "#b86d22";
  ctx.fillRect(x + 6, y + 6, TILE - 12, TILE - 12);
}

/**
 * Vẽ item trên map.
 */
function drawBoardItem(
  ctx: CanvasRenderingContext2D,
  assets: Assets | null,
  item: BoardItem,
  now: number,
) {
  const x = item.col * TILE;
  const y = item.row * TILE;
  const cx = x + TILE / 2;
  const cy = y + TILE / 2;

  const floatY = Math.sin(now * 0.006 + item.row + item.col) * 2;
  const pulse = 1 + Math.sin(now * 0.008 + item.row) * 0.04;

  ctx.save();
  ctx.translate(cx, cy + floatY);
  ctx.scale(pulse, pulse);
  ctx.translate(-cx, -cy);

  ctx.fillStyle = "rgba(0,0,0,0.18)";
  ctx.beginPath();
  ctx.ellipse(cx, y + TILE - 10, 13, 6, 0, 0, Math.PI * 2);
  ctx.fill();

  const img = getItemImage(assets, item.type);

  if (img) {
    ctx.drawImage(img, x + 9, y + 8, TILE - 18, TILE - 18);
  } else {
    ctx.fillStyle =
      item.type === "BOMB_UP"
        ? "#111111"
        : item.type === "FLAME_UP"
          ? "#ff7a1f"
          : item.type === "SPEED_UP"
            ? "#22c55e"
            : item.type === "SHIELD"
              ? "#3b82f6"
              : item.type === "HEART"
                ? "#ef4444"
                : item.type === "TELEPORT"
                  ? "#8b5cf6"
                  : item.type === "RANDOM_BOMB"
                    ? "#f97316"
                    : "#38bdf8";

    ctx.beginPath();
    ctx.arc(cx, cy, 12, 0, Math.PI * 2);
    ctx.fill();

    ctx.fillStyle = "#fff";
    ctx.font = "bold 10px Arial";
    ctx.fillText(getItemLabel(item.type), cx - 9, cy + 4);
  }

  ctx.restore();
}

/**
 * Vẽ túi đồ trên HUD.
 */
function drawInventorySlots(
  ctx: CanvasRenderingContext2D,
  assets: Assets | null,
  currentPlayer: PlayerState | undefined,
) {
  const startX = CANVAS_WIDTH - 270;
  const startY = 8;

  ctx.fillStyle = "#e2e8f0";
  ctx.font = "bold 14px Arial";
  ctx.fillText("Túi đồ", startX, startY + 14);

  for (let i = 0; i < 5; i++) {
    const x = startX + i * 48;
    const y = startY + 20;

    ctx.fillStyle = "#08111f";
    ctx.fillRect(x, y, 40, 40);

    ctx.strokeStyle = "rgba(255,255,255,0.35)";
    ctx.lineWidth = 2;
    ctx.strokeRect(x, y, 40, 40);

    ctx.fillStyle = "rgba(255,255,255,0.7)";
    ctx.font = "11px Arial";
    ctx.fillText(String(i + 1), x + 14, y + 53);

    const item = currentPlayer?.inventory?.[i];
    if (!item) continue;

    const img = getItemImage(assets, item);
    if (img) {
      ctx.drawImage(img, x + 6, y + 6, 28, 28);
    } else {
      ctx.fillStyle = "#facc15";
      ctx.font = "bold 11px Arial";
      ctx.fillText(getItemLabel(item), x + 6, y + 24);
    }
  }
}

/**
 * Vẽ thẻ thông tin ngắn của từng player trên HUD.
 */
function drawPlayerStatCard(
  ctx: CanvasRenderingContext2D,
  player: PlayerState,
  index: number,
  now: number,
) {
  const x = 18 + index * 112;
  const y = 36;
  const color = getPlayerColor(player.id);
  const frozen = now < player.frozenUntil;

  ctx.fillStyle = frozen ? "rgba(56,189,248,0.16)" : "rgba(255,255,255,0.08)";
  ctx.fillRect(x, y, 102, 30);

  ctx.fillStyle = color;
  ctx.fillRect(x, y, 5, 30);

  ctx.fillStyle = "#fff";
  ctx.font = "bold 12px Arial";
  ctx.fillText(`${getPlayerName(player).slice(0, 10)}`, x + 10, y + 13);

  ctx.fillStyle = "#cbd5e1";
  ctx.font = "12px Arial";
  ctx.fillText(`❤ ${player.lives}`, x + 10, y + 27);

  if (frozen) {
    ctx.fillStyle = "#7dd3fc";
    ctx.font = "bold 11px Arial";
    ctx.fillText("FROZEN", x + 46, y + 27);
  }
}

/**
 * Vẽ HUD.
 */
function drawHUD(
  ctx: CanvasRenderingContext2D,
  assets: Assets | null,
  players: PlayerState[],
  bombs: BombState[],
  currentPlayer: PlayerState | undefined,
  now: number,
) {
  const grad = ctx.createLinearGradient(0, 0, CANVAS_WIDTH, 0);
  grad.addColorStop(0, "#0b3d7a");
  grad.addColorStop(1, "#1184ea");
  ctx.fillStyle = grad;
  ctx.fillRect(0, 0, CANVAS_WIDTH, HUD_HEIGHT);

  ctx.fillStyle = "#fff";
  ctx.font = "bold 28px Arial";
  ctx.fillText("Bomber Game", 18, 28);

  ctx.fillStyle = "#dbeafe";
  ctx.font = "bold 13px Arial";
  ctx.fillText("Move: WASD / Arrow", 215, 23);
  ctx.fillText("Bomb: Space / Enter", 215, 42);
  ctx.fillText("Use item: 1 - 5", 215, 61);

  ctx.fillStyle = "#fff";
  ctx.font = "bold 14px Arial";
  ctx.fillText(`Bombs on map: ${bombs.length}`, 470, 60);

  players.forEach((player, index) => {
    drawPlayerStatCard(ctx, player, index, now);
  });

  drawInventorySlots(ctx, assets, currentPlayer);
}

/**
 * Vẽ bomb trên map.
 * - bomb thường: viền cam nhẹ
 * - random bomb: viền tím / cam
 * - freeze bomb: viền xanh băng
 */
function drawBomb(
  ctx: CanvasRenderingContext2D,
  assets: Assets | null,
  bomb: BombState,
  now: number,
) {
  const x = bomb.col * TILE;
  const y = bomb.row * TILE;

  const elapsed = now - bomb.placedAt;
  const danger = Math.max(0, Math.min(1, elapsed / BOMB_FUSE_MS));
  const pulse = 1 + Math.sin(now * 0.015) * 0.03 + danger * 0.06;

  const isFreeze = bomb.freezeEffect;
  const isRandom = bomb.randomPattern;

  ctx.save();
  ctx.translate(x + TILE / 2, y + TILE / 2);
  ctx.scale(pulse, pulse);
  ctx.translate(-TILE / 2, -TILE / 2);

  ctx.fillStyle = "rgba(0,0,0,0.24)";
  ctx.beginPath();
  ctx.ellipse(TILE / 2, TILE - 10, 15, 8, 0, 0, Math.PI * 2);
  ctx.fill();

  if (danger > 0.55) {
    ctx.save();
    ctx.shadowBlur = 18;
    ctx.shadowColor = isFreeze ? "#7dd3fc" : isRandom ? "#c084fc" : "#ff8a52";
    ctx.fillStyle = isFreeze
      ? "rgba(56,189,248,0.25)"
      : isRandom
        ? "rgba(168,85,247,0.20)"
        : "rgba(255,120,70,0.22)";
    ctx.beginPath();
    ctx.arc(TILE / 2, TILE / 2, 21, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
  }

  const bombImg = isFreeze
    ? assets?.bomb.cap3 ?? assets?.bomb.cap1 ?? null
    : isRandom
      ? assets?.bomb.cap2 ?? assets?.bomb.cap1 ?? null
      : assets?.bomb.cap1 ?? null;

  if (bombImg) {
    ctx.drawImage(bombImg, 8, 6, TILE - 16, TILE - 12);
  } else {
    ctx.fillStyle = isFreeze ? "#38bdf8" : isRandom ? "#a855f7" : "#222";
    ctx.beginPath();
    ctx.arc(TILE / 2, TILE / 2, 16, 0, Math.PI * 2);
    ctx.fill();
  }

  ctx.restore();
}

/**
 * Vẽ phần ngoài của flame.
 */
function drawFlameOuter(ctx: CanvasRenderingContext2D, kind: FlameKind) {
  if (kind === "center") {
    ctx.beginPath();
    ctx.arc(TILE / 2, TILE / 2, 13, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillRect(TILE / 2 - 9, 4, 18, TILE - 8);
    ctx.fillRect(4, TILE / 2 - 9, TILE - 8, 18);
  }

  if (kind === "horizontal") {
    ctx.fillRect(4, TILE / 2 - 10, TILE - 8, 20);
  }

  if (kind === "vertical") {
    ctx.fillRect(TILE / 2 - 10, 4, 20, TILE - 8);
  }

  if (kind === "left_end") {
    ctx.fillRect(TILE / 2 - 1, TILE / 2 - 10, TILE / 2 - 13, 20);
    ctx.beginPath();
    ctx.arc(14, TILE / 2, 12, 0, Math.PI * 2);
    ctx.fill();
  }

  if (kind === "right_end") {
    ctx.fillRect(TILE / 2, TILE / 2 - 10, TILE / 2 - 13, 20);
    ctx.beginPath();
    ctx.arc(TILE - 14, TILE / 2, 12, 0, Math.PI * 2);
    ctx.fill();
  }

  if (kind === "top_end") {
    ctx.fillRect(TILE / 2 - 10, TILE / 2 - 1, 20, TILE / 2 - 13);
    ctx.beginPath();
    ctx.arc(TILE / 2, 14, 12, 0, Math.PI * 2);
    ctx.fill();
  }

  if (kind === "bottom_end") {
    ctx.fillRect(TILE / 2 - 10, TILE / 2, 20, TILE / 2 - 13);
    ctx.beginPath();
    ctx.arc(TILE / 2, TILE - 14, 12, 0, Math.PI * 2);
    ctx.fill();
  }
}

/**
 * Vẽ phần trong của flame.
 */
function drawFlameInner(ctx: CanvasRenderingContext2D, kind: FlameKind) {
  if (kind === "center") {
    ctx.beginPath();
    ctx.arc(TILE / 2, TILE / 2, 8, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillRect(TILE / 2 - 5, 9, 10, TILE - 18);
    ctx.fillRect(9, TILE / 2 - 5, TILE - 18, 10);
  }

  if (kind === "horizontal") {
    ctx.fillRect(9, TILE / 2 - 5, TILE - 18, 10);
  }

  if (kind === "vertical") {
    ctx.fillRect(TILE / 2 - 5, 9, 10, TILE - 18);
  }

  if (kind === "left_end") {
    ctx.fillRect(TILE / 2 - 2, TILE / 2 - 5, TILE / 2 - 16, 10);
    ctx.beginPath();
    ctx.arc(16, TILE / 2, 7, 0, Math.PI * 2);
    ctx.fill();
  }

  if (kind === "right_end") {
    ctx.fillRect(TILE / 2, TILE / 2 - 5, TILE / 2 - 16, 10);
    ctx.beginPath();
    ctx.arc(TILE - 16, TILE / 2, 7, 0, Math.PI * 2);
    ctx.fill();
  }

  if (kind === "top_end") {
    ctx.fillRect(TILE / 2 - 5, TILE / 2 - 2, 10, TILE / 2 - 16);
    ctx.beginPath();
    ctx.arc(TILE / 2, 16, 7, 0, Math.PI * 2);
    ctx.fill();
  }

  if (kind === "bottom_end") {
    ctx.fillRect(TILE / 2 - 5, TILE / 2, 10, TILE / 2 - 16);
    ctx.beginPath();
    ctx.arc(TILE / 2, TILE - 16, 7, 0, Math.PI * 2);
    ctx.fill();
  }
}

/**
 * Vẽ flame.
 * - normal: cam
 * - random bomb: tím / cam
 * - freeze bomb: xanh băng
 */
function drawFlameCell(
  ctx: CanvasRenderingContext2D,
  kind: FlameKind,
  x: number,
  y: number,
  progress: number,
  variant: "normal" | "random" | "freeze",
) {
  const fade = progress > 0.72 ? 1 - (progress - 0.72) / 0.28 : 1;
  const pulse = 1 + Math.sin(progress * Math.PI) * 0.08;

  ctx.save();
  ctx.globalAlpha = fade;
  ctx.translate(x + TILE / 2, y + TILE / 2);
  ctx.scale(pulse, pulse);
  ctx.translate(-TILE / 2, -TILE / 2);

  ctx.save();
  ctx.shadowBlur = 22;
  ctx.shadowColor =
    variant === "freeze"
      ? "#7dd3fc"
      : variant === "random"
        ? "#c084fc"
        : "#ff8a3d";

  const outer = ctx.createLinearGradient(0, 0, TILE, TILE);
  if (variant === "freeze") {
    outer.addColorStop(0, "#e0f2fe");
    outer.addColorStop(0.35, "#7dd3fc");
    outer.addColorStop(0.75, "#38bdf8");
    outer.addColorStop(1, "#0ea5e9");
  } else if (variant === "random") {
    outer.addColorStop(0, "#f5d0fe");
    outer.addColorStop(0.35, "#d946ef");
    outer.addColorStop(0.75, "#f97316");
    outer.addColorStop(1, "#7c3aed");
  } else {
    outer.addColorStop(0, "#fff6b0");
    outer.addColorStop(0.35, "#ffcf4d");
    outer.addColorStop(0.75, "#ff7a1f");
    outer.addColorStop(1, "#d93304");
  }

  ctx.fillStyle = outer;
  drawFlameOuter(ctx, kind);
  ctx.restore();

  ctx.fillStyle = variant === "freeze" ? "#ffffff" : "#ffffff";
  drawFlameInner(ctx, kind);

  ctx.restore();
}

/**
 * Flash sáng lúc bom vừa nổ.
 */
function drawExplosionFlash(
  ctx: CanvasRenderingContext2D,
  row: number,
  col: number,
  progress: number,
  variant: "normal" | "random" | "freeze",
) {
  if (progress > 0.18) return;

  const cx = col * TILE + TILE / 2;
  const cy = row * TILE + TILE / 2;
  const radius = TILE * (0.24 + progress * 1.05);
  const alpha = 1 - progress / 0.18;

  const grad = ctx.createRadialGradient(cx, cy, 0, cx, cy, radius);

  if (variant === "freeze") {
    grad.addColorStop(0, `rgba(255,255,255,${0.95 * alpha})`);
    grad.addColorStop(0.35, `rgba(186,230,253,${0.75 * alpha})`);
    grad.addColorStop(0.75, `rgba(56,189,248,${0.40 * alpha})`);
    grad.addColorStop(1, "rgba(14,165,233,0)");
  } else if (variant === "random") {
    grad.addColorStop(0, `rgba(255,255,255,${0.95 * alpha})`);
    grad.addColorStop(0.35, `rgba(233,213,255,${0.75 * alpha})`);
    grad.addColorStop(0.75, `rgba(168,85,247,${0.40 * alpha})`);
    grad.addColorStop(1, "rgba(124,58,237,0)");
  } else {
    grad.addColorStop(0, `rgba(255,255,255,${0.95 * alpha})`);
    grad.addColorStop(0.35, `rgba(255,235,170,${0.75 * alpha})`);
    grad.addColorStop(0.75, `rgba(255,130,40,${0.40 * alpha})`);
    grad.addColorStop(1, "rgba(255,80,0,0)");
  }

  ctx.save();
  ctx.fillStyle = grad;
  ctx.beginPath();
  ctx.arc(cx, cy, radius, 0, Math.PI * 2);
  ctx.fill();
  ctx.restore();
}

/**
 * Vẽ particle.
 */
function drawParticles(ctx: CanvasRenderingContext2D, particles: Particle[]) {
  for (const p of particles) {
    const alpha = Math.max(0, p.life / p.maxLife);

    const grad = ctx.createRadialGradient(p.x, p.y, 0, p.x, p.y, p.size * 2.2);
    grad.addColorStop(0, `rgba(255,255,255,${0.95 * alpha})`);
    grad.addColorStop(0.35, `rgba(255,220,120,${0.75 * alpha})`);
    grad.addColorStop(0.8, `rgba(255,110,20,${0.45 * alpha})`);
    grad.addColorStop(1, "rgba(255,60,0,0)");

    ctx.save();
    ctx.fillStyle = grad;
    ctx.beginPath();
    ctx.arc(p.x, p.y, p.size * 2, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
  }
}

/**
 * Chọn bộ sprite nam / nữ.
 */
function pickPlayerSprites(assets: Assets | null, player: PlayerState) {
  if (!assets) return null;
  return player.gender === "FEMALE" ? assets.female : assets.male;
}

/**
 * Chọn đúng ảnh nhân vật theo hướng và frame đi bộ.
 */
function pickPlayerImage(assets: Assets | null, player: PlayerState) {
  const spriteSet = pickPlayerSprites(assets, player);
  if (!spriteSet) return null;

  if (player.dir === "down") {
    return player.walkFrame === 0 ? spriteSet.downStand : spriteSet.downWalk;
  }
  if (player.dir === "up") {
    return player.walkFrame === 0 ? spriteSet.upStand : spriteSet.upWalk;
  }
  if (player.dir === "left") {
    return player.walkFrame === 0 ? spriteSet.leftStand : spriteSet.leftWalk;
  }
  return player.walkFrame === 0 ? spriteSet.rightStand : spriteSet.rightWalk;
}

/**
 * Vẽ nhân vật.
 * Nếu bị đóng băng thì thêm viền xanh.
 */
function drawPlayer(
  ctx: CanvasRenderingContext2D,
  assets: Assets | null,
  player: PlayerState,
  now: number,
) {
  const x = player.col * TILE;
  const y = player.row * TILE;
  const blink =
    now < player.invulnerableUntil && Math.floor(now / 90) % 2 === 0;
  const frozen = now < player.frozenUntil;

  if (blink) return;
  if (player.lives <= 0) return;

  const playerColor = getPlayerColor(player.id);

  ctx.save();

  ctx.fillStyle = "rgba(0,0,0,0.24)";
  ctx.beginPath();
  ctx.ellipse(x + TILE / 2, y + TILE - 10, 15, 8, 0, 0, Math.PI * 2);
  ctx.fill();

  ctx.strokeStyle = frozen ? "#7dd3fc" : playerColor;
  ctx.lineWidth = 3;
  ctx.beginPath();
  ctx.ellipse(x + TILE / 2, y + TILE - 10, 18, 10, 0, 0, Math.PI * 2);
  ctx.stroke();

  if (frozen) {
    ctx.save();
    ctx.shadowBlur = 16;
    ctx.shadowColor = "#7dd3fc";
    ctx.fillStyle = "rgba(125,211,252,0.18)";
    ctx.beginPath();
    ctx.arc(x + TILE / 2, y + TILE / 2, 22, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
  }

  const img = pickPlayerImage(assets, player);
  if (img) {
    ctx.drawImage(img, x + 6, y + 4, TILE - 12, TILE - 8);
  } else {
    ctx.fillStyle = playerColor;
    ctx.fillRect(x + 16, y + 12, 24, 28);
  }

  if (frozen) {
    ctx.fillStyle = "#e0f2fe";
    ctx.font = "bold 12px Arial";
    ctx.fillText("❄", x + TILE - 18, y + 14);
  }

  ctx.fillStyle = playerColor;
  ctx.font = "bold 12px Arial";
  ctx.fillText(getPlayerName(player).slice(0, 10), x + 4, y + 8);

  ctx.restore();
}

/**
 * Vẽ text nổi khi nhặt item.
 */
function drawPickupEffects(
  ctx: CanvasRenderingContext2D,
  now: number,
  effects: PickupEffect[],
) {
  for (const fx of effects) {
    const progress = Math.min(1, (now - fx.createdAt) / fx.duration);
    const alpha = 1 - progress;

    const px = fx.x * TILE + TILE / 2 - 20;
    const py = fx.y * TILE + 10 - progress * 22;

    ctx.save();
    ctx.globalAlpha = alpha;
    ctx.fillStyle = "#fff176";
    ctx.font = "bold 14px Arial";
    ctx.fillText(fx.text, px, py);
    ctx.restore();
  }
}

/**
 * Hàm render toàn bộ scene game.
 */
export function drawScene(
  ctx: CanvasRenderingContext2D,
  assets: Assets | null,
  now: number,
  board: TileType[][],
  players: PlayerState[],
  bombs: BombState[],
  explosions: ExplosionState[],
  particles: Particle[],
  items: BoardItem[],
  pickupEffects: PickupEffect[],
  shakeUntil: number,
  myPlayerId?: number,
) {
  ctx.clearRect(0, 0, CANVAS_WIDTH, HUD_HEIGHT + ROWS * TILE);

  const currentPlayer = players.find((p) => p.id === myPlayerId);
  drawHUD(ctx, assets, players, bombs, currentPlayer, now);

  ctx.save();
  ctx.translate(0, HUD_HEIGHT);

  if (now < shakeUntil) {
    const remain = (shakeUntil - now) / SHAKE_MS;
    const power = remain * 4;
    ctx.translate(
      (Math.random() - 0.5) * power,
      (Math.random() - 0.5) * power,
    );
  }

  for (let row = 0; row < ROWS; row++) {
    for (let col = 0; col < COLS; col++) {
      drawGroundTile(ctx, row, col);

      if (board[row][col] === 1) {
        drawHardWall(ctx, assets?.map.hardWall ?? null, row, col);
      }
      if (board[row][col] === 2) {
        drawSoftWall(ctx, assets?.map.softWall ?? null, row, col);
      }
    }
  }

  for (const item of items) {
    drawBoardItem(ctx, assets, item, now);
  }

  for (const bomb of bombs) {
    drawBomb(ctx, assets, bomb, now);
  }

  for (const exp of explosions) {
    const progress = Math.min(1, (now - exp.startedAt) / exp.duration);
    const variant = exp.freezeEffect
      ? "freeze"
      : exp.randomPattern
        ? "random"
        : "normal";

    drawExplosionFlash(ctx, exp.row, exp.col, progress, variant);

    for (const cell of exp.cells) {
      const x = cell.col * TILE;
      const y = cell.row * TILE;
      drawFlameCell(ctx, cell.kind, x, y, progress, variant);
    }
  }

  drawParticles(ctx, particles);

  for (const player of players) {
    drawPlayer(ctx, assets, player, now);
  }

  drawPickupEffects(ctx, now, pickupEffects);

  ctx.restore();
}
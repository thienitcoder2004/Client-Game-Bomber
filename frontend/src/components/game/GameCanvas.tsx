import { useEffect, useRef } from "react";
import { getMoveCooldownBySpeedLevel } from "../../config/gameConfig";
import {
  CANVAS_HEIGHT,
  CANVAS_WIDTH,
  KEY_GAP_MAX_MS,
} from "../../game/constants";
import { drawScene } from "../../game/render";
import type {
  Assets,
  BoardItem,
  BombState,
  ExplosionState,
  Particle,
  PickupEffect,
  PlayerState,
  TileType,
} from "../../game/types";
import type { ClientWsMessage } from "../../game/wsTypes";

type Props = {
  assetsRef: React.MutableRefObject<Assets | null>;
  boardRef: React.MutableRefObject<TileType[][]>;
  playersRef: React.MutableRefObject<PlayerState[]>;
  bombsRef: React.MutableRefObject<BombState[]>;
  explosionsRef: React.MutableRefObject<ExplosionState[]>;
  particlesRef: React.MutableRefObject<Particle[]>;
  itemsRef: React.MutableRefObject<BoardItem[]>;
  pickupFxRef: React.MutableRefObject<PickupEffect[]>;
  shakeUntilRef: React.MutableRefObject<number>;
  playerIdRef: React.MutableRefObject<1 | 2 | 3 | 4 | null>;
  gameOverRef: React.MutableRefObject<boolean>;
  heldKeysRef: React.MutableRefObject<Set<string>>;
  lastInputAtRef: React.MutableRefObject<number>;
  lastMoveSentAtRef: React.MutableRefObject<number>;
  getDirectionFromKeys: () => "up" | "down" | "left" | "right" | null;
  sendWs: (payload: ClientWsMessage) => void;
  renderScale: number;
};

export default function GameCanvas({
  assetsRef,
  boardRef,
  playersRef,
  bombsRef,
  explosionsRef,
  particlesRef,
  itemsRef,
  pickupFxRef,
  shakeUntilRef,
  playerIdRef,
  gameOverRef,
  heldKeysRef,
  lastInputAtRef,
  lastMoveSentAtRef,
  getDirectionFromKeys,
  sendWs,
  renderScale,
}: Props) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    const dpr = window.devicePixelRatio || 1;
    canvas.width = CANVAS_WIDTH * dpr;
    canvas.height = CANVAS_HEIGHT * dpr;
    canvas.style.width = `${Math.round(CANVAS_WIDTH * renderScale)}px`;
    canvas.style.height = `${Math.round(CANVAS_HEIGHT * renderScale)}px`;
    ctx.scale(dpr, dpr);
    ctx.imageSmoothingEnabled = true;

    let raf = 0;
    let lastFrame = performance.now();

    /**
     * Cập nhật particle mỗi frame.
     */
    const updateParticles = (delta: number) => {
      for (const p of particlesRef.current) {
        p.x += p.vx * delta * 0.06;
        p.y += p.vy * delta * 0.06;
        p.vx *= 0.985;
        p.vy *= 0.985;
        p.life -= delta;
      }

      particlesRef.current = particlesRef.current.filter((p) => p.life > 0);
    };

    /**
     * Main render loop.
     */
    const loop = (frameNow: number) => {
      const delta = frameNow - lastFrame;
      lastFrame = frameNow;

      const worldNow = Date.now();

      if (worldNow - lastInputAtRef.current > KEY_GAP_MAX_MS) {
        heldKeysRef.current.clear();
      }

      const me = playersRef.current.find((p) => p.id === playerIdRef.current);
      const moveInterval = getMoveCooldownBySpeedLevel(me?.speedLevel);
      const direction = getDirectionFromKeys();

      const isFrozen = !!me && worldNow < me.frozenUntil;

      // Nếu bị đóng băng thì không gửi move lên server nữa
      if (
        !gameOverRef.current &&
        !isFrozen &&
        direction &&
        worldNow - lastMoveSentAtRef.current >= moveInterval
      ) {
        sendWs({
          type: "move",
          direction,
        });
        lastMoveSentAtRef.current = worldNow;
      }

      pickupFxRef.current = pickupFxRef.current.filter(
        (fx) => worldNow - fx.createdAt < fx.duration,
      );

      updateParticles(delta);

      drawScene(
        ctx,
        assetsRef.current,
        worldNow,
        boardRef.current,
        playersRef.current,
        bombsRef.current,
        explosionsRef.current,
        particlesRef.current,
        itemsRef.current,
        pickupFxRef.current,
        shakeUntilRef.current,
        playerIdRef.current ?? undefined,
      );

      raf = requestAnimationFrame(loop);
    };

    raf = requestAnimationFrame(loop);

    return () => cancelAnimationFrame(raf);
  }, [
    assetsRef,
    boardRef,
    bombsRef,
    explosionsRef,
    gameOverRef,
    getDirectionFromKeys,
    heldKeysRef,
    itemsRef,
    lastInputAtRef,
    lastMoveSentAtRef,
    particlesRef,
    pickupFxRef,
    playerIdRef,
    playersRef,
    renderScale,
    sendWs,
    shakeUntilRef,
  ]);

  return (
    <canvas
      ref={canvasRef}
      style={{
        display: "block",
        touchAction: "none",
        userSelect: "none",
        WebkitUserSelect: "none",
      }}
    />
  );
}

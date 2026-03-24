import {
  useRef,
  useState,
  type CSSProperties,
  type MutableRefObject,
  type MouseEvent as ReactMouseEvent,
  type TouchEvent as ReactTouchEvent,
} from "react";
import { playBombPlace, primeAudio } from "../../game/audio";
import type { GameAudio } from "../../game/audio";
import type { ItemType } from "../../game/types";
import type { ClientWsMessage } from "../../game/wsTypes";

type Props = {
  visible: boolean;
  canControl: boolean;
  heldKeysRef: MutableRefObject<Set<string>>;
  lastInputAtRef: MutableRefObject<number>;
  lastMoveSentAtRef: MutableRefObject<number>;
  sendWs: (payload: ClientWsMessage) => void;
  inventory: ItemType[];
  audioRef: MutableRefObject<GameAudio>;
  zoomPercent: number;
  onZoomIn: () => void;
  onZoomOut: () => void;
};

const getNowMs = () => Date.now();

const overlayStyle: CSSProperties = {
  position: "fixed",
  inset: 0,
  pointerEvents: "none",
  zIndex: 9999,
};

const floatingPanelStyle: CSSProperties = {
  pointerEvents: "auto",
  background: "rgba(15,23,42,0.72)",
  border: "1px solid rgba(255,255,255,0.12)",
  backdropFilter: "blur(12px)",
  boxShadow: "0 16px 40px rgba(0,0,0,0.35)",
};

const miniButtonStyle: CSSProperties = {
  minWidth: 48,
  height: 42,
  borderRadius: 14,
  border: "1px solid rgba(255,255,255,0.14)",
  color: "#fff",
  fontWeight: 800,
  cursor: "pointer",
  touchAction: "none",
  userSelect: "none",
  WebkitTapHighlightColor: "transparent",
  padding: "0 12px",
};

const actionButtonStyle: CSSProperties = {
  borderRadius: 999,
  border: "1px solid rgba(255,255,255,0.16)",
  color: "#fff",
  fontWeight: 900,
  cursor: "pointer",
  touchAction: "none",
  userSelect: "none",
  WebkitTapHighlightColor: "transparent",
};

const directionKeyCodes = [
  "ArrowUp",
  "ArrowDown",
  "ArrowLeft",
  "ArrowRight",
] as const;

function itemShortLabel(item: ItemType) {
  if (item === "BOMB_UP") return "B+";
  if (item === "FLAME_UP") return "F+";
  if (item === "SPEED_UP") return "S+";
  if (item === "SHIELD") return "SH";
  return "HP";
}

export default function MobileControls({
  visible,
  canControl,
  heldKeysRef,
  lastInputAtRef,
  lastMoveSentAtRef,
  sendWs,
  inventory,
  audioRef,
  zoomPercent,
  onZoomIn,
  onZoomOut,
}: Props) {
  const joystickRef = useRef<HTMLDivElement | null>(null);
  const lastDirectionRef = useRef<"up" | "down" | "left" | "right" | null>(
    null,
  );

  const [stickOffset, setStickOffset] = useState({ x: 0, y: 0 });
  const [dragging, setDragging] = useState(false);

  if (!visible) return null;

  const JOYSTICK_SIZE = 180;
  const STICK_SIZE = 70;
  const MAX_RADIUS = 50;
  const DEAD_ZONE = 26;
  const SWITCH_BIAS = 18;

  const clearDirections = () => {
    for (const code of directionKeyCodes) {
      heldKeysRef.current.delete(code);
    }
  };

  const directionToKey = (direction: "up" | "down" | "left" | "right") => {
    if (direction === "up") return "ArrowUp";
    if (direction === "down") return "ArrowDown";
    if (direction === "left") return "ArrowLeft";
    return "ArrowRight";
  };

  const getDirectionFromDelta = (
    dx: number,
    dy: number,
  ): "up" | "down" | "left" | "right" | null => {
    const absX = Math.abs(dx);
    const absY = Math.abs(dy);

    if (absX < DEAD_ZONE && absY < DEAD_ZONE) {
      return null;
    }

    if (absX > absY + SWITCH_BIAS) {
      return dx > 0 ? "right" : "left";
    }

    if (absY > absX + SWITCH_BIAS) {
      return dy > 0 ? "down" : "up";
    }

    return lastDirectionRef.current;
  };

  const sendImmediateMove = (direction: "up" | "down" | "left" | "right") => {
    const now = Date.now();

    sendWs({
      type: "move",
      direction,
    });

    lastMoveSentAtRef.current = now;
    lastInputAtRef.current = now;
  };

  const applyJoystick = (clientX: number, clientY: number) => {
    if (!canControl || !joystickRef.current) return;

    const rect = joystickRef.current.getBoundingClientRect();
    const centerX = rect.left + rect.width / 2;
    const centerY = rect.top + rect.height / 2;

    let dx = clientX - centerX;
    let dy = clientY - centerY;

    const distance = Math.sqrt(dx * dx + dy * dy);

    if (distance > MAX_RADIUS) {
      const ratio = MAX_RADIUS / distance;
      dx *= ratio;
      dy *= ratio;
    }

    setStickOffset({ x: dx, y: dy });
    lastInputAtRef.current = Date.now();

    const direction = getDirectionFromDelta(dx, dy);

    if (!direction) {
      clearDirections();
      lastDirectionRef.current = null;
      return;
    }

    clearDirections();
    heldKeysRef.current.add(directionToKey(direction));

    if (lastDirectionRef.current !== direction) {
      primeAudio(audioRef.current);
      sendImmediateMove(direction);
      lastDirectionRef.current = direction;
    }
  };

  const resetJoystick = () => {
    setStickOffset({ x: 0, y: 0 });
    clearDirections();
    lastDirectionRef.current = null;
    lastInputAtRef.current = Date.now();
    setDragging(false);
  };

  const handleTouchStart = (e: ReactTouchEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();

    const touch = e.touches[0];
    if (!touch) return;

    setDragging(true);
    applyJoystick(touch.clientX, touch.clientY);
  };

  const handleTouchMove = (e: ReactTouchEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();

    const touch = e.touches[0];
    if (!touch) return;

    applyJoystick(touch.clientX, touch.clientY);
  };

  const handleTouchEnd = (e: ReactTouchEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();
    resetJoystick();
  };

  const handleMouseDown = (e: ReactMouseEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();
    setDragging(true);
    applyJoystick(e.clientX, e.clientY);
  };

  const handleMouseMove = (e: ReactMouseEvent<HTMLDivElement>) => {
    if (!dragging) return;
    e.preventDefault();
    e.stopPropagation();
    applyJoystick(e.clientX, e.clientY);
  };

  const handleMouseUp = (e: ReactMouseEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();
    resetJoystick();
  };

  const sendBomb = () => {
    if (!canControl) return;
    primeAudio(audioRef.current);
    lastInputAtRef.current = Date.now();
    sendWs({ type: "bomb" });
    playBombPlace(audioRef.current);
  };

  const sendBombSkill = () => {
    if (!canControl) return;
    lastInputAtRef.current = Date.now();
    sendWs({
      type: "skill_bomb",
    });
  };

  const sendSpeedSkill = () => {
    if (!canControl) return;
    lastInputAtRef.current = Date.now();
    sendWs({
      type: "skill_speed",
    });
  };

  const sendItem = (slotIndex: number) => {
    if (!canControl) return;

    lastInputAtRef.current = getNowMs();
    sendWs({
      type: "use_item",
      slotIndex,
    });
  };

  return (
    <div style={overlayStyle}>
      <div
        style={{
          position: "fixed",
          top: 12,
          right: 12,
          display: "flex",
          alignItems: "center",
          gap: 8,
          padding: 8,
          borderRadius: 18,
          ...floatingPanelStyle,
        }}
      >
        <button
          type="button"
          onClick={onZoomOut}
          style={{
            ...miniButtonStyle,
            background: "linear-gradient(180deg, #334155, #1e293b)",
          }}
        >
          −
        </button>

        <div
          style={{
            minWidth: 62,
            textAlign: "center",
            fontSize: 12,
            fontWeight: 800,
            color: "#e2e8f0",
          }}
        >
          {zoomPercent}%
        </div>

        <button
          type="button"
          onClick={onZoomIn}
          style={{
            ...miniButtonStyle,
            background: "linear-gradient(180deg, #334155, #1e293b)",
          }}
        >
          +
        </button>
      </div>

      <div
        style={{
          position: "fixed",
          left: 12,
          bottom: "calc(28px + env(safe-area-inset-bottom, 0px))",
          padding: 12,
          borderRadius: 28,
          ...floatingPanelStyle,
        }}
      >
        <div
          ref={joystickRef}
          onTouchStart={handleTouchStart}
          onTouchMove={handleTouchMove}
          onTouchEnd={handleTouchEnd}
          onTouchCancel={handleTouchEnd}
          onMouseDown={handleMouseDown}
          onMouseMove={handleMouseMove}
          onMouseUp={handleMouseUp}
          onMouseLeave={handleMouseUp}
          style={{
            width: JOYSTICK_SIZE,
            height: JOYSTICK_SIZE,
            borderRadius: "50%",
            position: "relative",
            touchAction: "none",
            userSelect: "none",
            background:
              "radial-gradient(circle at 30% 30%, rgba(255,255,255,0.10), rgba(255,255,255,0.03) 55%, rgba(2,6,23,0.18) 100%)",
            border: "1px solid rgba(255,255,255,0.10)",
            boxShadow:
              "inset 0 8px 24px rgba(255,255,255,0.05), inset 0 -8px 24px rgba(0,0,0,0.22)",
          }}
        >
          <div
            style={{
              position: "absolute",
              left: "50%",
              top: "50%",
              width: 20,
              height: 20,
              borderRadius: "50%",
              transform: "translate(-50%, -50%)",
              background: "rgba(255,255,255,0.10)",
            }}
          />

          <div
            style={{
              position: "absolute",
              left: "50%",
              top: "50%",
              width: STICK_SIZE,
              height: STICK_SIZE,
              borderRadius: "50%",
              transform: `translate(calc(-50% + ${stickOffset.x}px), calc(-50% + ${stickOffset.y}px))`,
              background:
                "linear-gradient(180deg, rgba(148,163,184,0.95), rgba(71,85,105,0.95))",
              border: "1px solid rgba(255,255,255,0.20)",
              boxShadow:
                "0 8px 24px rgba(0,0,0,0.35), inset 0 4px 10px rgba(255,255,255,0.18)",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              color: "#fff",
              fontSize: 24,
              fontWeight: 900,
              pointerEvents: "none",
            }}
          >
            ●
          </div>
        </div>
      </div>

      <div
        style={{
          position: "fixed",
          right: 12,
          bottom: "calc(28px + env(safe-area-inset-bottom, 0px))",
          display: "flex",
          flexDirection: "column",
          alignItems: "flex-end",
          gap: 10,
        }}
      >
        <div
          style={{
            display: "flex",
            gap: 10,
            padding: 10,
            borderRadius: 24,
            ...floatingPanelStyle,
          }}
        >
          <button
            type="button"
            onTouchStart={(e) => {
              e.preventDefault();
              e.stopPropagation();
              sendBombSkill();
            }}
            onMouseDown={(e) => {
              e.preventDefault();
              e.stopPropagation();
              sendBombSkill();
            }}
            onContextMenu={(e) => e.preventDefault()}
            disabled={!canControl}
            style={{
              ...actionButtonStyle,
              width: 72,
              height: 72,
              opacity: !canControl ? 0.5 : 1,
              background: "linear-gradient(180deg, #8b5cf6, #6d28d9)",
              fontSize: 13,
            }}
          >
            BOMB+
          </button>

          <button
            type="button"
            onTouchStart={(e) => {
              e.preventDefault();
              e.stopPropagation();
              sendSpeedSkill();
            }}
            onMouseDown={(e) => {
              e.preventDefault();
              e.stopPropagation();
              sendSpeedSkill();
            }}
            onContextMenu={(e) => e.preventDefault()}
            disabled={!canControl}
            style={{
              ...actionButtonStyle,
              width: 72,
              height: 72,
              opacity: !canControl ? 0.5 : 1,
              background: "linear-gradient(180deg, #22c55e, #15803d)",
              fontSize: 13,
            }}
          >
            SPEED
          </button>

          <button
            type="button"
            onTouchStart={(e) => {
              e.preventDefault();
              e.stopPropagation();
              sendBomb();
            }}
            onMouseDown={(e) => {
              e.preventDefault();
              e.stopPropagation();
              sendBomb();
            }}
            onContextMenu={(e) => e.preventDefault()}
            disabled={!canControl}
            style={{
              ...actionButtonStyle,
              width: 92,
              height: 92,
              opacity: !canControl ? 0.5 : 1,
              background: "linear-gradient(180deg, #f97316, #ea580c)",
              fontSize: 22,
            }}
          >
            BOM
          </button>
        </div>

        <div
          style={{
            display: "flex",
            gap: 8,
            padding: 8,
            borderRadius: 18,
            ...floatingPanelStyle,
          }}
        >
          {inventory.length === 0 ? (
            <div
              style={{
                color: "#94a3b8",
                fontSize: 12,
                fontWeight: 700,
                padding: "10px 12px",
              }}
            >
              Chưa có item
            </div>
          ) : (
            inventory.map((item, index) => (
              <button
                key={`${item}-${index}`}
                type="button"
                onTouchStart={(e) => {
                  e.preventDefault();
                  e.stopPropagation();
                  sendItem(index);
                }}
                onMouseDown={(e) => {
                  e.preventDefault();
                  e.stopPropagation();
                  sendItem(index);
                }}
                onContextMenu={(e) => e.preventDefault()}
                disabled={!canControl}
                title={`Ô ${index + 1}`}
                style={{
                  ...miniButtonStyle,
                  opacity: !canControl ? 0.5 : 1,
                  background:
                    index === 0
                      ? "linear-gradient(180deg, #7e22ce, #581c87)"
                      : "linear-gradient(180deg, #1d4ed8, #1e40af)",
                }}
              >
                {itemShortLabel(item)}
              </button>
            ))
          )}
        </div>
      </div>
    </div>
  );
}

import { useEffect, useRef } from "react";
import { KEY_GAP_MAX_MS } from "../game/constants";
import { playBombPlace, primeAudio } from "../game/audio";
import type { ClientWsMessage } from "../game/wsTypes";
import type { GameAudio } from "../game/audio";
import type { Direction } from "../game/types";

type Params = {
  canControl: () => boolean;
  sendWs: (payload: ClientWsMessage) => void;
  audioRef: React.MutableRefObject<GameAudio>;
};

export function useKeyboardControls({
  canControl,
  sendWs,
  audioRef,
}: Params) {
  const heldKeysRef = useRef<Set<string>>(new Set());
  const lastInputAtRef = useRef(0);
  const lastMoveSentAtRef = useRef(0);

  useEffect(() => {
    const isControlKey = (code: string) =>
      [
        "ArrowUp",
        "ArrowDown",
        "ArrowLeft",
        "ArrowRight",
        "Space",
        "Enter",
        "KeyW",
        "KeyA",
        "KeyS",
        "KeyD",
        "KeyQ",
        "KeyE",
        "Digit1",
        "Digit2",
        "Digit3",
        "Digit4",
        "Digit5",
      ].includes(code);

    const slotMap: Record<string, number> = {
      Digit1: 0,
      Digit2: 1,
      Digit3: 2,
      Digit4: 3,
      Digit5: 4,
    };

    const onKeyDown = (e: KeyboardEvent) => {
      if (!canControl()) return;

      if (isControlKey(e.code)) {
        e.preventDefault();
      }

      primeAudio(audioRef.current);

      const now = Date.now();

      if (now - lastInputAtRef.current > KEY_GAP_MAX_MS) {
        heldKeysRef.current.clear();
      }

      lastInputAtRef.current = now;
      heldKeysRef.current.add(e.code);

      if (e.code === "KeyQ" && !e.repeat) {
        sendWs({ type: "skill_bomb" });
        return;
      }

      if (e.code === "KeyE" && !e.repeat) {
        sendWs({ type: "skill_speed" });
        return;
      }

      if (slotMap[e.code] !== undefined && !e.repeat) {
        sendWs({
          type: "use_item",
          slotIndex: slotMap[e.code],
        });
        return;
      }

      if ((e.code === "Space" || e.code === "Enter") && !e.repeat) {
        sendWs({ type: "bomb" });
        playBombPlace(audioRef.current);
      }
    };

    const onKeyUp = (e: KeyboardEvent) => {
      heldKeysRef.current.delete(e.code);
    };

    window.addEventListener("keydown", onKeyDown);
    window.addEventListener("keyup", onKeyUp);

    return () => {
      window.removeEventListener("keydown", onKeyDown);
      window.removeEventListener("keyup", onKeyUp);
    };
  }, [audioRef, canControl, sendWs]);

  const getDirectionFromKeys = (): Direction | null => {
    const keys = heldKeysRef.current;
    if (keys.has("KeyW") || keys.has("ArrowUp")) return "up";
    if (keys.has("KeyS") || keys.has("ArrowDown")) return "down";
    if (keys.has("KeyA") || keys.has("ArrowLeft")) return "left";
    if (keys.has("KeyD") || keys.has("ArrowRight")) return "right";
    return null;
  };

  return {
    heldKeysRef,
    lastInputAtRef,
    lastMoveSentAtRef,
    getDirectionFromKeys,
  };
}
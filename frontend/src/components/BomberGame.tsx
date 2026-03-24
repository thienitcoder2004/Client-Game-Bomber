import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { loadAssets } from "../game/assets";
import { CANVAS_HEIGHT, CANVAS_WIDTH, COLS, ROWS } from "../game/constants";
import GameCanvas from "./game/GameCanvas";
import MobileControls from "./game/MobileControls";
import GameResultModal from "./game/GameResultModal";
import GameStatusBar from "./game/GameStatusBar";
import GameWaitingOverlay from "./game/GameWaitingOverlay";
import { useGameSocket } from "../hooks/useGameSocket";
import { useKeyboardControls } from "../hooks/useKeyboardControls";

type Props = {
  backPath?: string;
};

export default function BomberGame({ backPath = "/lobby" }: Props) {
  const navigate = useNavigate();
  const assetsRef = useRef<Awaited<ReturnType<typeof loadAssets>> | null>(null);

  const { statusText, playerLabel, roomState, matchResult, sendWs, refs } =
    useGameSocket(ROWS, COLS);

  const {
    heldKeysRef,
    lastInputAtRef,
    lastMoveSentAtRef,
    getDirectionFromKeys,
  } = useKeyboardControls({
    canControl: () =>
      refs.playerIdRef.current != null &&
      !refs.gameOverRef.current &&
      refs.gameStartedRef.current,
    sendWs,
    audioRef: refs.audioRef,
  });

  const [viewport, setViewport] = useState(() => ({
    width: typeof window !== "undefined" ? window.innerWidth : CANVAS_WIDTH,
    height: typeof window !== "undefined" ? window.innerHeight : CANVAS_HEIGHT,
  }));

  const [zoomLevel, setZoomLevel] = useState(1);
  const [allowPortraitPlay, setAllowPortraitPlay] = useState(false);

  useEffect(() => {
    let mounted = true;

    loadAssets().then((assets) => {
      if (mounted) {
        assetsRef.current = assets;
      }
    });

    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    const updateViewport = () => {
      const nextWidth = window.innerWidth;
      const nextHeight = window.innerHeight;

      setViewport({
        width: nextWidth,
        height: nextHeight,
      });

      if (nextWidth >= nextHeight && allowPortraitPlay) {
        setAllowPortraitPlay(false);
      }
    };

    updateViewport();
    window.addEventListener("resize", updateViewport);
    window.addEventListener("orientationchange", updateViewport);

    return () => {
      window.removeEventListener("resize", updateViewport);
      window.removeEventListener("orientationchange", updateViewport);
    };
  }, [allowPortraitPlay]);

  const searchParams = useMemo(() => {
    if (typeof window === "undefined") {
      return new URLSearchParams();
    }
    return new URLSearchParams(window.location.search);
  }, []);

  const isTouchDevice = useMemo(() => {
    if (typeof window === "undefined" || typeof navigator === "undefined") {
      return false;
    }

    return (
      navigator.maxTouchPoints > 0 ||
      window.matchMedia("(pointer: coarse)").matches
    );
  }, []);

  const isMobileLayout = isTouchDevice || viewport.width <= 1024;
  const isLandscape = viewport.width >= viewport.height;

  const isCustomRoom =
    searchParams.get("mode") === "room" && !!searchParams.get("roomCode");

  const fitScale = useMemo(() => {
    if (!isMobileLayout) return 1;

    const horizontalPadding = 18;
    const reservedTop = isLandscape ? 92 : 160;

    const byWidth = (viewport.width - horizontalPadding) / CANVAS_WIDTH;
    const byHeight = (viewport.height - reservedTop) / CANVAS_HEIGHT;

    return Math.min(1, Math.max(0.28, Math.min(byWidth, byHeight)));
  }, [isLandscape, isMobileLayout, viewport.height, viewport.width]);

  const renderScale = isMobileLayout
    ? Math.max(0.28, Math.min(1.7, fitScale * zoomLevel))
    : 1;

  const shellWidth = Math.round(CANVAS_WIDTH * renderScale);

  const myPlayer =
    refs.playersRef.current.find((p) => p.id === refs.playerIdRef.current) ??
    null;

  const canTouchControl =
    refs.playerIdRef.current != null &&
    !refs.gameOverRef.current &&
    refs.gameStartedRef.current;

  // ===== QUAN TRỌNG =====
  // Trong room mode, bot đã được thêm ở room lobby rồi,
  // nên KHÔNG cho thêm lại trong màn game waiting overlay.
  const canAddBot = false;

  const handleAddBot = () => {
    sendWs({ type: "add_bot" });
  };

  const sendRestart = () => {
    sendWs({ type: "restart" });
  };

  const showPortraitHint = isMobileLayout && !isLandscape && !allowPortraitPlay;

  return (
    <>
      <div
        style={{
          width: shellWidth,
          maxWidth: "100%",
          margin: isMobileLayout ? "10px auto 0" : "24px auto",
          borderRadius: 22,
          overflow: "hidden",
          border: "3px solid #0f172a",
          boxShadow:
            "0 25px 50px rgba(0,0,0,0.35), 0 0 0 6px rgba(255,255,255,0.03)",
          background: "#0b1220",
          position: "relative",
        }}
      >
        <GameStatusBar
          statusText={statusText}
          playerLabel={playerLabel}
          compact={isMobileLayout}
        />

        <GameCanvas
          assetsRef={assetsRef}
          boardRef={refs.boardRef}
          playersRef={refs.playersRef}
          bombsRef={refs.bombsRef}
          explosionsRef={refs.explosionsRef}
          particlesRef={refs.particlesRef}
          itemsRef={refs.itemsRef}
          pickupFxRef={refs.pickupFxRef}
          shakeUntilRef={refs.shakeUntilRef}
          playerIdRef={refs.playerIdRef}
          gameOverRef={refs.gameOverRef}
          heldKeysRef={heldKeysRef}
          lastInputAtRef={lastInputAtRef}
          lastMoveSentAtRef={lastMoveSentAtRef}
          getDirectionFromKeys={getDirectionFromKeys}
          sendWs={sendWs}
          renderScale={renderScale}
        />

        <GameWaitingOverlay
          open={!roomState.gameStarted}
          connectedPlayers={roomState.connectedPlayers}
          requiredPlayers={roomState.requiredPlayers}
          countdownSeconds={roomState.countdownSeconds}
          players={refs.playersRef.current}
          onLeave={() => navigate(backPath)}
          canAddBot={isCustomRoom ? false : canAddBot}
          onAddBot={handleAddBot}
        />

        <GameResultModal
          open={matchResult.gameOver}
          winnerId={matchResult.winnerId}
          resultMessage={matchResult.resultMessage}
          players={matchResult.players}
          onRestart={sendRestart}
        />
      </div>

      <MobileControls
        visible={isMobileLayout && (isLandscape || allowPortraitPlay)}
        canControl={canTouchControl}
        heldKeysRef={heldKeysRef}
        lastInputAtRef={lastInputAtRef}
        lastMoveSentAtRef={lastMoveSentAtRef}
        sendWs={sendWs}
        inventory={myPlayer?.inventory ?? []}
        audioRef={refs.audioRef}
        zoomPercent={Math.round(renderScale * 100)}
        onZoomIn={() =>
          setZoomLevel((prev) =>
            Math.min(1.85, Number((prev + 0.1).toFixed(2))),
          )
        }
        onZoomOut={() =>
          setZoomLevel((prev) => Math.max(0.6, Number((prev - 0.1).toFixed(2))))
        }
      />

      {showPortraitHint && (
        <div
          style={{
            position: "fixed",
            inset: 0,
            zIndex: 60,
            background:
              "linear-gradient(180deg, rgba(2,6,23,0.94), rgba(15,23,42,0.96))",
            display: "grid",
            placeItems: "center",
            padding: 20,
            textAlign: "center",
          }}
        >
          <div
            style={{
              maxWidth: 380,
              background: "rgba(15,23,42,0.95)",
              border: "1px solid rgba(255,255,255,0.08)",
              borderRadius: 24,
              padding: 24,
              boxShadow: "0 24px 60px rgba(0,0,0,0.35)",
            }}
          >
            <div style={{ fontSize: 54, marginBottom: 12 }}>📱↔️</div>
            <h2 style={{ marginTop: 0, marginBottom: 10 }}>Hãy xoay ngang</h2>

            <div
              style={{ color: "#cbd5e1", lineHeight: 1.7, marginBottom: 18 }}
            >
              Chế độ mobile của game tối ưu cho màn hình ngang để thấy map rõ
              hơn và bấm phím ảo dễ hơn.
            </div>

            <div
              style={{
                display: "flex",
                gap: 10,
                justifyContent: "center",
                flexWrap: "wrap",
              }}
            >
              <button
                type="button"
                onClick={() => setAllowPortraitPlay(true)}
                style={{
                  border: "none",
                  borderRadius: 14,
                  padding: "12px 16px",
                  fontWeight: 800,
                  color: "#fff",
                  cursor: "pointer",
                  background: "linear-gradient(180deg, #475569, #334155)",
                }}
              >
                Vẫn chơi
              </button>

              <button
                type="button"
                onClick={() => navigate(backPath)}
                style={{
                  border: "none",
                  borderRadius: 14,
                  padding: "12px 16px",
                  fontWeight: 800,
                  color: "#fff",
                  cursor: "pointer",
                  background: "linear-gradient(180deg, #2563eb, #1d4ed8)",
                }}
              >
                Về sảnh
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}

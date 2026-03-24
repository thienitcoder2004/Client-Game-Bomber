import type { PlayerState } from "../../game/types";
import Button from "../common/Button";

type Props = {
  open: boolean;
  connectedPlayers: number;
  requiredPlayers: number;
  countdownSeconds: number | null;
  players: PlayerState[];
  onLeave: () => void;

  // ===== thêm bot =====
  canAddBot?: boolean;
  onAddBot?: () => void;
};

export default function GameWaitingOverlay({
  open,
  connectedPlayers,
  requiredPlayers,
  countdownSeconds,
  players,
  onLeave,
  canAddBot = false,
  onAddBot,
}: Props) {
  if (!open) return null;

  const waiting = connectedPlayers < requiredPlayers;
  const title = waiting
    ? "Đang chờ người chơi..."
    : `Đã đủ người • Bắt đầu sau ${countdownSeconds ?? 0}s`;

  const canStillAddBot = canAddBot && waiting;

  return (
    <div
      style={{
        position: "absolute",
        inset: 0,
        background: "rgba(2,6,23,0.84)",
        backdropFilter: "blur(4px)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        zIndex: 18,
        padding: 20,
      }}
    >
      <div
        style={{
          width: 720,
          maxWidth: "96%",
          background: "linear-gradient(180deg, #0f172a, #111827)",
          border: "2px solid #334155",
          borderRadius: 20,
          padding: 24,
          color: "#fff",
          boxShadow: "0 30px 60px rgba(0,0,0,0.45)",
        }}
      >
        <h2 style={{ marginTop: 0, marginBottom: 10, fontSize: 30 }}>
          {title}
        </h2>

        <div style={{ color: "#cbd5e1", marginBottom: 18, fontSize: 16 }}>
          {waiting
            ? `Hiện tại có ${connectedPlayers}/${requiredPlayers} người trong phòng`
            : "Tất cả người chơi đã vào đủ, chuẩn bị bắt đầu trận đấu"}
        </div>

        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(2, minmax(0, 1fr))",
            gap: 14,
            marginBottom: 20,
          }}
        >
          {Array.from({ length: requiredPlayers }, (_, index) => {
            const slot = index + 1;
            const player = players.find((p) => p.id === slot);

            return (
              <div
                key={slot}
                style={{
                  padding: 16,
                  borderRadius: 14,
                  border: "1px solid rgba(255,255,255,0.08)",
                  background: player
                    ? player.bot
                      ? "rgba(168,85,247,0.16)"
                      : "rgba(59,130,246,0.12)"
                    : "rgba(255,255,255,0.04)",
                }}
              >
                <div style={{ fontWeight: 700, marginBottom: 6 }}>
                  Slot {slot}
                </div>

                <div style={{ color: player ? "#fff" : "#94a3b8" }}>
                  {player
                    ? player.characterName || `Player ${slot}`
                    : "Đang chờ..."}
                </div>

                {player?.bot && (
                  <div
                    style={{
                      marginTop: 8,
                      display: "inline-flex",
                      alignItems: "center",
                      padding: "4px 10px",
                      borderRadius: 999,
                      fontSize: 12,
                      fontWeight: 800,
                      background: "rgba(168,85,247,0.18)",
                      color: "#e9d5ff",
                      border: "1px solid rgba(168,85,247,0.28)",
                    }}
                  >
                    BOT
                  </div>
                )}
              </div>
            );
          })}
        </div>

        <div
          style={{
            display: "flex",
            justifyContent: "center",
            gap: 12,
            flexWrap: "wrap",
          }}
        >
          {canAddBot && (
            <Button
              onClick={onAddBot}
              disabled={!canStillAddBot}
              style={{
                background: "linear-gradient(180deg, #a855f7, #7e22ce)",
              }}
            >
              + Thêm bot
            </Button>
          )}

          <Button
            onClick={onLeave}
            style={{
              background: "linear-gradient(180deg, #ef4444, #dc2626)",
            }}
          >
            Rời phòng
          </Button>
        </div>

        {canAddBot && waiting && (
          <div
            style={{
              marginTop: 12,
              textAlign: "center",
              color: "#cbd5e1",
              fontSize: 14,
            }}
          >
            Chủ phòng có thể thêm bot để đủ người và bắt đầu nhanh hơn
          </div>
        )}
      </div>
    </div>
  );
}

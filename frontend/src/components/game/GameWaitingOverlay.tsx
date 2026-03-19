import type { PlayerState } from "../../game/types";
import Button from "../common/Button";

type Props = {
  open: boolean;
  connectedPlayers: number;
  requiredPlayers: number;
  countdownSeconds: number | null;
  players: PlayerState[];
  onLeave: () => void;
};

export default function GameWaitingOverlay({
  open,
  connectedPlayers,
  requiredPlayers,
  countdownSeconds,
  players,
  onLeave,
}: Props) {
  if (!open) return null;

  const waiting = connectedPlayers < requiredPlayers;
  const title = waiting
    ? "Đang chờ người chơi..."
    : `Đã đủ người • Bắt đầu sau ${countdownSeconds ?? 0}s`;

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
                    ? "rgba(59,130,246,0.12)"
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
              </div>
            );
          })}
        </div>

        <div style={{ display: "flex", justifyContent: "center" }}>
          <Button
            onClick={onLeave}
            style={{
              background: "linear-gradient(180deg, #ef4444, #dc2626)",
            }}
          >
            Rời phòng
          </Button>
        </div>
      </div>
    </div>
  );
}

import Button from "../common/Button";
import type { PlayerState } from "../../game/types";

type Props = {
  open: boolean;
  winnerId: number | null;
  resultMessage: string | null;
  players: PlayerState[];
  onRestart: () => void;
};

export default function GameResultModal({
  open,
  winnerId,
  resultMessage,
  players,
  onRestart,
}: Props) {
  if (!open) return null;

  return (
    <div
      style={{
        position: "absolute",
        inset: 0,
        background: "rgba(0,0,0,0.72)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        zIndex: 20,
        padding: 20,
      }}
    >
      <div
        style={{
          width: 780,
          maxWidth: "96%",
          background: "linear-gradient(180deg, #0f172a, #111827)",
          border: "2px solid #334155",
          borderRadius: 18,
          padding: 22,
          color: "#fff",
          boxShadow: "0 30px 60px rgba(0,0,0,0.45)",
        }}
      >
        <h2 style={{ marginTop: 0, marginBottom: 10, fontSize: 30 }}>
          {winnerId ? "Có người chiến thắng" : "Kết thúc trận đấu"}
        </h2>

        <div
          style={{
            marginBottom: 16,
            color: "#cbd5e1",
            fontSize: 15,
          }}
        >
          {resultMessage ?? "Trận đấu đã kết thúc"}
        </div>

        <table
          style={{
            width: "100%",
            borderCollapse: "collapse",
            fontSize: 14,
            overflow: "hidden",
            borderRadius: 12,
          }}
        >
          <thead>
            <tr
              style={{
                background: "rgba(255,255,255,0.06)",
              }}
            >
              <th style={{ textAlign: "left", padding: 10 }}>Nhân vật</th>
              <th style={{ textAlign: "left", padding: 10 }}>Bom đã dùng</th>
              <th style={{ textAlign: "left", padding: 10 }}>Giết</th>
              <th style={{ textAlign: "left", padding: 10 }}>Chết</th>
              <th style={{ textAlign: "left", padding: 10 }}>Mạng còn</th>
              <th style={{ textAlign: "left", padding: 10 }}>OVR</th>
            </tr>
          </thead>
          <tbody>
            {players.map((p, index) => (
              <tr
                key={p.id}
                style={{
                  background:
                    index % 2 === 0
                      ? "rgba(255,255,255,0.02)"
                      : "rgba(255,255,255,0.04)",
                }}
              >
                <td style={{ padding: 10 }}>{p.characterName || `P${p.id}`}</td>
                <td style={{ padding: 10 }}>{p.bombsPlaced}</td>
                <td style={{ padding: 10 }}>{p.kills}</td>
                <td style={{ padding: 10 }}>{p.deaths}</td>
                <td style={{ padding: 10 }}>{p.lives}</td>
                <td style={{ padding: 10 }}>{p.ovr}</td>
              </tr>
            ))}
          </tbody>
        </table>

        <div
          style={{
            marginTop: 20,
            display: "flex",
            justifyContent: "center",
          }}
        >
          <Button onClick={onRestart}>Chơi lại</Button>
        </div>
      </div>
    </div>
  );
}

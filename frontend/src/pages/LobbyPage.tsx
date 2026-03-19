import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import Button from "../components/common/Button";
import Card from "../components/common/Card";
import Loading from "../components/common/Loading";
import { useAuth } from "../hooks/useAuth";

const chipStyle: React.CSSProperties = {
  display: "inline-flex",
  alignItems: "center",
  gap: 8,
  padding: "8px 12px",
  borderRadius: 999,
  background: "rgba(255,255,255,0.06)",
  border: "1px solid rgba(255,255,255,0.08)",
  color: "#dbeafe",
  fontSize: 13,
  fontWeight: 700,
};

export default function LobbyPage() {
  const navigate = useNavigate();
  const { profile, loading, logout } = useAuth();

  useEffect(() => {
    if (!loading && profile && !profile.profileCompleted) {
      navigate("/character", { replace: true });
    }
  }, [loading, profile, navigate]);

  if (loading) {
    return <Loading text="Đang tải sảnh game..." />;
  }

  return (
    <div
      style={{
        minHeight: "100vh",
        padding: 24,
        background:
          "radial-gradient(circle at top left, rgba(59,130,246,0.20), transparent 24%), radial-gradient(circle at top right, rgba(34,197,94,0.12), transparent 18%), radial-gradient(circle at bottom right, rgba(168,85,247,0.12), transparent 20%), linear-gradient(180deg, #030712 0%, #08111f 45%, #0f172a 100%)",
      }}
    >
      <div style={{ maxWidth: 1250, margin: "0 auto" }}>
        <Card style={{ padding: 28, marginBottom: 18 }}>
          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              gap: 20,
              alignItems: "flex-start",
              flexWrap: "wrap",
            }}
          >
            <div style={{ maxWidth: 760 }}>
              <div style={{ ...chipStyle, marginBottom: 14 }}>
                🎮 GAME LOBBY
              </div>

              <h1
                style={{
                  margin: 0,
                  color: "#fff",
                  fontSize: 42,
                  lineHeight: 1.1,
                }}
              >
                Sảnh game
              </h1>

              <p
                style={{
                  color: "#cbd5e1",
                  lineHeight: 1.8,
                  marginTop: 14,
                  marginBottom: 0,
                  fontSize: 16,
                }}
              >
                Chọn chế độ chơi, xem thông tin nhân vật, vào trận nhanh hoặc
                tạo phòng riêng để chơi cùng bạn bè.
              </p>
            </div>

            <div style={{ ...chipStyle, color: "#86efac" }}>
              {profile?.characterName || "Người chơi"}
            </div>
          </div>
        </Card>

        <div
          style={{
            display: "grid",
            gridTemplateColumns: "1.05fr 1fr",
            gap: 18,
          }}
        >
          <Card>
            <h3 style={{ marginTop: 0, marginBottom: 18, color: "#fff" }}>
              Thông tin người chơi
            </h3>

            <div
              style={{
                display: "grid",
                gridTemplateColumns: "repeat(2, minmax(0, 1fr))",
                gap: 12,
              }}
            >
              <div
                style={{
                  padding: 14,
                  borderRadius: 16,
                  background: "rgba(255,255,255,0.04)",
                  border: "1px solid rgba(255,255,255,0.06)",
                }}
              >
                <div
                  style={{ color: "#94a3b8", fontSize: 13, marginBottom: 6 }}
                >
                  Username
                </div>
                <div style={{ color: "#fff", fontWeight: 700 }}>
                  {profile?.username || "-"}
                </div>
              </div>

              <div
                style={{
                  padding: 14,
                  borderRadius: 16,
                  background: "rgba(255,255,255,0.04)",
                  border: "1px solid rgba(255,255,255,0.06)",
                }}
              >
                <div
                  style={{ color: "#94a3b8", fontSize: 13, marginBottom: 6 }}
                >
                  Email
                </div>
                <div
                  style={{
                    color: "#fff",
                    fontWeight: 700,
                    wordBreak: "break-word",
                  }}
                >
                  {profile?.email || "-"}
                </div>
              </div>

              <div
                style={{
                  padding: 14,
                  borderRadius: 16,
                  background: "rgba(255,255,255,0.04)",
                  border: "1px solid rgba(255,255,255,0.06)",
                }}
              >
                <div
                  style={{ color: "#94a3b8", fontSize: 13, marginBottom: 6 }}
                >
                  Tên nhân vật
                </div>
                <div style={{ color: "#fff", fontWeight: 700 }}>
                  {profile?.characterName || "-"}
                </div>
              </div>

              <div
                style={{
                  padding: 14,
                  borderRadius: 16,
                  background: "rgba(255,255,255,0.04)",
                  border: "1px solid rgba(255,255,255,0.06)",
                }}
              >
                <div
                  style={{ color: "#94a3b8", fontSize: 13, marginBottom: 6 }}
                >
                  Giới tính
                </div>
                <div style={{ color: "#fff", fontWeight: 700 }}>
                  {profile?.gender === "MALE"
                    ? "Nam"
                    : profile?.gender === "FEMALE"
                      ? "Nữ"
                      : "-"}
                </div>
              </div>
            </div>

            <div
              style={{
                display: "flex",
                gap: 12,
                marginTop: 20,
                flexWrap: "wrap",
              }}
            >
              <Button onClick={() => navigate("/character")}>
                Đổi nhân vật
              </Button>

              <Button
                onClick={() => navigate("/history")}
                style={{
                  background: "linear-gradient(180deg, #a855f7, #7e22ce)",
                  boxShadow: "0 12px 28px rgba(126,34,206,0.30)",
                }}
              >
                Xem lịch sử đấu
              </Button>

              <Button
                onClick={() => {
                  logout();
                  navigate("/login");
                }}
                style={{
                  background: "linear-gradient(180deg, #ef4444, #dc2626)",
                  boxShadow: "0 12px 28px rgba(220,38,38,0.30)",
                }}
              >
                Đăng xuất
              </Button>
            </div>
          </Card>

          <Card>
            <h3 style={{ marginTop: 0, marginBottom: 18, color: "#fff" }}>
              Chọn chế độ
            </h3>

            <div
              style={{
                display: "grid",
                gap: 14,
              }}
            >
              <div
                style={{
                  padding: 18,
                  borderRadius: 18,
                  background:
                    "linear-gradient(135deg, rgba(34,197,94,0.12), rgba(255,255,255,0.03))",
                  border: "1px solid rgba(34,197,94,0.18)",
                }}
              >
                <div
                  style={{
                    color: "#86efac",
                    fontSize: 13,
                    fontWeight: 800,
                    marginBottom: 6,
                  }}
                >
                  QUICK PLAY
                </div>
                <div
                  style={{
                    color: "#fff",
                    fontSize: 22,
                    fontWeight: 800,
                    marginBottom: 8,
                  }}
                >
                  Chơi ngay
                </div>
                <div
                  style={{
                    color: "#cbd5e1",
                    lineHeight: 1.7,
                    marginBottom: 14,
                  }}
                >
                  Vào trận nhanh bằng hệ thống realtime hiện tại.
                </div>

                <Button
                  onClick={() => navigate("/game")}
                  style={{
                    background: "linear-gradient(180deg, #22c55e, #16a34a)",
                    boxShadow: "0 12px 28px rgba(22,163,74,0.30)",
                  }}
                >
                  Vào trận ngay
                </Button>
              </div>

              <div
                style={{
                  padding: 18,
                  borderRadius: 18,
                  background:
                    "linear-gradient(135deg, rgba(59,130,246,0.12), rgba(255,255,255,0.03))",
                  border: "1px solid rgba(59,130,246,0.18)",
                }}
              >
                <div
                  style={{
                    color: "#93c5fd",
                    fontSize: 13,
                    fontWeight: 800,
                    marginBottom: 6,
                  }}
                >
                  PRIVATE ROOM
                </div>
                <div
                  style={{
                    color: "#fff",
                    fontSize: 22,
                    fontWeight: 800,
                    marginBottom: 8,
                  }}
                >
                  Phòng riêng
                </div>
                <div
                  style={{
                    color: "#cbd5e1",
                    lineHeight: 1.7,
                    marginBottom: 14,
                  }}
                >
                  Tạo phòng 2, 3 hoặc 4 người. Mời bạn bè hoặc nhập mã phòng để
                  chơi cùng.
                </div>

                <Button
                  onClick={() => navigate("/rooms")}
                  style={{
                    background: "linear-gradient(180deg, #3b82f6, #2563eb)",
                    boxShadow: "0 12px 28px rgba(37,99,235,0.30)",
                  }}
                >
                  Vào khu phòng riêng
                </Button>
              </div>
            </div>
          </Card>
        </div>
      </div>
    </div>
  );
}

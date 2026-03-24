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
  const { profile, loading, logout, isAdmin } = useAuth();

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

            <div
              style={{
                display: "flex",
                gap: 10,
                flexWrap: "wrap",
                alignItems: "center",
              }}
            >
              {isAdmin && (
                <div
                  style={{
                    ...chipStyle,
                    color: "#fde68a",
                    background: "rgba(250,204,21,0.14)",
                    border: "1px solid rgba(250,204,21,0.24)",
                  }}
                >
                  ADMIN
                </div>
              )}
              <div style={{ ...chipStyle, color: "#86efac" }}>
                {profile?.characterName || "Người chơi"}
              </div>
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
              <InfoItem label="Username" value={profile?.username || "-"} />
              <InfoItem label="Email" value={profile?.email || "-"} breakWord />
              <InfoItem
                label="Tên nhân vật"
                value={profile?.characterName || "-"}
              />
              <InfoItem
                label="Giới tính"
                value={
                  profile?.gender === "MALE"
                    ? "Nam"
                    : profile?.gender === "FEMALE"
                      ? "Nữ"
                      : "-"
                }
              />
              <InfoItem
                label="Vai trò"
                value={profile?.role === "ADMIN" ? "Admin" : "Người chơi"}
              />
              <InfoItem
                label="Trạng thái hồ sơ"
                value={
                  profile?.profileCompleted ? "Đã hoàn tất" : "Chưa hoàn tất"
                }
              />
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
                onClick={() => navigate("/friends")}
                style={{
                  background: "linear-gradient(180deg, #06b6d4, #0891b2)",
                  boxShadow: "0 12px 28px rgba(8,145,178,0.30)",
                }}
              >
                Bạn bè & chat
              </Button>

              {isAdmin && (
                <Button
                  onClick={() => navigate("/admin")}
                  style={{
                    background: "linear-gradient(180deg, #f59e0b, #d97706)",
                    boxShadow: "0 12px 28px rgba(217,119,6,0.30)",
                  }}
                >
                  Trang admin
                </Button>
              )}

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

            <div style={{ display: "grid", gap: 14 }}>
              <ModeCard
                title="QUICK PLAY"
                titleColor="#86efac"
                background="linear-gradient(135deg, rgba(34,197,94,0.12), rgba(255,255,255,0.03))"
                border="1px solid rgba(34,197,94,0.18)"
                text="Tìm trận nhanh để vào game ngay với cấu hình có sẵn của hệ thống."
                actionText="Chơi ngay"
                onClick={() => navigate("/game")}
              />

              <ModeCard
                title="ROOM LOBBY"
                titleColor="#93c5fd"
                background="linear-gradient(135deg, rgba(59,130,246,0.12), rgba(255,255,255,0.03))"
                border="1px solid rgba(59,130,246,0.18)"
                text="Tạo phòng 2, 3 hoặc 4 người rồi mời bạn bè hoặc thêm bot vào chơi cùng."
                actionText="Vào phòng chờ"
                onClick={() => navigate("/rooms")}
              />

              <ModeCard
                title="MATCH HISTORY"
                titleColor="#d8b4fe"
                background="linear-gradient(135deg, rgba(168,85,247,0.12), rgba(255,255,255,0.03))"
                border="1px solid rgba(168,85,247,0.18)"
                text="Xem lại kết quả trận đấu, người thắng và chỉ số của các trận bạn đã chơi."
                actionText="Xem lịch sử"
                onClick={() => navigate("/history")}
              />
            </div>
          </Card>
        </div>
      </div>
    </div>
  );
}

function InfoItem({
  label,
  value,
  breakWord = false,
}: {
  label: string;
  value: string;
  breakWord?: boolean;
}) {
  return (
    <div
      style={{
        padding: 14,
        borderRadius: 16,
        background: "rgba(255,255,255,0.04)",
        border: "1px solid rgba(255,255,255,0.06)",
      }}
    >
      <div style={{ color: "#94a3b8", fontSize: 13, marginBottom: 6 }}>
        {label}
      </div>
      <div
        style={{
          color: "#fff",
          fontWeight: 700,
          wordBreak: breakWord ? "break-word" : "normal",
        }}
      >
        {value}
      </div>
    </div>
  );
}

function ModeCard({
  title,
  titleColor,
  background,
  border,
  text,
  actionText,
  onClick,
}: {
  title: string;
  titleColor: string;
  background: string;
  border: string;
  text: string;
  actionText: string;
  onClick: () => void;
}) {
  return (
    <div
      style={{
        padding: 18,
        borderRadius: 18,
        background,
        border,
      }}
    >
      <div
        style={{
          color: titleColor,
          fontSize: 13,
          fontWeight: 800,
          marginBottom: 6,
        }}
      >
        {title}
      </div>
      <div style={{ color: "#e2e8f0", lineHeight: 1.7, marginBottom: 16 }}>
        {text}
      </div>
      <Button onClick={onClick}>{actionText}</Button>
    </div>
  );
}

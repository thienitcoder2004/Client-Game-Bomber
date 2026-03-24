import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import Button from "../components/common/Button";
import Card from "../components/common/Card";
import Loading from "../components/common/Loading";
import { useAuth } from "../hooks/useAuth";
import { adminApi } from "../services/adminApi";
import type { AdminMatch, AdminUser } from "../types/admin";

type TabKey = "users" | "matches";

function formatDate(value?: string) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "-";

  return new Intl.DateTimeFormat("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).format(date);
}

export default function AdminPage() {
  const navigate = useNavigate();
  const { profile, loading: authLoading } = useAuth();

  const [tab, setTab] = useState<TabKey>("users");
  const [loading, setLoading] = useState(true);
  const [errorText, setErrorText] = useState("");
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [matches, setMatches] = useState<AdminMatch[]>([]);
  const [workingUserId, setWorkingUserId] = useState<string | null>(null);

  const sortedUsers = useMemo(() => {
    return [...users].sort((a, b) => {
      if (a.role === "ADMIN" && b.role !== "ADMIN") return -1;
      if (a.role !== "ADMIN" && b.role === "ADMIN") return 1;
      return (a.email || "").localeCompare(b.email || "");
    });
  }, [users]);

  const loadAll = async () => {
    try {
      setLoading(true);
      setErrorText("");

      const [usersData, matchesData] = await Promise.all([
        adminApi.getUsers(),
        adminApi.getMatches(),
      ]);

      setUsers(usersData);
      setMatches(matchesData);
    } catch (error) {
      setErrorText(
        error instanceof Error ? error.message : "Không tải được dữ liệu admin",
      );
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!authLoading && profile?.role !== "ADMIN") {
      navigate("/lobby", { replace: true });
      return;
    }

    if (!authLoading && profile?.role === "ADMIN") {
      loadAll();
    }
  }, [authLoading, profile?.role]);

  const onLock = async (userId: string) => {
    if (!window.confirm("Bạn có chắc muốn khóa tài khoản này không?")) return;

    try {
      setWorkingUserId(userId);
      const updated = await adminApi.lockUser(userId);
      setUsers((prev) =>
        prev.map((item) => (item.id === userId ? updated : item)),
      );
    } catch (error) {
      alert(error instanceof Error ? error.message : "Khóa tài khoản thất bại");
    } finally {
      setWorkingUserId(null);
    }
  };

  const onUnlock = async (userId: string) => {
    try {
      setWorkingUserId(userId);
      const updated = await adminApi.unlockUser(userId);
      setUsers((prev) =>
        prev.map((item) => (item.id === userId ? updated : item)),
      );
    } catch (error) {
      alert(
        error instanceof Error ? error.message : "Mở khóa tài khoản thất bại",
      );
    } finally {
      setWorkingUserId(null);
    }
  };

  const onDelete = async (userId: string) => {
    const ok = window.confirm(
      "Bạn có chắc muốn xóa tài khoản này không? Hành động này không thể hoàn tác.",
    );
    if (!ok) return;

    try {
      setWorkingUserId(userId);
      await adminApi.deleteUser(userId);
      setUsers((prev) => prev.filter((item) => item.id !== userId));
    } catch (error) {
      alert(error instanceof Error ? error.message : "Xóa tài khoản thất bại");
    } finally {
      setWorkingUserId(null);
    }
  };

  if (authLoading || loading) {
    return <Loading text="Đang tải trang admin..." />;
  }

  return (
    <div
      style={{
        minHeight: "100vh",
        padding: 24,
        background:
          "radial-gradient(circle at top left, rgba(59,130,246,0.20), transparent 24%), radial-gradient(circle at top right, rgba(234,179,8,0.16), transparent 18%), radial-gradient(circle at bottom right, rgba(168,85,247,0.12), transparent 20%), linear-gradient(180deg, #030712 0%, #08111f 45%, #0f172a 100%)",
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
              <div
                style={{
                  display: "inline-flex",
                  alignItems: "center",
                  gap: 8,
                  padding: "8px 12px",
                  borderRadius: 999,
                  background: "rgba(250,204,21,0.14)",
                  border: "1px solid rgba(250,204,21,0.24)",
                  color: "#fde68a",
                  fontSize: 13,
                  fontWeight: 800,
                  marginBottom: 14,
                }}
              >
                🛡️ ADMIN PANEL
              </div>

              <h1
                style={{
                  margin: 0,
                  color: "#fff",
                  fontSize: 42,
                  lineHeight: 1.1,
                }}
              >
                Quản trị game
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
                Xem danh sách tài khoản, khóa, mở khóa, xóa người chơi và theo
                dõi lịch sử trận đấu.
              </p>
            </div>

            <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
              <Button onClick={loadAll}>Tải lại</Button>
              <Button
                onClick={() => navigate("/lobby")}
                style={{
                  background: "linear-gradient(180deg, #64748b, #334155)",
                  boxShadow: "0 12px 28px rgba(51,65,85,0.28)",
                }}
              >
                Về lobby
              </Button>
            </div>
          </div>
        </Card>

        <div
          style={{
            display: "flex",
            gap: 12,
            marginBottom: 18,
            flexWrap: "wrap",
          }}
        >
          <Button onClick={() => setTab("users")}>Quản lý user</Button>
          <Button
            onClick={() => setTab("matches")}
            style={{
              background:
                tab === "matches"
                  ? "linear-gradient(180deg, #a855f7, #7e22ce)"
                  : "linear-gradient(180deg, #3b82f6, #1d4ed8)",
            }}
          >
            Lịch sử trận
          </Button>
        </div>

        {errorText && (
          <Card
            style={{ marginBottom: 18, borderColor: "rgba(239,68,68,0.28)" }}
          >
            <div style={{ color: "#fca5a5", fontWeight: 700 }}>{errorText}</div>
          </Card>
        )}

        {tab === "users" ? (
          <Card style={{ overflowX: "auto" }}>
            <div
              style={{
                color: "#fff",
                fontSize: 22,
                fontWeight: 800,
                marginBottom: 16,
              }}
            >
              Danh sách người chơi
            </div>

            <table
              style={{
                width: "100%",
                minWidth: 1120,
                borderCollapse: "collapse",
              }}
            >
              <thead>
                <tr>
                  <th style={thStyle}>Email</th>
                  <th style={thStyle}>Username</th>
                  <th style={thStyle}>Tên nhân vật</th>
                  <th style={thStyle}>Giới tính</th>
                  <th style={thStyle}>Role</th>
                  <th style={thStyle}>Trạng thái</th>
                  <th style={thStyle}>Tạo lúc</th>
                  <th style={thStyle}>Hành động</th>
                </tr>
              </thead>
              <tbody>
                {sortedUsers.map((user) => {
                  const isProcessing = workingUserId === user.id;

                  return (
                    <tr key={user.id}>
                      <td style={tdStyle}>{user.email || "-"}</td>
                      <td style={tdStyle}>{user.username || "-"}</td>
                      <td style={tdStyle}>{user.characterName || "-"}</td>
                      <td style={tdStyle}>
                        {user.gender === "MALE"
                          ? "Nam"
                          : user.gender === "FEMALE"
                            ? "Nữ"
                            : "-"}
                      </td>
                      <td style={tdStyle}>
                        <span
                          style={badgeStyle(
                            user.role === "ADMIN" ? "admin" : "user",
                          )}
                        >
                          {user.role || "USER"}
                        </span>
                      </td>
                      <td style={tdStyle}>
                        <span
                          style={badgeStyle(user.active ? "active" : "locked")}
                        >
                          {user.active ? "Hoạt động" : "Đã khóa"}
                        </span>
                      </td>
                      <td style={tdStyle}>{formatDate(user.createdAt)}</td>
                      <td style={tdStyle}>
                        <div
                          style={{ display: "flex", gap: 8, flexWrap: "wrap" }}
                        >
                          {user.active ? (
                            <Button
                              onClick={() => onLock(user.id)}
                              disabled={isProcessing}
                              style={{
                                padding: "10px 14px",
                                background:
                                  "linear-gradient(180deg, #ef4444, #dc2626)",
                                boxShadow: "0 12px 28px rgba(220,38,38,0.28)",
                              }}
                            >
                              {isProcessing ? "Đang khóa..." : "Khóa"}
                            </Button>
                          ) : (
                            <Button
                              onClick={() => onUnlock(user.id)}
                              disabled={isProcessing}
                              style={{
                                padding: "10px 14px",
                                background:
                                  "linear-gradient(180deg, #22c55e, #15803d)",
                                boxShadow: "0 12px 28px rgba(21,128,61,0.28)",
                              }}
                            >
                              {isProcessing ? "Đang mở..." : "Mở khóa"}
                            </Button>
                          )}

                          <Button
                            onClick={() => onDelete(user.id)}
                            disabled={isProcessing}
                            style={{
                              padding: "10px 14px",
                              background:
                                "linear-gradient(180deg, #7f1d1d, #991b1b)",
                              boxShadow: "0 12px 28px rgba(127,29,29,0.28)",
                            }}
                          >
                            {isProcessing ? "Đang xử lý..." : "Xóa"}
                          </Button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </Card>
        ) : (
          <div style={{ display: "grid", gap: 16 }}>
            {matches.length === 0 ? (
              <Card>
                <div style={{ color: "#cbd5e1" }}>
                  Chưa có trận nào được lưu.
                </div>
              </Card>
            ) : (
              matches.map((match) => (
                <Card key={match.id}>
                  <div
                    style={{
                      display: "grid",
                      gridTemplateColumns:
                        "repeat(auto-fit, minmax(220px, 1fr))",
                      gap: 16,
                    }}
                  >
                    <InfoBlock
                      label="Room code"
                      value={match.roomCode || "-"}
                    />
                    <InfoBlock
                      label="Người thắng"
                      value={match.winnerCharacterName || "Hòa"}
                    />
                    <InfoBlock
                      label="Bắt đầu"
                      value={formatDate(match.startedAt)}
                    />
                    <InfoBlock
                      label="Kết thúc"
                      value={formatDate(match.endedAt)}
                    />
                    <InfoBlock
                      label="Số người chơi"
                      value={String(match.playerCount || 0)}
                    />
                  </div>

                  <div style={{ marginTop: 16 }}>
                    <div
                      style={{
                        color: "#94a3b8",
                        fontSize: 13,
                        marginBottom: 6,
                      }}
                    >
                      Danh sách người chơi
                    </div>
                    <div
                      style={{
                        color: "#fff",
                        fontWeight: 700,
                        lineHeight: 1.7,
                      }}
                    >
                      {match.playerNames?.length
                        ? match.playerNames.join(", ")
                        : "-"}
                    </div>
                  </div>
                </Card>
              ))
            )}
          </div>
        )}
      </div>
    </div>
  );
}

function InfoBlock({ label, value }: { label: string; value: string }) {
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
      <div style={{ color: "#fff", fontWeight: 700 }}>{value}</div>
    </div>
  );
}

const thStyle: React.CSSProperties = {
  color: "#94a3b8",
  fontSize: 13,
  textAlign: "left",
  padding: "0 12px 12px",
  borderBottom: "1px solid rgba(255,255,255,0.08)",
};

const tdStyle: React.CSSProperties = {
  color: "#fff",
  fontSize: 14,
  padding: "16px 12px",
  borderBottom: "1px solid rgba(255,255,255,0.06)",
  verticalAlign: "middle",
};

function badgeStyle(
  type: "admin" | "user" | "active" | "locked",
): React.CSSProperties {
  if (type === "admin") {
    return {
      display: "inline-flex",
      padding: "7px 12px",
      borderRadius: 999,
      color: "#fde68a",
      background: "rgba(250,204,21,0.14)",
      border: "1px solid rgba(250,204,21,0.24)",
      fontWeight: 800,
      fontSize: 12,
    };
  }

  if (type === "user") {
    return {
      display: "inline-flex",
      padding: "7px 12px",
      borderRadius: 999,
      color: "#bfdbfe",
      background: "rgba(59,130,246,0.14)",
      border: "1px solid rgba(59,130,246,0.24)",
      fontWeight: 800,
      fontSize: 12,
    };
  }

  if (type === "active") {
    return {
      display: "inline-flex",
      padding: "7px 12px",
      borderRadius: 999,
      color: "#86efac",
      background: "rgba(34,197,94,0.14)",
      border: "1px solid rgba(34,197,94,0.24)",
      fontWeight: 800,
      fontSize: 12,
    };
  }

  return {
    display: "inline-flex",
    padding: "7px 12px",
    borderRadius: 999,
    color: "#fca5a5",
    background: "rgba(239,68,68,0.14)",
    border: "1px solid rgba(239,68,68,0.24)",
    fontWeight: 800,
    fontSize: 12,
  };
}

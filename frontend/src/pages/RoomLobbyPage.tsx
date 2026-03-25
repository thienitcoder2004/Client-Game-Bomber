import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import Button from "../components/common/Button";
import { GAME_CONFIG, getDefaultRoomSize } from "../config/gameConfig";
import Card from "../components/common/Card";
import Input from "../components/common/Input";
import { useRoomLobbySocket } from "../hooks/useRoomLobbySocket";
import type { MatchMode } from "../game/roomWsTypes";

const badgeStyle: React.CSSProperties = {
  display: "inline-flex",
  alignItems: "center",
  gap: 8,
  padding: "8px 12px",
  borderRadius: 999,
  background: "rgba(255,255,255,0.06)",
  border: "1px solid rgba(255,255,255,0.08)",
  color: "#e2e8f0",
  fontSize: 13,
  fontWeight: 700,
};

/**
 * Tính text + màu trạng thái phòng
 *
 * WAITING:
 * - thiếu người  -> "Thiếu X người"
 * - đủ người     -> "Đã đủ người"
 *
 * PLAYING:
 * - đang chơi    -> "Đang chơi"
 */
function getRoomStatusMeta(room: {
  playerCount: number;
  maxPlayers: number;
  status: string;
}) {
  const missingPlayers = Math.max(0, room.maxPlayers - room.playerCount);
  const full = room.playerCount >= room.maxPlayers;
  const playing = room.status === "PLAYING";

  if (playing) {
    return {
      text: "Đang chơi",
      background: "rgba(245,158,11,0.12)",
      color: "#fcd34d",
      border: "1px solid rgba(245,158,11,0.22)",
    };
  }

  if (full) {
    return {
      text: "Đã đủ người",
      background: "rgba(34,197,94,0.10)",
      color: "#86efac",
      border: "1px solid rgba(34,197,94,0.20)",
    };
  }

  return {
    text: `Thiếu ${missingPlayers} người`,
    background: "rgba(59,130,246,0.12)",
    color: "#93c5fd",
    border: "1px solid rgba(59,130,246,0.22)",
  };
}

/**
 * Meta để hiện nhãn mode
 */
function getMatchModeMeta(matchMode: MatchMode) {
  if (matchMode === "DUO") {
    return {
      label: "Chơi đôi 2v2",
      shortLabel: "DUO",
      description: "4 người chia 2 đội, mỗi đội 2 người",
      background: "rgba(59,130,246,0.12)",
      color: "#93c5fd",
      border: "1px solid rgba(59,130,246,0.22)",
    };
  }

  return {
    label: "Chơi đơn",
    shortLabel: "SOLO",
    description: "4 người đánh tự do, ai sống cuối cùng thắng",
    background: "rgba(34,197,94,0.10)",
    color: "#86efac",
    border: "1px solid rgba(34,197,94,0.20)",
  };
}

export default function RoomLobbyPage() {
  const navigate = useNavigate();
  const {
    connected,
    statusText,
    rooms,
    currentRoom,
    createRoom,
    joinRoom,
    leaveRoom,
    startRoom,
    refreshRooms,
    lastStartedRoomInfo,
    addBot,
    removeBot,
    kickMember,
  } = useRoomLobbySocket();

  const [search, setSearch] = useState("");
  const [roomCodeInput, setRoomCodeInput] = useState("");
  const [showCreateModal, setShowCreateModal] = useState(false);

  const [roomName, setRoomName] = useState("");
  const [maxPlayers, setMaxPlayers] = useState<number>(getDefaultRoomSize());
  const [isPrivate, setIsPrivate] = useState(false);

  // ===== mode mới của phòng =====
  const [matchMode, setMatchMode] = useState<MatchMode>("SOLO");

  /**
   * Khi chủ phòng bấm Chơi:
   * - backend gửi room_started
   * - frontend chuyển sang màn game
   * - truyền thêm matchMode để game socket biết trận này SOLO hay DUO
   */
  useEffect(() => {
    if (!lastStartedRoomInfo) return;

    navigate(
      `/game?mode=room` +
        `&roomCode=${encodeURIComponent(lastStartedRoomInfo.roomCode)}` +
        `&requiredPlayers=${lastStartedRoomInfo.maxPlayers}` +
        `&humanCount=${lastStartedRoomInfo.humanCount}` +
        `&botCount=${lastStartedRoomInfo.botCount}` +
        `&matchMode=${lastStartedRoomInfo.matchMode}`,
      { replace: true },
    );
  }, [lastStartedRoomInfo, navigate]);

  /**
   * Lọc phòng theo từ khóa:
   * - tên phòng
   * - mã phòng
   * - tên chủ phòng
   */
  const filteredRooms = useMemo(() => {
    const keyword = search.trim().toLowerCase();

    return rooms.filter((room) => {
      if (!keyword) return true;

      return (
        room.roomName.toLowerCase().includes(keyword) ||
        room.roomCode.toLowerCase().includes(keyword) ||
        room.hostName.toLowerCase().includes(keyword)
      );
    });
  }, [rooms, search]);

  /**
   * Tạo phòng
   *
   * - SOLO: dùng maxPlayers người chơi như đang chọn
   * - DUO : luôn ép 4 người vì là 2v2
   */
  const handleCreateRoom = () => {
    const finalMaxPlayers = matchMode === "DUO" ? 4 : maxPlayers;

    createRoom(roomName, finalMaxPlayers, isPrivate, matchMode);

    setShowCreateModal(false);
    setRoomName("");
    setMaxPlayers(getDefaultRoomSize());
    setIsPrivate(false);
    setMatchMode("SOLO");
  };

  /**
   * Điều kiện host có thể thêm bot
   */
  const canAddBot =
    !!currentRoom &&
    currentRoom.isHost &&
    currentRoom.playerCount < currentRoom.maxPlayers &&
    currentRoom.status !== "PLAYING";

  /**
   * Trạng thái phòng hiện tại
   */
  const currentRoomStatusMeta = currentRoom
    ? getRoomStatusMeta({
        playerCount: currentRoom.playerCount,
        maxPlayers: currentRoom.maxPlayers,
        status: currentRoom.status,
      })
    : null;

  const currentRoomMissingPlayers = currentRoom
    ? Math.max(0, currentRoom.maxPlayers - currentRoom.playerCount)
    : 0;

  const currentRoomModeMeta = currentRoom
    ? getMatchModeMeta(currentRoom.matchMode ?? "SOLO")
    : null;

  const selectedModeMeta = getMatchModeMeta(matchMode);

  return (
    <div
      style={{
        minHeight: "100vh",
        padding: 24,
        background:
          "radial-gradient(circle at top left, rgba(59,130,246,0.20), transparent 24%), radial-gradient(circle at top right, rgba(34,197,94,0.12), transparent 18%), radial-gradient(circle at bottom right, rgba(168,85,247,0.12), transparent 20%), linear-gradient(180deg, #030712 0%, #08111f 45%, #0f172a 100%)",
      }}
    >
      <div style={{ maxWidth: 1380, margin: "0 auto" }}>
        {/* =====================================================
            HEADER TRANG
        ===================================================== */}
        <Card style={{ marginBottom: 18, padding: 28 }}>
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
              <div style={{ ...badgeStyle, marginBottom: 14 }}>
                🎮 PRIVATE ROOM SYSTEM
              </div>

              <h1
                style={{
                  margin: 0,
                  color: "#fff",
                  fontSize: 42,
                  lineHeight: 1.1,
                }}
              >
                Phòng riêng
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
                Tạo phòng mới, tìm phòng đang chờ hoặc nhập mã phòng để tham
                gia. Chủ phòng là người đầu tiên tạo và là người bấm nút Chơi.
              </p>
            </div>

            <div
              style={{
                display: "flex",
                gap: 12,
                flexWrap: "wrap",
                alignItems: "center",
              }}
            >
              <Button
                onClick={() => navigate("/lobby")}
                style={{
                  background: "linear-gradient(180deg, #475569, #334155)",
                  boxShadow: "none",
                }}
              >
                ← Về sảnh
              </Button>

              <Button onClick={refreshRooms}>Làm mới</Button>

              <Button
                onClick={() => setShowCreateModal(true)}
                style={{
                  background: "linear-gradient(180deg, #22c55e, #16a34a)",
                  boxShadow: "0 12px 28px rgba(22,163,74,0.30)",
                }}
              >
                + Tạo phòng
              </Button>
            </div>
          </div>

          {/* =====================================================
              TÌM PHÒNG / NHẬP MÃ PHÒNG
          ===================================================== */}
          <div
            style={{
              marginTop: 20,
              display: "grid",
              gridTemplateColumns: "1fr auto",
              gap: 14,
              alignItems: "end",
            }}
          >
            <Input
              label="Tìm phòng"
              value={search}
              onChange={setSearch}
              placeholder="Tên phòng, mã phòng, chủ phòng..."
            />

            <div style={{ minWidth: 260 }}>
              <Input
                label="Nhập mã phòng"
                value={roomCodeInput}
                onChange={setRoomCodeInput}
                placeholder="Ví dụ: #1234"
              />
              <Button
                onClick={() => joinRoom(roomCodeInput.trim().toUpperCase())}
                style={{ width: "100%" }}
              >
                Vào bằng mã phòng
              </Button>
            </div>
          </div>

          {/* =====================================================
              TRẠNG THÁI KẾT NỐI SOCKET
          ===================================================== */}
          <div
            style={{
              marginTop: 16,
              display: "flex",
              justifyContent: "space-between",
              gap: 12,
              flexWrap: "wrap",
              alignItems: "center",
            }}
          >
            <span
              style={{
                ...badgeStyle,
                background: connected
                  ? "rgba(34,197,94,0.10)"
                  : "rgba(239,68,68,0.10)",
                color: connected ? "#86efac" : "#fca5a5",
              }}
            >
              {connected ? "Đã kết nối" : "Mất kết nối"}
            </span>

            <div style={{ color: "#94a3b8", fontSize: 14 }}>{statusText}</div>
          </div>
        </Card>

        {/* =====================================================
            PHÒNG HIỆN TẠI
        ===================================================== */}
        {currentRoom && (
          <Card
            style={{
              marginBottom: 18,
              background:
                "linear-gradient(135deg, rgba(59,130,246,0.10), rgba(15,23,42,0.94))",
            }}
          >
            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                gap: 16,
                flexWrap: "wrap",
                alignItems: "center",
                marginBottom: 16,
              }}
            >
              <div>
                <div
                  style={{
                    color: "#93c5fd",
                    fontSize: 13,
                    fontWeight: 800,
                    marginBottom: 4,
                  }}
                >
                  PHÒNG HIỆN TẠI
                </div>

                <h3 style={{ margin: 0, color: "#fff", fontSize: 28 }}>
                  {currentRoom.roomName}
                </h3>

                <div
                  style={{
                    marginTop: 10,
                    display: "flex",
                    gap: 8,
                    flexWrap: "wrap",
                  }}
                >
                  <span style={badgeStyle}>Mã: {currentRoom.roomCode}</span>

                  <span style={badgeStyle}>
                    {currentRoom.playerCount}/{currentRoom.maxPlayers}
                  </span>

                  <span
                    style={{
                      ...badgeStyle,
                      background: currentRoomStatusMeta?.background,
                      color: currentRoomStatusMeta?.color,
                      border: currentRoomStatusMeta?.border,
                    }}
                  >
                    {currentRoomStatusMeta?.text}
                  </span>

                  <span
                    style={{
                      ...badgeStyle,
                      background: currentRoomModeMeta?.background,
                      color: currentRoomModeMeta?.color,
                      border: currentRoomModeMeta?.border,
                    }}
                  >
                    {currentRoomModeMeta?.label}
                  </span>

                  <span
                    style={{
                      ...badgeStyle,
                      background: currentRoom.isHost
                        ? "rgba(34,197,94,0.12)"
                        : "rgba(255,255,255,0.06)",
                      color: currentRoom.isHost ? "#86efac" : "#e2e8f0",
                    }}
                  >
                    {currentRoom.isHost ? "👑 Chủ phòng" : "🎯 Thành viên"}
                  </span>
                </div>
              </div>
            </div>

            {/* =====================================================
                DANH SÁCH THÀNH VIÊN TRONG PHÒNG
            ===================================================== */}
            <div
              style={{
                display: "grid",
                gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))",
                gap: 12,
                marginBottom: 18,
              }}
            >
              {currentRoom.members.map((member) => {
                const isBot = Boolean(member.bot);

                const canRemoveThisBot =
                  currentRoom.isHost &&
                  isBot &&
                  currentRoom.status === "WAITING";

                const canKickThisMember =
                  currentRoom.isHost &&
                  !isBot &&
                  !member.host &&
                  currentRoom.status === "WAITING";

                return (
                  <div
                    key={member.clientId}
                    style={{
                      padding: 14,
                      borderRadius: 16,
                      background: member.host
                        ? "rgba(34,197,94,0.10)"
                        : isBot
                          ? "rgba(168,85,247,0.14)"
                          : "rgba(255,255,255,0.05)",
                      border: member.host
                        ? "1px solid rgba(34,197,94,0.18)"
                        : isBot
                          ? "1px solid rgba(168,85,247,0.26)"
                          : "1px solid rgba(255,255,255,0.06)",
                    }}
                  >
                    <div style={{ color: "#fff", fontWeight: 800 }}>
                      {member.characterName}
                    </div>

                    <div
                      style={{ color: "#94a3b8", marginTop: 5, fontSize: 13 }}
                    >
                      {member.host ? "Chủ phòng" : isBot ? "Bot" : "Người chơi"}
                    </div>

                    {isBot && (
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

                    {(canRemoveThisBot || canKickThisMember) && (
                      <div
                        style={{
                          marginTop: 12,
                          display: "flex",
                          gap: 8,
                          flexWrap: "wrap",
                        }}
                      >
                        {canRemoveThisBot && (
                          <Button
                            onClick={() => removeBot(member.clientId)}
                            style={{
                              background:
                                "linear-gradient(180deg, #f97316, #ea580c)",
                              boxShadow: "none",
                              padding: "10px 14px",
                            }}
                          >
                            Xóa bot
                          </Button>
                        )}

                        {canKickThisMember && (
                          <Button
                            onClick={() => kickMember(member.clientId)}
                            style={{
                              background:
                                "linear-gradient(180deg, #ef4444, #dc2626)",
                              boxShadow: "none",
                              padding: "10px 14px",
                            }}
                          >
                            Mời ra
                          </Button>
                        )}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>

            {/* =====================================================
                NÚT HÀNH ĐỘNG
            ===================================================== */}
            <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
              {currentRoom.isHost && (
                <>
                  <Button
                    onClick={addBot}
                    disabled={!canAddBot}
                    style={{
                      background: "linear-gradient(180deg, #a855f7, #7e22ce)",
                      boxShadow: "0 12px 28px rgba(126,34,206,0.30)",
                    }}
                  >
                    + Thêm bot
                  </Button>

                  <Button
                    onClick={startRoom}
                    disabled={!currentRoom.canStart}
                    style={{
                      background: "linear-gradient(180deg, #22c55e, #16a34a)",
                      boxShadow: "0 12px 28px rgba(22,163,74,0.30)",
                    }}
                  >
                    ▶ Chơi
                  </Button>
                </>
              )}

              <Button
                onClick={leaveRoom}
                style={{
                  background: "linear-gradient(180deg, #ef4444, #dc2626)",
                  boxShadow: "0 12px 28px rgba(220,38,38,0.30)",
                }}
              >
                Rời phòng
              </Button>
            </div>

            {/* =====================================================
                GỢI Ý / TRẠNG THÁI PHÒNG
            ===================================================== */}
            {currentRoom.status === "WAITING" &&
              currentRoom.isHost &&
              currentRoomMissingPlayers > 0 && (
                <div
                  style={{
                    marginTop: 12,
                    color: "#cbd5e1",
                    lineHeight: 1.6,
                  }}
                >
                  Phòng còn thiếu {currentRoomMissingPlayers} người. Chủ phòng
                  có thể thêm bot để đủ người nhanh hơn.
                </div>
              )}

            {currentRoom.status === "WAITING" &&
              currentRoom.isHost &&
              !currentRoom.canStart && (
                <div
                  style={{
                    marginTop: 12,
                    color: "#fbbf24",
                    lineHeight: 1.6,
                  }}
                >
                  Cần đủ {currentRoom.maxPlayers} người để chủ phòng bấm Chơi.
                </div>
              )}

            {currentRoom.status === "WAITING" &&
              currentRoom.playerCount === currentRoom.maxPlayers && (
                <div
                  style={{
                    marginTop: 12,
                    color: "#86efac",
                    lineHeight: 1.6,
                    fontWeight: 700,
                  }}
                >
                  Phòng đã đủ người, có thể bắt đầu trận đấu.
                </div>
              )}

            {currentRoom.status === "WAITING" &&
              currentRoom.matchMode === "DUO" && (
                <div
                  style={{
                    marginTop: 12,
                    color: "#93c5fd",
                    lineHeight: 1.6,
                    fontWeight: 700,
                  }}
                >
                  Chế độ chơi đôi: trận sẽ chạy theo luật 2v2.
                </div>
              )}

            {currentRoom.status === "PLAYING" && (
              <div
                style={{
                  marginTop: 12,
                  color: "#fcd34d",
                  lineHeight: 1.6,
                  fontWeight: 700,
                }}
              >
                Phòng đang trong trận đấu.
              </div>
            )}
          </Card>
        )}

        {/* =====================================================
            DANH SÁCH PHÒNG
        ===================================================== */}
        <Card>
          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              gap: 12,
              flexWrap: "wrap",
              alignItems: "center",
              marginBottom: 16,
            }}
          >
            <div>
              <h3 style={{ margin: 0, color: "#fff", fontSize: 28 }}>
                Danh sách phòng
              </h3>
              <div style={{ color: "#94a3b8", marginTop: 6 }}>
                Bấm vào phòng để tham gia nhanh
              </div>
            </div>

            <span style={badgeStyle}>{filteredRooms.length} phòng</span>
          </div>

          {filteredRooms.length === 0 ? (
            <div
              style={{
                padding: 28,
                borderRadius: 18,
                background: "rgba(255,255,255,0.04)",
                border: "1px solid rgba(255,255,255,0.05)",
                color: "#94a3b8",
                textAlign: "center",
              }}
            >
              Không có phòng phù hợp
            </div>
          ) : (
            <div
              style={{
                display: "grid",
                gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))",
                gap: 14,
              }}
            >
              {filteredRooms.map((room) => {
                const full = room.playerCount >= room.maxPlayers;
                const playing = room.status === "PLAYING";
                const statusMeta = getRoomStatusMeta({
                  playerCount: room.playerCount,
                  maxPlayers: room.maxPlayers,
                  status: room.status,
                });

                const roomModeMeta = getMatchModeMeta(room.matchMode ?? "SOLO");

                const missingPlayers = Math.max(
                  0,
                  room.maxPlayers - room.playerCount,
                );

                return (
                  <button
                    key={room.roomCode}
                    onClick={() => {
                      if (!full && !playing) joinRoom(room.roomCode);
                    }}
                    disabled={full || playing}
                    style={{
                      textAlign: "left",
                      padding: 18,
                      borderRadius: 20,
                      border: "1px solid rgba(255,255,255,0.08)",
                      background:
                        full || playing
                          ? "linear-gradient(180deg, rgba(71,85,105,0.40), rgba(30,41,59,0.42))"
                          : "linear-gradient(180deg, rgba(59,130,246,0.10), rgba(255,255,255,0.03))",
                      color: "#fff",
                      cursor: full || playing ? "not-allowed" : "pointer",
                      opacity: full || playing ? 0.75 : 1,
                      transition: "all 0.2s ease",
                      boxShadow:
                        full || playing
                          ? "none"
                          : "0 10px 24px rgba(37,99,235,0.14)",
                    }}
                  >
                    {/* Tên phòng + mã phòng */}
                    <div
                      style={{
                        display: "flex",
                        justifyContent: "space-between",
                        gap: 10,
                        alignItems: "center",
                        marginBottom: 12,
                      }}
                    >
                      <div
                        style={{
                          fontWeight: 800,
                          fontSize: 18,
                          lineHeight: 1.3,
                        }}
                      >
                        {room.roomName}
                      </div>

                      <span
                        style={{
                          ...badgeStyle,
                          padding: "6px 10px",
                          fontSize: 12,
                        }}
                      >
                        {room.roomCode}
                      </span>
                    </div>

                    {/* Thông tin phòng */}
                    <div
                      style={{
                        color: "#94a3b8",
                        lineHeight: 1.8,
                        fontSize: 14,
                      }}
                    >
                      <div>Chủ phòng: {room.hostName}</div>
                      <div>
                        Người chơi: {room.playerCount}/{room.maxPlayers}
                      </div>
                      <div>Chế độ: {roomModeMeta.label}</div>

                      {!playing && missingPlayers > 0 && (
                        <div>Còn thiếu: {missingPlayers} người</div>
                      )}

                      {!playing && missingPlayers === 0 && (
                        <div>Trạng thái: Đã đủ người</div>
                      )}

                      {playing && <div>Trạng thái: Đang trong trận</div>}
                    </div>

                    {/* Badge trạng thái */}
                    <div
                      style={{
                        marginTop: 14,
                        display: "flex",
                        gap: 8,
                        flexWrap: "wrap",
                      }}
                    >
                      <span
                        style={{
                          ...badgeStyle,
                          background: statusMeta.background,
                          color: statusMeta.color,
                          border: statusMeta.border,
                        }}
                      >
                        {statusMeta.text}
                      </span>

                      <span
                        style={{
                          ...badgeStyle,
                          background: roomModeMeta.background,
                          color: roomModeMeta.color,
                          border: roomModeMeta.border,
                        }}
                      >
                        {roomModeMeta.shortLabel}
                      </span>
                    </div>
                  </button>
                );
              })}
            </div>
          )}
        </Card>
      </div>

      {/* =====================================================
          MODAL TẠO PHÒNG
      ===================================================== */}
      {showCreateModal && (
        <div
          style={{
            position: "fixed",
            inset: 0,
            background: "rgba(2,6,23,0.72)",
            backdropFilter: "blur(6px)",
            display: "grid",
            placeItems: "center",
            padding: 20,
            zIndex: 60,
          }}
        >
          <div style={{ width: 560, maxWidth: "100%" }}>
            <Card
              style={{
                padding: 24,
                background:
                  "linear-gradient(180deg, rgba(15,23,42,0.98), rgba(15,23,42,0.92))",
              }}
            >
              <div
                style={{
                  display: "flex",
                  justifyContent: "space-between",
                  gap: 12,
                  alignItems: "center",
                  marginBottom: 18,
                }}
              >
                <div>
                  <div
                    style={{
                      color: "#86efac",
                      fontSize: 13,
                      fontWeight: 800,
                      marginBottom: 4,
                    }}
                  >
                    CREATE ROOM
                  </div>

                  <h3 style={{ margin: 0, color: "#fff", fontSize: 28 }}>
                    Tạo phòng mới
                  </h3>
                </div>

                <Button
                  onClick={() => setShowCreateModal(false)}
                  style={{
                    background: "linear-gradient(180deg, #475569, #334155)",
                    boxShadow: "none",
                  }}
                >
                  Đóng
                </Button>
              </div>

              <Input
                label="Tên phòng"
                value={roomName}
                onChange={setRoomName}
                placeholder="Ví dụ: Phòng của Hải"
              />

              {/* ===== chọn mode ===== */}
              <div style={{ marginBottom: 14 }}>
                <div
                  style={{
                    marginBottom: 10,
                    fontWeight: 800,
                    color: "#e2e8f0",
                    fontSize: 14,
                  }}
                >
                  Kiểu trận
                </div>

                <div
                  style={{
                    display: "grid",
                    gridTemplateColumns: "repeat(2, minmax(0, 1fr))",
                    gap: 12,
                  }}
                >
                  <ModeSelectCard
                    active={matchMode === "SOLO"}
                    title="Chơi đơn"
                    description="4 người đánh tự do, ai sống cuối cùng thắng."
                    meta={getMatchModeMeta("SOLO")}
                    onClick={() => setMatchMode("SOLO")}
                  />

                  <ModeSelectCard
                    active={matchMode === "DUO"}
                    title="Chơi đôi"
                    description="2 đội, mỗi đội 2 người. Trận này luôn dùng 4 người."
                    meta={getMatchModeMeta("DUO")}
                    onClick={() => {
                      setMatchMode("DUO");
                      setMaxPlayers(4);
                    }}
                  />
                </div>

                <div
                  style={{
                    marginTop: 10,
                    color: "#94a3b8",
                    fontSize: 13,
                    lineHeight: 1.7,
                  }}
                >
                  Đã chọn:{" "}
                  <span
                    style={{ color: selectedModeMeta.color, fontWeight: 800 }}
                  >
                    {selectedModeMeta.label}
                  </span>{" "}
                  • {selectedModeMeta.description}
                </div>
              </div>

              <label style={{ display: "block", marginBottom: 14 }}>
                <div
                  style={{
                    marginBottom: 8,
                    fontWeight: 800,
                    color: "#e2e8f0",
                    fontSize: 14,
                  }}
                >
                  Số người tối đa
                </div>

                <select
                  value={matchMode === "DUO" ? 4 : maxPlayers}
                  onChange={(e) => setMaxPlayers(Number(e.target.value))}
                  disabled={matchMode === "DUO"}
                  style={{
                    width: "100%",
                    padding: "13px 14px",
                    borderRadius: 16,
                    border: "1px solid rgba(255,255,255,0.12)",
                    background:
                      matchMode === "DUO"
                        ? "rgba(148,163,184,0.12)"
                        : "rgba(255,255,255,0.05)",
                    color: "#fff",
                    outline: "none",
                    cursor: matchMode === "DUO" ? "not-allowed" : "pointer",
                    opacity: matchMode === "DUO" ? 0.85 : 1,
                  }}
                >
                  {(matchMode === "DUO"
                    ? [4]
                    : GAME_CONFIG.room.allowedRoomSizes
                  ).map((size) => (
                    <option key={size} value={size} style={{ color: "#000" }}>
                      {size} người
                    </option>
                  ))}
                </select>

                {matchMode === "DUO" && (
                  <div
                    style={{
                      marginTop: 8,
                      color: "#93c5fd",
                      fontSize: 13,
                      lineHeight: 1.7,
                    }}
                  >
                    Chế độ chơi đôi được cố định 4 người để chạy đúng luật 2v2.
                  </div>
                )}
              </label>

              <label
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: 10,
                  color: "#e2e8f0",
                  fontWeight: 700,
                  marginBottom: 20,
                }}
              >
                <input
                  type="checkbox"
                  checked={isPrivate}
                  onChange={(e) => setIsPrivate(e.target.checked)}
                />
                Phòng riêng tư
              </label>

              <div
                style={{ display: "flex", gap: 12, justifyContent: "flex-end" }}
              >
                <Button
                  onClick={() => setShowCreateModal(false)}
                  style={{
                    background: "linear-gradient(180deg, #475569, #334155)",
                    boxShadow: "none",
                  }}
                >
                  Hủy
                </Button>

                <Button
                  onClick={handleCreateRoom}
                  style={{
                    background: "linear-gradient(180deg, #22c55e, #16a34a)",
                    boxShadow: "0 12px 28px rgba(22,163,74,0.30)",
                  }}
                >
                  + Tạo phòng
                </Button>
              </div>
            </Card>
          </div>
        </div>
      )}
    </div>
  );
}

type ModeSelectCardProps = {
  active: boolean;
  title: string;
  description: string;
  meta: ReturnType<typeof getMatchModeMeta>;
  onClick: () => void;
};

function ModeSelectCard({
  active,
  title,
  description,
  meta,
  onClick,
}: ModeSelectCardProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      style={{
        textAlign: "left",
        padding: 16,
        borderRadius: 18,
        border: active
          ? `1px solid ${meta.color}`
          : "1px solid rgba(255,255,255,0.08)",
        background: active ? meta.background : "rgba(255,255,255,0.04)",
        color: "#fff",
        cursor: "pointer",
        transition: "all 0.2s ease",
      }}
    >
      <div
        style={{
          display: "inline-flex",
          padding: "6px 10px",
          borderRadius: 999,
          fontSize: 12,
          fontWeight: 800,
          background: meta.background,
          color: meta.color,
          border: meta.border,
          marginBottom: 10,
        }}
      >
        {meta.shortLabel}
      </div>

      <div style={{ fontSize: 18, fontWeight: 800, marginBottom: 8 }}>
        {title}
      </div>

      <div style={{ color: "#cbd5e1", lineHeight: 1.7, fontSize: 14 }}>
        {description}
      </div>
    </button>
  );
}

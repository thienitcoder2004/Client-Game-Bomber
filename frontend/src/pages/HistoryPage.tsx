import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import Button from "../components/common/Button";
import Card from "../components/common/Card";
import Loading from "../components/common/Loading";
import { historyApi } from "../services/historyApi";
import type { MatchHistoryItem } from "../types/history";
import { getStoredAuthUser } from "../utils/storage";

const ITEMS_PER_PAGE = 10;

export default function HistoryPage() {
  const navigate = useNavigate();
  const [items, setItems] = useState<MatchHistoryItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [errorText, setErrorText] = useState("");
  const [selectedMatch, setSelectedMatch] = useState<MatchHistoryItem | null>(
    null,
  );
  const [currentPage, setCurrentPage] = useState(1);

  const me = useMemo(() => getStoredAuthUser(), []);

  useEffect(() => {
    historyApi
      .getMyHistory()
      .then((data) => {
        setItems(data);
        setCurrentPage(1);
      })
      .catch((error) => {
        setErrorText(
          error instanceof Error ? error.message : "Không tải được lịch sử",
        );
      })
      .finally(() => {
        setLoading(false);
      });
  }, []);

  const totalPages = Math.max(1, Math.ceil(items.length / ITEMS_PER_PAGE));

  const safeCurrentPage = Math.min(currentPage, totalPages);

  const paginatedItems = useMemo(() => {
    const start = (safeCurrentPage - 1) * ITEMS_PER_PAGE;
    const end = start + ITEMS_PER_PAGE;
    return items.slice(start, end);
  }, [items, safeCurrentPage]);

  const getResultText = (match: MatchHistoryItem) => {
    if (!me?.userId) return "Không xác định";
    if (!match.winnerUserId) return "Hòa";
    return match.winnerUserId === me.userId ? "Thắng" : "Thua";
  };

  const getResultStyles = (match: MatchHistoryItem) => {
    const result = getResultText(match);

    if (result === "Thắng") {
      return {
        color: "#22c55e",
        background: "rgba(34, 197, 94, 0.14)",
        border: "1px solid rgba(34, 197, 94, 0.35)",
      };
    }

    if (result === "Thua") {
      return {
        color: "#ef4444",
        background: "rgba(239, 68, 68, 0.14)",
        border: "1px solid rgba(239, 68, 68, 0.35)",
      };
    }

    return {
      color: "#f59e0b",
      background: "rgba(245, 158, 11, 0.14)",
      border: "1px solid rgba(245, 158, 11, 0.35)",
    };
  };

  const formatDateTime = (value?: string) => {
    if (!value) return "N/A";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;

    return new Intl.DateTimeFormat("vi-VN", {
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
    }).format(date);
  };

  const getDurationText = (start?: string, end?: string) => {
    if (!start || !end) return "N/A";

    const startDate = new Date(start).getTime();
    const endDate = new Date(end).getTime();

    if (Number.isNaN(startDate) || Number.isNaN(endDate)) return "N/A";

    const diffSeconds = Math.max(0, Math.floor((endDate - startDate) / 1000));
    const minutes = Math.floor(diffSeconds / 60);
    const seconds = diffSeconds % 60;

    if (minutes === 0) return `${seconds}s`;
    return `${minutes}m ${seconds}s`;
  };

  const getPlayerResultText = (winner: boolean, hasWinnerUser: boolean) => {
    if (!hasWinnerUser) return "Hòa";
    return winner ? "Thắng" : "Thua";
  };

  const handleChangePage = (page: number) => {
    setCurrentPage(page);
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  if (loading) {
    return <Loading text="Đang tải lịch sử đấu..." />;
  }

  return (
    <div
      style={{
        minHeight: "100vh",
        padding: 24,
        background:
          "radial-gradient(circle at top, #162033 0%, #0b1020 45%, #070b16 100%)",
      }}
    >
      <div style={{ maxWidth: 1100, margin: "0 auto" }}>
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            gap: 12,
            alignItems: "center",
            marginBottom: 20,
            flexWrap: "wrap",
          }}
        >
          <div>
            <h1
              style={{
                margin: 0,
                fontSize: 32,
                color: "#f8fafc",
              }}
            >
              Lịch sử đấu
            </h1>
            <div style={{ color: "#94a3b8", marginTop: 6 }}>
              Xem kết quả các trận đã chơi và chi tiết người tham gia
            </div>
            {items.length > 0 && (
              <div style={{ color: "#94a3b8", marginTop: 8, fontSize: 14 }}>
                Hiển thị 10 trận / 1 trang — Trang {currentPage}/{totalPages}
              </div>
            )}
          </div>

          <Button onClick={() => navigate("/lobby")}>Về sảnh</Button>
        </div>

        {errorText && (
          <Card
            style={{
              marginBottom: 16,
              color: "#fecaca",
              background: "rgba(127, 29, 29, 0.35)",
              border: "1px solid rgba(239, 68, 68, 0.35)",
            }}
          >
            {errorText}
          </Card>
        )}

        {items.length === 0 ? (
          <Card
            style={{
              textAlign: "center",
              padding: 28,
              color: "#cbd5e1",
            }}
          >
            Chưa có lịch sử trận đấu nào.
          </Card>
        ) : (
          <>
            <div
              style={{
                display: "grid",
                gap: 16,
              }}
            >
              {paginatedItems.map((match) => {
                const resultText = getResultText(match);
                const resultStyles = getResultStyles(match);

                return (
                  <Card
                    key={match.id}
                    style={{
                      padding: 20,
                      border: "1px solid rgba(148, 163, 184, 0.14)",
                      boxShadow: "0 18px 40px rgba(0,0,0,0.22)",
                      background:
                        "linear-gradient(180deg, rgba(15,23,42,0.96) 0%, rgba(15,23,42,0.82) 100%)",
                    }}
                  >
                    <div
                      style={{
                        display: "flex",
                        justifyContent: "space-between",
                        alignItems: "flex-start",
                        gap: 16,
                        flexWrap: "wrap",
                      }}
                    >
                      <div style={{ flex: 1, minWidth: 260 }}>
                        <div
                          style={{
                            display: "flex",
                            alignItems: "center",
                            gap: 10,
                            flexWrap: "wrap",
                            marginBottom: 10,
                          }}
                        >
                          <div
                            style={{
                              fontWeight: 800,
                              fontSize: 20,
                              color: "#f8fafc",
                            }}
                          >
                            Room: {match.roomCode || "N/A"}
                          </div>

                          <div
                            style={{
                              ...resultStyles,
                              padding: "6px 12px",
                              borderRadius: 999,
                              fontWeight: 700,
                              fontSize: 13,
                            }}
                          >
                            {resultText}
                          </div>
                        </div>

                        <div
                          style={{
                            display: "grid",
                            gap: 8,
                            color: "#cbd5e1",
                            fontSize: 14,
                          }}
                        >
                          <div>
                            Người thắng:{" "}
                            <strong style={{ color: "#f8fafc" }}>
                              {match.winnerCharacterName || "Không xác định"}
                            </strong>
                          </div>
                          <div>Bắt đầu: {formatDateTime(match.startedAt)}</div>
                          <div>Kết thúc: {formatDateTime(match.endedAt)}</div>
                          <div>
                            Thời lượng:{" "}
                            {getDurationText(match.startedAt, match.endedAt)}
                          </div>
                          <div>Số người chơi: {match.players.length}</div>
                        </div>
                      </div>

                      <div
                        style={{
                          display: "flex",
                          alignItems: "center",
                          gap: 10,
                          flexWrap: "wrap",
                        }}
                      >
                        <Button onClick={() => setSelectedMatch(match)}>
                          Xem chi tiết
                        </Button>
                      </div>
                    </div>
                  </Card>
                );
              })}
            </div>

            {totalPages > 1 && (
              <div
                style={{
                  marginTop: 24,
                  display: "flex",
                  justifyContent: "center",
                  alignItems: "center",
                  gap: 8,
                  flexWrap: "wrap",
                }}
              >
                <Button
                  onClick={() => handleChangePage(currentPage - 1)}
                  disabled={currentPage === 1}
                >
                  Trước
                </Button>

                {Array.from({ length: totalPages }, (_, index) => {
                  const page = index + 1;
                  const isActive = page === currentPage;

                  return (
                    <button
                      key={page}
                      onClick={() => handleChangePage(page)}
                      style={{
                        minWidth: 42,
                        height: 42,
                        borderRadius: 12,
                        border: isActive
                          ? "1px solid rgba(59,130,246,0.8)"
                          : "1px solid rgba(148,163,184,0.2)",
                        background: isActive
                          ? "linear-gradient(180deg, #3b82f6, #1d4ed8)"
                          : "rgba(255,255,255,0.04)",
                        color: "#fff",
                        fontWeight: 700,
                        cursor: "pointer",
                      }}
                    >
                      {page}
                    </button>
                  );
                })}

                <Button
                  onClick={() => handleChangePage(currentPage + 1)}
                  disabled={currentPage === totalPages}
                >
                  Sau
                </Button>
              </div>
            )}
          </>
        )}
      </div>

      {selectedMatch && (
        <div
          onClick={() => setSelectedMatch(null)}
          style={{
            position: "fixed",
            inset: 0,
            background: "rgba(2, 6, 23, 0.75)",
            backdropFilter: "blur(6px)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            padding: 20,
            zIndex: 1000,
          }}
        >
          <div
            onClick={(event) => event.stopPropagation()}
            style={{
              width: "min(980px, 100%)",
              maxHeight: "85vh",
              overflowY: "auto",
              borderRadius: 20,
              background: "linear-gradient(180deg, #0f172a 0%, #111827 100%)",
              border: "1px solid rgba(148, 163, 184, 0.16)",
              boxShadow: "0 24px 80px rgba(0,0,0,0.45)",
              padding: 22,
            }}
          >
            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "flex-start",
                gap: 16,
                marginBottom: 18,
                flexWrap: "wrap",
              }}
            >
              <div>
                <h2
                  style={{
                    margin: 0,
                    color: "#f8fafc",
                    fontSize: 26,
                  }}
                >
                  Chi tiết trận đấu
                </h2>
                <div style={{ color: "#94a3b8", marginTop: 6 }}>
                  Room: {selectedMatch.roomCode || "N/A"}
                </div>
              </div>

              <Button onClick={() => setSelectedMatch(null)}>Đóng</Button>
            </div>

            <div
              style={{
                display: "grid",
                gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))",
                gap: 12,
                marginBottom: 18,
              }}
            >
              <div
                style={{
                  padding: 14,
                  borderRadius: 14,
                  background: "rgba(255,255,255,0.04)",
                  color: "#cbd5e1",
                }}
              >
                <div style={{ fontSize: 13, color: "#94a3b8" }}>
                  Người thắng
                </div>
                <div
                  style={{
                    marginTop: 6,
                    fontWeight: 700,
                    color: "#f8fafc",
                  }}
                >
                  {selectedMatch.winnerCharacterName || "Không xác định"}
                </div>
              </div>

              <div
                style={{
                  padding: 14,
                  borderRadius: 14,
                  background: "rgba(255,255,255,0.04)",
                  color: "#cbd5e1",
                }}
              >
                <div style={{ fontSize: 13, color: "#94a3b8" }}>Bắt đầu</div>
                <div
                  style={{
                    marginTop: 6,
                    fontWeight: 700,
                    color: "#f8fafc",
                  }}
                >
                  {formatDateTime(selectedMatch.startedAt)}
                </div>
              </div>

              <div
                style={{
                  padding: 14,
                  borderRadius: 14,
                  background: "rgba(255,255,255,0.04)",
                  color: "#cbd5e1",
                }}
              >
                <div style={{ fontSize: 13, color: "#94a3b8" }}>Kết thúc</div>
                <div
                  style={{
                    marginTop: 6,
                    fontWeight: 700,
                    color: "#f8fafc",
                  }}
                >
                  {formatDateTime(selectedMatch.endedAt)}
                </div>
              </div>

              <div
                style={{
                  padding: 14,
                  borderRadius: 14,
                  background: "rgba(255,255,255,0.04)",
                  color: "#cbd5e1",
                }}
              >
                <div style={{ fontSize: 13, color: "#94a3b8" }}>Thời lượng</div>
                <div
                  style={{
                    marginTop: 6,
                    fontWeight: 700,
                    color: "#f8fafc",
                  }}
                >
                  {getDurationText(
                    selectedMatch.startedAt,
                    selectedMatch.endedAt,
                  )}
                </div>
              </div>
            </div>

            <div
              style={{
                overflowX: "auto",
                borderRadius: 16,
                border: "1px solid rgba(148,163,184,0.14)",
              }}
            >
              <table
                style={{
                  width: "100%",
                  borderCollapse: "collapse",
                  minWidth: 760,
                }}
              >
                <thead>
                  <tr
                    style={{
                      background: "rgba(255,255,255,0.06)",
                    }}
                  >
                    <th style={thStyle}>Người chơi</th>
                    <th style={thStyle}>Kết quả</th>
                    <th style={thStyle}>Bom đặt</th>
                    <th style={thStyle}>Hạ gục</th>
                    <th style={thStyle}>Bị hạ</th>
                    <th style={thStyle}>Mạng còn</th>
                    <th style={thStyle}>OVR</th>
                  </tr>
                </thead>
                <tbody>
                  {selectedMatch.players.map((player, index) => {
                    const playerResult = getPlayerResultText(
                      player.winner,
                      Boolean(selectedMatch.winnerUserId),
                    );

                    return (
                      <tr
                        key={`${selectedMatch.id}-${player.userId}-${index}`}
                        style={{
                          background:
                            index % 2 === 0
                              ? "rgba(255,255,255,0.02)"
                              : "rgba(255,255,255,0.04)",
                        }}
                      >
                        <td style={tdStyle}>
                          <div
                            style={{
                              display: "flex",
                              alignItems: "center",
                              gap: 8,
                              fontWeight: 700,
                              color: "#f8fafc",
                            }}
                          >
                            <span>{player.characterName}</span>
                            {player.winner && <span>🏆</span>}
                          </div>
                        </td>

                        <td
                          style={{
                            ...tdStyle,
                            fontWeight: 700,
                            color:
                              playerResult === "Thắng"
                                ? "#22c55e"
                                : playerResult === "Thua"
                                  ? "#ef4444"
                                  : "#f59e0b",
                          }}
                        >
                          {playerResult}
                        </td>

                        <td style={tdStyle}>{player.bombsPlaced}</td>
                        <td style={tdStyle}>{player.kills}</td>
                        <td style={tdStyle}>{player.deaths}</td>
                        <td style={tdStyle}>{player.livesLeft}</td>
                        <td style={tdStyle}>{player.ovr}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

const thStyle: React.CSSProperties = {
  textAlign: "left",
  padding: 14,
  color: "#e2e8f0",
  fontSize: 14,
};

const tdStyle: React.CSSProperties = {
  padding: 14,
  color: "#cbd5e1",
  fontSize: 14,
};

import {
  useEffect,
  useMemo,
  useState,
  type CSSProperties,
  type ReactNode,
} from "react";
import { useNavigate } from "react-router-dom";
import Button from "../components/common/Button";
import Card from "../components/common/Card";
import Input from "../components/common/Input";
import { useAuth } from "../hooks/useAuth";
import { useFriendChatSocket } from "../hooks/useFriendChatSocket";
import { friendApi } from "../services/friendApi";
import type {
  FriendChatMessage,
  FriendListItem,
  FriendRequestItem,
  FriendSearchItem,
} from "../types/friend";

const badgeStyle: CSSProperties = {
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

const RECALL_WINDOW_MS = 5 * 60 * 1000;

export default function FriendsPage() {
  const navigate = useNavigate();
  const { profile } = useAuth();

  const [keyword, setKeyword] = useState("");
  const [searchResults, setSearchResults] = useState<FriendSearchItem[]>([]);
  const [friends, setFriends] = useState<FriendListItem[]>([]);
  const [incomingRequests, setIncomingRequests] = useState<FriendRequestItem[]>(
    [],
  );
  const [outgoingRequests, setOutgoingRequests] = useState<FriendRequestItem[]>(
    [],
  );
  const [selectedFriend, setSelectedFriend] = useState<FriendListItem | null>(
    null,
  );
  const [messageInput, setMessageInput] = useState("");
  const [pageStatus, setPageStatus] = useState("Sẵn sàng");
  const [, forceTick] = useState(0);

  const {
    connected,
    statusText,
    messages,
    setMessages,
    sendMessage,
    recallMessage,
    friendEventVersion,
    lastFriendEvent,
  } = useFriendChatSocket(selectedFriend?.user.userId);

  const selectedFriendId = selectedFriend?.user.userId;

  useEffect(() => {
    const timer = window.setInterval(() => {
      forceTick((v) => v + 1);
    }, 10000);

    return () => window.clearInterval(timer);
  }, []);

  const sortedMessages = useMemo(() => {
    if (!selectedFriendId) return [];

    return [...messages]
      .filter(
        (item) =>
          item.senderId === selectedFriendId ||
          item.receiverId === selectedFriendId,
      )
      .sort((a, b) => a.createdAt.localeCompare(b.createdAt));
  }, [messages, selectedFriendId]);

  const loadDashboard = async () => {
    try {
      const [friendItems, incomingItems, outgoingItems] = await Promise.all([
        friendApi.getFriends(),
        friendApi.getIncomingRequests(),
        friendApi.getOutgoingRequests(),
      ]);

      setFriends(friendItems);
      setIncomingRequests(incomingItems);
      setOutgoingRequests(outgoingItems);
    } catch (error) {
      setPageStatus(
        error instanceof Error
          ? error.message
          : "Không thể tải danh sách bạn bè",
      );
    }
  };

  useEffect(() => {
    let cancelled = false;

    const fetchInitialDashboard = async () => {
      try {
        const [friendItems, incomingItems, outgoingItems] = await Promise.all([
          friendApi.getFriends(),
          friendApi.getIncomingRequests(),
          friendApi.getOutgoingRequests(),
        ]);

        if (cancelled) return;

        setFriends(friendItems);
        setIncomingRequests(incomingItems);
        setOutgoingRequests(outgoingItems);
      } catch (error) {
        if (cancelled) return;

        setPageStatus(
          error instanceof Error
            ? error.message
            : "Không thể tải danh sách bạn bè",
        );
      }
    };

    void fetchInitialDashboard();

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!selectedFriendId) {
      setMessages([]);
      return;
    }

    let cancelled = false;

    const fetchChatHistory = async () => {
      try {
        const data = await friendApi.getChatHistory(selectedFriendId);

        if (cancelled) return;
        setMessages(data);
      } catch (error) {
        if (cancelled) return;

        setPageStatus(
          error instanceof Error
            ? error.message
            : "Không tải được lịch sử chat",
        );
        setMessages([]);
      }
    };

    void fetchChatHistory();

    return () => {
      cancelled = true;
    };
  }, [selectedFriendId, setMessages]);

  useEffect(() => {
    if (friendEventVersion === 0) return;

    let cancelled = false;

    const refreshRealtimeData = async () => {
      try {
        const [friendItems, incomingItems, outgoingItems] = await Promise.all([
          friendApi.getFriends(),
          friendApi.getIncomingRequests(),
          friendApi.getOutgoingRequests(),
        ]);

        if (cancelled) return;

        setFriends(friendItems);
        setIncomingRequests(incomingItems);
        setOutgoingRequests(outgoingItems);

        if (keyword.trim()) {
          const result = await friendApi.search(keyword.trim());
          if (cancelled) return;
          setSearchResults(result);
        }

        if (selectedFriendId) {
          const updatedSelectedFriend = friendItems.find(
            (item) => item.user.userId === selectedFriendId,
          );

          if (!updatedSelectedFriend) {
            setSelectedFriend(null);
            setMessages([]);
            return;
          }

          setSelectedFriend(updatedSelectedFriend);
        }

        if (lastFriendEvent?.event === "friend_request_received") {
          setPageStatus("Bạn vừa nhận được một lời mời kết bạn");
        } else if (lastFriendEvent?.event === "friendship_accepted") {
          setPageStatus("Đã cập nhật kết bạn realtime");
        } else if (lastFriendEvent?.event === "friend_request_removed") {
          setPageStatus("Lời mời kết bạn đã được cập nhật");
        } else if (lastFriendEvent?.event === "friend_removed") {
          setPageStatus("Danh sách bạn bè đã được cập nhật");
        } else if (lastFriendEvent?.event === "friend_presence_changed") {
          setPageStatus("Trạng thái online bạn bè đã được cập nhật");
        }
      } catch (error) {
        if (cancelled) return;

        setPageStatus(
          error instanceof Error
            ? error.message
            : "Không thể cập nhật dữ liệu realtime",
        );
      }
    };

    void refreshRealtimeData();

    return () => {
      cancelled = true;
    };
  }, [
    friendEventVersion,
    keyword,
    selectedFriendId,
    setMessages,
    lastFriendEvent,
  ]);

  const handleSearch = async () => {
    if (!keyword.trim()) {
      setSearchResults([]);
      return;
    }

    try {
      const result = await friendApi.search(keyword.trim());
      setSearchResults(result);
      setPageStatus(`Tìm thấy ${result.length} người chơi`);
    } catch (error) {
      setPageStatus(
        error instanceof Error ? error.message : "Không tìm được người chơi",
      );
    }
  };

  const handleSendRequest = async (targetUserId: string) => {
    try {
      const response = await friendApi.sendRequest({ targetUserId });
      setPageStatus(response.message);
      await Promise.all([handleSearch(), loadDashboard()]);
    } catch (error) {
      setPageStatus(
        error instanceof Error ? error.message : "Không gửi được lời mời",
      );
    }
  };

  const handleAccept = async (requestId: string) => {
    try {
      const response = await friendApi.acceptRequest(requestId);
      setPageStatus(response.message);
      await loadDashboard();
      await handleSearch();
    } catch (error) {
      setPageStatus(
        error instanceof Error ? error.message : "Không chấp nhận được lời mời",
      );
    }
  };

  const handleDeleteRequest = async (requestId: string) => {
    try {
      const response = await friendApi.deleteRequest(requestId);
      setPageStatus(response.message);
      await loadDashboard();
      await handleSearch();
    } catch (error) {
      setPageStatus(
        error instanceof Error ? error.message : "Không xóa được lời mời",
      );
    }
  };

  const handleRemoveFriend = async (friendUserId: string) => {
    try {
      const response = await friendApi.removeFriend(friendUserId);
      setPageStatus(response.message);

      if (selectedFriend?.user.userId === friendUserId) {
        setSelectedFriend(null);
        setMessages([]);
      }

      await loadDashboard();
      await handleSearch();
    } catch (error) {
      setPageStatus(
        error instanceof Error ? error.message : "Không xóa được bạn bè",
      );
    }
  };

  const handleSendChat = () => {
    if (!selectedFriendId || !messageInput.trim()) return;
    sendMessage(selectedFriendId, messageInput.trim());
    setMessageInput("");
  };

  const handleRecall = (messageId: string) => {
    recallMessage(messageId);
  };

  return (
    <div
      style={{
        minHeight: "100vh",
        padding: 24,
        background:
          "radial-gradient(circle at top left, rgba(59,130,246,0.20), transparent 24%), radial-gradient(circle at top right, rgba(34,197,94,0.12), transparent 18%), radial-gradient(circle at bottom right, rgba(168,85,247,0.12), transparent 20%), linear-gradient(180deg, #030712 0%, #08111f 45%, #0f172a 100%)",
      }}
    >
      <div style={{ maxWidth: 1440, margin: "0 auto" }}>
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
              <div style={{ ...badgeStyle, marginBottom: 14 }}>
                👥 FRIENDS & CHAT
              </div>

              <h1
                style={{
                  margin: 0,
                  color: "#fff",
                  fontSize: 42,
                  lineHeight: 1.1,
                }}
              >
                Kết bạn và chat
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
                Tìm người chơi theo username hoặc tên nhân vật, gửi lời mời kết
                bạn, chấp nhận lời mời và chat realtime 1-1 ngay trong game.
              </p>
            </div>

            <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
              <span style={{ ...badgeStyle, color: "#86efac" }}>
                {profile?.characterName || profile?.username || "Người chơi"}
              </span>

              <span
                style={{
                  ...badgeStyle,
                  color: connected ? "#86efac" : "#fca5a5",
                  background: connected
                    ? "rgba(34,197,94,0.10)"
                    : "rgba(239,68,68,0.10)",
                }}
              >
                {connected ? "Chat online" : "Chat offline"}
              </span>

              <Button
                onClick={() => navigate("/lobby")}
                style={{
                  background: "linear-gradient(180deg, #475569, #334155)",
                  boxShadow: "none",
                }}
              >
                ← Về sảnh
              </Button>
            </div>
          </div>

          <div style={{ marginTop: 16, color: "#94a3b8", fontSize: 14 }}>
            {pageStatus} • {statusText}
          </div>
        </Card>

        <div
          style={{
            display: "grid",
            gridTemplateColumns: "1.1fr 1fr 1.35fr",
            gap: 18,
            alignItems: "start",
          }}
        >
          <Card>
            <h3 style={{ marginTop: 0, color: "#fff" }}>Tìm người chơi</h3>

            <Input
              label="Username hoặc tên nhân vật"
              value={keyword}
              onChange={setKeyword}
              placeholder="Nhập để tìm bạn bè..."
            />

            <Button onClick={handleSearch} style={{ width: "100%" }}>
              Tìm kiếm
            </Button>

            <div style={{ marginTop: 16, display: "grid", gap: 10 }}>
              {searchResults.map((item) => (
                <UserCard
                  key={item.userId}
                  title={item.characterName || item.username}
                  subtitle={`@${item.username}`}
                  online={item.online}
                  footer={
                    item.relationshipStatus === "NONE"
                      ? "Chưa kết bạn"
                      : item.relationshipStatus === "FRIEND"
                        ? "Đã là bạn bè"
                        : item.relationshipStatus === "PENDING_IN"
                          ? "Đã gửi lời mời cho bạn"
                          : "Bạn đã gửi lời mời"
                  }
                  action={
                    item.relationshipStatus === "NONE" ? (
                      <Button
                        onClick={() => void handleSendRequest(item.userId)}
                      >
                        Kết bạn
                      </Button>
                    ) : item.relationshipStatus === "PENDING_IN" ? (
                      <div
                        style={{ display: "flex", gap: 8, flexWrap: "wrap" }}
                      >
                        <Button
                          onClick={() => {
                            if (!item.requestId) return;
                            void handleAccept(item.requestId);
                          }}
                          style={{
                            background:
                              "linear-gradient(180deg, #22c55e, #16a34a)",
                            boxShadow: "0 12px 28px rgba(22,163,74,0.30)",
                          }}
                        >
                          Chấp nhận
                        </Button>

                        <Button
                          onClick={() => {
                            if (!item.requestId) return;
                            void handleDeleteRequest(item.requestId);
                          }}
                          style={{
                            background:
                              "linear-gradient(180deg, #ef4444, #dc2626)",
                            boxShadow: "0 12px 28px rgba(220,38,38,0.30)",
                          }}
                        >
                          Từ chối
                        </Button>
                      </div>
                    ) : null
                  }
                />
              ))}
            </div>
          </Card>

          <Card>
            <h3 style={{ marginTop: 0, color: "#fff" }}>Lời mời & bạn bè</h3>

            <SectionTitle text={`Lời mời đến (${incomingRequests.length})`} />

            <div style={{ display: "grid", gap: 10, marginBottom: 18 }}>
              {incomingRequests.map((item) => (
                <UserCard
                  key={item.requestId}
                  title={item.user.characterName || item.user.username}
                  subtitle={`@${item.user.username}`}
                  online={item.user.online}
                  footer="Đã gửi lời mời kết bạn cho bạn"
                  action={
                    <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                      <Button
                        onClick={() => void handleAccept(item.requestId)}
                        style={{
                          background:
                            "linear-gradient(180deg, #22c55e, #16a34a)",
                          boxShadow: "0 12px 28px rgba(22,163,74,0.30)",
                        }}
                      >
                        Chấp nhận
                      </Button>

                      <Button
                        onClick={() => void handleDeleteRequest(item.requestId)}
                        style={{
                          background:
                            "linear-gradient(180deg, #ef4444, #dc2626)",
                          boxShadow: "0 12px 28px rgba(220,38,38,0.30)",
                        }}
                      >
                        Xóa
                      </Button>
                    </div>
                  }
                />
              ))}
            </div>

            <SectionTitle text={`Đã gửi (${outgoingRequests.length})`} />

            <div style={{ display: "grid", gap: 10, marginBottom: 18 }}>
              {outgoingRequests.map((item) => (
                <UserCard
                  key={item.requestId}
                  title={item.user.characterName || item.user.username}
                  subtitle={`@${item.user.username}`}
                  online={item.user.online}
                  footer="Bạn đã gửi lời mời kết bạn"
                  action={
                    <Button
                      onClick={() => void handleDeleteRequest(item.requestId)}
                      style={{
                        background: "linear-gradient(180deg, #ef4444, #dc2626)",
                        boxShadow: "0 12px 28px rgba(220,38,38,0.30)",
                      }}
                    >
                      Hủy lời mời
                    </Button>
                  }
                />
              ))}
            </div>

            <SectionTitle text={`Bạn bè (${friends.length})`} />

            <div style={{ display: "grid", gap: 10 }}>
              {friends.map((item) => (
                <UserCard
                  key={item.friendshipId}
                  title={item.user.characterName || item.user.username}
                  subtitle={`@${item.user.username}`}
                  online={item.user.online}
                  footer={item.user.online ? "Đang online" : "Đang offline"}
                  action={
                    <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                      <Button
                        onClick={() => setSelectedFriend(item)}
                        style={{
                          background:
                            "linear-gradient(180deg, #3b82f6, #1d4ed8)",
                        }}
                      >
                        Chat
                      </Button>

                      <Button
                        onClick={() =>
                          void handleRemoveFriend(item.user.userId)
                        }
                        style={{
                          background:
                            "linear-gradient(180deg, #ef4444, #dc2626)",
                          boxShadow: "0 12px 28px rgba(220,38,38,0.30)",
                        }}
                      >
                        Xóa bạn
                      </Button>
                    </div>
                  }
                />
              ))}
            </div>
          </Card>

          <Card
            style={{
              minHeight: 760,
              display: "flex",
              flexDirection: "column",
            }}
          >
            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                gap: 10,
                alignItems: "center",
                marginBottom: 16,
                flexWrap: "wrap",
              }}
            >
              <div>
                <h3 style={{ margin: 0, color: "#fff" }}>
                  {selectedFriend
                    ? `Chat với ${selectedFriend.user.characterName || selectedFriend.user.username}`
                    : "Khung chat"}
                </h3>

                <div style={{ color: "#94a3b8", fontSize: 14, marginTop: 6 }}>
                  {selectedFriend
                    ? selectedFriend.user.online
                      ? "Người này đang online"
                      : "Người này đang offline"
                    : "Chọn một người bạn để bắt đầu chat"}
                </div>
              </div>

              {selectedFriend && (
                <span
                  style={{
                    ...badgeStyle,
                    color: selectedFriend.user.online ? "#86efac" : "#cbd5e1",
                  }}
                >
                  {selectedFriend.user.online ? "Online" : "Offline"}
                </span>
              )}
            </div>

            <div
              style={{
                flex: 1,
                minHeight: 520,
                maxHeight: 520,
                overflowY: "auto",
                borderRadius: 18,
                border: "1px solid rgba(255,255,255,0.08)",
                background: "rgba(2,6,23,0.55)",
                padding: 14,
                display: "flex",
                flexDirection: "column",
                gap: 10,
              }}
            >
              {!selectedFriend ? (
                <EmptyText text="Bạn chưa chọn người bạn nào để chat." />
              ) : sortedMessages.length === 0 ? (
                <EmptyText text="Chưa có tin nhắn nào. Hãy gửi lời chào đầu tiên." />
              ) : (
                sortedMessages.map((item) => (
                  <ChatBubble
                    key={item.id + item.createdAt}
                    mine={item.senderId === profile?.userId}
                    message={item}
                    canRecall={canRecallMessage(item, profile?.userId)}
                    onRecall={handleRecall}
                  />
                ))
              )}
            </div>

            <div style={{ marginTop: 14 }}>
              <label
                style={{
                  display: "block",
                  marginBottom: 10,
                  color: "#e2e8f0",
                  fontWeight: 800,
                }}
              >
                Nhập tin nhắn
              </label>

              <textarea
                value={messageInput}
                onChange={(e) => setMessageInput(e.target.value)}
                placeholder={
                  selectedFriend ? "Nhập nội dung chat..." : "Chọn bạn bè trước"
                }
                disabled={!selectedFriend}
                rows={4}
                style={{
                  width: "100%",
                  padding: 14,
                  borderRadius: 16,
                  border: "1px solid rgba(255,255,255,0.12)",
                  background: "rgba(255,255,255,0.05)",
                  color: "#fff",
                  boxSizing: "border-box",
                  resize: "vertical",
                  outline: "none",
                }}
              />

              <div
                style={{
                  display: "flex",
                  justifyContent: "space-between",
                  gap: 12,
                  marginTop: 12,
                  alignItems: "center",
                }}
              >
                <span style={{ color: "#94a3b8", fontSize: 13 }}>
                  Tối đa 500 ký tự
                </span>

                <Button
                  onClick={handleSendChat}
                  disabled={!selectedFriend || !messageInput.trim()}
                >
                  Gửi tin nhắn
                </Button>
              </div>
            </div>
          </Card>
        </div>
      </div>
    </div>
  );
}

function canRecallMessage(
  message: FriendChatMessage,
  currentUserId?: string,
): boolean {
  if (!currentUserId) return false;
  if (message.senderId !== currentUserId) return false;
  if (message.recalled) return false;

  const createdAt = new Date(message.createdAt).getTime();
  const now = Date.now();

  return now - createdAt <= RECALL_WINDOW_MS;
}

function SectionTitle({ text }: { text: string }) {
  return (
    <div
      style={{
        color: "#93c5fd",
        fontSize: 13,
        fontWeight: 800,
        marginBottom: 10,
        marginTop: 4,
      }}
    >
      {text}
    </div>
  );
}

function UserCard({
  title,
  subtitle,
  online,
  footer,
  action,
}: {
  title: string;
  subtitle: string;
  online: boolean;
  footer: string;
  action?: ReactNode;
}) {
  return (
    <div
      style={{
        padding: 14,
        borderRadius: 18,
        background: "rgba(255,255,255,0.04)",
        border: "1px solid rgba(255,255,255,0.06)",
      }}
    >
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          gap: 12,
          alignItems: "center",
          flexWrap: "wrap",
        }}
      >
        <div>
          <div style={{ color: "#fff", fontWeight: 800, fontSize: 15 }}>
            {title}
          </div>

          <div style={{ color: "#94a3b8", fontSize: 13, marginTop: 4 }}>
            {subtitle}
          </div>

          <div
            style={{
              color: online ? "#86efac" : "#94a3b8",
              fontSize: 13,
              marginTop: 6,
            }}
          >
            {footer}
          </div>
        </div>

        {action}
      </div>
    </div>
  );
}

function ChatBubble({
  mine,
  message,
  canRecall,
  onRecall,
}: {
  mine: boolean;
  message: FriendChatMessage;
  canRecall: boolean;
  onRecall: (messageId: string) => void;
}) {
  const wrapperStyle: CSSProperties = {
    display: "flex",
    justifyContent: mine ? "flex-end" : "flex-start",
    alignItems: "flex-start",
    width: "100%",
  };

  const bubbleStyle: CSSProperties = {
    display: "inline-flex",
    flexDirection: "column",
    alignItems: "flex-start",
    width: "fit-content",
    maxWidth: "72%",
    minWidth: 64,
    padding: "12px 14px",
    borderRadius: 18,
    background: message.recalled
      ? "rgba(148,163,184,0.16)"
      : mine
        ? "linear-gradient(180deg, #2563eb, #1d4ed8)"
        : "rgba(255,255,255,0.08)",
    color: "#fff",
    border: message.recalled
      ? "1px solid rgba(148,163,184,0.20)"
      : mine
        ? "1px solid rgba(59,130,246,0.35)"
        : "1px solid rgba(255,255,255,0.08)",
    boxSizing: "border-box",
    overflowWrap: "anywhere",
    wordBreak: "break-word",
    whiteSpace: "pre-wrap",
  };

  return (
    <div style={wrapperStyle}>
      <div style={bubbleStyle}>
        <div
          style={{
            lineHeight: 1.6,
            fontStyle: message.recalled ? "italic" : "normal",
            color: message.recalled ? "#cbd5e1" : "#fff",
          }}
        >
          {message.recalled ? "Tin nhắn đã được thu hồi" : message.content}
        </div>

        <div
          style={{
            marginTop: 8,
            display: "flex",
            gap: 10,
            flexWrap: "wrap",
            alignItems: "center",
          }}
        >
          <span style={{ fontSize: 12, opacity: 0.8 }}>
            {new Date(message.createdAt).toLocaleTimeString("vi-VN")}{" "}
            {new Date(message.createdAt).toLocaleDateString("vi-VN")}
          </span>

          {mine && canRecall && !message.recalled && (
            <button
              type="button"
              onClick={() => onRecall(message.id)}
              style={{
                border: "none",
                background: "transparent",
                color: "#bfdbfe",
                cursor: "pointer",
                fontSize: 12,
                padding: 0,
                fontWeight: 700,
              }}
            >
              Thu hồi
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

function EmptyText({ text }: { text: string }) {
  return (
    <div
      style={{
        minHeight: 220,
        display: "grid",
        placeItems: "center",
        color: "#94a3b8",
        textAlign: "center",
      }}
    >
      {text}
    </div>
  );
}

import { useEffect, useRef, useState } from "react";
import { GAME_CONFIG } from "../config/gameConfig";
import type { FriendChatMessage } from "../types/friend";
import { getStoredToken } from "../utils/storage";

type FriendEventPayload = {
  event:
    | "friend_request_sent"
    | "friend_request_received"
    | "friend_request_removed"
    | "friendship_accepted"
    | "friend_removed"
    | "friend_presence_changed";
  friendUserId?: string;
  requestId?: string;
  online?: boolean;
};

type FriendChatServerMessage =
  | { type: "init"; data: { userId: string; connected: boolean } }
  | { type: "chat_message"; data: FriendChatMessage }
  | {
      type: "message_recalled";
      data: { messageId: string; recalledAt: string | null };
    }
  | { type: "friend_event"; data: FriendEventPayload }
  | { type: "error"; data: string };

export function useFriendChatSocket(activeFriendUserId?: string) {
  const socketRef = useRef<WebSocket | null>(null);
  const activeFriendRef = useRef<string | undefined>(activeFriendUserId);

  const [connected, setConnected] = useState(false);
  const [statusText, setStatusText] = useState("Đang kết nối chat...");
  const [messages, setMessages] = useState<FriendChatMessage[]>([]);
  const [friendEventVersion, setFriendEventVersion] = useState(0);
  const [lastFriendEvent, setLastFriendEvent] =
    useState<FriendEventPayload | null>(null);

  useEffect(() => {
    activeFriendRef.current = activeFriendUserId;
  }, [activeFriendUserId]);

  useEffect(() => {
    const token = getStoredToken();
    const wsBase = GAME_CONFIG.network.wsBaseUrl;
    const wsUrl = `${wsBase}/ws/friends-chat${
      token ? `?token=${encodeURIComponent(token)}` : ""
    }`;

    const ws = new WebSocket(wsUrl);
    socketRef.current = ws;

    ws.onopen = () => {
      setConnected(true);
      setStatusText("Đã kết nối chat bạn bè");
    };

    ws.onmessage = (event) => {
      const message = JSON.parse(event.data) as FriendChatServerMessage;

      if (message.type === "chat_message") {
        const item = message.data;
        const currentFriendId = activeFriendRef.current;

        if (
          currentFriendId &&
          (item.senderId === currentFriendId ||
            item.receiverId === currentFriendId)
        ) {
          setMessages((prev) => {
            const existed = prev.some((msg) => msg.id === item.id);
            if (existed) return prev;
            return [...prev, item];
          });
        }
        return;
      }

      if (message.type === "message_recalled") {
        setMessages((prev) =>
          prev.map((msg) =>
            msg.id === message.data.messageId
              ? {
                  ...msg,
                  content: "",
                  recalled: true,
                  recalledAt: message.data.recalledAt,
                }
              : msg,
          ),
        );
        return;
      }

      if (message.type === "friend_event") {
        setLastFriendEvent(message.data);
        setFriendEventVersion((prev) => prev + 1);
        return;
      }

      if (message.type === "error") {
        setStatusText(message.data);
      }
    };

    ws.onclose = () => {
      setConnected(false);
      setStatusText("Mất kết nối chat bạn bè");
    };

    ws.onerror = () => {
      setStatusText("Lỗi kết nối chat bạn bè");
    };

    return () => {
      ws.close();
    };
  }, []);

  const sendMessage = (targetUserId: string, content: string) => {
    const socket = socketRef.current;
    if (!socket || socket.readyState !== WebSocket.OPEN) return;

    socket.send(
      JSON.stringify({
        type: "send_message",
        targetUserId,
        content,
      }),
    );
  };

  const recallMessage = (messageId: string) => {
    const socket = socketRef.current;
    if (!socket || socket.readyState !== WebSocket.OPEN) return;

    socket.send(
      JSON.stringify({
        type: "recall_message",
        messageId,
      }),
    );
  };

  return {
    connected,
    statusText,
    messages,
    setMessages,
    sendMessage,
    recallMessage,
    friendEventVersion,
    lastFriendEvent,
  };
}
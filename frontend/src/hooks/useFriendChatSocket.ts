import { useEffect, useRef, useState } from "react";
import { GAME_CONFIG } from "../config/gameConfig";
import type { FriendChatMessage } from "../types/friend";
import { getStoredToken } from "../utils/storage";

type FriendChatServerMessage =
  | { type: "init"; data: { userId: string; connected: boolean } }
  | { type: "chat_message"; data: FriendChatMessage }
  | { type: "error"; data: string };

export function useFriendChatSocket(activeFriendUserId?: string) {
  const socketRef = useRef<WebSocket | null>(null);
  const [connected, setConnected] = useState(false);
  const [statusText, setStatusText] = useState("Đang kết nối chat...");
  const [messages, setMessages] = useState<FriendChatMessage[]>([]);

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
        if (
          activeFriendUserId &&
          (item.senderId === activeFriendUserId ||
            item.receiverId === activeFriendUserId)
        ) {
          setMessages((prev) => [...prev, item]);
        }
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
  }, [activeFriendUserId]);

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

  return {
    connected,
    statusText,
    messages,
    setMessages,
    sendMessage,
  };
}
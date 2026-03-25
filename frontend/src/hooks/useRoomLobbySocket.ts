import { useEffect, useRef, useState } from "react";
import { GAME_CONFIG } from "../config/gameConfig";
import type {
  CurrentRoomState,
  MatchMode,
  RoomClientMessage,
  RoomServerMessage,
  RoomStartedInfo,
  RoomSummary,
} from "../game/roomWsTypes";
import { getStoredToken } from "../utils/storage";

/**
 * Hook quản lý websocket của hệ phòng riêng.
 *
 * Nhiệm vụ:
 * - kết nối /ws/rooms
 * - nhận danh sách phòng
 * - nhận trạng thái phòng hiện tại
 * - tạo phòng / vào phòng / rời phòng
 * - thêm bot / xóa bot / kick người chơi
 * - nhận room_started để chuyển sang màn game
 */
export function useRoomLobbySocket() {
  const socketRef = useRef<WebSocket | null>(null);

  const [connected, setConnected] = useState(false);
  const [statusText, setStatusText] = useState("Đang kết nối phòng...");
  const [rooms, setRooms] = useState<RoomSummary[]>([]);
  const [currentRoom, setCurrentRoom] = useState<CurrentRoomState | null>(null);
  const [lastStartedRoomInfo, setLastStartedRoomInfo] =
    useState<RoomStartedInfo | null>(null);

  /**
   * Gửi payload lên websocket room lobby
   */
  const send = (payload: RoomClientMessage) => {
    const socket = socketRef.current;
    if (!socket || socket.readyState !== WebSocket.OPEN) return;
    socket.send(JSON.stringify(payload));
  };

  useEffect(() => {
    const token = getStoredToken();
    const wsBase = GAME_CONFIG.network.wsBaseUrl;
    const wsUrl = `${wsBase}/ws/rooms${
      token ? `?token=${encodeURIComponent(token)}` : ""
    }`;

    const ws = new WebSocket(wsUrl);
    socketRef.current = ws;

    ws.onopen = () => {
      setConnected(true);
      setStatusText("Đã kết nối hệ phòng riêng");
      send({ type: "list_rooms" });
    };

    ws.onmessage = (event) => {
      const message = JSON.parse(event.data) as RoomServerMessage;

      if (message.type === "rooms") {
        setRooms(message.data);
        return;
      }

      if (message.type === "room_state") {
        setCurrentRoom(message.data);
        return;
      }

      if (message.type === "room_created") {
        setStatusText(`Đã tạo phòng ${message.data.roomCode}`);
        return;
      }

      if (message.type === "room_started") {
        setLastStartedRoomInfo(message.data);
        setStatusText(
          `Phòng ${message.data.roomCode} đã bắt đầu (${message.data.matchMode})`,
        );
        return;
      }

      if (message.type === "error") {
        setStatusText(message.data);
      }
    };

    ws.onclose = () => {
      setConnected(false);
      setStatusText("Mất kết nối hệ phòng");
    };

    ws.onerror = () => {
      setStatusText("Lỗi kết nối WebSocket phòng");
    };

    return () => {
      ws.close();
    };
  }, []);

  /**
   * Yêu cầu server gửi lại danh sách phòng
   */
  const refreshRooms = () => send({ type: "list_rooms" });

  /**
   * Tạo phòng mới
   */
  const createRoom = (
    roomName: string,
    maxPlayers: number,
    isPrivate: boolean,
    matchMode: MatchMode,
  ) => {
    send({ type: "create_room", roomName, maxPlayers, isPrivate, matchMode });
  };

  /**
   * Vào phòng theo mã
   */
  const joinRoom = (roomCode: string) => {
    send({ type: "join_room", roomCode });
  };

  /**
   * Rời phòng hiện tại
   */
  const leaveRoom = () => {
    send({ type: "leave_room" });
  };

  /**
   * Chủ phòng bấm Chơi
   */
  const startRoom = () => {
    send({ type: "start_room" });
  };

  /**
   * Host thêm bot
   */
  const addBot = () => {
    send({ type: "add_bot" });
  };

  /**
   * Host xóa bot theo clientId bot
   */
  const removeBot = (targetClientId: string) => {
    send({ type: "remove_bot", targetClientId });
  };

  /**
   * Host kick người chơi thật
   */
  const kickMember = (targetClientId: string) => {
    send({ type: "kick_member", targetClientId });
  };

  return {
    connected,
    statusText,
    rooms,
    currentRoom,
    lastStartedRoomInfo,
    refreshRooms,
    createRoom,
    joinRoom,
    leaveRoom,
    startRoom,
    addBot,
    removeBot,
    kickMember,
  };
}
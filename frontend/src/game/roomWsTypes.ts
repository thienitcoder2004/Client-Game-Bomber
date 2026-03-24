// ===============================
// Kiểu dữ liệu cho socket phòng riêng
// ===============================

// 1 thành viên trong phòng
export type RoomMember = {
  clientId: string;
  characterName: string;
  host: boolean;

  // true nếu đây là bot
  bot?: boolean;
};

// Tóm tắt 1 phòng để hiện ở danh sách phòng
export type RoomSummary = {
  roomCode: string;
  roomName: string;
  hostName: string;
  playerCount: number;
  maxPlayers: number;
  status: "WAITING" | "PLAYING";
};

// Trạng thái chi tiết của phòng hiện tại
export type CurrentRoomState = {
  roomCode: string;
  roomName: string;
  playerCount: number;
  maxPlayers: number;
  status: "WAITING" | "PLAYING";
  isHost: boolean;
  canStart: boolean;
  members: RoomMember[];
};

// Dữ liệu khi chủ phòng bấm Chơi
export type RoomStartedInfo = {
  roomCode: string;
  maxPlayers: number;
  humanCount: number;
  botCount: number;
};

export type RoomsMessage = {
  type: "rooms";
  data: RoomSummary[];
};

export type RoomStateMessage = {
  type: "room_state";
  data: CurrentRoomState | null;
};

export type RoomCreatedMessage = {
  type: "room_created";
  data: {
    roomCode: string;
  };
};

export type RoomStartedMessage = {
  type: "room_started";
  data: RoomStartedInfo;
};

export type RoomErrorMessage = {
  type: "error";
  data: string;
};

export type RoomServerMessage =
  | RoomsMessage
  | RoomStateMessage
  | RoomCreatedMessage
  | RoomStartedMessage
  | RoomErrorMessage;

// ===============================
// Message client -> server
// ===============================

export type ListRoomsClientMessage = {
  type: "list_rooms";
};

export type CreateRoomClientMessage = {
  type: "create_room";
  roomName: string;
  maxPlayers: number;
  isPrivate: boolean;
};

export type JoinRoomClientMessage = {
  type: "join_room";
  roomCode: string;
};

export type LeaveRoomClientMessage = {
  type: "leave_room";
};

export type StartRoomClientMessage = {
  type: "start_room";
};

export type AddBotRoomClientMessage = {
  type: "add_bot";
};

export type RemoveBotRoomClientMessage = {
  type: "remove_bot";
  targetClientId: string;
};

export type KickMemberRoomClientMessage = {
  type: "kick_member";
  targetClientId: string;
};

export type RoomClientMessage =
  | ListRoomsClientMessage
  | CreateRoomClientMessage
  | JoinRoomClientMessage
  | LeaveRoomClientMessage
  | StartRoomClientMessage
  | AddBotRoomClientMessage
  | RemoveBotRoomClientMessage
  | KickMemberRoomClientMessage;
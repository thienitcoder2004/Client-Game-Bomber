export type RoomSummary = {
  roomCode: string;
  roomName: string;
  hostName: string;
  playerCount: number;
  maxPlayers: number;
  status: "WAITING" | "PLAYING";
  isPrivate: boolean;
};

export type RoomMember = {
  clientId: string;
  characterName: string;
  host: boolean;
};

export type CurrentRoomState = {
  roomCode: string;
  roomName: string;
  maxPlayers: number;
  playerCount: number;
  status: "WAITING" | "PLAYING";
  isPrivate: boolean;
  isHost: boolean;
  canStart: boolean;
  hostName: string;
  members: RoomMember[];
};

export type RoomStartedInfo = {
  roomCode: string;
  roomName: string;
  maxPlayers: number;
};

export type RoomServerMessage =
  | { type: "init"; data: { clientId: string } }
  | { type: "rooms"; data: RoomSummary[] }
  | { type: "room_state"; data: CurrentRoomState | null }
  | { type: "room_created"; data: { roomCode: string } }
  | { type: "room_started"; data: RoomStartedInfo }
  | { type: "error"; data: string };

export type RoomClientMessage =
  | { type: "list_rooms" }
  | {
      type: "create_room";
      roomName: string;
      maxPlayers: number;
      isPrivate: boolean;
    }
  | { type: "join_room"; roomCode: string }
  | { type: "leave_room" }
  | { type: "start_room" };
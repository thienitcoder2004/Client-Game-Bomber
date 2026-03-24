import { useEffect, useRef, useState } from "react";
import { GAME_CONFIG } from "../config/gameConfig";
import { SHAKE_MS } from "../game/constants";
import { spawnExplosionParticles } from "../game/explosion";
import { createGameAudio, playBombExplode, playStep } from "../game/audio";
import type {
  BoardItem,
  BombState,
  ExplosionState,
  ItemType,
  Particle,
  PickupEffect,
  PlayerState,
  TileType,
  Direction,
} from "../game/types";
import type {
  ClientWsMessage,
  RemoteGameState,
  ServerWsMessage,
} from "../game/wsTypes";
import { getStoredToken } from "../utils/storage";

function createEmptyBoard(rows: number, cols: number): TileType[][] {
  return Array.from({ length: rows }, () =>
    Array.from({ length: cols }, () => 0 as TileType),
  );
}

function normalizeBoard(board: number[][]): TileType[][] {
  return board.map((row) =>
    row.map((cell) => {
      if (cell === 1) return 1;
      if (cell === 2) return 2;
      return 0;
    }),
  );
}

function createPlaceholderPlayers(rows: number, cols: number): PlayerState[] {
  return [
    {
      id: 1,
      row: 1,
      col: 1,
      dir: "down",
      walkFrame: 0,
      lives: GAME_CONFIG.player.startLives,
      invulnerableUntil: 0,
      frozenUntil: 0,
      inventory: [],
      maxBombs: GAME_CONFIG.player.startMaxBombs,
      bombRange: GAME_CONFIG.player.startBombRange,
      speedLevel: GAME_CONFIG.player.startSpeedLevel,
      bombsPlaced: 0,
      kills: 0,
      deaths: 0,
      ovr: 0,
      characterName: "Player 1",
      gender: "MALE",
      avatarCode: "male-default",
      bot: false,
    },
    {
      id: 2,
      row: 1,
      col: cols - 2,
      dir: "down",
      walkFrame: 0,
      lives: GAME_CONFIG.player.startLives,
      invulnerableUntil: 0,
      frozenUntil: 0,
      inventory: [],
      maxBombs: GAME_CONFIG.player.startMaxBombs,
      bombRange: GAME_CONFIG.player.startBombRange,
      speedLevel: GAME_CONFIG.player.startSpeedLevel,
      bombsPlaced: 0,
      kills: 0,
      deaths: 0,
      ovr: 0,
      characterName: "Player 2",
      gender: "FEMALE",
      avatarCode: "female-default",
      bot: false,
    },
    {
      id: 3,
      row: rows - 2,
      col: 1,
      dir: "down",
      walkFrame: 0,
      lives: GAME_CONFIG.player.startLives,
      invulnerableUntil: 0,
      frozenUntil: 0,
      inventory: [],
      maxBombs: GAME_CONFIG.player.startMaxBombs,
      bombRange: GAME_CONFIG.player.startBombRange,
      speedLevel: GAME_CONFIG.player.startSpeedLevel,
      bombsPlaced: 0,
      kills: 0,
      deaths: 0,
      ovr: 0,
      characterName: "Player 3",
      gender: "MALE",
      avatarCode: "male-default",
      bot: false,
    },
    {
      id: 4,
      row: rows - 2,
      col: cols - 2,
      dir: "down",
      walkFrame: 0,
      lives: GAME_CONFIG.player.startLives,
      invulnerableUntil: 0,
      frozenUntil: 0,
      inventory: [],
      maxBombs: GAME_CONFIG.player.startMaxBombs,
      bombRange: GAME_CONFIG.player.startBombRange,
      speedLevel: GAME_CONFIG.player.startSpeedLevel,
      bombsPlaced: 0,
      kills: 0,
      deaths: 0,
      ovr: 0,
      characterName: "Player 4",
      gender: "FEMALE",
      avatarCode: "female-default",
      bot: false,
    },
  ];
}

function getPickupText(item: ItemType) {
  if (item === "BOMB_UP") return "+Bomb";
  if (item === "FLAME_UP") return "+Flame";
  if (item === "SPEED_UP") return "+Speed";
  if (item === "SHIELD") return "+Shield";
  if (item === "HEART") return "+Heart";
  if (item === "TELEPORT") return "+Warp";
  if (item === "RANDOM_BOMB") return "+Random";
  return "+Freeze";
}

export function useGameSocket(rows: number, cols: number) {
  const socketRef = useRef<WebSocket | null>(null);
  const playerIdRef = useRef<1 | 2 | 3 | 4 | null>(null);
  const gameOverRef = useRef(false);
  const gameStartedRef = useRef(false);

  const boardRef = useRef<TileType[][]>(createEmptyBoard(rows, cols));
  const playersRef = useRef<PlayerState[]>(createPlaceholderPlayers(rows, cols));
  const bombsRef = useRef<BombState[]>([]);
  const explosionsRef = useRef<ExplosionState[]>([]);
  const particlesRef = useRef<Particle[]>([]);
  const itemsRef = useRef<BoardItem[]>([]);
  const pickupFxRef = useRef<PickupEffect[]>([]);
  const shakeUntilRef = useRef(0);
  const prevPlayersMapRef = useRef<Map<number, PlayerState>>(new Map());
  const audioRef = useRef(createGameAudio());

  const [statusText, setStatusText] = useState("Đang kết nối server...");
  const [playerLabel, setPlayerLabel] = useState("Chưa được gán người chơi");
  const [connected, setConnected] = useState(false);

  const [roomState, setRoomState] = useState<{
    waitingForPlayers: boolean;
    gameStarted: boolean;
    connectedPlayers: number;
    requiredPlayers: number;
    countdownSeconds: number | null;
  }>({
    waitingForPlayers: true,
    gameStarted: false,
    connectedPlayers: 0,
    requiredPlayers: GAME_CONFIG.room.defaultRequiredPlayers,
    countdownSeconds: null,
  });

  const [matchResult, setMatchResult] = useState<{
    gameOver: boolean;
    winnerId: number | null;
    resultMessage: string | null;
    players: PlayerState[];
  }>({
    gameOver: false,
    winnerId: null,
    resultMessage: null,
    players: [],
  });

  useEffect(() => {
    const token = getStoredToken();

    const params = new URLSearchParams(window.location.search);
    const roomCode = params.get("roomCode")?.trim();
    const requiredPlayers = params.get("requiredPlayers")?.trim();
    const humanCount = params.get("humanCount")?.trim();
    const botCount = params.get("botCount")?.trim();
    const mode = params.get("mode")?.trim();

    const wsBase = GAME_CONFIG.network.wsBaseUrl;
    const query = new URLSearchParams();

    if (token) query.set("token", token);
    if (roomCode) query.set("roomCode", roomCode);
    if (requiredPlayers) query.set("requiredPlayers", requiredPlayers);

    // ===== rất quan trọng =====
    // truyền tiếp config room lobby sang game socket
    if (humanCount) query.set("humanCount", humanCount);
    if (botCount) query.set("botCount", botCount);
    if (mode) query.set("mode", mode);

    const wsUrl = `${wsBase}/ws/game?${query.toString()}`;

    const ws = new WebSocket(wsUrl);
    socketRef.current = ws;

    ws.onopen = () => {
      setConnected(true);
      setStatusText(
        roomCode
          ? `Đã kết nối trận phòng ${roomCode}. Đang chờ người chơi...`
          : "Đã kết nối server. Đang vào phòng chờ...",
      );
    };

    ws.onmessage = (event) => {
      const message = JSON.parse(event.data) as ServerWsMessage;

      if (message.type === "init") {
        playerIdRef.current = message.data.playerId;
        setPlayerLabel(`Bạn là Player ${message.data.playerId}`);
        return;
      }

      if (message.type === "error") {
        setStatusText(String(message.data));
        return;
      }

      if (message.type === "state") {
        const nextState: RemoteGameState = message.data;
        const now = Date.now();

        gameOverRef.current = nextState.gameOver ?? false;
        gameStartedRef.current = nextState.gameStarted ?? false;

        setRoomState({
          waitingForPlayers: nextState.waitingForPlayers ?? true,
          gameStarted: nextState.gameStarted ?? false,
          connectedPlayers: nextState.connectedPlayers ?? 0,
          requiredPlayers:
            nextState.requiredPlayers ?? GAME_CONFIG.room.defaultRequiredPlayers,
          countdownSeconds: nextState.countdownSeconds ?? null,
        });

        if (nextState.waitingForPlayers) {
          setStatusText(
            `Đang chờ đủ người chơi (${nextState.connectedPlayers}/${nextState.requiredPlayers})`,
          );
        } else if (!nextState.gameStarted && nextState.countdownSeconds != null) {
          setStatusText(
            `Đã đủ người. Trận bắt đầu sau ${nextState.countdownSeconds}s`,
          );
        } else if (nextState.gameStarted && !nextState.gameOver) {
          setStatusText("Trận đấu đang diễn ra");
        }

        const prevExplosionIds = new Set(explosionsRef.current.map((e) => e.id));
        const newExplosions = (nextState.explosions ?? []).filter(
          (e) => !prevExplosionIds.has(e.id),
        );

        if (newExplosions.length > 0) {
          particlesRef.current.push(
            ...newExplosions.flatMap((e) => spawnExplosionParticles(e)),
          );
          shakeUntilRef.current = now + SHAKE_MS;
          playBombExplode(audioRef.current);
        }

        const nextPlayers: PlayerState[] = (nextState.players ?? []).map(
          (remotePlayer) => {
            const prevPlayer = prevPlayersMapRef.current.get(remotePlayer.id);
            const moved =
              !!prevPlayer &&
              (prevPlayer.row !== remotePlayer.row ||
                prevPlayer.col !== remotePlayer.col);

            return {
              id: remotePlayer.id,
              row: remotePlayer.row,
              col: remotePlayer.col,
              dir:
                (remotePlayer.direction as Direction) ??
                prevPlayer?.dir ??
                "down",
              walkFrame: moved ? (prevPlayer?.walkFrame === 1 ? 0 : 1) : 0,
              lives: remotePlayer.lives,
              invulnerableUntil: remotePlayer.invulnerableUntil ?? 0,
              frozenUntil: remotePlayer.frozenUntil ?? 0,
              inventory: remotePlayer.inventory ?? [],
              maxBombs: remotePlayer.maxBombs ?? 1,
              bombRange: remotePlayer.bombRange ?? 1,
              speedLevel: remotePlayer.speedLevel ?? 1,
              bombsPlaced: remotePlayer.bombsPlaced ?? 0,
              kills: remotePlayer.kills ?? 0,
              deaths: remotePlayer.deaths ?? 0,
              ovr: remotePlayer.ovr ?? 0,
              characterName:
                remotePlayer.characterName || `Player ${remotePlayer.id}`,
              gender: remotePlayer.gender || "",
              avatarCode: remotePlayer.avatarCode || "",
              bot: remotePlayer.bot ?? false,
            };
          },
        );

        const myId = playerIdRef.current;
        if (myId != null) {
          const prevMe = prevPlayersMapRef.current.get(myId);
          const nextMe = nextPlayers.find((p) => p.id === myId);

          if (
            prevMe &&
            nextMe &&
            nextState.gameStarted &&
            (prevMe.row !== nextMe.row || prevMe.col !== nextMe.col)
          ) {
            playStep(audioRef.current, now);
          }

          if (
            prevMe &&
            nextMe &&
            nextMe.inventory.length > prevMe.inventory.length
          ) {
            const picked = nextMe.inventory[nextMe.inventory.length - 1];
            pickupFxRef.current.push({
              id: `${Date.now()}-${Math.random()}`,
              x: nextMe.col,
              y: nextMe.row,
              text: getPickupText(picked),
              createdAt: now,
              duration: 900,
            });
          }

          if (nextMe && nextMe.frozenUntil > now) {
            setStatusText("Bạn đang bị đóng băng...");
          } else if (nextMe) {
            setPlayerLabel(`Bạn là ${nextMe.characterName}`);
          }
        }

        boardRef.current = normalizeBoard(nextState.board ?? []);
        playersRef.current = nextPlayers;
        bombsRef.current = nextState.bombs ?? [];
        explosionsRef.current = nextState.explosions ?? [];
        itemsRef.current = nextState.items ?? [];
        prevPlayersMapRef.current = new Map(nextPlayers.map((p) => [p.id, p]));

        setMatchResult({
          gameOver: nextState.gameOver ?? false,
          winnerId: nextState.winnerId ?? null,
          resultMessage: nextState.resultMessage ?? null,
          players: [...nextPlayers].sort((a, b) => b.ovr - a.ovr),
        });

        if (nextState.gameOver) {
          setStatusText(nextState.resultMessage ?? "Trận đấu đã kết thúc");
        }
      }
    };

    ws.onclose = () => {
      setConnected(false);
      setStatusText("Mất kết nối server.");
    };

    ws.onerror = () => {
      setStatusText("Lỗi kết nối WebSocket.");
    };

    return () => {
      ws.close();
    };
  }, [rows, cols]);

  const sendWs = (payload: ClientWsMessage) => {
    const socket = socketRef.current;
    if (!socket || socket.readyState !== WebSocket.OPEN) return;
    socket.send(JSON.stringify(payload));
  };

  return {
    connected,
    statusText,
    setStatusText,
    playerLabel,
    roomState,
    matchResult,
    sendWs,
    refs: {
      socketRef,
      playerIdRef,
      gameOverRef,
      gameStartedRef,
      boardRef,
      playersRef,
      bombsRef,
      explosionsRef,
      particlesRef,
      itemsRef,
      pickupFxRef,
      shakeUntilRef,
      audioRef,
    },
  };
}
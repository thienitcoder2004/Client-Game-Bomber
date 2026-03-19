/**
 * File cấu hình gameplay chính của frontend.
 *
 * Bạn nên sửa thông số ở đây trước, thay vì sửa rải rác nhiều file.
 * Frontend này dùng file này để:
 * - gọi đúng API / WebSocket
 * - hiển thị đúng kích thước map
 * - đồng bộ các mốc thời gian hiệu ứng
 * - lấy các giới hạn cơ bản của người chơi / phòng
 */
export const GAME_CONFIG = {
  info: {
    // Tên project để ghi chú nội bộ.
    projectName: "Client Game Bomber",
    // Ghi chú ngắn cho người chỉnh file.
    note: "Chỉnh các thông số game chính ở đây để đỡ sửa nhiều file.",
  },

  // network: {
  //   // URL backend HTTP API.
  //   // Ưu tiên lấy từ biến môi trường VITE_API_BASE_URL.
  //   // Nếu không có thì dùng URL mặc định bên dưới.
  //   apiBaseUrl:
  //     (import.meta.env.VITE_API_BASE_URL as string | undefined)?.trim() ||
  //     "https://herta-proannexation-alia.ngrok-free.dev",

  //   // URL WebSocket của game / room.
  //   // Ưu tiên lấy từ biến môi trường VITE_WS_BASE_URL.
  //   wsBaseUrl:
  //     (import.meta.env.VITE_WS_BASE_URL as string | undefined)?.trim() ||
  //     "wss://herta-proannexation-alia.ngrok-free.dev",

  //   // Header phụ để giảm cảnh báo của ngrok khi gọi request.
  //   ngrokSkipBrowserWarningHeaderValue: "1",
  // },

  network: {
  apiBaseUrl:
    (import.meta.env.VITE_API_BASE_URL as string | undefined)?.trim() ||
    "http://localhost:8080",

  wsBaseUrl:
    (import.meta.env.VITE_WS_BASE_URL as string | undefined)?.trim() ||
    "ws://localhost:8080",

  ngrokSkipBrowserWarningHeaderValue: "",
},

  room: {
    // Số người mặc định của phòng khi tạo mới.
    defaultRequiredPlayers: 4,

    // Các kích thước phòng được phép chọn ở frontend.
    allowedRoomSizes: [2, 3, 4] as number[],
  },

  board: {
    // Số hàng map hiển thị.
    rows: 13,
    // Số cột map hiển thị.
    cols: 15,
    // Kích thước 1 ô vuông của bản đồ (pixel).
    tileSize: 56,
    // Chiều cao khu vực HUD / thanh trạng thái phía trên (pixel).
    hudHeight: 78,
  },

  player: {
    // Số mạng ban đầu.
    startLives: 3,
    // Số bom được đặt cùng lúc khi mới vào trận.
    startMaxBombs: 1,
    // Tầm nổ bom ban đầu.
    startBombRange: 1,
    // Cấp tốc độ di chuyển ban đầu.
    startSpeedLevel: 1,
    // Số ô chứa vật phẩm tối đa trong túi đồ.
    maxInventorySlots: 5,
    // Giới hạn số bom tối đa có thể nâng cấp tới.
    maxBombs: 5,
    // Giới hạn tầm nổ tối đa có thể nâng cấp tới.
    maxBombRange: 5,
    // Giới hạn cấp tốc độ tối đa.
    maxSpeedLevel: 5,
    // Giới hạn số mạng tối đa.
    maxLives: 5,
    // Thời gian khiên tồn tại sau khi kích hoạt.
    shieldDurationMs: 5000,
  },

  timing: {
    // Khoảng thời gian tối đa để coi input bàn phím vẫn còn hiệu lực.
    keyGapMaxMs: 3000,
    // Thời gian rung màn hình / hiệu ứng hit (ms).
    shakeMs: 100,
    // Thời gian từ lúc đặt bom đến lúc nổ.
    bombFuseMs: 1000,
    // Thời gian hiệu ứng nổ hiển thị ở frontend.
    explosionMs: 360,
    // Thời gian bất tử ngắn sau khi dính sát thương.
    invulnerableMs: 1400,

    // Cooldown di chuyển theo từng cấp tốc độ.
    // Số càng nhỏ thì nhân vật đi càng nhanh.
    moveCooldownBySpeedLevel: {
      1: 160,
      2: 135,
      3: 110,
      4: 90,
      5: 75,
    } as Record<number, number>,
  },
} as const;

/**
 * Lấy cooldown di chuyển theo speed level hiện tại.
 * Nếu level không hợp lệ thì dùng level mặc định lúc bắt đầu trận.
 */
export const getMoveCooldownBySpeedLevel = (speedLevel?: number) => {
  const level = Number(speedLevel ?? GAME_CONFIG.player.startSpeedLevel);
  return (
    GAME_CONFIG.timing.moveCooldownBySpeedLevel[level] ??
    GAME_CONFIG.timing.moveCooldownBySpeedLevel[
      GAME_CONFIG.player.startSpeedLevel
    ]
  );
};

/**
 * Lấy kích thước phòng mặc định hợp lệ.
 * Nếu defaultRequiredPlayers không nằm trong danh sách cho phép
 * thì lấy phần tử đầu tiên của allowedRoomSizes.
 */
export const getDefaultRoomSize = () => {
  return (
    GAME_CONFIG.room.allowedRoomSizes.find(
      (size) => size === GAME_CONFIG.room.defaultRequiredPlayers,
    ) ?? GAME_CONFIG.room.allowedRoomSizes[0]
  );
};

package com.bomberserver.backend.service;

import com.bomberserver.backend.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service xử lý AI cho bot trong game Bomber.
 *
 * - Phân tích trạng thái hiện tại của trận đấu
 * - Quyết định bot nên đi đâu, đặt bom hay dùng item gì
 * - Giúp bot hành xử giống người chơi hơn:
 *   + biết né bom
 *   + biết đuổi người chơi
 *   + biết phá tường
 *   + biết nhặt item
 *   + biết dùng skill và item khi hợp lý
 *
 * - Mỗi lần bot tới lượt "suy nghĩ", hàm decide(...) sẽ được gọi
 * - Bot sẽ kiểm tra theo thứ tự ưu tiên:
 *   1. Có đang nguy hiểm không? Nếu có thì né trước
 *   2. Có item tăng sức mạnh thì dùng ngay
 *   3. Có người chơi trong bán kính phát hiện không?
 *   4. Có cơ hội giết người hay phá tường không?
 *   5. Có item tốt ở gần không?
 *   6. Nếu không có gì đặc biệt thì đi ngẫu nhiên nhưng vẫn an toàn
 */
@Service
public class BotService {

    /**
     * Random dùng cho các quyết định ngẫu nhiên của bot,
     * ví dụ như chọn hướng đi fallback cuối cùng.
     */
    private final Random random = new Random();

    // COOLDOWN DI CHUYỂN / SUY NGHĨ THEO SPEED LEVEL
    // Speed level càng cao thì bot phản ứng càng nhanh
    /**
     * Cooldown suy nghĩ / di chuyển của bot khi speed level = 1.
     * Giá trị đọc từ file application.properties.
     */
    @Value("${game.timing.move-cooldown-level-1-ms:3000}")
    private long moveCooldownLevel1Ms;

    /**
     * Cooldown suy nghĩ / di chuyển của bot khi speed level = 2.
     */
    @Value("${game.timing.move-cooldown-level-2-ms:2500}")
    private long moveCooldownLevel2Ms;

    /**
     * Cooldown suy nghĩ / di chuyển của bot khi speed level = 3.
     */
    @Value("${game.timing.move-cooldown-level-3-ms:2000}")
    private long moveCooldownLevel3Ms;

    /**
     * Cooldown suy nghĩ / di chuyển của bot khi speed level = 4.
     */
    @Value("${game.timing.move-cooldown-level-4-ms:1500}")
    private long moveCooldownLevel4Ms;

    /**
     * Cooldown suy nghĩ / di chuyển của bot khi speed level = 5.
     */
    @Value("${game.timing.move-cooldown-level-5-ms:1000}")
    private long moveCooldownLevel5Ms;

    // BOT AI CONFIG
    // Các thông số giới hạn phạm vi tìm kiếm / hành vi bot
    /**
     * Bán kính phát hiện người chơi của bot.
     * - detectRadius = 6
     * - Nếu người chơi ở xa hơn 6 ô Manhattan
     *   thì bot sẽ tạm bỏ qua, không dí theo.
     */
    @Value("${game.bot.detect-radius:6}")
    private int detectRadius;

    /**
     * Độ sâu tối đa khi BFS tìm đường dí theo người chơi.
     * Số càng lớn thì bot càng "thông minh" hơn khi đuổi người,
     * nhưng cũng tốn nhiều xử lý hơn.
     */
    @Value("${game.bot.chase-search-depth:8}")
    private int chaseSearchDepth;

    /**
     * Độ sâu tối đa khi BFS tìm vị trí phù hợp để phá tường mềm.
     */
    @Value("${game.bot.break-wall-search-depth:6}")
    private int breakWallSearchDepth;

    /**
     * Độ sâu tối đa khi BFS đi nhặt item.
     */
    @Value("${game.bot.item-search-depth:5}")
    private int itemSearchDepth;

    /**
     * Độ sâu tối đa khi BFS tìm đường chạy thoát khỏi vùng nguy hiểm.
     */
    @Value("${game.bot.escape-search-depth:8}")
    private int escapeSearchDepth;

    /**
     * Kết quả cuối cùng mà bot trả ra sau 1 lần quyết định.
     *
     * move:
     * - Hướng bot sẽ đi trong tick này
     *
     * placeBomb:
     * - Có đặt bom hay không
     *
     * useItemSlot:
     * - Nếu bot muốn dùng item thì đây là index slot trong inventory
     *
     * useSkillBomb:
     * - Có bật skill tăng số bom / hỗ trợ bom không
     *
     * useSkillSpeed:
     * - Có bật skill tăng tốc không
     */
    public record BotDecision(
            Direction move,
            boolean placeBomb,
            Integer useItemSlot,
            boolean useSkillBomb,
            boolean useSkillSpeed
    ) {
        /**
         * Trạng thái mặc định: bot không làm gì cả.
         *
         * Dùng khi:
         * - dữ liệu đầu vào lỗi
         * - bot chưa tới lượt suy nghĩ
         * - không có hành động phù hợp
         */
        public static BotDecision idle() {
            return new BotDecision(null, false, null, false, false);
        }
    }

    /**
     * Functional interface dùng để truyền điều kiện mục tiêu cho BFS.
     *
     * Ý nghĩa:
     * - Khi gọi BFS, ta có thể định nghĩa "mục tiêu" là gì:
     *   + ô có người chơi
     *   + ô có item
     *   + ô cạnh tường mềm
     *   + ô an toàn
     *
     * ok(row, col) trả về true nếu ô đó là mục tiêu cần tìm.
     */
    @FunctionalInterface
    private interface TargetCheck {
        boolean ok(int row, int col);
    }

    /**
     * Bom ảo dùng để mô phỏng tình huống:
     * "Nếu bot đặt bom ngay tại vị trí hiện tại thì vùng nổ sẽ ra sao?"
     *
     * Dùng cho:
     * - kiểm tra bot có tự nhốt mình không
     * - kiểm tra bot có thể chạy thoát sau khi đặt bom không
     */
    private record VirtualBomb(int row, int col, int range) {
    }

    /**
     * Node dùng trong BFS.
     *
     * row, col:
     * - vị trí hiện tại của node
     *
     * firstMove:
     * - hướng đầu tiên từ vị trí bot phải đi
     * - rất quan trọng vì BFS có thể tìm mục tiêu xa,
     *   nhưng bot chỉ cần biết "bước đầu tiên nên đi hướng nào"
     *
     * depth:
     * - số bước tính từ vị trí xuất phát
     */
    private record SearchNode(int row, int col, Direction firstMove, int depth) {
    }

    /**
     * Kết quả trả về của một lần BFS.
     *
     * firstMove:
     * - bước đầu tiên nên đi
     *
     * depth:
     * - độ sâu / khoảng cách từ vị trí bot đến mục tiêu
     */
    private record SearchResult(Direction firstMove, int depth) {
    }

    /**
     * Hàm chính quyết định bot sẽ làm gì trong tick hiện tại.
     *
     * Đây là "bộ não" chính của bot.
     *
     * Thứ tự ưu tiên:
     * 1. Nếu đang đứng ở vùng nguy hiểm -> chạy trước
     * 2. Nếu có item tăng sức mạnh dùng ngay -> dùng luôn
     * 3. Nếu có người chơi ở hơi xa -> bật speed để dí
     * 4. Nếu lại gần người -> chuẩn bị bom đặc biệt
     * 5. Nếu có thể giết người -> đặt bom
     * 6. Nếu đang cạnh tường mềm -> cân nhắc phá tường
     * 7. Nếu thấy người trong bán kính -> dí theo bằng BFS
     * 8. Nếu không thấy người -> tìm vị trí cạnh tường mềm để mở map
     * 9. Nếu rảnh -> đi nhặt item tốt
     * 10. Nếu không còn gì ưu tiên -> đi ngẫu nhiên nhưng an toàn
     *
     * @param board         bản đồ game
     * @param activePlayers danh sách người chơi đang hoạt động
     * @param bombs         danh sách bom hiện tại
     * @param explosions    danh sách vùng nổ đang tồn tại
     * @param items         danh sách item trên map
     * @param bot           player đại diện cho bot đang suy nghĩ
     * @param now           thời gian hiện tại (milliseconds hoặc cùng đơn vị hệ thống đang dùng)
     * @return quyết định của bot trong lượt này
     */
    public BotDecision decide(
            int[][] board,
            List<Player> activePlayers,
            List<Bomb> bombs,
            List<Explosion> explosions,
            List<Item> items,
            Player bot,
            long now
    ) {
        // Nếu dữ liệu đầu vào bị null thì bot không làm gì để tránh lỗi NullPointerException
        if (board == null || activePlayers == null || bombs == null || explosions == null || items == null || bot == null) {
            return BotDecision.idle();
        }

        // Nếu đây không phải bot hoặc bot đã chết thì không xử lý AI nữa
        if (!bot.bot || bot.lives <= 0) {
            return BotDecision.idle();
        }

        // Tính cooldown suy nghĩ hiện tại theo speed level của bot.
        // Speed level càng cao thì bot được suy nghĩ lại càng sớm.
        long botMoveCooldown = getMoveCooldownForSpeedLevel(bot.speedLevel);

        // Nếu chưa tới thời điểm bot được suy nghĩ tiếp thì đứng yên
        if (now < bot.botNextThinkAt) {
            return BotDecision.idle();
        }

        // Cập nhật thời gian bot được suy nghĩ ở lượt sau
        bot.botNextThinkAt = now + botMoveCooldown;

        // Tìm người chơi thật gần bot nhất trong bán kính phát hiện
        // Bot sẽ bỏ qua:
        // - chính nó
        // - bot khác
        // - người đã chết
        // - người ngoài phạm vi detectRadius
        Player target = findNearestHuman(activePlayers, bot, detectRadius);

        // =========================================================
        // 1) Nếu bot đang ở ô nguy hiểm -> tìm đường chạy trước
        // =========================================================
        if (isDangerCell(board, bombs, explosions, bot.row, bot.col, null)) {
            boolean useSpeedSkill = false;

            // Tìm 1 hướng giúp bot thoát khỏi danger
            Direction escapeMove = findEscapeMoveAllowFutureBlast(
                    board,
                    activePlayers,
                    bombs,
                    explosions,
                    bot
            );

            // Nếu tìm được đường thoát thì đi theo hướng đó
            if (escapeMove != null) {
                return new BotDecision(
                        escapeMove,
                        false,
                        null,
                        false,
                        useSpeedSkill
                );
            }

            // Nếu không có đường chạy, thử dùng khiên để sống sót
            Integer shieldSlot = findItemSlot(bot, ItemType.SHIELD);
            if (shieldSlot != null && now >= bot.invulnerableUntil) {
                return new BotDecision(
                        null,
                        false,
                        shieldSlot,
                        false,
                        false
                );
            }

            // Nếu không có khiên thì thử dịch chuyển
            Integer teleportSlot = findItemSlot(bot, ItemType.TELEPORT);
            if (teleportSlot != null) {
                return new BotDecision(
                        null,
                        false,
                        teleportSlot,
                        false,
                        false
                );
            }

            // Nếu vẫn bó tay, đi ngẫu nhiên theo hướng còn an toàn nhất
            Direction panic = randomSafeDirection(board, activePlayers, bombs, explosions, bot);
            return new BotDecision(
                    panic,
                    false,
                    null,
                    false,
                    useSpeedSkill
            );
        }

        // =========================================================
        // 2) Dùng item nâng cấp "lành tính" ngay
        // Bot mạnh lên sớm thì chơi ổn định hơn
        // =========================================================
        Integer instantItem = findImmediateUpgradeItemSlot(bot);
        if (instantItem != null) {
            return new BotDecision(null, false, instantItem, false, false);
        }

        // =========================================================
        // 3) Nếu mục tiêu còn hơi xa thì bật speed skill để dí
        // =========================================================
        if (target != null
                && manhattan(bot.row, bot.col, target.row, target.col) >= 3
                && bot.speedBoostUntil <= now) {
            return new BotDecision(
                    null,
                    false,
                    null,
                    false,
                    true
            );
        }

        // =========================================================
        // 4) Nếu mục tiêu ở gần thì chuẩn bị bom đặc biệt
        // =========================================================
        if (target != null && manhattan(bot.row, bot.col, target.row, target.col) <= 3) {
            // Ưu tiên dùng bom đóng băng trước
            Integer freezeSlot = findItemSlot(bot, ItemType.FREEZE_BOMB);
            if (freezeSlot != null && !bot.nextBombFreeze) {
                return new BotDecision(null, false, freezeSlot, false, false);
            }

            // Sau đó cân nhắc bom random
            Integer randomBombSlot = findItemSlot(bot, ItemType.RANDOM_BOMB);
            if (randomBombSlot != null && !bot.nextBombRandom) {
                return new BotDecision(null, false, randomBombSlot, false, false);
            }
        }

        // =========================================================
        // 5) Nếu có cơ hội hạ người chơi thì đặt bom
        // =========================================================
        if (target != null && shouldPlaceBombToKill(board, activePlayers, bombs, explosions, bot, target, now)) {
            // Nếu số bom tối đa còn thấp thì cân nhắc bật skill bomb trước
            boolean useSkillBomb = bot.maxBombs < 2;
            return new BotDecision(
                    null,
                    true,
                    null,
                    useSkillBomb,
                    false
            );
        }

        // =========================================================
        // 6) Nếu đứng gần tường mềm -> cân nhắc phá tường
        // =========================================================

        // Nếu phá tường có nguy cơ chết thì thử bật shield trước
        Integer wallShield = findShieldForBreakingWall(board, activePlayers, bombs, explosions, bot, now);
        if (wallShield != null) {
            return new BotDecision(null, false, wallShield, false, false);
        }

        // Nếu đủ điều kiện để phá tường thì đặt bom
        if (shouldPlaceBombToBreakWall(board, activePlayers, bombs, explosions, bot, now)) {
            boolean useSkillBomb = bot.maxBombs < 2;
            return new BotDecision(
                    null,
                    true,
                    null,
                    useSkillBomb,
                    false
            );
        }

        // =========================================================
        // 7) Nếu có mục tiêu trong bán kính thì BFS dí theo
        // =========================================================
        if (target != null) {
            SearchResult chase = bfsNearest(
                    board,
                    activePlayers,
                    bombs,
                    explosions,
                    bot,
                    chaseSearchDepth,
                    (r, c) -> r == target.row && c == target.col,
                    true,
                    null
            );

            // Nếu tìm được đường đi đến mục tiêu thì đi bước đầu tiên
            if (chase != null && chase.firstMove() != null) {
                return new BotDecision(chase.firstMove(), false, null, false, false);
            }
        }

        // =========================================================
        // 8) Không dí được ai thì tìm vị trí cạnh tường mềm
        // =========================================================
        SearchResult breakWallMove = bfsNearest(
                board,
                activePlayers,
                bombs,
                explosions,
                bot,
                breakWallSearchDepth,
                (r, c) -> hasAdjacentSoftWall(board, r, c),
                false,
                null
        );

        if (breakWallMove != null && breakWallMove.firstMove() != null) {
            return new BotDecision(breakWallMove.firstMove(), false, null, false, false);
        }

        // =========================================================
        // 9) Nếu rảnh thì tìm item tốt để nhặt
        // =========================================================
        SearchResult bestItemMove = findBestItemMove(
                board,
                activePlayers,
                bombs,
                explosions,
                items,
                bot,
                itemSearchDepth
        );

        if (bestItemMove != null && bestItemMove.firstMove() != null) {
            return new BotDecision(bestItemMove.firstMove(), false, null, false, false);
        }

        // =========================================================
        // 10) Cuối cùng không có gì đặc biệt -> đi ngẫu nhiên an toàn
        // =========================================================
        Direction wander = randomSafeDirection(board, activePlayers, bombs, explosions, bot);
        return new BotDecision(wander, false, null, false, false);
    }

    /**
     * Kiểm tra một ô có đang có lửa nổ thật hay không.
     *
     * Đây là danger tức thời, cực kỳ nguy hiểm.
     * Nếu một ô đang có flame thì bot tuyệt đối không nên đứng hoặc đi vào.
     *
     * @param explosions danh sách vụ nổ hiện tại
     * @param row        hàng cần kiểm tra
     * @param col        cột cần kiểm tra
     * @return true nếu ô đang có lửa nổ
     */
    private boolean isExplosionCell(List<Explosion> explosions, int row, int col) {
        for (Explosion e : explosions) {
            for (FlameCell cell : e.cells) {
                if (cell.row == row && cell.col == col) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Kiểm tra một ô có nằm trong vùng đe dọa của bom hay không.
     *
     * Bao gồm:
     * - bom thật đang tồn tại trên map
     * - bom ảo (được mô phỏng để dự đoán tương lai)
     *
     * @param board       bản đồ
     * @param bombs       danh sách bom thật
     * @param row         ô cần kiểm tra
     * @param col         ô cần kiểm tra
     * @param virtualBomb bom ảo, có thể null
     * @return true nếu ô nằm trong vùng nổ dự kiến
     */
    private boolean isBombThreatCell(
            int[][] board,
            List<Bomb> bombs,
            int row,
            int col,
            VirtualBomb virtualBomb
    ) {
        // Kiểm tra danger từ bom thật
        for (Bomb b : bombs) {
            if (hitsCell(board, b.row, b.col, b.range, row, col)) {
                return true;
            }
        }

        // Kiểm tra danger từ bom ảo
        if (virtualBomb != null && hitsCell(board, virtualBomb.row, virtualBomb.col, virtualBomb.range, row, col)) {
            return true;
        }

        return false;
    }

    /**
     * Tìm hướng chạy thoát khi bot đang ở vùng nguy hiểm.
     *
     * Điểm hay của logic này:
     * - Ô cuối cùng bot tới phải an toàn
     * - Trong lúc chạy, bot có thể đi qua ô chưa cháy ngay lúc này
     * - Bot chỉ cấm đi vào ô đang có lửa thật
     *
     * Điều này làm bot thông minh hơn:
     * - Không bị "đơ" chỉ vì thấy nguy hiểm trong tương lai
     * - Trông giống người chơi thật hơn
     *
     * @return hướng đầu tiên bot cần đi để thoát
     */
    private Direction findEscapeMoveAllowFutureBlast(
            int[][] board,
            List<Player> activePlayers,
            List<Bomb> bombs,
            List<Explosion> explosions,
            Player bot
    ) {
        int rows = board.length;
        int cols = board[0].length;

        boolean[][] visited = new boolean[rows][cols];
        ArrayDeque<SearchNode> queue = new ArrayDeque<>();

        // Bắt đầu BFS từ vị trí hiện tại của bot
        queue.add(new SearchNode(bot.row, bot.col, null, 0));
        visited[bot.row][bot.col] = true;

        while (!queue.isEmpty()) {
            SearchNode cur = queue.poll();

            // Nếu đây không phải ô xuất phát và ô hiện tại đã an toàn hoàn toàn
            // thì trả về bước đi đầu tiên để tới đó
            if (!(cur.row == bot.row && cur.col == bot.col)
                    && !isExplosionCell(explosions, cur.row, cur.col)
                    && !isBombThreatCell(board, bombs, cur.row, cur.col, null)) {
                return cur.firstMove;
            }

            // Nếu đã vượt quá giới hạn depth chạy trốn thì dừng mở rộng node này
            if (cur.depth >= escapeSearchDepth) {
                continue;
            }

            // Thử 4 hướng cơ bản
            for (Direction d : List.of(Direction.up, Direction.down, Direction.left, Direction.right)) {
                int nr = cur.row + dr(d);
                int nc = cur.col + dc(d);

                // Bỏ qua nếu ngoài map hoặc đã duyệt
                if (!inBounds(board, nr, nc) || visited[nr][nc]) {
                    continue;
                }

                // Bỏ qua nếu không thể đi vào ô này
                if (!canWalk(board, activePlayers, bombs, nr, nc, bot.id, false)) {
                    continue;
                }

                // Cấm tuyệt đối bước vào lửa đang cháy
                if (isExplosionCell(explosions, nr, nc)) {
                    continue;
                }

                visited[nr][nc] = true;
                queue.add(new SearchNode(
                        nr,
                        nc,
                        cur.firstMove == null ? d : cur.firstMove,
                        cur.depth + 1
                ));
            }
        }

        return null;
    }

    /**
     * Tìm item nâng cấp "lành tính" nên dùng ngay trong inventory của bot.
     *
     * Mục tiêu:
     * - giúp bot mạnh lên sớm
     * - không cần chần chừ với các item thuần nâng cấp
     *
     * Thứ tự ưu tiên:
     * 1. HEART
     * 2. BOMB_UP
     * 3. FLAME_UP
     * 4. SPEED_UP
     *
     * @param bot bot hiện tại
     * @return slot index của item cần dùng, hoặc null nếu không có
     */
    private Integer findImmediateUpgradeItemSlot(Player bot) {
        Integer heart = findItemSlot(bot, ItemType.HEART);
        if (heart != null) return heart;

        Integer bombUp = findItemSlot(bot, ItemType.BOMB_UP);
        if (bombUp != null) return bombUp;

        Integer flameUp = findItemSlot(bot, ItemType.FLAME_UP);
        if (flameUp != null) return flameUp;

        Integer speedUp = findItemSlot(bot, ItemType.SPEED_UP);
        if (speedUp != null) return speedUp;

        return null;
    }

    /**
     * Nếu bot đang ở cạnh tường mềm và có ý định phá tường,
     * nhưng việc đặt bom có thể nguy hiểm, thì cân nhắc dùng shield trước.
     *
     * Chỉ trả về shield nếu:
     * - bot hiện không bất tử
     * - bot đang đứng cạnh tường mềm
     * - bot không chắc chạy thoát sau khi đặt bom
     *
     * @return slot shield hoặc null
     */
    private Integer findShieldForBreakingWall(
            int[][] board,
            List<Player> activePlayers,
            List<Bomb> bombs,
            List<Explosion> explosions,
            Player bot,
            long now
    ) {
        // Nếu bot đang trong thời gian bất tử thì không cần dùng shield
        if (now < bot.invulnerableUntil) {
            return null;
        }

        // Nếu không đứng cạnh tường mềm thì logic này không cần dùng
        if (!hasAdjacentSoftWall(board, bot.row, bot.col)) {
            return null;
        }

        // Nếu bot có thể thoát sau khi đặt bom thì cũng không cần shield
        if (canEscapeAfterPlant(board, activePlayers, bombs, explosions, bot)) {
            return null;
        }

        return findItemSlot(bot, ItemType.SHIELD);
    }

    /**
     * Kiểm tra bot có nên đặt bom để phá tường mềm không.
     *
     * Điều kiện cần:
     * - số bom đang hoạt động của bot chưa chạm max
     * - không bị cooldown đặt bom
     * - bot đang đứng cạnh tường mềm
     * - sau khi mô phỏng đặt bom, bot vẫn còn đường thoát
     *
     * Nếu hợp lệ:
     * - cập nhật cooldown đặt bom cho bot
     * - trả về true
     */
    private boolean shouldPlaceBombToBreakWall(
            int[][] board,
            List<Player> activePlayers,
            List<Bomb> bombs,
            List<Explosion> explosions,
            Player bot,
            long now
    ) {
        // Đếm số bom thật hiện tại thuộc về bot
        long activeBombs = bombs.stream().filter(b -> b.ownerId == bot.id).count();

        if (activeBombs >= bot.maxBombs) return false;
        if (now < bot.botBombCooldownUntil) return false;
        if (!hasAdjacentSoftWall(board, bot.row, bot.col)) return false;

        // Chỉ cho đặt bom nếu bot thoát được
        if (canEscapeAfterPlant(board, activePlayers, bombs, explosions, bot)) {
            bot.botBombCooldownUntil = now + 950L;
            return true;
        }

        return false;
    }

    /**
     * Tìm hướng đi tới item tốt nhất trong phạm vi gần.
     *
     * Cách làm:
     * - Dùng BFS duyệt các ô xung quanh
     * - Nếu gặp item, tính điểm:
     *   score = itemPriority * 100 - depth * 12
     *
     * Ý nghĩa:
     * - item càng quý -> điểm càng cao
     * - item càng xa -> bị trừ điểm
     *
     * Bot sẽ chọn item có tổng điểm cao nhất.
     */
    private SearchResult findBestItemMove(
            int[][] board,
            List<Player> activePlayers,
            List<Bomb> bombs,
            List<Explosion> explosions,
            List<Item> items,
            Player bot,
            int maxDepth
    ) {
        if (items.isEmpty()) return null;

        // Gom item vào map để truy vấn nhanh theo tọa độ "row:col"
        Map<String, Item> itemMap = new HashMap<>();
        for (Item item : items) {
            itemMap.put(item.row + ":" + item.col, item);
        }

        int rows = board.length;
        int cols = board[0].length;

        boolean[][] visited = new boolean[rows][cols];
        ArrayDeque<SearchNode> queue = new ArrayDeque<>();

        queue.add(new SearchNode(bot.row, bot.col, null, 0));
        visited[bot.row][bot.col] = true;

        SearchResult best = null;
        int bestScore = Integer.MIN_VALUE;

        while (!queue.isEmpty()) {
            SearchNode cur = queue.poll();

            // Nếu ô hiện tại có item và không phải ô xuất phát
            Item item = itemMap.get(cur.row + ":" + cur.col);
            if (item != null && !(cur.row == bot.row && cur.col == bot.col)) {
                int score = itemPriority(item.type) * 100 - cur.depth * 12;

                // Chọn item có điểm cao nhất
                if (score > bestScore && cur.firstMove != null) {
                    bestScore = score;
                    best = new SearchResult(cur.firstMove, cur.depth);
                }
            }

            if (cur.depth >= maxDepth) {
                continue;
            }

            for (Direction d : List.of(Direction.up, Direction.down, Direction.left, Direction.right)) {
                int nr = cur.row + dr(d);
                int nc = cur.col + dc(d);

                if (!inBounds(board, nr, nc) || visited[nr][nc]) {
                    continue;
                }

                if (!canWalk(board, activePlayers, bombs, nr, nc, bot.id, false)) {
                    continue;
                }

                // Không đi vào vùng nguy hiểm chỉ để nhặt item
                if (isDangerCell(board, bombs, explosions, nr, nc, null)) {
                    continue;
                }

                visited[nr][nc] = true;
                queue.add(new SearchNode(
                        nr,
                        nc,
                        cur.firstMove == null ? d : cur.firstMove,
                        cur.depth + 1
                ));
            }
        }

        return best;
    }

    /**
     * Trả về độ ưu tiên của từng loại item.
     *
     * Số càng lớn thì item càng đáng giá đối với bot.
     */
    private int itemPriority(ItemType type) {
        return switch (type) {
            case HEART -> 10;
            case FLAME_UP -> 9;
            case BOMB_UP -> 8;
            case SPEED_UP -> 8;
            case FREEZE_BOMB -> 7;
            case RANDOM_BOMB -> 6;
            case SHIELD -> 6;
            case TELEPORT -> 5;
        };
    }

    /**
     * Tìm người chơi thật gần bot nhất trong bán kính detectRadius.
     *
     * Bỏ qua:
     * - player null
     * - chính bot
     * - người đã chết
     * - bot khác
     * - người ngoài bán kính phát hiện
     *
     * Khoảng cách dùng là Manhattan:
     * |r1-r2| + |c1-c2|
     */
    private Player findNearestHuman(List<Player> activePlayers, Player bot, int detectRadius) {
        Player best = null;
        int bestDist = Integer.MAX_VALUE;

        for (Player p : activePlayers) {
            if (p == null || p.id == bot.id || p.lives <= 0 || p.bot) {
                continue;
            }

            int dist = manhattan(bot.row, bot.col, p.row, p.col);

            // Ngoài bán kính phát hiện thì bỏ qua
            if (dist > detectRadius) {
                continue;
            }

            if (dist < bestDist) {
                bestDist = dist;
                best = p;
            }
        }

        return best;
    }

    /**
     * Kiểm tra bot có nên đặt bom để hạ mục tiêu không.
     *
     * Điều kiện:
     * - số bom hiện tại chưa vượt quá giới hạn
     * - bot không đang cooldown bom
     * - mục tiêu ở sát cạnh hoặc cùng hàng / cùng cột trong tầm nổ
     * - đường nổ không bị tường chắn
     * - bot có thể chạy thoát sau khi đặt bom
     *
     * Nếu thỏa:
     * - cập nhật cooldown bom
     * - trả về true
     */
    private boolean shouldPlaceBombToKill(
            int[][] board,
            List<Player> activePlayers,
            List<Bomb> bombs,
            List<Explosion> explosions,
            Player bot,
            Player target,
            long now
    ) {
        long activeBombs = bombs.stream().filter(b -> b.ownerId == bot.id).count();
        if (activeBombs >= bot.maxBombs) return false;
        if (now < bot.botBombCooldownUntil) return false;

        // Kiểm tra khoảng cách gần sát cạnh
        int dist = manhattan(bot.row, bot.col, target.row, target.col);
        boolean adjacent = dist == 1;

        // Kiểm tra cùng hàng và trong tầm bom
        boolean sameRow =
                bot.row == target.row
                        && Math.abs(bot.col - target.col) <= bot.bombRange
                        && clearLineForBlast(board, bot.row, bot.col, target.row, target.col, true);

        // Kiểm tra cùng cột và trong tầm bom
        boolean sameCol =
                bot.col == target.col
                        && Math.abs(bot.row - target.row) <= bot.bombRange
                        && clearLineForBlast(board, bot.row, bot.col, target.row, target.col, true);

        // Nếu không đủ điều kiện trúng người thì không đặt
        if (!(adjacent || sameRow || sameCol)) {
            return false;
        }

        // Nếu đặt xong mà không chạy được thì thôi
        if (!canEscapeAfterPlant(board, activePlayers, bombs, explosions, bot)) {
            return false;
        }

        // Có thể đặt bom -> tạo cooldown để bot không spam bom liên tục
        bot.botBombCooldownUntil = now + 1150L;
        return true;
    }

    /**
     * Mô phỏng xem:
     * "Nếu bot đặt bom ngay tại vị trí hiện tại thì bot có thể chạy thoát không?"
     *
     * Cách làm:
     * - tạo bom ảo tại vị trí bot
     * - BFS tìm 1 ô khác vị trí hiện tại mà an toàn
     *
     * Logic quan trọng:
     * - không cho bot đi vào danger thật hiện tại
     * - nhưng không chặn đường chỉ vì bom ảo sẽ nổ trong tương lai
     *
     * Điều này giúp bot không quá "ngu":
     * - vẫn dám chạy xuyên qua đường hiện tại còn an toàn
     * - miễn là đích cuối cùng đủ an toàn
     */
    private boolean canEscapeAfterPlant(
            int[][] board,
            List<Player> activePlayers,
            List<Bomb> bombs,
            List<Explosion> explosions,
            Player bot
    ) {
        VirtualBomb virtualBomb = new VirtualBomb(bot.row, bot.col, bot.bombRange);

        int rows = board.length;
        int cols = board[0].length;

        boolean[][] visited = new boolean[rows][cols];
        ArrayDeque<SearchNode> queue = new ArrayDeque<>();

        queue.add(new SearchNode(bot.row, bot.col, null, 0));
        visited[bot.row][bot.col] = true;

        while (!queue.isEmpty()) {
            SearchNode cur = queue.poll();

            // Nếu tìm được 1 ô khác ô hiện tại và ô đó không còn danger
            if (!(cur.row == bot.row && cur.col == bot.col)
                    && !isDangerCell(board, bombs, explosions, cur.row, cur.col, virtualBomb)) {
                return true;
            }

            if (cur.depth >= escapeSearchDepth) {
                continue;
            }

            for (Direction d : List.of(Direction.up, Direction.down, Direction.left, Direction.right)) {
                int nr = cur.row + dr(d);
                int nc = cur.col + dc(d);

                if (!inBounds(board, nr, nc) || visited[nr][nc]) {
                    continue;
                }

                if (!canWalk(board, activePlayers, bombs, nr, nc, bot.id, false)) {
                    continue;
                }

                // Không bước vào danger thật hiện tại
                if (isDangerCell(board, bombs, explosions, nr, nc, null)) {
                    continue;
                }

                visited[nr][nc] = true;
                queue.add(new SearchNode(
                        nr,
                        nc,
                        cur.firstMove == null ? d : cur.firstMove,
                        cur.depth + 1
                ));
            }
        }

        return false;
    }

    /**
     * BFS tổng quát để tìm mục tiêu gần nhất.
     *
     * Đây là hàm BFS nền tảng được tái sử dụng cho nhiều mục đích:
     * - đuổi người chơi
     * - tìm vị trí phá tường
     * - tìm điểm đến phù hợp
     *
     * @param targetCheck       điều kiện xác định ô mục tiêu
     * @param allowOccupiedGoal có cho phép đích là ô đang có người đứng hay không
     * @param virtualBomb       bom ảo để tránh danger giả lập, có thể null
     * @return SearchResult chứa bước đi đầu tiên và độ sâu, hoặc null nếu không tìm được
     */
    private SearchResult bfsNearest(
            int[][] board,
            List<Player> activePlayers,
            List<Bomb> bombs,
            List<Explosion> explosions,
            Player bot,
            int maxDepth,
            TargetCheck targetCheck,
            boolean allowOccupiedGoal,
            VirtualBomb virtualBomb
    ) {
        int rows = board.length;
        int cols = board[0].length;

        boolean[][] visited = new boolean[rows][cols];
        ArrayDeque<SearchNode> queue = new ArrayDeque<>();

        queue.add(new SearchNode(bot.row, bot.col, null, 0));
        visited[bot.row][bot.col] = true;

        while (!queue.isEmpty()) {
            SearchNode cur = queue.poll();

            // Nếu đã tới mục tiêu (và không phải ô xuất phát) thì trả kết quả
            if (!(cur.row == bot.row && cur.col == bot.col) && targetCheck.ok(cur.row, cur.col)) {
                return new SearchResult(cur.firstMove, cur.depth);
            }

            if (cur.depth >= maxDepth) {
                continue;
            }

            for (Direction d : List.of(Direction.up, Direction.down, Direction.left, Direction.right)) {
                int nr = cur.row + dr(d);
                int nc = cur.col + dc(d);

                if (!inBounds(board, nr, nc) || visited[nr][nc]) {
                    continue;
                }

                boolean isGoal = targetCheck.ok(nr, nc);

                // Nếu ô này là mục tiêu và allowOccupiedGoal=true
                // thì cho phép đi vào dù đang có người đứng
                if (!canWalk(board, activePlayers, bombs, nr, nc, bot.id, allowOccupiedGoal && isGoal)) {
                    continue;
                }

                // Không đi vào vùng danger
                if (isDangerCell(board, bombs, explosions, nr, nc, virtualBomb)) {
                    continue;
                }

                visited[nr][nc] = true;
                queue.add(new SearchNode(
                        nr,
                        nc,
                        cur.firstMove == null ? d : cur.firstMove,
                        cur.depth + 1
                ));
            }
        }

        return null;
    }

    /**
     * Chọn một hướng đi ngẫu nhiên nhưng vẫn an toàn.
     *
     * Đây là phương án dự phòng cuối cùng khi:
     * - không có mục tiêu
     * - không có item cần nhặt
     * - không có tường cần phá
     * - không có người để dí
     *
     * Cách làm:
     * - tạo danh sách 4 hướng
     * - shuffle ngẫu nhiên
     * - chọn hướng đầu tiên có thể đi và không nguy hiểm
     */
    private Direction randomSafeDirection(
            int[][] board,
            List<Player> activePlayers,
            List<Bomb> bombs,
            List<Explosion> explosions,
            Player bot
    ) {
        List<Direction> dirs = new ArrayList<>(List.of(
                Direction.up,
                Direction.down,
                Direction.left,
                Direction.right
        ));

        Collections.shuffle(dirs, random);

        for (Direction d : dirs) {
            int nr = bot.row + dr(d);
            int nc = bot.col + dc(d);

            if (!canWalk(board, activePlayers, bombs, nr, nc, bot.id, false)) {
                continue;
            }

            if (isDangerCell(board, bombs, explosions, nr, nc, null)) {
                continue;
            }

            return d;
        }

        return null;
    }

    /**
     * Tìm vị trí slot của item trong inventory của player.
     *
     * @param player player cần kiểm tra
     * @param type   loại item cần tìm
     * @return index slot nếu có, ngược lại trả về null
     */
    private Integer findItemSlot(Player player, ItemType type) {
        for (int i = 0; i < player.inventory.size(); i++) {
            if (player.inventory.get(i) == type) {
                return i;
            }
        }
        return null;
    }

    /**
     * Kiểm tra bot có đang đứng cạnh tường mềm hay không.
     *
     * Tường mềm ở đây được giả sử là board[][] == 2.
     *
     * Nếu có tường mềm ở 1 trong 4 ô kề cạnh
     * thì đây là vị trí hợp lý để cân nhắc đặt bom phá tường.
     */
    private boolean hasAdjacentSoftWall(int[][] board, int row, int col) {
        int[][] dirs = {
                {-1, 0}, {1, 0}, {0, -1}, {0, 1}
        };

        for (int[] dir : dirs) {
            int nr = row + dir[0];
            int nc = col + dir[1];
            if (inBounds(board, nr, nc) && board[nr][nc] == 2) {
                return true;
            }
        }

        return false;
    }

    /**
     * Kiểm tra một ô có thể đi vào được hay không.
     *
     * Điều kiện để đi được:
     * - tọa độ nằm trong map
     * - ô đó là ô trống (board[row][col] == 0)
     * - không có bom đứng trên ô
     * - không có player khác đứng chặn
     *
     * allowOccupiedGoal:
     * - dùng cho trường hợp đặc biệt khi ô đích chính là mục tiêu
     * - ví dụ bot muốn đi tới vị trí player đang đứng
     */
    private boolean canWalk(
            int[][] board,
            List<Player> activePlayers,
            List<Bomb> bombs,
            int row,
            int col,
            int selfId,
            boolean allowOccupiedGoal
    ) {
        if (!inBounds(board, row, col)) return false;
        if (board[row][col] != 0) return false;

        // Không đi vào ô có bom
        for (Bomb b : bombs) {
            if (b.row == row && b.col == col) {
                return false;
            }
        }

        // Nếu không cho phép đích có người đứng thì kiểm tra va chạm player
        if (!allowOccupiedGoal) {
            for (Player p : activePlayers) {
                if (p == null || p.lives <= 0 || p.id == selfId) continue;
                if (p.row == row && p.col == col) return false;
            }
        }

        return true;
    }

    /**
     * Kiểm tra một ô có nguy hiểm hay không.
     *
     * Một ô được xem là danger nếu:
     * - đang có flame explosion
     * - nằm trong vùng nổ của bom thật
     * - nằm trong vùng nổ của bom ảo (nếu được truyền vào)
     */
    private boolean isDangerCell(
            int[][] board,
            List<Bomb> bombs,
            List<Explosion> explosions,
            int row,
            int col,
            VirtualBomb virtualBomb
    ) {
        // Danger từ explosion đang cháy
        for (Explosion e : explosions) {
            for (FlameCell cell : e.cells) {
                if (cell.row == row && cell.col == col) {
                    return true;
                }
            }
        }

        // Danger từ bom thật
        for (Bomb b : bombs) {
            if (hitsCell(board, b.row, b.col, b.range, row, col)) {
                return true;
            }
        }

        // Danger từ bom ảo
        if (virtualBomb != null && hitsCell(board, virtualBomb.row, virtualBomb.col, virtualBomb.range, row, col)) {
            return true;
        }

        return false;
    }

    /**
     * Kiểm tra ô target có bị trúng trong vùng nổ của 1 quả bom hay không.
     *
     * Bom chỉ trúng nếu:
     * - target cùng hàng hoặc cùng cột với bom
     * - khoảng cách không vượt quá range
     * - đường nổ không bị tường chắn
     *
     * @return true nếu target bị ăn nổ
     */
    private boolean hitsCell(int[][] board, int bombRow, int bombCol, int range, int targetRow, int targetCol) {
        // Trúng ngay chính ô đặt bom
        if (bombRow == targetRow && bombCol == targetCol) return true;

        // Kiểm tra cùng hàng
        if (bombRow == targetRow) {
            int dist = Math.abs(bombCol - targetCol);
            if (dist <= range && clearLineForBlast(board, bombRow, bombCol, targetRow, targetCol, false)) {
                return true;
            }
        }

        // Kiểm tra cùng cột
        if (bombCol == targetCol) {
            int dist = Math.abs(bombRow - targetRow);
            if (dist <= range && clearLineForBlast(board, bombRow, bombCol, targetRow, targetCol, false)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Chuyển speed level của bot thành cooldown tương ứng.
     *
     * Speed level cao hơn -> cooldown nhỏ hơn -> bot phản ứng nhanh hơn.
     *
     * Level được ép trong khoảng [1..5] để tránh dữ liệu lỗi.
     */
    private long getMoveCooldownForSpeedLevel(int speedLevel) {
        int level = Math.max(1, Math.min(5, speedLevel));
        return switch (level) {
            case 1 -> moveCooldownLevel1Ms;
            case 2 -> moveCooldownLevel2Ms;
            case 3 -> moveCooldownLevel3Ms;
            case 4 -> moveCooldownLevel4Ms;
            case 5 -> moveCooldownLevel5Ms;
            default -> moveCooldownLevel1Ms;
        };
    }

    /**
     * Kiểm tra đường nổ từ điểm (r1, c1) tới (r2, c2) có bị tường cản không.
     *
     * Chỉ hỗ trợ 2 trường hợp:
     * - cùng hàng
     * - cùng cột
     *
     * Nếu giữa đường có tường cứng hoặc tường mềm thì vụ nổ bị chặn.
     *
     * ignoreTargetCell:
     * - true  -> bỏ qua chuyện ô đích có phải tường không
     * - false -> ô đích cũng phải không bị chặn
     */
    private boolean clearLineForBlast(int[][] board, int r1, int c1, int r2, int c2, boolean ignoreTargetCell) {
        // Trường hợp cùng hàng
        if (r1 == r2) {
            int start = Math.min(c1, c2);
            int end = Math.max(c1, c2);

            // Kiểm tra các ô giữa đường
            for (int c = start + 1; c < end; c++) {
                if (board[r1][c] == 1 || board[r1][c] == 2) {
                    return false;
                }
            }

            // Kiểm tra ô đích nếu không được phép bỏ qua
            if (!ignoreTargetCell && (board[r2][c2] == 1 || board[r2][c2] == 2)) {
                return false;
            }

            return true;
        }

        // Trường hợp cùng cột
        if (c1 == c2) {
            int start = Math.min(r1, r2);
            int end = Math.max(r1, r2);

            // Kiểm tra các ô giữa đường
            for (int r = start + 1; r < end; r++) {
                if (board[r][c1] == 1 || board[r][c1] == 2) {
                    return false;
                }
            }

            // Kiểm tra ô đích nếu không được phép bỏ qua
            if (!ignoreTargetCell && (board[r2][c2] == 1 || board[r2][c2] == 2)) {
                return false;
            }

            return true;
        }

        // Nếu không cùng hàng, không cùng cột thì không phải đường nổ thẳng
        return false;
    }

    /**
     * Kiểm tra tọa độ có nằm trong giới hạn bản đồ hay không.
     *
     * @return true nếu (row, col) hợp lệ
     */
    private boolean inBounds(int[][] board, int row, int col) {
        return row >= 0 && col >= 0 && row < board.length && col < board[0].length;
    }

    /**
     * Lấy delta row theo hướng.
     *
     * up   -> -1
     * down -> +1
     * left/right -> 0
     */
    private int dr(Direction d) {
        return switch (d) {
            case up -> -1;
            case down -> 1;
            default -> 0;
        };
    }

    /**
     * Lấy delta col theo hướng.
     *
     * left  -> -1
     * right -> +1
     * up/down -> 0
     */
    private int dc(Direction d) {
        return switch (d) {
            case left -> -1;
            case right -> 1;
            default -> 0;
        };
    }

    /**
     * Tính khoảng cách Manhattan giữa 2 ô trên lưới.
     *
     * Công thức:
     * |r1 - r2| + |c1 - c2|
     *
     * Đây là khoảng cách phù hợp với game đi 4 hướng,
     * vì người chơi / bot chỉ đi:
     * - lên
     * - xuống
     * - trái
     * - phải
     */
    private int manhattan(int r1, int c1, int r2, int c2) {
        return Math.abs(r1 - r2) + Math.abs(c1 - c2);
    }
}
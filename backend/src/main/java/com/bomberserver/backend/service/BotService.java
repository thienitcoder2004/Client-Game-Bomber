package com.bomberserver.backend.service;

import com.bomberserver.backend.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class BotService {

    private final Random random = new Random();

    // =========================================================
    // COOLDOWN DI CHUYỂN THEO SPEED LEVEL
    // Speed level càng cao -> bot suy nghĩ / di chuyển càng nhanh
    // =========================================================
    @Value("${game.timing.move-cooldown-level-1-ms:3000}")
    private long moveCooldownLevel1Ms;

    @Value("${game.timing.move-cooldown-level-2-ms:2500}")
    private long moveCooldownLevel2Ms;

    @Value("${game.timing.move-cooldown-level-3-ms:2000}")
    private long moveCooldownLevel3Ms;

    @Value("${game.timing.move-cooldown-level-4-ms:1500}")
    private long moveCooldownLevel4Ms;

    @Value("${game.timing.move-cooldown-level-5-ms:1000}")
    private long moveCooldownLevel5Ms;

    // =========================================================
    // BOT AI CONFIG
    // detectRadius         : bán kính phát hiện người chơi
    // chaseSearchDepth     : số bước tối đa để dí người
    // breakWallSearchDepth : số bước tối đa để tìm chỗ phá tường
    // itemSearchDepth      : số bước tối đa để tìm item
    // escapeSearchDepth    : số bước tối đa để chạy bom
    // =========================================================
    @Value("${game.bot.detect-radius:6}")
    private int detectRadius;

    @Value("${game.bot.chase-search-depth:8}")
    private int chaseSearchDepth;

    @Value("${game.bot.break-wall-search-depth:6}")
    private int breakWallSearchDepth;

    @Value("${game.bot.item-search-depth:5}")
    private int itemSearchDepth;

    @Value("${game.bot.escape-search-depth:8}")
    private int escapeSearchDepth;

    /**
     * Kết quả quyết định cuối cùng mà bot trả về trong 1 lần suy nghĩ.
     *
     * move          : hướng bot sẽ di chuyển
     * placeBomb     : bot có đặt bom không
     * useItemSlot   : bot có dùng item ở slot nào không
     * useSkillBomb  : có bật skill tăng bomb không
     * useSkillSpeed : có bật skill tăng tốc không
     */
    public record BotDecision(
            Direction move,
            boolean placeBomb,
            Integer useItemSlot,
            boolean useSkillBomb,
            boolean useSkillSpeed
    ) {
        /**
         * Trạng thái đứng yên, không làm gì.
         */
        public static BotDecision idle() {
            return new BotDecision(null, false, null, false, false);
        }
    }

    /**
     * Interface truyền điều kiện mục tiêu vào BFS.
     *
     * Ví dụ:
     * - Tìm ô có người chơi
     * - Tìm ô cạnh tường mềm
     * - Tìm ô chứa item
     */
    @FunctionalInterface
    private interface TargetCheck {
        boolean ok(int row, int col);
    }

    /**
     * Bom ảo dùng để mô phỏng:
     * Nếu bot đặt bom ngay vị trí hiện tại thì vùng nổ tương lai sẽ như thế nào.
     *
     * Mục đích:
     * - kiểm tra đặt bom xong có thoát được không
     * - giảm bot tự sát
     */
    private record VirtualBomb(int row, int col, int range) {
    }

    /**
     * Node dùng trong BFS.
     *
     * row, col  : vị trí node hiện tại
     * firstMove : bước đầu tiên bot phải đi để tới node này
     * depth     : số bước đã đi từ điểm xuất phát
     */
    private record SearchNode(int row, int col, Direction firstMove, int depth) {
    }

    /**
     * Kết quả BFS.
     *
     * firstMove : hướng đầu tiên bot nên đi
     * depth     : độ sâu / khoảng cách tới mục tiêu
     */
    private record SearchResult(Direction firstMove, int depth) {
    }

    /**
     * Hàm quyết định chính của bot.
     *
     * Thứ tự ưu tiên:
     * 1. Nếu đang ở ô nguy hiểm thì né trước
     * 2. Nếu có item nâng cấp an toàn thì dùng ngay
     * 3. Nếu có mục tiêu ở hơi xa thì bật speed skill để dí
     * 4. Nếu ở gần mục tiêu thì chuẩn bị bom đặc biệt
     * 5. Nếu có cơ hội giết người thì đặt bom
     * 6. Nếu cạnh tường mềm thì cân nhắc phá tường
     * 7. Nếu có người trong bán kính phát hiện thì dí theo
     * 8. Nếu chưa dí ai thì đi tới chỗ phá map
     * 9. Nếu rảnh thì đi nhặt item gần
     * 10. Cuối cùng mới đi ngẫu nhiên nhưng vẫn an toàn
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
        if (board == null || activePlayers == null || bombs == null || explosions == null || items == null || bot == null) {
            return BotDecision.idle();
        }

        if (!bot.bot || bot.lives <= 0) {
            return BotDecision.idle();
        }

        // Khống chế nhịp suy nghĩ của bot theo speed level.
        // Nếu chưa tới lượt suy nghĩ tiếp theo thì bot đứng im.
        long botMoveCooldown = getMoveCooldownForSpeedLevel(bot.speedLevel);
        if (now < bot.botNextThinkAt) {
            return BotDecision.idle();
        }
        bot.botNextThinkAt = now + botMoveCooldown;

        // Chỉ tìm người chơi thật trong bán kính phát hiện.
        // Như vậy bot không bị hút mục tiêu ở quá xa trên toàn map.
        Player target = findNearestHuman(activePlayers, bot, detectRadius);

        // =========================================================
        // 1) Nếu đang ở ô nguy hiểm -> né trước
        // =========================================================
        if (isDangerCell(board, bombs, explosions, bot.row, bot.col, null)) {
            boolean useSpeedSkill = false;

            Direction escapeMove = findEscapeMoveAllowFutureBlast(
                    board,
                    activePlayers,
                    bombs,
                    explosions,
                    bot
            );

            if (escapeMove != null) {
                return new BotDecision(
                        escapeMove,
                        false,
                        null,
                        false,
                        useSpeedSkill
                );
            }

            // Không chạy được thì ưu tiên dùng khiên
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

            // Nếu vẫn chưa ổn thì thử dùng teleport
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

            // Cuối cùng nếu bí quá thì đi ngẫu nhiên nhưng vẫn ưu tiên an toàn
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
        // Dùng sớm để bot mạnh lên ổn định
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
        // 4) Nếu gần người thì chuẩn bị bom đặc biệt
        // =========================================================
        if (target != null && manhattan(bot.row, bot.col, target.row, target.col) <= 3) {
            Integer freezeSlot = findItemSlot(bot, ItemType.FREEZE_BOMB);
            if (freezeSlot != null && !bot.nextBombFreeze) {
                return new BotDecision(null, false, freezeSlot, false, false);
            }

            Integer randomBombSlot = findItemSlot(bot, ItemType.RANDOM_BOMB);
            if (randomBombSlot != null && !bot.nextBombRandom) {
                return new BotDecision(null, false, randomBombSlot, false, false);
            }
        }

        // =========================================================
        // 5) Nếu có thể giết người thì đặt bom
        // Nếu số bom còn yếu thì bật skill bomb trước
        // =========================================================
        if (target != null && shouldPlaceBombToKill(board, activePlayers, bombs, explosions, bot, target, now)) {
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
        // 6) Nếu đang cạnh tường mềm thì cân nhắc phá tường
        // Nếu thấy nguy hiểm thì bật shield trước
        // =========================================================
        Integer wallShield = findShieldForBreakingWall(board, activePlayers, bombs, explosions, bot, now);
        if (wallShield != null) {
            return new BotDecision(null, false, wallShield, false, false);
        }

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

            if (chase != null && chase.firstMove() != null) {
                return new BotDecision(chase.firstMove(), false, null, false, false);
            }
        }

        // =========================================================
        // 8) Không dí được ai thì đi tới vị trí cạnh tường mềm
        // để chuẩn bị phá map / mở đường / kiếm item
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
        // 9) Nếu rảnh thì đi nhặt item gần và đáng giá
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
        // 10) Cuối cùng nếu không có gì ưu tiên hơn
        // thì đi ngẫu nhiên nhưng vẫn an toàn
        // =========================================================
        Direction wander = randomSafeDirection(board, activePlayers, bombs, explosions, bot);
        return new BotDecision(wander, false, null, false, false);
    }

    /**
     * Kiểm tra một ô có đang có lửa thật ngay lúc này hay không.
     *
     * Đây là danger tức thì.
     * Nếu true thì tuyệt đối không nên đi vào.
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
     * Kiểm tra một ô có nằm trong vùng nổ của bom thật
     * hoặc bom ảo hay không.
     *
     * Dùng để dự đoán danger trong tương lai.
     */
    private boolean isBombThreatCell(
            int[][] board,
            List<Bomb> bombs,
            int row,
            int col,
            VirtualBomb virtualBomb
    ) {
        for (Bomb b : bombs) {
            if (hitsCell(board, b.row, b.col, b.range, row, col)) {
                return true;
            }
        }

        if (virtualBomb != null && hitsCell(board, virtualBomb.row, virtualBomb.col, virtualBomb.range, row, col)) {
            return true;
        }

        return false;
    }

    /**
     * Tìm bước chạy thoát khi bot đang ở trong vùng nguy hiểm.
     *
     * Điểm đặc biệt:
     * - Ô đích cuối phải an toàn
     * - Nhưng trong lúc chạy có thể đi qua ô sẽ nguy hiểm trong tương lai,
     *   miễn hiện tại ô đó chưa có lửa thật
     *
     * Cách này làm bot né bom giống người thật hơn,
     * thay vì thấy "nguy hiểm tương lai" là đứng im luôn.
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

        queue.add(new SearchNode(bot.row, bot.col, null, 0));
        visited[bot.row][bot.col] = true;

        while (!queue.isEmpty()) {
            SearchNode cur = queue.poll();

            // Nếu tìm được 1 ô khác vị trí hiện tại và ô đó an toàn
            // thì trả về bước đầu tiên để đi tới ô đó
            if (!(cur.row == bot.row && cur.col == bot.col)
                    && !isExplosionCell(explosions, cur.row, cur.col)
                    && !isBombThreatCell(board, bombs, cur.row, cur.col, null)) {
                return cur.firstMove;
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

                // Tuyệt đối không bước vào lửa đang cháy thật
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
     * Tìm item nâng cấp nên dùng ngay trong inventory.
     *
     * Vì đây là các item tăng sức mạnh khá "lành tính",
     * bot dùng ngay để mạnh lên sớm:
     * - HEART
     * - BOMB_UP
     * - FLAME_UP
     * - SPEED_UP
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
     * Nếu bot đang đứng cạnh tường mềm mà phá tường có nguy cơ chết,
     * thì thử dùng shield trước để an toàn hơn.
     */
    private Integer findShieldForBreakingWall(
            int[][] board,
            List<Player> activePlayers,
            List<Bomb> bombs,
            List<Explosion> explosions,
            Player bot,
            long now
    ) {
        // Đang bất tử rồi thì không cần dùng khiên nữa
        if (now < bot.invulnerableUntil) {
            return null;
        }

        // Không cạnh tường mềm thì không cần logic này
        if (!hasAdjacentSoftWall(board, bot.row, bot.col)) {
            return null;
        }

        // Nếu đã đủ khả năng chạy sau khi đặt bom thì không cần khiên
        if (canEscapeAfterPlant(board, activePlayers, bombs, explosions, bot)) {
            return null;
        }

        return findItemSlot(bot, ItemType.SHIELD);
    }

    /**
     * Kiểm tra bot có nên đặt bom để phá tường mềm không.
     *
     * Điều kiện:
     * - Chưa vượt quá số bom tối đa
     * - Không bị cooldown bom
     * - Đang cạnh tường mềm
     * - Sau khi đặt bom vẫn thoát được
     */
    private boolean shouldPlaceBombToBreakWall(
            int[][] board,
            List<Player> activePlayers,
            List<Bomb> bombs,
            List<Explosion> explosions,
            Player bot,
            long now
    ) {
        long activeBombs = bombs.stream().filter(b -> b.ownerId == bot.id).count();

        if (activeBombs >= bot.maxBombs) return false;
        if (now < bot.botBombCooldownUntil) return false;
        if (!hasAdjacentSoftWall(board, bot.row, bot.col)) return false;

        if (canEscapeAfterPlant(board, activePlayers, bombs, explosions, bot)) {
            bot.botBombCooldownUntil = now + 950L;
            return true;
        }

        return false;
    }

    /**
     * Tìm hướng đi tới item tốt nhất ở gần.
     *
     * Cách chọn:
     * - mỗi item có điểm ưu tiên khác nhau
     * - khoảng cách càng xa thì bị trừ điểm
     * - item có tổng điểm cao nhất sẽ được chọn
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

        // Gom item theo tọa độ để tìm nhanh hơn
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

            Item item = itemMap.get(cur.row + ":" + cur.col);
            if (item != null && !(cur.row == bot.row && cur.col == bot.col)) {
                int score = itemPriority(item.type) * 100 - cur.depth * 12;
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
     * Điểm ưu tiên của từng loại item.
     *
     * Số càng cao thì bot càng thích item đó hơn.
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
     * Tìm người chơi thật gần bot nhất trong bán kính cho phép.
     *
     * Lưu ý:
     * - Bỏ qua chính bot
     * - Bỏ qua player đã chết
     * - Bỏ qua bot khác
     * - Bỏ qua người chơi ngoài detectRadius
     *
     * Nhờ vậy bot không còn quét toàn map nữa.
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
     * Kiểm tra bot có nên đặt bom để giết mục tiêu không.
     *
     * Điều kiện:
     * - Chưa vượt quá số bom tối đa
     * - Không bị cooldown bom
     * - Mục tiêu ở sát cạnh, hoặc cùng hàng / cùng cột trong tầm nổ
     * - Đường nổ không bị tường chặn
     * - Sau khi đặt bom bot còn đường chạy
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

        int dist = manhattan(bot.row, bot.col, target.row, target.col);
        boolean adjacent = dist == 1;

        boolean sameRow =
                bot.row == target.row
                        && Math.abs(bot.col - target.col) <= bot.bombRange
                        && clearLineForBlast(board, bot.row, bot.col, target.row, target.col, true);

        boolean sameCol =
                bot.col == target.col
                        && Math.abs(bot.row - target.row) <= bot.bombRange
                        && clearLineForBlast(board, bot.row, bot.col, target.row, target.col, true);

        if (!(adjacent || sameRow || sameCol)) {
            return false;
        }

        if (!canEscapeAfterPlant(board, activePlayers, bombs, explosions, bot)) {
            return false;
        }

        bot.botBombCooldownUntil = now + 1150L;
        return true;
    }

    /**
     * Mô phỏng:
     * Nếu bot đặt bom ngay tại vị trí hiện tại,
     * liệu bot có tìm được một ô an toàn để chạy tới không.
     *
     * Điểm quan trọng:
     * - Có bom ảo tại vị trí bot
     * - BFS tìm ô đích an toàn
     * - Không cho bot bước vào danger hiện tại
     * - Nhưng không cấm chỉ vì bom ảo sẽ nổ trong tương lai
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

            // Tìm được ô khác vị trí hiện tại và ô đó không còn danger
            // kể cả khi có thêm bom ảo
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

                // Tránh danger thật hiện tại từ bom đang có / lửa đang cháy
                // nhưng không cấm đường chỉ vì bom ảo của chính bot
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
     * targetCheck:
     * - quy định ô nào được xem là mục tiêu
     *
     * allowOccupiedGoal:
     * - true  = cho phép ô mục tiêu đang có người đứng
     * - false = ô mục tiêu phải trống
     *
     * virtualBomb:
     * - truyền bom ảo nếu muốn BFS tránh thêm một danger giả lập
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

                if (!canWalk(board, activePlayers, bombs, nr, nc, bot.id, allowOccupiedGoal && isGoal)) {
                    continue;
                }

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
     * Chọn 1 hướng đi ngẫu nhiên nhưng vẫn an toàn.
     *
     * Đây là phương án fallback cuối cùng
     * khi bot không có mục tiêu ưu tiên nào tốt hơn.
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
     * Tìm vị trí slot của item trong inventory.
     *
     * Trả về:
     * - index nếu có
     * - null nếu không có
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
     * Kiểm tra bot có đang đứng cạnh tường mềm không.
     *
     * Nếu có thì đây là vị trí phù hợp để cân nhắc phá tường.
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
     * Kiểm tra một ô có thể đi vào hay không.
     *
     * Điều kiện:
     * - phải nằm trong map
     * - phải là ô trống
     * - không có bom
     * - không bị player khác đứng chặn
     *
     * allowOccupiedGoal:
     * - cho phép trường hợp đặc biệt là ô mục tiêu có người đứng
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

        for (Bomb b : bombs) {
            if (b.row == row && b.col == col) {
                return false;
            }
        }

        if (!allowOccupiedGoal) {
            for (Player p : activePlayers) {
                if (p == null || p.lives <= 0 || p.id == selfId) continue;
                if (p.row == row && p.col == col) return false;
            }
        }

        return true;
    }

    /**
     * Kiểm tra ô có nguy hiểm không.
     *
     * Một ô được xem là nguy hiểm nếu:
     * - đang có lửa thật
     * - nằm trong vùng nổ của bom thật
     * - nằm trong vùng nổ của bom ảo (nếu có)
     */
    private boolean isDangerCell(
            int[][] board,
            List<Bomb> bombs,
            List<Explosion> explosions,
            int row,
            int col,
            VirtualBomb virtualBomb
    ) {
        for (Explosion e : explosions) {
            for (FlameCell cell : e.cells) {
                if (cell.row == row && cell.col == col) {
                    return true;
                }
            }
        }

        for (Bomb b : bombs) {
            if (hitsCell(board, b.row, b.col, b.range, row, col)) {
                return true;
            }
        }

        if (virtualBomb != null && hitsCell(board, virtualBomb.row, virtualBomb.col, virtualBomb.range, row, col)) {
            return true;
        }

        return false;
    }

    /**
     * Kiểm tra ô target có nằm trong vùng nổ của quả bom không.
     *
     * Bom chỉ trúng khi:
     * - cùng hàng hoặc cùng cột
     * - trong tầm range
     * - không bị tường chặn đường nổ
     */
    private boolean hitsCell(int[][] board, int bombRow, int bombCol, int range, int targetRow, int targetCol) {
        if (bombRow == targetRow && bombCol == targetCol) return true;

        if (bombRow == targetRow) {
            int dist = Math.abs(bombCol - targetCol);
            if (dist <= range && clearLineForBlast(board, bombRow, bombCol, targetRow, targetCol, false)) {
                return true;
            }
        }

        if (bombCol == targetCol) {
            int dist = Math.abs(bombRow - targetRow);
            if (dist <= range && clearLineForBlast(board, bombRow, bombCol, targetRow, targetCol, false)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Chuyển speedLevel thành cooldown di chuyển / suy nghĩ của bot.
     *
     * Speed level càng cao thì cooldown càng thấp.
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
     * Kiểm tra đường nổ từ (r1, c1) tới (r2, c2) có bị tường cản hay không.
     *
     * ignoreTargetCell:
     * - true  = bỏ qua chuyện ô đích là tường
     * - false = ô đích cũng phải không bị chặn
     */
    private boolean clearLineForBlast(int[][] board, int r1, int c1, int r2, int c2, boolean ignoreTargetCell) {
        if (r1 == r2) {
            int start = Math.min(c1, c2);
            int end = Math.max(c1, c2);

            for (int c = start + 1; c < end; c++) {
                if (board[r1][c] == 1 || board[r1][c] == 2) {
                    return false;
                }
            }

            if (!ignoreTargetCell && (board[r2][c2] == 1 || board[r2][c2] == 2)) {
                return false;
            }

            return true;
        }

        if (c1 == c2) {
            int start = Math.min(r1, r2);
            int end = Math.max(r1, r2);

            for (int r = start + 1; r < end; r++) {
                if (board[r][c1] == 1 || board[r][c1] == 2) {
                    return false;
                }
            }

            if (!ignoreTargetCell && (board[r2][c2] == 1 || board[r2][c2] == 2)) {
                return false;
            }

            return true;
        }

        return false;
    }

    /**
     * Kiểm tra tọa độ có nằm trong map hay không.
     */
    private boolean inBounds(int[][] board, int row, int col) {
        return row >= 0 && col >= 0 && row < board.length && col < board[0].length;
    }

    /**
     * Delta row theo hướng.
     *
     * up   -> -1
     * down -> +1
     */
    private int dr(Direction d) {
        return switch (d) {
            case up -> -1;
            case down -> 1;
            default -> 0;
        };
    }

    /**
     * Delta col theo hướng.
     *
     * left  -> -1
     * right -> +1
     */
    private int dc(Direction d) {
        return switch (d) {
            case left -> -1;
            case right -> 1;
            default -> 0;
        };
    }

    /**
     * Tính khoảng cách Manhattan giữa 2 ô.
     *
     * Công thức:
     * |r1-r2| + |c1-c2|
     *
     * Rất phù hợp với game đi 4 hướng trên lưới.
     */
    private int manhattan(int r1, int c1, int r2, int c2) {
        return Math.abs(r1 - r2) + Math.abs(c1 - c2);
    }
}
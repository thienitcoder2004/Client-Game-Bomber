package com.bomberserver.backend.service;

import com.bomberserver.backend.model.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class BotAiService {

    private final Random random = new Random();

    /**
     * move         = hướng di chuyển
     * placeBomb    = có đặt bom không
     * useItemSlot  = dùng item trong túi đồ ở slot nào
     * useSkillBomb = có dùng skill tăng bom không
     * useSkillSpeed= có dùng skill tăng tốc không
     */
    public record BotDecision(
            Direction move,
            boolean placeBomb,
            Integer useItemSlot,
            boolean useSkillBomb,
            boolean useSkillSpeed
    ) {
        public static BotDecision idle() {
            return new BotDecision(null, false, null, false, false);
        }
    }

    /**
     * Hàm nhỏ để BFS kiểm tra xem ô nào là ô mục tiêu.
     */
    @FunctionalInterface
    private interface TargetCheck {
        boolean ok(int row, int col);
    }

    /**
     * Bom ảo dùng để mô phỏng:
     * nếu bot đặt bom ngay tại vị trí hiện tại,
     * vùng nổ tương lai sẽ là gì.
     */
    private record VirtualBomb(int row, int col, int range) {
    }

    /**
     * Node BFS.
     * firstMove = bước đầu tiên bot phải đi để tới node hiện tại.
     */
    private record SearchNode(int row, int col, Direction firstMove, int depth) {
    }

    /**
     * Kết quả BFS.
     */
    private record SearchResult(Direction firstMove, int depth) {
    }

    /**
     * Hàm quyết định chính của bot.
     *
     * Thứ tự ưu tiên:
     * 1. Né vùng nguy hiểm
     * 2. Dùng item nâng cấp ngay
     * 3. Dùng skill speed nếu cần chạy / rượt
     * 4. Chuẩn bị bom đặc biệt khi gần mục tiêu
     * 5. Nếu giết được thì đặt bom
     * 6. Nếu đang cạnh tường mềm thì ưu tiên phá
     * 7. Rượt người chơi
     * 8. Không rượt được thì phá map
     * 9. Không có gì cấp bách thì đi nhặt item
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

        // Bot suy nghĩ nhanh hơn để trông giống người chơi thật hơn
        if (now < bot.botNextThinkAt) {
            return BotDecision.idle();
        }
        bot.botNextThinkAt = now + 70 + random.nextInt(50);

        Player target = findNearestHuman(activePlayers, bot);

        // =========================================================
        // 1) Nếu đang đứng trong danger thì ưu tiên né bom
        // =========================================================
        if (isDangerCell(board, bombs, explosions, bot.row, bot.col, null)) {
            boolean useSpeedSkill = bot.speedBoostUntil <= now;

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

            // Nếu bí quá thì bật shield
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

            // Không còn cách thì teleport
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
        // =========================================================
        Integer instantItem = findImmediateUpgradeItemSlot(bot);
        if (instantItem != null) {
            return new BotDecision(null, false, instantItem, false, false);
        }

        // =========================================================
        // 3) Nếu gần mục tiêu nhưng còn hơi xa thì bật speed skill để dí
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
        //    Nếu số bom còn yếu thì tăng bomb skill trước
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
        // 6) Nếu đang cạnh tường mềm thì ưu tiên phá ngay
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
        // 7) Nếu có người thì dí theo
        // =========================================================
        if (target != null) {
            SearchResult chase = bfsNearest(
                    board,
                    activePlayers,
                    bombs,
                    explosions,
                    bot,
                    12,
                    (r, c) -> r == target.row && c == target.col,
                    true,
                    null
            );

            if (chase != null && chase.firstMove() != null) {
                return new BotDecision(chase.firstMove(), false, null, false, false);
            }
        }

        // =========================================================
        // 8) Không dí được ai thì đi tới vị trí sát tường mềm
        // =========================================================
        SearchResult breakWallMove = bfsNearest(
                board,
                activePlayers,
                bombs,
                explosions,
                bot,
                14,
                (r, c) -> hasAdjacentSoftWall(board, r, c),
                false,
                null
        );

        if (breakWallMove != null && breakWallMove.firstMove() != null) {
            return new BotDecision(breakWallMove.firstMove(), false, null, false, false);
        }

        // =========================================================
        // 9) Nếu rảnh thì đi nhặt item
        // =========================================================
        SearchResult bestItemMove = findBestItemMove(
                board,
                activePlayers,
                bombs,
                explosions,
                items,
                bot,
                8
        );

        if (bestItemMove != null && bestItemMove.firstMove() != null) {
            return new BotDecision(bestItemMove.firstMove(), false, null, false, false);
        }

        // =========================================================
        // 10) Bí quá thì đi ngẫu nhiên nhưng vẫn phải an toàn
        // =========================================================
        Direction wander = randomSafeDirection(board, activePlayers, bombs, explosions, bot);
        return new BotDecision(wander, false, null, false, false);
    }

    /**
     * Kiểm tra ô này hiện tại có lửa đang cháy không.
     * Đây là danger "ngay lập tức".
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
     * Kiểm tra ô này có nằm trong vùng nổ tương lai của bom không.
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
     * Tìm bước thoát khi đang bị bom đe dọa.
     *
     * Điểm quan trọng:
     * - Ô đích cuối cùng phải an toàn
     * - Nhưng trên đường chạy có thể băng qua vùng sẽ nổ trong tương lai
     *   miễn là hiện tại ô đó chưa có lửa thật
     *
     * Nhờ vậy bot né bom giống người chơi thật hơn.
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

            if (!(cur.row == bot.row && cur.col == bot.col)
                    && !isExplosionCell(explosions, cur.row, cur.col)
                    && !isBombThreatCell(board, bombs, cur.row, cur.col, null)) {
                return cur.firstMove;
            }

            if (cur.depth >= 10) continue;

            for (Direction d : List.of(Direction.up, Direction.down, Direction.left, Direction.right)) {
                int nr = cur.row + dr(d);
                int nc = cur.col + dc(d);

                if (!inBounds(board, nr, nc) || visited[nr][nc]) continue;
                if (!canWalk(board, activePlayers, bombs, nr, nc, bot.id, false)) continue;

                // Không đi vào lửa thật
                if (isExplosionCell(explosions, nr, nc)) continue;

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
     * Item dùng ngay:
     * HEART, BOMB_UP, FLAME_UP, SPEED_UP
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
     * Nếu bot đang cạnh tường mềm mà cảm thấy không đủ đường thoát,
     * thì dùng shield trước rồi mới phá.
     */
    private Integer findShieldForBreakingWall(
            int[][] board,
            List<Player> activePlayers,
            List<Bomb> bombs,
            List<Explosion> explosions,
            Player bot,
            long now
    ) {
        if (now < bot.invulnerableUntil) {
            return null;
        }

        if (!hasAdjacentSoftWall(board, bot.row, bot.col)) {
            return null;
        }

        if (canEscapeAfterPlant(board, activePlayers, bombs, explosions, bot)) {
            return null;
        }

        return findItemSlot(bot, ItemType.SHIELD);
    }

    /**
     * Điều kiện đặt bom để phá tường mềm.
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
     * Bot tìm item tốt nhất ở gần.
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

            if (cur.depth >= maxDepth) continue;

            for (Direction d : List.of(Direction.up, Direction.down, Direction.left, Direction.right)) {
                int nr = cur.row + dr(d);
                int nc = cur.col + dc(d);

                if (!inBounds(board, nr, nc) || visited[nr][nc]) continue;
                if (!canWalk(board, activePlayers, bombs, nr, nc, bot.id, false)) continue;
                if (isDangerCell(board, bombs, explosions, nr, nc, null)) continue;

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
     * Tìm người chơi thật gần nhất.
     */
    private Player findNearestHuman(List<Player> activePlayers, Player bot) {
        Player best = null;
        int bestDist = Integer.MAX_VALUE;

        for (Player p : activePlayers) {
            if (p == null || p.id == bot.id || p.lives <= 0 || p.bot) continue;

            int dist = manhattan(bot.row, bot.col, p.row, p.col);
            if (dist < bestDist) {
                bestDist = dist;
                best = p;
            }
        }

        return best;
    }

    /**
     * Điều kiện đặt bom để giết người.
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
     * Kiểm tra sau khi đặt bom, bot có thể tìm được một ô đích an toàn hay không.
     *
     * Bản này cho phép bot chạy xuyên qua vùng sẽ nổ trong tương lai,
     * miễn là ô hiện tại chưa có lửa thật.
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

            if (!(cur.row == bot.row && cur.col == bot.col)
                    && !isDangerCell(board, bombs, explosions, cur.row, cur.col, virtualBomb)) {
                return true;
            }

            if (cur.depth >= 10) continue;

            for (Direction d : List.of(Direction.up, Direction.down, Direction.left, Direction.right)) {
                int nr = cur.row + dr(d);
                int nc = cur.col + dc(d);

                if (!inBounds(board, nr, nc) || visited[nr][nc]) continue;
                if (!canWalk(board, activePlayers, bombs, nr, nc, bot.id, false)) continue;

                // Tránh danger hiện tại từ bom thật / lửa thật
                // nhưng không chặn bước đi chỉ vì bom ảo sẽ nổ trong tương lai
                if (isDangerCell(board, bombs, explosions, nr, nc, null)) continue;

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
     * BFS tìm mục tiêu gần nhất.
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

            if (cur.depth >= maxDepth) continue;

            for (Direction d : List.of(Direction.up, Direction.down, Direction.left, Direction.right)) {
                int nr = cur.row + dr(d);
                int nc = cur.col + dc(d);

                if (!inBounds(board, nr, nc) || visited[nr][nc]) continue;

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
     * Chọn 1 hướng ngẫu nhiên nhưng an toàn.
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

            if (!canWalk(board, activePlayers, bombs, nr, nc, bot.id, false)) continue;
            if (isDangerCell(board, bombs, explosions, nr, nc, null)) continue;

            return d;
        }

        return null;
    }

    /**
     * Tìm slot item trong inventory.
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
     * Kiểm tra bot có đứng cạnh tường mềm không.
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
     * Kiểm tra ô có đi được không.
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
     * Kiểm tra ô có nguy hiểm không:
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
     * Ô target có nằm trong vùng nổ của quả bom không.
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
     * Kiểm tra đường blast có bị tường cản không.
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
     * Kiểm tra trong map.
     */
    private boolean inBounds(int[][] board, int row, int col) {
        return row >= 0 && col >= 0 && row < board.length && col < board[0].length;
    }

    /**
     * Delta row theo hướng.
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
     */
    private int dc(Direction d) {
        return switch (d) {
            case left -> -1;
            case right -> 1;
            default -> 0;
        };
    }

    /**
     * Khoảng cách Manhattan.
     */
    private int manhattan(int r1, int c1, int r2, int c2) {
        return Math.abs(r1 - r2) + Math.abs(c1 - c2);
    }
}
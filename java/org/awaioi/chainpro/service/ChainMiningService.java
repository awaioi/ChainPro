package org.awaioi.chainpro.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.awaioi.chainpro.config.ChainSettings;
import org.awaioi.chainpro.data.PlayerToggleStore;
import org.awaioi.chainpro.protection.ProtectionService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ChainMiningService {

    private final JavaPlugin plugin;
    private final PlayerToggleStore toggleStore;
    private final ProtectionService protectionService;
    private volatile ChainSettings settings;

    private final Map<UUID, BukkitRunnable> activeTasks = new HashMap<>();
    private final Map<UUID, Long> lastTriggerMs = new HashMap<>();
    private final Set<BlockKey> internalBreaks = new HashSet<>();

    public ChainMiningService(JavaPlugin plugin, ChainSettings settings, PlayerToggleStore toggleStore, ProtectionService protectionService) {
        this.plugin = plugin;
        this.settings = settings;
        this.toggleStore = toggleStore;
        this.protectionService = protectionService;
    }

    public void updateSettings(ChainSettings newSettings) {
        this.settings = newSettings;
    }

    public ChainSettings getSettings() {
        return settings;
    }

    public void shutdown() {
        for (BukkitRunnable task : activeTasks.values()) {
            task.cancel();
        }
        activeTasks.clear();
        internalBreaks.clear();
        lastTriggerMs.clear();
    }

    public boolean isInternalBreak(Block block) {
        if (block == null) return false;
        return internalBreaks.contains(BlockKey.fromBlock(block));
    }

    public void tryStartChain(Player player, Block originBlock) {
        ChainSettings s = settings;

        if (player == null || originBlock == null) return;
        if (activeTasks.containsKey(player.getUniqueId())) return;
        if (s.worldBlacklist.contains(originBlock.getWorld().getName())) return;

        int y = originBlock.getY();
        if (y < s.yMin || y > s.yMax) return;

        if (!toggleStore.isEnabled(player.getUniqueId(), s.enabledByDefault)) return;

        Material toolType = player.getInventory().getItemInMainHand().getType();
        if (!s.isToolAllowed(toolType)) return;

        long now = System.currentTimeMillis();
        Long last = lastTriggerMs.get(player.getUniqueId());
        if (last != null && now - last < s.cooldownMs) return;
        lastTriggerMs.put(player.getUniqueId(), now);

        List<Block> targets = collectTargets(originBlock, s);
        if (targets.isEmpty()) return;

        if (targets.size() > s.maxBlocksPerChain) {
            player.sendActionBar(Component.text("连锁数量超过上限: " + s.maxBlocksPerChain, NamedTextColor.RED));
            return;
        }

        BukkitRunnable task = new BukkitRunnable() {
            private int index = 0;
            private int broken = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    stop(false);
                    return;
                }
                ChainSettings current = settings;
                int remaining = targets.size() - index;
                if (remaining <= 0) {
                    stop(true);
                    return;
                }
                int toBreak = Math.min(current.breaksPerTick, remaining);
                for (int i = 0; i < toBreak; i++) {
                    Block block = targets.get(index++);
                    if (block.getType() == Material.AIR) continue;
                    if (protectionService != null && !protectionService.canBreak(player, block)) {
                        stop(true);
                        return;
                    }
                    BlockKey key = BlockKey.fromBlock(block);
                    internalBreaks.add(key);
                    boolean ok;
                    try {
                        ok = player.breakBlock(block);
                    } catch (Throwable t) {
                        ok = false;
                    } finally {
                        internalBreaks.remove(key);
                    }
                    if (!ok) {
                        stop(true);
                        return;
                    }
                    broken++;
                }
            }

            private void stop(boolean notify) {
                cancel();
                activeTasks.remove(player.getUniqueId());

                if (!notify) return;
                ChainSettings current = settings;
                if (!current.notifyChainCount) return;
                if (!player.isOnline()) return;
                if (broken <= 0) return;
                player.sendActionBar(Component.text("本次连锁: " + broken + " 个方块", NamedTextColor.GREEN));
            }
        };

        activeTasks.put(player.getUniqueId(), task);
        task.runTaskTimer(plugin, 1L, 1L);
    }

    private List<Block> collectTargets(Block originBlock, ChainSettings s) {
        Material originType = originBlock.getType();
        BlockData originData = originBlock.getBlockData();

        boolean isTree = s.treeEnabled && s.isTreeLog(originType);
        if (!isTree && !s.isSameMaterialChainAllowed(originType)) {
            return Collections.emptyList();
        }

        Deque<Block> queue = new ArrayDeque<>();
        Set<BlockKey> visited = new HashSet<>();
        List<Block> result = new ArrayList<>();

        queue.add(originBlock);
        visited.add(BlockKey.fromBlock(originBlock));

        while (!queue.isEmpty() && result.size() < s.maxBlocksPerChain) {
            Block current = queue.removeFirst();

            if (!current.equals(originBlock)) {
                result.add(current);
                if (result.size() >= s.maxBlocksPerChain) break;
            }

            for (Block neighbor : neighbors6(current)) {
                BlockKey key = BlockKey.fromBlock(neighbor);
                if (!visited.add(key)) continue;

                if (isTree) {
                    if (!s.isTreeLog(neighbor.getType())) {
                        continue;
                    }
                } else {
                    if (!matchesNonTree(neighbor, originType, originData, s)) {
                        continue;
                    }
                }

                queue.addLast(neighbor);
                if (result.size() >= s.maxBlocksPerChain) break;
            }
        }

        if (isTree && s.treeBreakLeaves && result.size() < s.maxBlocksPerChain) {
            addTreeLeaves(originBlock, result, s);
        }

        return result;
    }

    private boolean matchesNonTree(Block block, Material originType, BlockData originData, ChainSettings s) {
        Material type = block.getType();
        if (type == Material.AIR) return false;
        if (type != originType) return false;
        if (!s.exactBlockDataMatch) return true;
        return block.getBlockData().equals(originData);
    }

    private void addTreeLeaves(Block originBlock, List<Block> result, ChainSettings s) {
        Set<BlockKey> existing = new HashSet<>();
        for (Block b : result) {
            existing.add(BlockKey.fromBlock(b));
        }
        existing.add(BlockKey.fromBlock(originBlock));

        int index = 0;
        while (index < result.size() && result.size() < s.maxBlocksPerChain) {
            Block log = result.get(index++);
            for (Block neighbor : neighbors6(log)) {
                if (result.size() >= s.maxBlocksPerChain) break;
                if (!s.isLeaf(neighbor.getType())) continue;
                BlockKey key = BlockKey.fromBlock(neighbor);
                if (!existing.add(key)) continue;
                result.add(neighbor);
            }
        }
    }

    private static List<Block> neighbors6(Block block) {
        Location loc = block.getLocation();
        World world = block.getWorld();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        return Arrays.asList(
                world.getBlockAt(x + 1, y, z),
                world.getBlockAt(x - 1, y, z),
                world.getBlockAt(x, y + 1, z),
                world.getBlockAt(x, y - 1, z),
                world.getBlockAt(x, y, z + 1),
                world.getBlockAt(x, y, z - 1)
        );
    }

    private static final class BlockKey {
        private final UUID worldId;
        private final int x;
        private final int y;
        private final int z;

        private BlockKey(UUID worldId, int x, int y, int z) {
            this.worldId = worldId;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        static BlockKey fromBlock(Block block) {
            Location loc = block.getLocation();
            return new BlockKey(block.getWorld().getUID(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BlockKey)) return false;
            BlockKey other = (BlockKey) o;
            if (x != other.x) return false;
            if (y != other.y) return false;
            if (z != other.z) return false;
            return worldId.equals(other.worldId);
        }

        @Override
        public int hashCode() {
            int result = worldId.hashCode();
            result = 31 * result + x;
            result = 31 * result + y;
            result = 31 * result + z;
            return result;
        }
    }
}

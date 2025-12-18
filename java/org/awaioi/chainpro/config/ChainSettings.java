package org.awaioi.chainpro.config;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ChainSettings {

    public final boolean enabledByDefault;
    public final long cooldownMs;
    public final int maxBlocksPerChain;
    public final int breaksPerTick;

    public final Set<String> worldBlacklist;
    public final int yMin;
    public final int yMax;

    public final boolean exactBlockDataMatch;

    public final Set<Material> toolWhitelist;
    public final Set<Material> sameMaterialWhitelist;

    public final boolean treeEnabled;
    public final boolean treeBreakLeaves;

    public final boolean notifyChainCount;

    private ChainSettings(
            boolean enabledByDefault,
            long cooldownMs,
            int maxBlocksPerChain,
            int breaksPerTick,
            Set<String> worldBlacklist,
            int yMin,
            int yMax,
            boolean exactBlockDataMatch,
            Set<Material> toolWhitelist,
            Set<Material> sameMaterialWhitelist,
            boolean treeEnabled,
            boolean treeBreakLeaves,
            boolean notifyChainCount
    ) {
        this.enabledByDefault = enabledByDefault;
        this.cooldownMs = cooldownMs;
        this.maxBlocksPerChain = maxBlocksPerChain;
        this.breaksPerTick = breaksPerTick;
        this.worldBlacklist = worldBlacklist;
        this.yMin = yMin;
        this.yMax = yMax;
        this.exactBlockDataMatch = exactBlockDataMatch;
        this.toolWhitelist = toolWhitelist;
        this.sameMaterialWhitelist = sameMaterialWhitelist;
        this.treeEnabled = treeEnabled;
        this.treeBreakLeaves = treeBreakLeaves;
        this.notifyChainCount = notifyChainCount;
    }

    public static ChainSettings fromConfig(FileConfiguration config) {
        boolean enabledByDefault = config.getBoolean("enabled-by-default", true);
        long cooldownMs = Math.max(0L, config.getLong("cooldown-ms", 200L));
        int maxBlocksPerChain = clamp(config.getInt("max-blocks-per-chain", 20), 1, 20);
        int breaksPerTick = clamp(config.getInt("breaks-per-tick", 16), 1, 256);

        Set<String> worldBlacklist = new HashSet<>(config.getStringList("world-blacklist"));
        int yMin = config.getInt("y-min", -64);
        int yMax = config.getInt("y-max", 320);

        boolean exactBlockDataMatch = config.getBoolean("matching.exact-blockdata", false);

        Set<Material> toolWhitelist = parseMaterialSet(config.getStringList("tools.whitelist"));
        Set<Material> sameMaterialWhitelist = parseMaterialSet(config.getStringList("blocks.same-material-whitelist"));

        ConfigurationSection treeSection = config.getConfigurationSection("tree");
        boolean treeEnabled = treeSection == null || treeSection.getBoolean("enabled", true);
        boolean treeBreakLeaves = treeSection != null && treeSection.getBoolean("break-leaves", false);

        boolean notifyChainCount = config.getBoolean("messages.notify-chain-count", true);

        return new ChainSettings(
                enabledByDefault,
                cooldownMs,
                maxBlocksPerChain,
                breaksPerTick,
                Collections.unmodifiableSet(worldBlacklist),
                yMin,
                yMax,
                exactBlockDataMatch,
                Collections.unmodifiableSet(toolWhitelist),
                Collections.unmodifiableSet(sameMaterialWhitelist),
                treeEnabled,
                treeBreakLeaves,
                notifyChainCount
        );
    }

    public boolean isToolAllowed(Material material) {
        if (material == null || material == Material.AIR) return false;
        if (!toolWhitelist.isEmpty()) return toolWhitelist.contains(material);
        String name = material.name();
        return name.endsWith("_PICKAXE") || name.endsWith("_AXE") || name.endsWith("_SHOVEL");
    }

    public boolean isSameMaterialChainAllowed(Material material) {
        if (material == null || material == Material.AIR) return false;
        if (!sameMaterialWhitelist.isEmpty()) return sameMaterialWhitelist.contains(material);
        return false;
    }

    public boolean isTreeLog(Material material) {
        if (material == null) return false;
        if (Tag.LOGS.isTagged(material)) return true;
        String name = material.name();
        return name.endsWith("_LOG") || name.endsWith("_WOOD") || name.endsWith("_STEM") || name.endsWith("_HYPHAE");
    }

    public boolean isLeaf(Material material) {
        return material != null && Tag.LEAVES.isTagged(material);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Set<Material> parseMaterialSet(List<String> names) {
        if (names == null || names.isEmpty()) return EnumSet.noneOf(Material.class);
        Set<Material> result = EnumSet.noneOf(Material.class);
        for (String name : names) {
            if (name == null) continue;
            String trimmed = name.trim();
            if (trimmed.isEmpty()) continue;
            Material mat = Material.matchMaterial(trimmed, false);
            if (mat != null) result.add(mat);
        }
        return result;
    }
}

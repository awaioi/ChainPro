package org.awaioi.chainpro.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerToggleStore {

    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;
    private final Map<UUID, Boolean> enabledByPlayer;

    public PlayerToggleStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
        this.enabledByPlayer = new ConcurrentHashMap<>();

        ConfigurationSection section = yaml.getConfigurationSection("players");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    enabledByPlayer.put(uuid, section.getBoolean(key));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    public boolean isEnabled(UUID uuid, boolean enabledByDefault) {
        return enabledByPlayer.getOrDefault(uuid, enabledByDefault);
    }

    public boolean toggle(UUID uuid, boolean enabledByDefault) {
        boolean newValue = !isEnabled(uuid, enabledByDefault);
        enabledByPlayer.put(uuid, newValue);
        return newValue;
    }

    public void setEnabled(UUID uuid, boolean enabled) {
        enabledByPlayer.put(uuid, enabled);
    }

    public void save() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) return;

        yaml.set("players", null);
        for (Map.Entry<UUID, Boolean> entry : enabledByPlayer.entrySet()) {
            yaml.set("players." + entry.getKey(), entry.getValue());
        }

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save data.yml: " + e.getMessage());
        }
    }
}

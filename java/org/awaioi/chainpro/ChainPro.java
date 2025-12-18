package org.awaioi.chainpro;

import org.awaioi.chainpro.command.ChainProCommand;
import org.awaioi.chainpro.config.ChainSettings;
import org.awaioi.chainpro.data.PlayerToggleStore;
import org.awaioi.chainpro.listener.BlockBreakListener;
import org.awaioi.chainpro.protection.ProtectionService;
import org.awaioi.chainpro.service.ChainMiningService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChainPro extends JavaPlugin {

    private ChainSettings settings;
    private PlayerToggleStore toggleStore;
    private ChainMiningService chainMiningService;
    private ProtectionService protectionService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();

        settings = ChainSettings.fromConfig(getConfig());
        toggleStore = new PlayerToggleStore(this);
        protectionService = new ProtectionService(this);
        chainMiningService = new ChainMiningService(this, settings, toggleStore, protectionService);

        getServer().getPluginManager().registerEvents(new BlockBreakListener(chainMiningService, toggleStore), this);

        PluginCommand command = getCommand("chainpro");
        if (command != null) {
            ChainProCommand executor = new ChainProCommand(this, toggleStore, chainMiningService);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    @Override
    public void onDisable() {
        if (chainMiningService != null) {
            chainMiningService.shutdown();
        }
        if (toggleStore != null) {
            toggleStore.save();
        }
    }

    public void reloadChainPro() {
        reloadConfig();
        settings = ChainSettings.fromConfig(getConfig());
        chainMiningService.updateSettings(settings);
    }
}

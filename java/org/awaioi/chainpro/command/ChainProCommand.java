package org.awaioi.chainpro.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.awaioi.chainpro.ChainPro;
import org.awaioi.chainpro.data.PlayerToggleStore;
import org.awaioi.chainpro.service.ChainMiningService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ChainProCommand implements CommandExecutor, TabCompleter {

    private final ChainPro plugin;
    private final PlayerToggleStore toggleStore;
    private final ChainMiningService chainMiningService;

    public ChainProCommand(ChainPro plugin, PlayerToggleStore toggleStore, ChainMiningService chainMiningService) {
        this.plugin = plugin;
        this.toggleStore = toggleStore;
        this.chainMiningService = chainMiningService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("用法: /" + label + " <toggle|reload|status>", NamedTextColor.YELLOW));
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "toggle":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(Component.text("只有玩家可以使用该命令", NamedTextColor.RED));
                    return true;
                }
                Player player = (Player) sender;
                if (!player.hasPermission("chainmine.use")) {
                    player.sendMessage(Component.text("你没有权限", NamedTextColor.RED));
                    return true;
                }
                boolean enabled = toggleStore.toggle(player.getUniqueId(), chainMiningService.getSettings().enabledByDefault);
                toggleStore.save();
                player.sendMessage(Component.text("连锁采集已" + (enabled ? "开启" : "关闭"), enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY));
                return true;
            case "reload":
                if (!sender.hasPermission("chainmine.reload") && !sender.hasPermission("chainmine.admin")) {
                    sender.sendMessage(Component.text("你没有权限", NamedTextColor.RED));
                    return true;
                }
                plugin.reloadChainPro();
                sender.sendMessage(Component.text("ChainPro 配置已重载", NamedTextColor.GREEN));
                return true;
            case "status":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(Component.text("该命令仅玩家可用", NamedTextColor.RED));
                    return true;
                }
                Player statusPlayer = (Player) sender;
                boolean statusEnabled = toggleStore.isEnabled(statusPlayer.getUniqueId(), chainMiningService.getSettings().enabledByDefault);
                statusPlayer.sendMessage(Component.text("连锁采集状态: " + (statusEnabled ? "开启" : "关闭"), statusEnabled ? NamedTextColor.GREEN : NamedTextColor.GRAY));
                return true;
            default:
                sender.sendMessage(Component.text("未知子命令: " + sub, NamedTextColor.RED));
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("toggle", "status", "reload");
        }
        return Collections.emptyList();
    }
}

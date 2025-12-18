package org.awaioi.chainpro.listener;

import org.awaioi.chainpro.data.PlayerToggleStore;
import org.awaioi.chainpro.service.ChainMiningService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public final class BlockBreakListener implements Listener {

    private final ChainMiningService chainMiningService;
    private final PlayerToggleStore toggleStore;

    public BlockBreakListener(ChainMiningService chainMiningService, PlayerToggleStore toggleStore) {
        this.chainMiningService = chainMiningService;
        this.toggleStore = toggleStore;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (chainMiningService.isInternalBreak(event.getBlock())) return;
        if (!event.getPlayer().hasPermission("chainmine.use")) return;
        if (!toggleStore.isEnabled(event.getPlayer().getUniqueId(), chainMiningService.getSettings().enabledByDefault)) return;
        chainMiningService.tryStartChain(event.getPlayer(), event.getBlock());
    }
}

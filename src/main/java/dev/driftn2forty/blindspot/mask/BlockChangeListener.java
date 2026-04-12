package dev.driftn2forty.blindspot.mask;

import dev.driftn2forty.blindspot.config.PluginConfig;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public final class BlockChangeListener implements Listener {

    private final PluginConfig config;
    private final BlockEntityCache beCache;
    private final BlockEntityCache scanCache;

    public BlockChangeListener(PluginConfig config, BlockEntityCache beCache, BlockEntityCache scanCache) {
        this.config = config;
        this.beCache = beCache;
        this.scanCache = scanCache;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        invalidateIfTracked(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        invalidateIfTracked(event.getBlock());
    }

    private void invalidateIfTracked(Block block) {
        if (!config.enabled) return;
        if (config.beEnabled && config.beMaskMaterials.contains(block.getType())) {
            beCache.invalidate(block.getChunk());
        }
        if (config.scanEnabled && config.scanMaterials.contains(block.getType())) {
            scanCache.invalidate(block.getChunk());
        }
    }
}

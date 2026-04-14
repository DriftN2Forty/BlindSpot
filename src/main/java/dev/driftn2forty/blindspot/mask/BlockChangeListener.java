package dev.driftn2forty.blindspot.mask;

import dev.driftn2forty.blindspot.config.PluginConfig;
import dev.driftn2forty.blindspot.proximity.PlayerDeltaTracker;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.PortalCreateEvent;

public final class BlockChangeListener implements Listener {

    private final PluginConfig config;
    private final BlockEntityCache beCache;
    private final BlockEntityCache scanCache;
    private final PlayerDeltaTracker deltaTracker;

    public BlockChangeListener(PluginConfig config, BlockEntityCache beCache,
                               BlockEntityCache scanCache, PlayerDeltaTracker deltaTracker) {
        this.config = config;
        this.beCache = beCache;
        this.scanCache = scanCache;
        this.deltaTracker = deltaTracker;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        invalidateIfTracked(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        invalidateIfTracked(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent event) {
        for (BlockState state : event.getBlocks()) {
            invalidateChunkFor(state.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        invalidateIfTracked(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            invalidateIfTracked(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            invalidateIfTracked(block);
        }
    }

    private void invalidateIfTracked(Block block) {
        if (!config.enabled) return;
        boolean invalidated = false;
        if (config.beEnabled && config.beMaskMaterials.contains(block.getType())) {
            beCache.invalidate(block.getChunk());
            invalidated = true;
        }
        if (config.scanEnabled && config.scanMaterials.contains(block.getType())) {
            scanCache.invalidate(block.getChunk());
            invalidated = true;
        }
        if (invalidated) {
            markNearbyPlayersDirty(block.getLocation());
        }
    }

    private void invalidateChunkFor(Block block) {
        if (!config.enabled) return;
        if (config.scanEnabled) {
            scanCache.invalidate(block.getChunk());
            markNearbyPlayersDirty(block.getLocation());
        }
    }

    private void markNearbyPlayersDirty(Location loc) {
        double range = 48;
        double rangeSq = range * range;
        for (Player p : loc.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(loc) <= rangeSq) {
                deltaTracker.markDirty(p.getUniqueId());
            }
        }
    }
}

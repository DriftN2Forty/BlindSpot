package dev.driftn2forty.blindspot.entity;

import dev.driftn2forty.blindspot.config.PluginConfig;
import dev.driftn2forty.blindspot.proximity.PlayerDeltaTracker;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;

/**
 * Marks nearby players as dirty in the {@link PlayerDeltaTracker} when an
 * item frame (or other hanging entity) is placed or removed. Without this,
 * stationary players would not re-check item-frame visibility until they
 * move or rotate.
 */
public final class HangingChangeListener implements Listener {

    private final PluginConfig config;
    private final PlayerDeltaTracker deltaTracker;

    public HangingChangeListener(PluginConfig config, PlayerDeltaTracker deltaTracker) {
        this.config = config;
        this.deltaTracker = deltaTracker;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        markNearbyPlayersDirty(event.getEntity().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        markNearbyPlayersDirty(event.getEntity().getLocation());
    }

    private void markNearbyPlayersDirty(Location loc) {
        if (!config.enabled || !config.entityEnabled) return;
        double range = Math.max(48, config.entityLosMaxRevealDistance + 8);
        double rangeSq = range * range;
        for (Player p : loc.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(loc) <= rangeSq) {
                deltaTracker.markDirty(p.getUniqueId());
            }
        }
    }
}

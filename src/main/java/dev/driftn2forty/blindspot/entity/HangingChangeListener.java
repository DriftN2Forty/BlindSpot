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

import java.util.List;
import java.util.UUID;

/**
 * Marks nearby players as dirty in all {@link PlayerDeltaTracker} instances
 * when an item frame (or other hanging entity) is placed or removed.
 */
public final class HangingChangeListener implements Listener {

    private final PluginConfig config;
    private final List<PlayerDeltaTracker> deltaTrackers;

    public HangingChangeListener(PluginConfig config, List<PlayerDeltaTracker> deltaTrackers) {
        this.config = config;
        this.deltaTrackers = deltaTrackers;
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
                UUID id = p.getUniqueId();
                for (PlayerDeltaTracker dt : deltaTrackers) {
                    dt.markDirty(id);
                }
            }
        }
    }
}

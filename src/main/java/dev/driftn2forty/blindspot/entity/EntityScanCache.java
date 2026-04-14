package dev.driftn2forty.blindspot.entity;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches the result of {@link Player#getNearbyEntities} so that all three
 * entity visibility services (general entities, players, item frames) can
 * share a single Bukkit scan per player per tick cycle.
 * <p>
 * The cache auto-invalidates when the server tick advances so callers do not
 * need to coordinate clearing.
 */
public final class EntityScanCache {

    private final Map<UUID, List<Entity>> cache = new ConcurrentHashMap<>();
    private int lastTick = -1;

    /**
     * Returns the cached nearby-entity list for the given player, computing
     * it on the first call per server tick.
     */
    public List<Entity> getNearbyEntities(Player player, double range) {
        int currentTick = Bukkit.getCurrentTick();
        if (currentTick != lastTick) {
            cache.clear();
            lastTick = currentTick;
        }
        return cache.computeIfAbsent(player.getUniqueId(),
                k -> player.getNearbyEntities(range, range, range));
    }
}

package dev.driftn2forty.blindspot.proximity;

import dev.driftn2forty.blindspot.config.PluginConfig;
import dev.driftn2forty.blindspot.guard.TpsThrottle;
import dev.driftn2forty.blindspot.mask.BlockEntityCache;
import dev.driftn2forty.blindspot.mask.MaskStateTracker;
import dev.driftn2forty.blindspot.util.TickTimings;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BlockVector;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Periodic visibility service for non-block-entity blocks discovered via
 * NMS chunk scanning. Same tick/reveal/remask pattern as
 * {@link BlockEntityVisibilityService} but reads from a scan-based cache.
 */
public final class BlockVisibilityService {

    private final Plugin plugin;
    private final PluginConfig config;
    private final VisibilityChecker proximity;
    private final BlockEntityCache scanCache;
    private final MaskStateTracker maskState;
    private final TpsThrottle tpsGuard;
    private final PlayerDeltaTracker deltaTracker;
    private BukkitTask task;
    private final Map<UUID, Map<BlockVector, Long>> remaskTimers = new ConcurrentHashMap<>();
    private final TickTimings timings = new TickTimings();
    private int tickCounter;
    private int fullTickEvery;
    private static final int NORMAL_INTERVAL = 8;

    public BlockVisibilityService(Plugin plugin, PluginConfig config, VisibilityChecker proximity,
                                  BlockEntityCache scanCache, MaskStateTracker maskState,
                                  TpsThrottle tpsGuard, PlayerDeltaTracker deltaTracker) {
        this.plugin = plugin;
        this.config = config;
        this.proximity = proximity;
        this.scanCache = scanCache;
        this.maskState = maskState;
        this.tpsGuard = tpsGuard;
        this.deltaTracker = deltaTracker;
    }

    public void start() {
        stop();
        this.tickCounter = 0;
        this.fullTickEvery = (NORMAL_INTERVAL + config.scanHighPriorityInterval - 1) / config.scanHighPriorityInterval;
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, config.scanHighPriorityInterval);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    public void restart() {
        if (task != null) task.cancel();
        task = null;
        remaskTimers.clear();
        if (!config.enabled || !config.scanEnabled) return;
        this.tickCounter = 0;
        this.fullTickEvery = (NORMAL_INTERVAL + config.scanHighPriorityInterval - 1) / config.scanHighPriorityInterval;
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, config.scanHighPriorityInterval);
    }

    public TickTimings getTimings() { return timings; }

    private void tick() {
        if (!config.enabled || !config.scanEnabled) return;
        if (!tpsGuard.allowHeavyWork()) return;

        boolean fullTick = tickCounter % fullTickEvery == 0;
        tickCounter++;

        long start = System.nanoTime();
        try {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.isOnline() || !config.isWorldEnabled(p.getWorld())
                        || p.hasPermission(config.bypassPermission)) continue;

                // Always process expired remask timers, even for stationary players.
                processExpiredRemaskTimers(p);

                if (!deltaTracker.hasMoved(p)) continue;

                Location loc = p.getLocation();
                int cx = loc.getBlockX() >> 4;
                int cz = loc.getBlockZ() >> 4;

                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        Chunk chunk = p.getWorld().getChunkAt(cx + dx, cz + dz);
                        List<BlockVector> positions = scanCache.getBlockEntityPositions(chunk);
                        if (positions.isEmpty()) continue;

                        for (BlockVector bp : positions) {
                            Location blockLoc = new Location(p.getWorld(),
                                    bp.getBlockX(), bp.getBlockY(), bp.getBlockZ());

                            if (!fullTick && loc.distanceSquared(blockLoc) > config.scanHighPriorityRadiusSq) continue;

                            boolean visible = proximity.isScanBlockVisible(p, bp);

                            if (visible) {
                                Map<BlockVector, Long> t = remaskTimers.get(p.getUniqueId());
                                if (t != null) t.remove(bp);
                                if (!maskState.isMaskedFor(p.getUniqueId(), bp)) continue;
                                p.sendBlockChange(blockLoc, blockLoc.getBlock().getBlockData());
                                maskState.setMasked(p.getUniqueId(), bp, false);
                            } else {
                                Material real = blockLoc.getBlock().getType();
                                if (!config.scanRemaskLeaving || !config.scanMaterials.contains(real)) continue;
                                if (maskState.isMaskedFor(p.getUniqueId(), bp)) continue;

                                Map<BlockVector, Long> timers = remaskTimers
                                        .computeIfAbsent(p.getUniqueId(), k -> new ConcurrentHashMap<>());
                                long now = System.currentTimeMillis();
                                Long since = timers.putIfAbsent(bp, now);
                                if (since == null) since = now;
                                if (now - since < config.scanRemaskDelayMs) continue;

                                Material ph = config.scanPlaceholders.getOrDefault(real, Material.STONE);
                                p.sendBlockChange(blockLoc, Bukkit.createBlockData(ph));
                                maskState.setMasked(p.getUniqueId(), bp, true);
                                timers.remove(bp);
                            }
                        }
                    }
                }
            }
        } finally {
            timings.record(System.nanoTime() - start);
        }
    }

    /**
     * Applies masks for any remask timers that have expired, without requiring
     * a fresh visibility check. This runs for all players regardless of
     * movement, so that stationary players still see blocks re-mask after the
     * configured delay.
     */
    private void processExpiredRemaskTimers(Player p) {
        Map<BlockVector, Long> timers = remaskTimers.get(p.getUniqueId());
        if (timers == null || timers.isEmpty()) return;

        long now = System.currentTimeMillis();
        var it = timers.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (now - entry.getValue() < config.scanRemaskDelayMs) continue;

            BlockVector bp = entry.getKey();
            if (maskState.isMaskedFor(p.getUniqueId(), bp)) {
                it.remove();
                continue;
            }

            Location blockLoc = new Location(p.getWorld(),
                    bp.getBlockX(), bp.getBlockY(), bp.getBlockZ());
            Material real = blockLoc.getBlock().getType();
            if (!config.scanMaterials.contains(real)) {
                it.remove();
                continue;
            }

            Material ph = config.scanPlaceholders.getOrDefault(real, Material.STONE);
            p.sendBlockChange(blockLoc, Bukkit.createBlockData(ph));
            maskState.setMasked(p.getUniqueId(), bp, true);
            it.remove();
        }
    }
}

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

public final class BlockEntityVisibilityService {

    private final Plugin plugin;
    private final PluginConfig config;
    private final VisibilityChecker proximity;
    private final BlockEntityCache beCache;
    private final MaskStateTracker maskState;
    private final TpsThrottle tpsGuard;
    private BukkitTask task;
    private final Map<UUID, Map<BlockVector, Long>> remaskTimers = new ConcurrentHashMap<>();
    private final TickTimings timings = new TickTimings();
    private int tickCounter;
    private int fullTickEvery;
    private static final int NORMAL_INTERVAL = 8;

    public BlockEntityVisibilityService(Plugin plugin, PluginConfig config, VisibilityChecker proximity,
                            BlockEntityCache beCache, MaskStateTracker maskState, TpsThrottle tpsGuard) {
        this.plugin = plugin;
        this.config = config;
        this.proximity = proximity;
        this.beCache = beCache;
        this.maskState = maskState;
        this.tpsGuard = tpsGuard;
    }

    public void start() {
        stop();
        this.tickCounter = 0;
        this.fullTickEvery = (NORMAL_INTERVAL + config.beHighPriorityInterval - 1) / config.beHighPriorityInterval;
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, config.beHighPriorityInterval);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    /** Restart after config reload — preserves mask state. */
    public void restart() {
        if (task != null) task.cancel();
        task = null;
        remaskTimers.clear();
        if (!config.enabled || !config.beEnabled) return;
        this.tickCounter = 0;
        this.fullTickEvery = (NORMAL_INTERVAL + config.beHighPriorityInterval - 1) / config.beHighPriorityInterval;
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, config.beHighPriorityInterval);
    }

    public TickTimings getTimings() { return timings; }

    private void tick() {
        if (!config.enabled || !config.beEnabled) return;
        if (!tpsGuard.allowHeavyWork()) return;

        boolean fullTick = tickCounter % fullTickEvery == 0;
        tickCounter++;

        long start = System.nanoTime();
        try {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.isOnline() || !config.isWorldEnabled(p.getWorld())
                    || p.hasPermission(config.bypassPermission)) continue;

            Location loc = p.getLocation();
            int cx = loc.getBlockX() >> 4;
            int cz = loc.getBlockZ() >> 4;

            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    Chunk chunk = p.getWorld().getChunkAt(cx + dx, cz + dz);
                    List<BlockVector> positions = beCache.getBlockEntityPositions(chunk);
                    if (positions.isEmpty()) continue;

                    for (BlockVector bp : positions) {
                        Location blockLoc = new Location(p.getWorld(),
                                bp.getBlockX(), bp.getBlockY(), bp.getBlockZ());

                        if (!fullTick && loc.distanceSquared(blockLoc) > config.beHighPriorityRadiusSq) continue;

                        boolean visible = proximity.isBlockVisible(p, bp);

                        if (visible) {
                            Map<BlockVector, Long> t = remaskTimers.get(p.getUniqueId());
                            if (t != null) t.remove(bp);
                            if (!maskState.isMaskedFor(p.getUniqueId(), bp)) continue;
                            Material real = blockLoc.getBlock().getType();
                            p.sendBlockChange(blockLoc, Bukkit.createBlockData(real));
                            maskState.setMasked(p.getUniqueId(), bp, false);
                        } else {
                            Material real = blockLoc.getBlock().getType();
                            if (!config.beRemaskLeaving || !config.beMaskMaterials.contains(real)) continue;
                            if (maskState.isMaskedFor(p.getUniqueId(), bp)) continue;

                            Map<BlockVector, Long> timers = remaskTimers
                                    .computeIfAbsent(p.getUniqueId(), k -> new ConcurrentHashMap<>());
                            long now = System.currentTimeMillis();
                            Long since = timers.putIfAbsent(bp, now);
                            if (since == null) since = now;
                            if (now - since < config.beRemaskDelayMs) continue;

                            Material ph = config.bePlaceholders.getOrDefault(real, Material.STONE);
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
}

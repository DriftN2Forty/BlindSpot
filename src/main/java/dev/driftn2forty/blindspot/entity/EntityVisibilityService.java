package dev.driftn2forty.blindspot.entity;

import dev.driftn2forty.blindspot.config.PluginConfig;
import dev.driftn2forty.blindspot.guard.TpsThrottle;
import dev.driftn2forty.blindspot.proximity.VisibilityChecker;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import dev.driftn2forty.blindspot.util.TickTimings;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class EntityVisibilityService {

    private final Plugin plugin;
    private final PluginConfig config;
    private final VisibilityChecker proximity;
    private final TpsThrottle tpsGuard;
    private BukkitTask task;
    private final Map<UUID, Set<UUID>> hiddenByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Long>> remaskTimers = new ConcurrentHashMap<>();
    private final TickTimings timings = new TickTimings();
    private int tickCounter;
    private int fullTickEvery;
    private static final int NORMAL_INTERVAL = 10;

    public EntityVisibilityService(Plugin plugin, PluginConfig config,
                                   VisibilityChecker proximity, TpsThrottle tpsGuard) {
        this.plugin = plugin;
        this.config = config;
        this.proximity = proximity;
        this.tpsGuard = tpsGuard;
    }

    public void start() {
        stop();
        if (!config.enabled || !config.entityEnabled) return;
        this.tickCounter = 0;
        this.fullTickEvery = (NORMAL_INTERVAL + config.entityHighPriorityInterval - 1) / config.entityHighPriorityInterval;
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, config.entityHighPriorityInterval);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        revealAll();
    }

    /** Restart after config reload — preserves hidden-entity state. */
    public void restart() {
        if (task != null) task.cancel();
        task = null;
        remaskTimers.clear();

        if (!config.enabled || !config.entityEnabled) {
            revealAll();
            return;
        }
        this.tickCounter = 0;
        this.fullTickEvery = (NORMAL_INTERVAL + config.entityHighPriorityInterval - 1) / config.entityHighPriorityInterval;
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, config.entityHighPriorityInterval);
    }

    private void revealAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Set<UUID> set = hiddenByPlayer.getOrDefault(p.getUniqueId(), Collections.emptySet());
            if (set.isEmpty()) continue;

            Map<UUID, Entity> entityMap = p.getWorld().getEntities().stream()
                    .collect(Collectors.toMap(Entity::getUniqueId, Function.identity(), (a, b) -> a));

            for (UUID uid : set) {
                Entity e = entityMap.get(uid);
                if (e == null) continue;
                try {
                    p.showEntity(plugin, e);
                } catch (Throwable ignored) {}
            }
        }
        hiddenByPlayer.clear();
    }

    public TickTimings getTimings() { return timings; }

    private void tick() {
        if (!config.enabled || !config.entityEnabled) return;
        if (!tpsGuard.allowHeavyWork()) return;

        boolean fullTick = tickCounter % fullTickEvery == 0;
        tickCounter++;

        long start = System.nanoTime();
        try {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.isOnline() || !config.isWorldEnabled(p.getWorld())
                    || p.hasPermission(config.bypassPermission)) continue;

            Set<UUID> hidden = hiddenByPlayer.computeIfAbsent(p.getUniqueId(),
                    k -> ConcurrentHashMap.newKeySet());
            double scan = Math.max(48, config.entityLosMaxRevealDistance + 8);
            Location playerLoc = p.getLocation();

            for (Entity e : p.getNearbyEntities(scan, scan, scan)) {
                if (e == p || !config.entitySuppressTypes.contains(e.getType())) continue;
                if (e.getType() == EntityType.ITEM_FRAME || e.getType() == EntityType.GLOW_ITEM_FRAME) continue;

                if (!fullTick && playerLoc.distanceSquared(e.getLocation()) > config.entityHighPriorityRadiusSq) continue;

                boolean visible = proximity.isEntityVisible(p, e);

                if (visible) {
                    Map<UUID, Long> timers = remaskTimers.get(p.getUniqueId());
                    if (timers != null) timers.remove(e.getUniqueId());
                    if (!hidden.contains(e.getUniqueId())) continue;
                    try {
                        p.showEntity(plugin, e);
                    } catch (Throwable ignored) {}
                    hidden.remove(e.getUniqueId());
                } else {
                    if (!config.entityRemaskLeaving) continue;
                    if (hidden.contains(e.getUniqueId())) continue;

                    Map<UUID, Long> timers = remaskTimers
                            .computeIfAbsent(p.getUniqueId(), k -> new ConcurrentHashMap<>());
                    long now = System.currentTimeMillis();
                    Long since = timers.putIfAbsent(e.getUniqueId(), now);
                    if (since == null) since = now;
                    if (now - since < config.entityRemaskDelayMs) continue;

                    try {
                        p.hideEntity(plugin, e);
                    } catch (Throwable ignored) {}
                    hidden.add(e.getUniqueId());
                    timers.remove(e.getUniqueId());
                }
            }
        }
        } finally {
            timings.record(System.nanoTime() - start);
        }
    }
}

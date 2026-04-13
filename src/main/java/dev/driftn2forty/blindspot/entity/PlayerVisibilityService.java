package dev.driftn2forty.blindspot.entity;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import dev.driftn2forty.blindspot.config.PluginConfig;
import dev.driftn2forty.blindspot.guard.TpsThrottle;
import dev.driftn2forty.blindspot.proximity.VisibilityChecker;
import dev.driftn2forty.blindspot.util.TickTimings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Packet-based visibility service for player entities.
 * <p>
 * Unlike Bukkit's {@code Player.hideEntity()}, which removes the target from
 * both the rendered world <b>and</b> the tab list (preventing command
 * tab-completion such as {@code /tp}), this service only sends a
 * {@code DESTROY_ENTITIES} packet.  The player stays in the tab list and can
 * still be referenced in commands.
 * <p>
 * Re-showing uses a Bukkit {@code hideEntity}/{@code showEntity} cycle to
 * force the server's entity tracker to resend spawn packets, identical to
 * the approach in {@link ItemFrameVisibilityService}.
 */
public final class PlayerVisibilityService {

    private final Plugin plugin;
    private final PluginConfig config;
    private final VisibilityChecker proximity;
    private final TpsThrottle tpsGuard;

    private BukkitTask task;
    private PacketListenerAbstract packetListener;

    /** observer UUID → (target entity ID → target UUID) for suppressed players */
    private final Map<UUID, Map<Integer, UUID>> suppressedPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Integer, Long>> remaskTimers = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> revealedByPlayer = new ConcurrentHashMap<>();
    private final TickTimings timings = new TickTimings();
    private int tickCounter;
    private int fullTickEvery;
    private static final int NORMAL_INTERVAL = 10;

    public PlayerVisibilityService(Plugin plugin, PluginConfig config,
                                   VisibilityChecker proximity, TpsThrottle tpsGuard) {
        this.plugin = plugin;
        this.config = config;
        this.proximity = proximity;
        this.tpsGuard = tpsGuard;
    }

    // ── lifecycle ──────────────────────────────────────────────────

    public void start() {
        stop();
        if (!config.enabled || !config.entityEnabled) return;
        if (!config.entitySuppressTypes.contains(EntityType.PLAYER)) return;

        this.packetListener = new PlayerSpawnInterceptor();
        PacketEvents.getAPI().getEventManager().registerListener(packetListener);

        this.tickCounter = 0;
        this.fullTickEvery = (NORMAL_INTERVAL + config.entityHighPriorityInterval - 1)
                / config.entityHighPriorityInterval;
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L,
                config.entityHighPriorityInterval);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;

        if (packetListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
            packetListener = null;
        }

        // Re-show all suppressed players via Bukkit API cycle so the server
        // tracker resyncs properly.
        for (Map.Entry<UUID, Map<Integer, UUID>> entry : suppressedPlayers.entrySet()) {
            Player observer = Bukkit.getPlayer(entry.getKey());
            if (observer == null || !observer.isOnline()) continue;
            for (UUID targetUuid : entry.getValue().values()) {
                Player target = Bukkit.getPlayer(targetUuid);
                if (target == null || !target.isOnline()) continue;
                showPlayer(observer, target);
            }
        }

        suppressedPlayers.clear();
        remaskTimers.clear();
        revealedByPlayer.clear();
    }

    /** Restart after config reload — preserves suppressed state. */
    public void restart() {
        if (task != null) task.cancel();
        task = null;
        remaskTimers.clear();
        revealedByPlayer.clear();

        if (!config.enabled || !config.entityEnabled
                || !config.entitySuppressTypes.contains(EntityType.PLAYER)) {
            // Feature now disabled — full cleanup
            if (packetListener != null) {
                PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
                packetListener = null;
            }
            // Re-show all suppressed
            for (Map.Entry<UUID, Map<Integer, UUID>> entry : suppressedPlayers.entrySet()) {
                Player observer = Bukkit.getPlayer(entry.getKey());
                if (observer == null || !observer.isOnline()) continue;
                for (UUID targetUuid : entry.getValue().values()) {
                    Player target = Bukkit.getPlayer(targetUuid);
                    if (target == null || !target.isOnline()) continue;
                    showPlayer(observer, target);
                }
            }
            suppressedPlayers.clear();
            return;
        }

        // Ensure packet listener is registered
        if (packetListener == null) {
            this.packetListener = new PlayerSpawnInterceptor();
            PacketEvents.getAPI().getEventManager().registerListener(packetListener);
        }

        this.tickCounter = 0;
        this.fullTickEvery = (NORMAL_INTERVAL + config.entityHighPriorityInterval - 1)
                / config.entityHighPriorityInterval;
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L,
                config.entityHighPriorityInterval);
    }

    public TickTimings getTimings() { return timings; }

    // ── tick ────────────────────────────────────────────────────────

    private void tick() {
        if (!config.enabled || !config.entityEnabled) return;
        if (!tpsGuard.allowHeavyWork()) return;

        boolean fullTick = tickCounter % fullTickEvery == 0;
        tickCounter++;

        long start = System.nanoTime();
        try {
            // purge offline players
            suppressedPlayers.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
            remaskTimers.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
            revealedByPlayer.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);

            for (Player observer : Bukkit.getOnlinePlayers()) {
                if (!observer.isOnline() || !config.isWorldEnabled(observer.getWorld())
                        || observer.hasPermission(config.bypassPermission)) continue;

                Map<Integer, UUID> suppressed = suppressedPlayers
                        .computeIfAbsent(observer.getUniqueId(), k -> new ConcurrentHashMap<>());
                Map<Integer, Long> timers = remaskTimers
                        .computeIfAbsent(observer.getUniqueId(), k -> new ConcurrentHashMap<>());
                Set<UUID> revealed = revealedByPlayer
                        .computeIfAbsent(observer.getUniqueId(), k -> ConcurrentHashMap.newKeySet());

                double scan = Math.max(48, config.entityLosMaxRevealDistance + 8);
                Location observerLoc = observer.getLocation();

                for (Entity e : observer.getNearbyEntities(scan, scan, scan)) {
                    if (e.getType() != EntityType.PLAYER) continue;
                    Player target = (Player) e;

                    if (!fullTick && observerLoc.distanceSquared(target.getLocation())
                            > config.entityHighPriorityRadiusSq) continue;

                    boolean visible = computeVisibility(observer, target);

                    if (visible) {
                        timers.remove(target.getEntityId());
                        revealed.add(target.getUniqueId());
                        if (suppressed.remove(target.getEntityId()) != null) {
                            showPlayer(observer, target);
                        }
                    } else {
                        if (suppressed.containsKey(target.getEntityId())) continue;

                        boolean wasEverRevealed = revealed.contains(target.getUniqueId());

                        if (wasEverRevealed) {
                            if (!config.entityRemaskLeaving) continue;

                            if (config.entityRequireCrouchToHide && target.isSneaking()) {
                                // crouching: hide instantly, no debounce
                            } else {
                                // standing at range (or non-crouch mode): use remaskDelay
                                long now = System.currentTimeMillis();
                                Long since = timers.putIfAbsent(target.getEntityId(), now);
                                if (since == null) since = now;
                                if (now - since < config.entityRemaskDelayMs) continue;
                            }
                        }

                        timers.remove(target.getEntityId());
                        suppressed.put(target.getEntityId(), target.getUniqueId());
                        sendDestroyPacket(observer, target.getEntityId());
                    }
                }
            }
        } finally {
            timings.record(System.nanoTime() - start);
        }
    }

    private boolean computeVisibility(Player observer, Player target) {
        if (config.entityRequireCrouchToHide) {
            boolean standing = !target.isSneaking();

            if (standing && config.entityCloseTrackingRadiusSq > 0) {
                double distSq = observer.getLocation().distanceSquared(target.getLocation());
                if (distSq <= config.entityCloseTrackingRadiusSq) {
                    return true;
                }
                return proximity.isEntityVisible(observer, target);
            }

            return standing || proximity.isEntityVisible(observer, target);
        }
        return proximity.isEntityVisible(observer, target);
    }

    // ── packets ────────────────────────────────────────────────────

    private void sendDestroyPacket(Player observer, int entityId) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(observer,
                new WrapperPlayServerDestroyEntities(entityId));
    }

    /**
     * Re-shows a previously suppressed player by forcing the server's entity
     * tracker to resync.  A hide+show cycle on the Bukkit API causes the
     * server to produce correct spawn + metadata packets.
     */
    private void showPlayer(Player observer, Player target) {
        observer.hideEntity(plugin, target);
        observer.showEntity(plugin, target);
    }

    // ── packet interceptor ─────────────────────────────────────────

    /**
     * Cancels server-initiated SPAWN_ENTITY and ENTITY_METADATA packets for
     * players that are currently suppressed for a given observer.
     */
    private class PlayerSpawnInterceptor extends PacketListenerAbstract {

        PlayerSpawnInterceptor() {
            super(PacketListenerPriority.NORMAL);
        }

        @Override
        public void onPacketSend(PacketSendEvent event) {
            if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
                handleSpawn(event);
            } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
                handleMetadata(event);
            }
        }

        private void handleSpawn(PacketSendEvent event) {
            WrapperPlayServerSpawnEntity wrapper = new WrapperPlayServerSpawnEntity(event);
            if (wrapper.getEntityType() != EntityTypes.PLAYER) return;

            Player observer = (Player) event.getPlayer();
            if (observer == null) return;

            Map<Integer, UUID> suppressed = suppressedPlayers.get(observer.getUniqueId());
            if (suppressed == null) return;

            UUID storedUuid = suppressed.get(wrapper.getEntityId());
            if (storedUuid != null && storedUuid.equals(wrapper.getUUID().orElse(null))) {
                event.setCancelled(true);
            }
        }

        private void handleMetadata(PacketSendEvent event) {
            Player observer = (Player) event.getPlayer();
            if (observer == null) return;

            Map<Integer, UUID> suppressed = suppressedPlayers.get(observer.getUniqueId());
            if (suppressed == null) return;

            WrapperPlayServerEntityMetadata wrapper =
                    new WrapperPlayServerEntityMetadata(event);
            if (suppressed.containsKey(wrapper.getEntityId())) {
                event.setCancelled(true);
            }
        }
    }
}

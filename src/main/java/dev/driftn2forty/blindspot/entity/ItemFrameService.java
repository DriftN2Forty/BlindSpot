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
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Packet-based visibility service for item frames and glow item frames.
 * <p>
 * {@code Player.hideEntity()} does not reliably hide item frames because the
 * server re-sends them via chunk entity tracking.  This service sends
 * DESTROY_ENTITIES packets to hide frames and uses a Bukkit
 * {@code hideEntity}/{@code showEntity} cycle to force a proper server-side
 * tracker resync when re-showing.  A packet interceptor prevents the server
 * from re-sending suppressed frames between ticks.
 */
public final class ItemFrameService {

    private static final Set<EntityType> FRAME_TYPES =
            EnumSet.of(EntityType.ITEM_FRAME, EntityType.GLOW_ITEM_FRAME);

    private final Plugin plugin;
    private final PluginConfig config;
    private final VisibilityChecker proximity;
    private final TpsThrottle tpsGuard;

    private BukkitTask task;
    private PacketListenerAbstract packetListener;

    /** player UUID → (entity ID → entity UUID) for suppressed item frames */
    private final Map<UUID, Map<Integer, UUID>> suppressedFrames = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Integer, Long>> remaskTimers = new ConcurrentHashMap<>();

    public ItemFrameService(Plugin plugin, PluginConfig config,
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

        boolean hasFrameTypes = false;
        for (EntityType t : FRAME_TYPES) {
            if (config.entitySuppressTypes.contains(t)) { hasFrameTypes = true; break; }
        }
        if (!hasFrameTypes) return;

        this.packetListener = new FrameSpawnInterceptor();
        PacketEvents.getAPI().getEventManager().registerListener(packetListener);

        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 10L);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;

        if (packetListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
            packetListener = null;
        }

        // No manual re-show needed: unregistering the interceptor above lets
        // the server's entity tracker resend frames naturally on its next tick.
        suppressedFrames.clear();
        remaskTimers.clear();
    }

    // ── tick ────────────────────────────────────────────────────────

    private void tick() {
        if (!config.enabled || !config.entityEnabled) return;
        if (!tpsGuard.allowHeavyWork()) return;

        // purge offline players
        suppressedFrames.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
        remaskTimers.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.isOnline() || !config.isWorldEnabled(p.getWorld())
                    || p.hasPermission(config.bypassPermission)) continue;

            Map<Integer, UUID> suppressed = suppressedFrames
                    .computeIfAbsent(p.getUniqueId(), k -> new ConcurrentHashMap<>());
            Map<Integer, Long> timers = remaskTimers
                    .computeIfAbsent(p.getUniqueId(), k -> new ConcurrentHashMap<>());

            double scan = Math.max(48, config.entityLosMaxRevealDistance + 8);

            for (Entity e : p.getNearbyEntities(scan, scan, scan)) {
                if (!FRAME_TYPES.contains(e.getType())) continue;
                if (!config.entitySuppressTypes.contains(e.getType())) continue;

                boolean visible = proximity.isEntityVisible(p, e);

                if (visible) {
                    timers.remove(e.getEntityId());
                    if (suppressed.remove(e.getEntityId()) != null) {
                        showFrame(p, (ItemFrame) e);
                    }
                } else {
                    if (suppressed.containsKey(e.getEntityId())) continue;

                    // debounce
                    long now = System.currentTimeMillis();
                    Long since = timers.putIfAbsent(e.getEntityId(), now);
                    if (since == null) since = now;
                    if (now - since < config.entityRemaskDelayMs) continue;

                    suppressed.put(e.getEntityId(), e.getUniqueId());
                    timers.remove(e.getEntityId());
                    sendDestroyPacket(p, e.getEntityId());
                }
            }
        }
    }

    // ── packets ────────────────────────────────────────────────────

    private void sendDestroyPacket(Player p, int entityId) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(p,
                new WrapperPlayServerDestroyEntities(entityId));
    }

    /**
     * Re-shows a previously suppressed frame by forcing the server's entity
     * tracker to resync.  A hide+show cycle on the Bukkit API makes the
     * server produce correct SPAWN_ENTITY + ENTITY_METADATA packets with
     * proper version-aware types, avoiding hardcoded metadata indices.
     */
    private void showFrame(Player p, ItemFrame frame) {
        p.hideEntity(plugin, frame);
        p.showEntity(plugin, frame);
    }

    // ── packet interceptor ─────────────────────────────────────────

    /**
     * Cancels server-initiated SPAWN_ENTITY and ENTITY_METADATA packets for
     * item frames that are currently suppressed.  Prevents the server's
     * entity tracker from re-showing or updating frames we've hidden.
     */
    private class FrameSpawnInterceptor extends PacketListenerAbstract {

        FrameSpawnInterceptor() {
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
            com.github.retrooper.packetevents.protocol.entity.type.EntityType type =
                    wrapper.getEntityType();
            if (type != EntityTypes.ITEM_FRAME && type != EntityTypes.GLOW_ITEM_FRAME) return;

            Player player = (Player) event.getPlayer();
            if (player == null) return;

            Map<Integer, UUID> suppressed = suppressedFrames.get(player.getUniqueId());
            if (suppressed == null) return;

            UUID storedUuid = suppressed.get(wrapper.getEntityId());
            if (storedUuid != null && storedUuid.equals(wrapper.getUUID().orElse(null))) {
                event.setCancelled(true);
            }
        }

        private void handleMetadata(PacketSendEvent event) {
            Player player = (Player) event.getPlayer();
            if (player == null) return;

            Map<Integer, UUID> suppressed = suppressedFrames.get(player.getUniqueId());
            if (suppressed == null) return;

            WrapperPlayServerEntityMetadata wrapper =
                    new WrapperPlayServerEntityMetadata(event);
            if (suppressed.containsKey(wrapper.getEntityId())) {
                event.setCancelled(true);
            }
        }
    }
}

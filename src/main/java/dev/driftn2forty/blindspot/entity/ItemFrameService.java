package dev.driftn2forty.blindspot.entity;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import dev.driftn2forty.blindspot.config.PluginConfig;
import dev.driftn2forty.blindspot.guard.TpsThrottle;
import dev.driftn2forty.blindspot.proximity.VisibilityChecker;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
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
 * server re-sends them via chunk entity tracking.  This service uses raw
 * packets instead: DESTROY_ENTITIES to hide and SPAWN_ENTITY + metadata to
 * show.  A packet interceptor prevents the server from re-sending suppressed
 * frames between ticks.
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

        // re-show every suppressed frame on disable
        for (Map.Entry<UUID, Map<Integer, UUID>> entry : suppressedFrames.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null || !p.isOnline()) continue;

            Set<Integer> ids = new HashSet<>(entry.getValue().keySet());
            if (ids.isEmpty()) continue;

            for (Entity e : p.getWorld().getEntities()) {
                if (!FRAME_TYPES.contains(e.getType())) continue;
                if (ids.remove(e.getEntityId())) {
                    sendSpawnPackets(p, (ItemFrame) e);
                    if (ids.isEmpty()) break;
                }
            }
        }
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
                        sendSpawnPackets(p, (ItemFrame) e);
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

    private void sendSpawnPackets(Player p, ItemFrame frame) {
        org.bukkit.Location loc = frame.getLocation();
        com.github.retrooper.packetevents.protocol.entity.type.EntityType peType =
                frame.getType() == EntityType.GLOW_ITEM_FRAME
                        ? EntityTypes.GLOW_ITEM_FRAME : EntityTypes.ITEM_FRAME;

        WrapperPlayServerSpawnEntity spawn = new WrapperPlayServerSpawnEntity(
                frame.getEntityId(),
                Optional.of(frame.getUniqueId()),
                peType,
                new Vector3d(loc.getX(), loc.getY(), loc.getZ()),
                loc.getPitch(),
                loc.getYaw(),
                loc.getYaw(),
                facingToData(frame.getFacing()),
                Optional.of(new Vector3d(0, 0, 0))
        );
        PacketEvents.getAPI().getPlayerManager().sendPacket(p, spawn);

        // item + rotation metadata (indices 8 & 9 for 1.21.x)
        List<EntityData<?>> metadata = new ArrayList<>(2);
        org.bukkit.inventory.ItemStack item = frame.getItem();
        if (item != null && item.getType() != Material.AIR) {
            metadata.add(new EntityData<>(8, EntityDataTypes.ITEMSTACK,
                    SpigotConversionUtil.fromBukkitItemStack(item)));
        }
        metadata.add(new EntityData<>(9, EntityDataTypes.INT,
                frame.getRotation().ordinal()));

        if (!metadata.isEmpty()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(p,
                    new WrapperPlayServerEntityMetadata(frame.getEntityId(), metadata));
        }
    }

    private static int facingToData(BlockFace face) {
        switch (face) {
            case DOWN:  return 0;
            case UP:    return 1;
            case NORTH: return 2;
            case SOUTH: return 3;
            case WEST:  return 4;
            case EAST:  return 5;
            default:    return 3;
        }
    }

    // ── packet interceptor ─────────────────────────────────────────

    /**
     * Cancels server-initiated SPAWN_ENTITY packets for item frames that
     * are currently suppressed.  Prevents the server's entity tracker from
     * re-showing frames we've hidden between ticks.
     */
    private class FrameSpawnInterceptor extends PacketListenerAbstract {

        FrameSpawnInterceptor() {
            super(PacketListenerPriority.NORMAL);
        }

        @Override
        public void onPacketSend(PacketSendEvent event) {
            if (event.getPacketType() != PacketType.Play.Server.SPAWN_ENTITY) return;

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
    }
}

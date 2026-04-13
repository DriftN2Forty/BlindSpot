package dev.driftn2forty.blindspot.mask;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import dev.driftn2forty.blindspot.config.PluginConfig;
import dev.driftn2forty.blindspot.guard.TpsThrottle;
import dev.driftn2forty.blindspot.proximity.VisibilityChecker;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BlockVector;

import java.util.List;

final class MapChunkPacketHandler extends PacketListenerAbstract {

    private final Plugin plugin;
    private final PluginConfig config;
    private final VisibilityChecker proximity;
    private final BlockEntityCache beCache;
    private final MaskStateTracker maskState;
    private final TpsThrottle tpsGuard;

    MapChunkPacketHandler(Plugin plugin, PluginConfig config, VisibilityChecker proximity,
                          BlockEntityCache beCache, MaskStateTracker maskState, TpsThrottle tpsGuard) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
        this.config = config;
        this.proximity = proximity;
        this.beCache = beCache;
        this.maskState = maskState;
        this.tpsGuard = tpsGuard;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.CHUNK_DATA) return;

        Player viewer = (Player) event.getPlayer();
        if (!BlockEntityMasker.shouldAffect(viewer, config)) return;

        WrapperPlayServerChunkData wrapper = new WrapperPlayServerChunkData(event);
        int chunkX = wrapper.getColumn().getX();
        int chunkZ = wrapper.getColumn().getZ();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!viewer.isOnline()) return;

            Chunk chunk = viewer.getWorld().getChunkAt(chunkX, chunkZ);
            List<BlockVector> positions = beCache.getBlockEntityPositions(chunk);
            if (positions.isEmpty()) return;

            int maskedCount = 0;
            for (BlockVector bp : positions) {
                boolean visible = proximity.isBlockVisible(viewer, bp);

                if (!visible) {
                    Location l = new Location(viewer.getWorld(),
                            bp.getBlockX(), bp.getBlockY(), bp.getBlockZ());
                    Material real = l.getBlock().getType();
                    Material ph = config.bePlaceholders.getOrDefault(real, Material.STONE);
                    viewer.sendBlockChange(l, Bukkit.createBlockData(ph));
                    maskState.setMasked(viewer.getUniqueId(), bp, true);
                    maskedCount++;
                } else {
                    maskState.setMasked(viewer.getUniqueId(), bp, false);
                }
            }

            if (config.debugVerbose && maskedCount > 0) {
                plugin.getLogger().info("[BlindSpot] MAP_CHUNK masked " + maskedCount
                        + " BE(s) for " + viewer.getName() + " at chunk "
                        + chunkX + "," + chunkZ + " (mode: " + config.beMode + ")");
            }
        });
    }
}

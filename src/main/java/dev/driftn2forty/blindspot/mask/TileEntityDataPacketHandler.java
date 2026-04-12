package dev.driftn2forty.blindspot.mask;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockEntityData;
import dev.driftn2forty.blindspot.config.PluginConfig;
import dev.driftn2forty.blindspot.proximity.VisibilityChecker;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BlockVector;

final class TileEntityDataPacketHandler extends PacketListenerAbstract {

    private final Plugin plugin;
    private final PluginConfig config;
    private final VisibilityChecker proximity;
    private final MaskStateTracker maskState;

    TileEntityDataPacketHandler(Plugin plugin, PluginConfig config,
                                VisibilityChecker proximity, MaskStateTracker maskState) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
        this.config = config;
        this.proximity = proximity;
        this.maskState = maskState;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.BLOCK_ENTITY_DATA) return;

        Player viewer = (Player) event.getPlayer();
        if (!BlockEntityMasker.shouldAffect(viewer, config)) return;

        WrapperPlayServerBlockEntityData wrapper = new WrapperPlayServerBlockEntityData(event);
        Vector3i pos = wrapper.getPosition();
        BlockVector bv = new BlockVector(pos.getX(), pos.getY(), pos.getZ());

        boolean masked = maskState.isMaskedFor(viewer.getUniqueId(), bv);
        if (masked) {
            event.setCancelled(true);
            if (config.debugVerbose) {
                plugin.getLogger().info("[BlindSpot] Cancelled BLOCK_ENTITY_DATA (masked) for "
                        + viewer.getName() + " at " + pos.getX() + "," + pos.getY() + "," + pos.getZ());
            }
            return;
        }

        boolean visible = proximity.isBlockVisible(viewer, bv);

        if (!visible) {
            event.setCancelled(true);
            if (config.debugVerbose) {
                plugin.getLogger().info("[BlindSpot] Cancelled BLOCK_ENTITY_DATA (out-of-LOS) for "
                        + viewer.getName() + " at " + pos.getX() + "," + pos.getY() + "," + pos.getZ());
            }
        }
    }
}

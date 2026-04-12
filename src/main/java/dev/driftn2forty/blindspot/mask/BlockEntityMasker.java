package dev.driftn2forty.blindspot.mask;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import dev.driftn2forty.blindspot.config.PluginConfig;
import dev.driftn2forty.blindspot.guard.TpsThrottle;
import dev.driftn2forty.blindspot.proximity.VisibilityChecker;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class BlockEntityMasker {

    private final Plugin plugin;
    private final PluginConfig config;
    private final VisibilityChecker proximity;
    private final BlockEntityCache beCache;
    private final MaskStateTracker maskState;
    private final TpsThrottle tpsGuard;
    private PacketListenerAbstract mapChunkListener;
    private PacketListenerAbstract beDataListener;

    public BlockEntityMasker(Plugin plugin, PluginConfig config,
                             VisibilityChecker proximity, BlockEntityCache beCache,
                             MaskStateTracker maskState, TpsThrottle tpsGuard) {
        this.plugin = plugin;
        this.config = config;
        this.proximity = proximity;
        this.beCache = beCache;
        this.maskState = maskState;
        this.tpsGuard = tpsGuard;
    }

    public void register() {
        unregister();
        if (!config.enabled || !config.beEnabled) return;

        this.mapChunkListener = new MapChunkPacketHandler(plugin, config, proximity,
                beCache, maskState, tpsGuard);
        PacketEvents.getAPI().getEventManager().registerListener(mapChunkListener);

        this.beDataListener = new TileEntityDataPacketHandler(plugin, config, proximity, maskState);
        PacketEvents.getAPI().getEventManager().registerListener(beDataListener);
    }

    public void unregister() {
        if (mapChunkListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(mapChunkListener);
            mapChunkListener = null;
        }
        if (beDataListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(beDataListener);
            beDataListener = null;
        }
    }

    static boolean shouldAffect(Player p, PluginConfig config) {
        if (p == null || !p.isOnline()) return false;
        if (!config.enabled || !config.beEnabled) return false;
        if (!config.isWorldEnabled(p.getWorld())) return false;
        return !p.hasPermission(config.bypassPermission);
    }
}

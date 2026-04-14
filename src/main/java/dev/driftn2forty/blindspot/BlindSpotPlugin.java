package dev.driftn2forty.blindspot;

import dev.driftn2forty.blindspot.command.ReloadCommand;
import dev.driftn2forty.blindspot.config.PluginConfig;
import dev.driftn2forty.blindspot.entity.EntityScanCache;
import dev.driftn2forty.blindspot.entity.EntityVisibilityService;
import dev.driftn2forty.blindspot.entity.HangingChangeListener;
import dev.driftn2forty.blindspot.entity.ItemFrameVisibilityService;
import dev.driftn2forty.blindspot.entity.PlayerVisibilityService;
import dev.driftn2forty.blindspot.guard.TpsGuard;
import dev.driftn2forty.blindspot.mask.BlockEntityMasker;
import dev.driftn2forty.blindspot.mask.BlockChangeListener;
import dev.driftn2forty.blindspot.mask.ChunkBECache;
import dev.driftn2forty.blindspot.mask.ChunkBlockCache;
import dev.driftn2forty.blindspot.mask.PlayerMaskState;
import dev.driftn2forty.blindspot.nms.NmsChunkScanner;
import dev.driftn2forty.blindspot.proximity.BlockEntityVisibilityService;
import dev.driftn2forty.blindspot.proximity.BlockVisibilityService;
import dev.driftn2forty.blindspot.proximity.PlayerDeltaTracker;
import dev.driftn2forty.blindspot.proximity.ProximityService;
import dev.driftn2forty.blindspot.proximity.RaycastCache;
import dev.driftn2forty.blindspot.util.TickTimings;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class BlindSpotPlugin extends JavaPlugin {

    private PluginConfig pluginConfig;
    private ProximityService proximityService;
    private ChunkBECache beCache;
    private PlayerMaskState maskState;
    private BlockEntityMasker blockEntityMasker;
    private EntityVisibilityService entityVisibilityService;
    private ItemFrameVisibilityService itemFrameVisibilityService;
    private PlayerVisibilityService playerVisibilityService;
    private TpsGuard tpsGuard;
    private BlockEntityVisibilityService blockEntityVisibilityService;
    private NmsChunkScanner nmsChunkScanner;
    private ChunkBlockCache scanCache;
    private BlockVisibilityService blockVisibilityService;
    private EntityScanCache entityScanCache;
    private PlayerDeltaTracker deltaTracker;
    private RaycastCache raycastCache;

    @Override
    public void onEnable() {

        saveDefaultConfig();
        this.pluginConfig = new PluginConfig(this);
        this.raycastCache = new RaycastCache();
        this.proximityService = new ProximityService(this.pluginConfig, this.raycastCache);
        this.beCache = new ChunkBECache(this.pluginConfig);
        this.maskState = new PlayerMaskState();
        this.tpsGuard = new TpsGuard(this.pluginConfig);
        this.entityScanCache = new EntityScanCache();
        this.deltaTracker = new PlayerDeltaTracker();

        // bStats metrics
        Metrics metrics = new Metrics(this, 30722);
        metrics.addCustomChart(new SimplePie("be_visibility_mode", () -> switch (this.pluginConfig.beMode) {
            case 1 -> "Proximity";
            case 2 -> "Line of Sight";
            case 3 -> "Proximity or LOS";
            default -> "Unknown";
        }));
        metrics.addCustomChart(new SimplePie("entity_visibility_mode", () -> switch (this.pluginConfig.entityMode) {
            case 1 -> "Proximity";
            case 2 -> "Line of Sight";
            case 3 -> "Proximity or LOS";
            default -> "Unknown";
        }));
        metrics.addCustomChart(new SimplePie("entity_trace_mode", () -> switch (this.pluginConfig.ptEntityTraceMode) {
            case 1 -> "Center";
            case 2 -> "Face Centers";
            case 3 -> "Corners";
            case 4 -> "Face Centers + Corners";
            default -> "Unknown";
        }));
        metrics.addCustomChart(new SimplePie("pt_blocks_enabled", () -> String.valueOf(this.pluginConfig.ptBlocksEnabled)));
        metrics.addCustomChart(new SimplePie("pt_entities_enabled", () -> String.valueOf(this.pluginConfig.ptEntitiesEnabled)));
        metrics.addCustomChart(new SimplePie("pt_max_retrace", () -> String.valueOf(this.pluginConfig.ptMaxRetrace)));

        if (Bukkit.getPluginManager().getPlugin("packetevents") == null) {
            getLogger().severe("packetevents is required. Disabling BlindSpot.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        this.blockEntityMasker = new BlockEntityMasker(this, this.pluginConfig,
                this.proximityService, this.beCache, this.maskState, this.tpsGuard);
        this.blockEntityMasker.register();

        // NMS scan-block system (non-block-entity masking)
        this.nmsChunkScanner = new NmsChunkScanner(getLogger());
        this.scanCache = new ChunkBlockCache(this.pluginConfig, this.nmsChunkScanner);

        Bukkit.getPluginManager().registerEvents(
                new BlockChangeListener(this.pluginConfig, this.beCache, this.scanCache,
                        this.deltaTracker, this.raycastCache), this);
        Bukkit.getPluginManager().registerEvents(
                new HangingChangeListener(this.pluginConfig, this.deltaTracker), this);

        this.entityVisibilityService = new EntityVisibilityService(this, this.pluginConfig,
                this.proximityService, this.tpsGuard, this.entityScanCache);
        this.entityVisibilityService.start();

        this.itemFrameVisibilityService = new ItemFrameVisibilityService(this, this.pluginConfig,
                this.proximityService, this.tpsGuard, this.entityScanCache, this.deltaTracker);
        this.itemFrameVisibilityService.start();

        this.playerVisibilityService = new PlayerVisibilityService(this, this.pluginConfig,
                this.proximityService, this.tpsGuard, this.entityScanCache);
        this.playerVisibilityService.start();

        this.blockEntityVisibilityService = new BlockEntityVisibilityService(this, this.pluginConfig, this.proximityService,
                this.beCache, this.maskState, this.tpsGuard, this.deltaTracker);
        this.blockEntityVisibilityService.start();

        if (this.nmsChunkScanner.isAvailable() && this.pluginConfig.scanEnabled) {
            this.blockVisibilityService = new BlockVisibilityService(this, this.pluginConfig,
                    this.proximityService, this.scanCache, this.maskState, this.tpsGuard,
                    this.deltaTracker);
            this.blockVisibilityService.start();
        }

        ReloadCommand reloadCmd = new ReloadCommand(this);
        getCommand("blindspot").setExecutor(reloadCmd);
        getCommand("blindspot").setTabCompleter(reloadCmd);
        getLogger().info("BlindSpot enabled.");
    }

    @Override
    public void onDisable() {
        if (this.blockEntityMasker != null) {
            this.blockEntityMasker.unregister();
        }
        if (this.entityVisibilityService != null) {
            this.entityVisibilityService.stop();
        }
        if (this.itemFrameVisibilityService != null) {
            this.itemFrameVisibilityService.stop();
        }
        if (this.playerVisibilityService != null) {
            this.playerVisibilityService.stop();
        }
        if (this.blockEntityVisibilityService != null) {
            this.blockEntityVisibilityService.stop();
        }
        if (this.blockVisibilityService != null) {
            this.blockVisibilityService.stop();
        }
        getLogger().info("BlindSpot disabled.");
    }

    public void reloadBlindSpot() {
        reloadConfig();
        this.pluginConfig.reload();
        this.beCache.clear();
        if (this.scanCache != null) {
            this.scanCache.clear();
        }

        if (this.blockEntityMasker != null) {
            this.blockEntityMasker.unregister();
            this.blockEntityMasker.register();
        }
        if (this.entityVisibilityService != null) {
            this.entityVisibilityService.restart();
        }
        if (this.itemFrameVisibilityService != null) {
            this.itemFrameVisibilityService.restart();
        }
        if (this.playerVisibilityService != null) {
            this.playerVisibilityService.restart();
        }
        if (this.blockEntityVisibilityService != null) {
            this.blockEntityVisibilityService.restart();
        }
        if (this.blockVisibilityService != null) {
            this.blockVisibilityService.restart();
        }
        getLogger().info("BlindSpot config reloaded.");
    }

    public PluginConfig getPluginConfig() {
        return this.pluginConfig;
    }

    public TickTimings[] getEntityTimings() {
        return new TickTimings[]{
                entityVisibilityService != null ? entityVisibilityService.getTimings() : null,
                itemFrameVisibilityService != null ? itemFrameVisibilityService.getTimings() : null,
                playerVisibilityService != null ? playerVisibilityService.getTimings() : null
        };
    }

    public TickTimings getBlockEntityTimings() {
        return blockEntityVisibilityService != null ? blockEntityVisibilityService.getTimings() : null;
    }

    public TickTimings getScanBlockTimings() {
        return blockVisibilityService != null ? blockVisibilityService.getTimings() : null;
    }
}

package dev.driftn2forty.blindspot;

import dev.driftn2forty.blindspot.command.ReloadCommand;
import dev.driftn2forty.blindspot.config.PluginConfig;
import dev.driftn2forty.blindspot.entity.EntityVisibilityService;
import dev.driftn2forty.blindspot.entity.ItemFrameVisibilityService;
import dev.driftn2forty.blindspot.guard.TpsGuard;
import dev.driftn2forty.blindspot.mask.BlockEntityMasker;
import dev.driftn2forty.blindspot.mask.BlockChangeListener;
import dev.driftn2forty.blindspot.mask.ChunkBECache;
import dev.driftn2forty.blindspot.mask.PlayerMaskState;
import dev.driftn2forty.blindspot.proximity.BlockEntityVisibilityService;
import dev.driftn2forty.blindspot.proximity.ProximityService;
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
    private TpsGuard tpsGuard;
    private BlockEntityVisibilityService blockEntityVisibilityService;

    @Override
    public void onEnable() {

        saveDefaultConfig();
        this.pluginConfig = new PluginConfig(this);
        this.proximityService = new ProximityService(this.pluginConfig);
        this.beCache = new ChunkBECache(this.pluginConfig);
        this.maskState = new PlayerMaskState();
        this.tpsGuard = new TpsGuard(this.pluginConfig);

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

        Bukkit.getPluginManager().registerEvents(
                new BlockChangeListener(this.pluginConfig, this.beCache), this);

        this.entityVisibilityService = new EntityVisibilityService(this, this.pluginConfig,
                this.proximityService, this.tpsGuard);
        this.entityVisibilityService.start();

        this.itemFrameVisibilityService = new ItemFrameVisibilityService(this, this.pluginConfig,
                this.proximityService, this.tpsGuard);
        this.itemFrameVisibilityService.start();

        this.blockEntityVisibilityService = new BlockEntityVisibilityService(this, this.pluginConfig, this.proximityService,
                this.beCache, this.maskState, this.tpsGuard);
        this.blockEntityVisibilityService.start();

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
        if (this.blockEntityVisibilityService != null) {
            this.blockEntityVisibilityService.stop();
        }
        getLogger().info("BlindSpot disabled.");
    }

    public void reloadBlindSpot() {
        reloadConfig();
        this.pluginConfig.reload();
        this.beCache.clear();

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
        if (this.blockEntityVisibilityService != null) {
            this.blockEntityVisibilityService.restart();
        }
        getLogger().info("BlindSpot config reloaded.");
    }

    public PluginConfig getPluginConfig() {
        return this.pluginConfig;
    }

    public TickTimings[] getEntityTimings() {
        return new TickTimings[]{
                entityVisibilityService != null ? entityVisibilityService.getTimings() : null,
                itemFrameVisibilityService != null ? itemFrameVisibilityService.getTimings() : null
        };
    }

    public TickTimings getBlockEntityTimings() {
        return blockEntityVisibilityService != null ? blockEntityVisibilityService.getTimings() : null;
    }
}

package dev.driftn2forty.blindspot;

import dev.driftn2forty.blindspot.command.ReloadCommand;
import dev.driftn2forty.blindspot.config.PluginConfig;
import dev.driftn2forty.blindspot.entity.EntityVisibilityService;
import dev.driftn2forty.blindspot.entity.ItemFrameService;
import dev.driftn2forty.blindspot.guard.TpsGuard;
import dev.driftn2forty.blindspot.mask.BlockEntityMasker;
import dev.driftn2forty.blindspot.mask.BlockChangeListener;
import dev.driftn2forty.blindspot.mask.ChunkBECache;
import dev.driftn2forty.blindspot.mask.PlayerMaskState;
import dev.driftn2forty.blindspot.proximity.MovementRevealer;
import dev.driftn2forty.blindspot.proximity.ProximityService;
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
    private ItemFrameService itemFrameService;
    private TpsGuard tpsGuard;
    private MovementRevealer movementRevealer;

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
            case 1 -> "Bounding Box";
            case 2 -> "Eye Location";
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

        this.itemFrameService = new ItemFrameService(this, this.pluginConfig,
                this.proximityService, this.tpsGuard);
        this.itemFrameService.start();

        this.movementRevealer = new MovementRevealer(this, this.pluginConfig, this.proximityService,
                this.beCache, this.maskState, this.tpsGuard);
        this.movementRevealer.start();

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
        if (this.itemFrameService != null) {
            this.itemFrameService.stop();
        }
        if (this.movementRevealer != null) {
            this.movementRevealer.stop();
        }
        getLogger().info("BlindSpot disabled.");
    }

    public void reloadBlindSpot() {
        reloadConfig();
        this.pluginConfig.reload();
        this.beCache.clear();
        this.maskState.clearAll();

        if (this.blockEntityMasker != null) {
            this.blockEntityMasker.unregister();
            this.blockEntityMasker.register();
        }
        if (this.entityVisibilityService != null) {
            this.entityVisibilityService.stop();
            this.entityVisibilityService.start();
        }
        if (this.itemFrameService != null) {
            this.itemFrameService.stop();
            this.itemFrameService.start();
        }
        if (this.movementRevealer != null) {
            this.movementRevealer.stop();
            this.movementRevealer.start();
        }
        getLogger().info("BlindSpot config reloaded.");
    }

    public PluginConfig getPluginConfig() {
        return this.pluginConfig;
    }
}

package dev.driftn2forty.blindspot.config;

import dev.driftn2forty.blindspot.util.MoreMaterials;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.stream.Collectors;

public final class PluginConfig {

    private final Plugin plugin;

    public boolean enabled;
    public Set<String> includeWorlds;
    public Set<String> excludeWorlds;

    public boolean beEnabled;
    public int beRevealRadius;
    public boolean beRemaskLeaving;
    public long beRemaskDelayMs;
    public int beMode;
    public int beLosMaxRevealDistance;
    public int beBlockTraceMode;
    public int beTraceModeFallbackDistance;
    public double beTraceModeFallbackDistanceSq;
    public Map<Material, Material> bePlaceholders;
    public Set<Material> beMaskMaterials;

    public boolean entityEnabled;
    public int entityRevealRadius;
    public int entityMode;
    public boolean entityRemaskLeaving;
    public long entityRemaskDelayMs;
    public int entityLosMaxRevealDistance;
    public Set<EntityType> entitySuppressTypes;
    public int entityTraceModeFallbackDistance;
    public double entityTraceModeFallbackDistanceSq;
    public boolean entityRequireCrouchToHide;
    public int entityCloseTrackingRadius;
    public double entityCloseTrackingRadiusSq;

    public int beHighPriorityRadius;
    public double beHighPriorityRadiusSq;
    public int beHighPriorityInterval;

    public int entityHighPriorityRadius;
    public double entityHighPriorityRadiusSq;
    public int entityHighPriorityInterval;

    public boolean tpsGuardEnabled;
    public double tpsGuardMin;
    public String bypassPermission;
    public boolean debugVerbose;

    public boolean ptBlocksEnabled;
    public boolean ptEntitiesEnabled;
    public int ptMaxRetrace;
    public int ptEntityTraceMode;
    public Set<Material> ptMaterials;

    public boolean scanEnabled;
    public int scanMode;
    public int scanRevealRadius;
    public boolean scanRemaskLeaving;
    public long scanRemaskDelayMs;
    public int scanLosMaxRevealDistance;
    public int scanBlockTraceMode;
    public int scanTraceModeFallbackDistance;
    public double scanTraceModeFallbackDistanceSq;
    public int scanHighPriorityRadius;
    public double scanHighPriorityRadiusSq;
    public int scanHighPriorityInterval;
    public Map<Material, Material> scanPlaceholders;
    public Set<Material> scanMaterials;

    public boolean perfEntityScanCache;
    public boolean perfRaycastCache;
    public int perfDeltaTrackerSensitivity;

    public PluginConfig(Plugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        this.enabled = cfg.getBoolean("enabled", true);
        this.debugVerbose = cfg.getBoolean("debug.verbose", false);
        this.includeWorlds = new HashSet<>(cfg.getStringList("worlds.include"));
        this.excludeWorlds = new HashSet<>(cfg.getStringList("worlds.exclude"));

        this.beEnabled = cfg.getBoolean("blockEntities.enabled", true);
        this.beRevealRadius = Math.max(2, cfg.getInt("blockEntities.revealRadius", 12));
        this.beRemaskLeaving = cfg.getBoolean("blockEntities.remaskWhenLeaving", true);
        this.beRemaskDelayMs = Math.max(0, cfg.getInt("blockEntities.remaskDelay", 10)) * 1000L;
        this.beMode = clampMode(cfg.getInt("blockEntities.mode", 2));
        this.beLosMaxRevealDistance = Math.max(8, cfg.getInt("blockEntities.losMaxRevealDistance", 120));
        this.beBlockTraceMode = Math.max(1, Math.min(4, cfg.getInt("blockEntities.blockTraceMode", 2)));
        this.beTraceModeFallbackDistance = Math.max(0, cfg.getInt("blockEntities.traceModeFallbackDistance", 48));
        this.beTraceModeFallbackDistanceSq = (double) beTraceModeFallbackDistance * beTraceModeFallbackDistance;
        this.beHighPriorityRadius = Math.max(1, cfg.getInt("blockEntities.tickPriority.highPriorityRadius", 24));
        this.beHighPriorityInterval = Math.min(8, Math.max(1, cfg.getInt("blockEntities.tickPriority.highPriorityInterval", 4)));
        this.beHighPriorityRadiusSq = (double) beHighPriorityRadius * beHighPriorityRadius;

        this.bePlaceholders = new EnumMap<>(Material.class);
        ConfigurationSection ph = cfg.getConfigurationSection("blockEntities.placeholders");
        if (ph != null) {
            for (String k : ph.getKeys(false)) {
                Material from = MoreMaterials.match(k);
                Material to = MoreMaterials.match(ph.getString(k, "STONE"));
                if (from != null && to != null && to.isBlock()) {
                    this.bePlaceholders.put(from, to);
                }
            }
        }

        this.beMaskMaterials = EnumSet.noneOf(Material.class);
        for (String name : cfg.getStringList("blockEntities.maskMaterials")) {
            Material m = MoreMaterials.match(name);
            if (m != null) {
                this.beMaskMaterials.add(m);
            } else if (debugVerbose) {
                plugin.getLogger().warning("[config] Unknown maskMaterial: " + name);
            }
        }

        this.entityEnabled = cfg.getBoolean("entities.enabled", true);
        this.entityRevealRadius = Math.max(4, cfg.getInt("entities.revealRadius", 12));
        this.entityMode = clampMode(cfg.getInt("entities.mode", 2));
        this.entityRemaskLeaving = cfg.getBoolean("entities.remaskWhenLeaving", true);
        this.entityRemaskDelayMs = Math.max(0, cfg.getInt("entities.remaskDelay", 10)) * 1000L;
        this.entityLosMaxRevealDistance = Math.max(8, cfg.getInt("entities.losMaxRevealDistance", 120));
        Set<EntityType> parsedTypes = EnumSet.noneOf(EntityType.class);
        for (String s : cfg.getStringList("entities.suppressTypes")) {
            try { parsedTypes.add(EntityType.valueOf(s)); } catch (IllegalArgumentException ignored) {}
        }
        this.entitySuppressTypes = Collections.unmodifiableSet(parsedTypes);
        this.entityRequireCrouchToHide = cfg.getBoolean("entities.requireCrouchToHide", false);
        this.entityCloseTrackingRadius = Math.max(0, cfg.getInt("entities.closeTrackingRadius", 0));
        this.entityCloseTrackingRadiusSq = (double) entityCloseTrackingRadius * entityCloseTrackingRadius;
        this.entityTraceModeFallbackDistance = Math.max(0, cfg.getInt("entities.traceModeFallbackDistance", 48));
        this.entityTraceModeFallbackDistanceSq = (double) entityTraceModeFallbackDistance * entityTraceModeFallbackDistance;
        this.entityHighPriorityRadius = Math.max(1, cfg.getInt("entities.tickPriority.highPriorityRadius", 24));
        this.entityHighPriorityInterval = Math.min(10, Math.max(1, cfg.getInt("entities.tickPriority.highPriorityInterval", 5)));
        this.entityHighPriorityRadiusSq = (double) entityHighPriorityRadius * entityHighPriorityRadius;

        this.tpsGuardEnabled = cfg.getBoolean("tpsGuard.enabled", true);
        this.tpsGuardMin = cfg.getDouble("tpsGuard.minTps", 18.5);
        this.bypassPermission = cfg.getString("permissions.bypass", "blindspot.bypass");

        this.perfEntityScanCache = cfg.getBoolean("performance.entityScanCache", true);
        this.perfRaycastCache = cfg.getBoolean("performance.raycastCache", true);
        this.perfDeltaTrackerSensitivity = Math.max(0, Math.min(3, cfg.getInt("performance.deltaTrackerSensitivity", 2)));

        this.ptBlocksEnabled = cfg.getBoolean("losPassthrough.enableBlocks", true);
        this.ptEntitiesEnabled = cfg.getBoolean("losPassthrough.enableEntities", true);
        this.ptMaxRetrace = Math.max(0, Math.min(10, cfg.getInt("losPassthrough.maxRetrace", 5)));
        this.ptEntityTraceMode = Math.max(1, Math.min(4, cfg.getInt("entities.entityTraceMode", 3)));
        this.ptMaterials = EnumSet.noneOf(Material.class);
        for (String name : cfg.getStringList("losPassthrough.materials")) {
            Material m = MoreMaterials.match(name);
            if (m != null) {
                this.ptMaterials.add(m);
            } else if (debugVerbose) {
                plugin.getLogger().warning("[config] Unknown passthrough material: " + name);
            }
        }

        // --- Scan Blocks (non-block-entity masking via NMS palette scan) ---
        this.scanEnabled = cfg.getBoolean("scanBlocks.enabled", true);
        this.scanMode = clampMode(cfg.getInt("scanBlocks.mode", 2));
        this.scanRevealRadius = Math.max(2, cfg.getInt("scanBlocks.revealRadius", 12));
        this.scanRemaskLeaving = cfg.getBoolean("scanBlocks.remaskWhenLeaving", true);
        this.scanRemaskDelayMs = Math.max(0, cfg.getInt("scanBlocks.remaskDelay", 2)) * 1000L;
        this.scanLosMaxRevealDistance = Math.max(8, cfg.getInt("scanBlocks.losMaxRevealDistance", 120));
        this.scanBlockTraceMode = Math.max(1, Math.min(4, cfg.getInt("scanBlocks.blockTraceMode", 2)));
        this.scanTraceModeFallbackDistance = Math.max(0, cfg.getInt("scanBlocks.traceModeFallbackDistance", 48));
        this.scanTraceModeFallbackDistanceSq = (double) scanTraceModeFallbackDistance * scanTraceModeFallbackDistance;
        this.scanHighPriorityRadius = Math.max(1, cfg.getInt("scanBlocks.tickPriority.highPriorityRadius", 16));
        this.scanHighPriorityInterval = Math.min(8, Math.max(1, cfg.getInt("scanBlocks.tickPriority.highPriorityInterval", 2)));
        this.scanHighPriorityRadiusSq = (double) scanHighPriorityRadius * scanHighPriorityRadius;

        this.scanPlaceholders = new EnumMap<>(Material.class);
        ConfigurationSection scanPh = cfg.getConfigurationSection("scanBlocks.placeholders");
        if (scanPh != null) {
            for (String k : scanPh.getKeys(false)) {
                Material from = MoreMaterials.match(k);
                Material to = MoreMaterials.match(scanPh.getString(k, "STONE"));
                if (from != null && to != null && to.isBlock()) {
                    this.scanPlaceholders.put(from, to);
                }
            }
        }

        this.scanMaterials = EnumSet.noneOf(Material.class);
        for (String name : cfg.getStringList("scanBlocks.materials")) {
            Material m = MoreMaterials.match(name);
            if (m != null) {
                this.scanMaterials.add(m);
            } else if (debugVerbose) {
                plugin.getLogger().warning("[config] Unknown scan material: " + name);
            }
        }
    }

    public boolean isWorldEnabled(World world) {
        if (world == null) return false;
        if (excludeWorlds.contains(world.getName())) return false;
        if (includeWorlds.isEmpty()) return true;
        return includeWorlds.contains(world.getName());
    }

    private static int clampMode(int value) {
        return Math.max(1, Math.min(3, value));
    }
}

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
    public Map<Material, Material> bePlaceholders;
    public Set<Material> beMaskMaterials;

    public boolean entityEnabled;
    public int entityRevealRadius;
    public int entityMode;
    public boolean entityRemaskLeaving;
    public long entityRemaskDelayMs;
    public int entityLosMaxRevealDistance;
    public Set<EntityType> entitySuppressTypes;

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
        this.entityHighPriorityRadius = Math.max(1, cfg.getInt("entities.tickPriority.highPriorityRadius", 24));
        this.entityHighPriorityInterval = Math.min(10, Math.max(1, cfg.getInt("entities.tickPriority.highPriorityInterval", 5)));
        this.entityHighPriorityRadiusSq = (double) entityHighPriorityRadius * entityHighPriorityRadius;

        this.tpsGuardEnabled = cfg.getBoolean("tpsGuard.enabled", true);
        this.tpsGuardMin = cfg.getDouble("tpsGuard.minTps", 18.5);
        this.bypassPermission = cfg.getString("permissions.bypass", "blindspot.bypass");

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

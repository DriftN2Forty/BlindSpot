package dev.driftn2forty.blindspot.mask;

import dev.driftn2forty.blindspot.config.PluginConfig;
import org.bukkit.Chunk;
import org.bukkit.block.BlockState;
import org.bukkit.util.BlockVector;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class ChunkBECache implements BlockEntityCache {

    private final PluginConfig config;
    private final Map<String, List<BlockVector>> cache = new ConcurrentHashMap<>();

    public ChunkBECache(PluginConfig config) {
        this.config = config;
    }

    public void clear() {
        cache.clear();
    }

    @Override
    public void invalidate(Chunk chunk) {
        cache.remove(key(chunk));
    }

    private String key(Chunk c) {
        return c.getWorld().getName() + ":" + c.getX() + ":" + c.getZ();
    }

    public List<BlockVector> getBlockEntityPositions(Chunk chunk) {
        if (!config.isWorldEnabled(chunk.getWorld())) return List.of();
        String k = key(chunk);
        return cache.computeIfAbsent(k, kk -> compute(chunk));
    }

    private List<BlockVector> compute(Chunk chunk) {
        try {
            BlockState[] states = chunk.getTileEntities();
            if (states == null || states.length == 0) return List.of();
            return Arrays.stream(states)
                    .filter(Objects::nonNull)
                    .filter(bs -> config.beMaskMaterials.contains(bs.getType()))
                    .map(bs -> new BlockVector(bs.getX(), bs.getY(), bs.getZ()))
                    .collect(Collectors.toUnmodifiableList());
        } catch (Throwable t) {
            return List.of();
        }
    }
}

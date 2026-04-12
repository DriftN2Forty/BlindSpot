package dev.driftn2forty.blindspot.mask;

import dev.driftn2forty.blindspot.config.PluginConfig;
import dev.driftn2forty.blindspot.nms.NmsChunkScanner;
import org.bukkit.Chunk;
import org.bukkit.util.BlockVector;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache of non-block-entity block positions discovered via NMS palette scanning.
 * Same lifecycle pattern as {@link ChunkBECache}: lazy compute-on-first-access,
 * invalidate on block place/break.
 */
public final class ChunkBlockCache implements BlockEntityCache {

    private final PluginConfig config;
    private final NmsChunkScanner scanner;
    private final Map<String, List<BlockVector>> cache = new ConcurrentHashMap<>();

    public ChunkBlockCache(PluginConfig config, NmsChunkScanner scanner) {
        this.config = config;
        this.scanner = scanner;
    }

    @Override
    public List<BlockVector> getBlockEntityPositions(Chunk chunk) {
        if (!scanner.isAvailable()) return Collections.emptyList();
        if (!config.isWorldEnabled(chunk.getWorld())) return Collections.emptyList();
        return cache.computeIfAbsent(key(chunk), k -> compute(chunk));
    }

    @Override
    public void invalidate(Chunk chunk) {
        cache.remove(key(chunk));
    }

    @Override
    public void clear() {
        cache.clear();
    }

    private String key(Chunk c) {
        return c.getWorld().getName() + ":" + c.getX() + ":" + c.getZ();
    }

    private List<BlockVector> compute(Chunk chunk) {
        try {
            return scanner.scan(chunk, config.scanMaterials);
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }
}

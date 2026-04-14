package dev.driftn2forty.blindspot.proximity;

import org.bukkit.Bukkit;
import org.bukkit.util.BlockVector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-TTL cache for block-visibility raycast results. Avoids redundant
 * raycasts for blocks that were already checked for the same player from
 * approximately the same position and look direction.
 * <p>
 * Cache key: (player UUID, player block position, yaw/pitch bucket, block position).
 * The cache auto-invalidates per player when their block position changes or
 * they rotate beyond the bucket threshold, and globally when the server tick
 * advances beyond the TTL.
 */
public final class RaycastCache {

    /** Number of ticks a cached result is considered valid. */
    private static final int TTL_TICKS = 6;

    /** Yaw/pitch are bucketed to this many degrees to allow minor rotations. */
    private static final int ANGLE_BUCKET = 5;

    private final Map<UUID, PlayerCache> perPlayer = new ConcurrentHashMap<>();

    /**
     * Returns the cached visibility result, or {@code null} if not cached.
     */
    public Boolean get(UUID playerId, int playerBlockX, int playerBlockY, int playerBlockZ,
                       float yaw, float pitch, BlockVector blockPos) {
        PlayerCache pc = perPlayer.get(playerId);
        if (pc == null) return null;

        int currentTick = Bukkit.getCurrentTick();
        if (currentTick - pc.createdTick > TTL_TICKS) {
            perPlayer.remove(playerId);
            return null;
        }

        // Invalidate if player crossed a block boundary or rotated significantly
        if (pc.blockX != playerBlockX || pc.blockY != playerBlockY || pc.blockZ != playerBlockZ
                || pc.yawBucket != bucket(yaw) || pc.pitchBucket != bucket(pitch)) {
            perPlayer.remove(playerId);
            return null;
        }

        return pc.results.get(blockPos);
    }

    /**
     * Stores a visibility result in the cache.
     */
    public void put(UUID playerId, int playerBlockX, int playerBlockY, int playerBlockZ,
                    float yaw, float pitch, BlockVector blockPos, boolean visible) {
        int currentTick = Bukkit.getCurrentTick();
        int yBucket = bucket(yaw);
        int pBucket = bucket(pitch);

        PlayerCache pc = perPlayer.get(playerId);
        if (pc == null || currentTick - pc.createdTick > TTL_TICKS
                || pc.blockX != playerBlockX || pc.blockY != playerBlockY || pc.blockZ != playerBlockZ
                || pc.yawBucket != yBucket || pc.pitchBucket != pBucket) {
            pc = new PlayerCache(playerBlockX, playerBlockY, playerBlockZ,
                    yBucket, pBucket, currentTick);
            perPlayer.put(playerId, pc);
        }

        pc.results.put(blockPos, visible);
    }

    /** Remove tracking for a player (e.g. on disconnect). */
    public void remove(UUID playerId) {
        perPlayer.remove(playerId);
    }

    private static int bucket(float angle) {
        return Math.floorDiv((int) angle, ANGLE_BUCKET);
    }

    private static final class PlayerCache {
        final int blockX, blockY, blockZ;
        final int yawBucket, pitchBucket;
        final int createdTick;
        final Map<BlockVector, Boolean> results = new HashMap<>();

        PlayerCache(int blockX, int blockY, int blockZ,
                    int yawBucket, int pitchBucket, int createdTick) {
            this.blockX = blockX;
            this.blockY = blockY;
            this.blockZ = blockZ;
            this.yawBucket = yawBucket;
            this.pitchBucket = pitchBucket;
            this.createdTick = createdTick;
        }
    }
}

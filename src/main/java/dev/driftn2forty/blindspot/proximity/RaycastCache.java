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
    public Boolean get(UUID playerId, double eyeX, double eyeY, double eyeZ,
                       float yaw, float pitch, BlockVector blockPos) {
        PlayerCache pc = perPlayer.get(playerId);
        if (pc == null) return null;

        int currentTick = Bukkit.getCurrentTick();
        if (currentTick - pc.createdTick > TTL_TICKS) {
            perPlayer.remove(playerId);
            return null;
        }

        // Invalidate if player moved beyond half-block bucket or rotated significantly
        if (pc.halfX != halfBlock(eyeX) || pc.halfY != halfBlock(eyeY) || pc.halfZ != halfBlock(eyeZ)
                || pc.yawBucket != bucket(yaw) || pc.pitchBucket != bucket(pitch)) {
            perPlayer.remove(playerId);
            return null;
        }

        return pc.results.get(blockPos);
    }

    /**
     * Stores a visibility result in the cache.
     */
    public void put(UUID playerId, double eyeX, double eyeY, double eyeZ,
                    float yaw, float pitch, BlockVector blockPos, boolean visible) {
        int currentTick = Bukkit.getCurrentTick();
        int yBucket = bucket(yaw);
        int pBucket = bucket(pitch);
        int hx = halfBlock(eyeX);
        int hy = halfBlock(eyeY);
        int hz = halfBlock(eyeZ);

        PlayerCache pc = perPlayer.get(playerId);
        if (pc == null || currentTick - pc.createdTick > TTL_TICKS
                || pc.halfX != hx || pc.halfY != hy || pc.halfZ != hz
                || pc.yawBucket != yBucket || pc.pitchBucket != pBucket) {
            pc = new PlayerCache(hx, hy, hz, yBucket, pBucket, currentTick);
            perPlayer.put(playerId, pc);
        }

        pc.results.put(blockPos, visible);
    }

    /** Remove tracking for a player (e.g. on disconnect). */
    public void remove(UUID playerId) {
        perPlayer.remove(playerId);
    }

    /** Flush cached results for a player (e.g. when nearby blocks change). */
    public void invalidate(UUID playerId) {
        perPlayer.remove(playerId);
    }

    private static int bucket(float angle) {
        return Math.floorDiv((int) angle, ANGLE_BUCKET);
    }

    /** Maps a coordinate to a half-block bucket (0.5-block granularity). */
    private static int halfBlock(double coord) {
        return (int) Math.floor(coord * 2);
    }

    private static final class PlayerCache {
        final int halfX, halfY, halfZ;
        final int yawBucket, pitchBucket;
        final int createdTick;
        final Map<BlockVector, Boolean> results = new HashMap<>();

        PlayerCache(int halfX, int halfY, int halfZ,
                    int yawBucket, int pitchBucket, int createdTick) {
            this.halfX = halfX;
            this.halfY = halfY;
            this.halfZ = halfZ;
            this.yawBucket = yawBucket;
            this.pitchBucket = pitchBucket;
            this.createdTick = createdTick;
        }
    }
}

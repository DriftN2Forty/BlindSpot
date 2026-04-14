package dev.driftn2forty.blindspot.proximity;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-player position and look-direction snapshots to detect when a
 * player is stationary. Used by block-entity, scan-block, and item-frame
 * services to skip redundant visibility checks when neither the player nor
 * nearby blocks have changed.
 * <p>
 * A player is considered "moved" if their position shifted by more than
 * {@value #POS_THRESHOLD} blocks or their look direction changed by more
 * than {@value #ROT_THRESHOLD} degrees since the last snapshot.
 * <p>
 * External events (block changes, hanging entity changes) can flag a player
 * as dirty via {@link #markDirty(UUID)} to force a re-check even when the
 * player hasn't moved.
 */
public final class PlayerDeltaTracker {

    private static final double POS_THRESHOLD = 0.1;
    private static final double POS_THRESHOLD_SQ = POS_THRESHOLD * POS_THRESHOLD;
    private static final float ROT_THRESHOLD = 2.0f;

    private final Map<UUID, Snapshot> snapshots = new ConcurrentHashMap<>();

    /**
     * Returns {@code true} if the player has moved or rotated beyond
     * threshold, or has been flagged dirty since the last call. Updates the
     * stored snapshot on every call.
     */
    public boolean hasMoved(Player player) {
        UUID id = player.getUniqueId();
        Location loc = player.getLocation();
        Snapshot prev = snapshots.get(id);

        Snapshot current = new Snapshot(loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch(), false);
        snapshots.put(id, current);

        if (prev == null || prev.dirty) return true;

        double dx = current.x - prev.x;
        double dy = current.y - prev.y;
        double dz = current.z - prev.z;
        if (dx * dx + dy * dy + dz * dz > POS_THRESHOLD_SQ) return true;

        float dYaw = Math.abs(current.yaw - prev.yaw);
        if (dYaw > 180f) dYaw = 360f - dYaw;
        if (dYaw > ROT_THRESHOLD) return true;

        float dPitch = Math.abs(current.pitch - prev.pitch);
        if (dPitch > ROT_THRESHOLD) return true;

        return false;
    }

    /**
     * Flags a player for re-check on the next tick regardless of movement
     * (e.g. when a nearby block or hanging entity changes).
     */
    public void markDirty(UUID playerId) {
        Snapshot s = snapshots.get(playerId);
        if (s != null) {
            snapshots.put(playerId, new Snapshot(s.x, s.y, s.z, s.yaw, s.pitch, true));
        }
        // If no snapshot exists, hasMoved() will return true on first call anyway.
    }

    /** Remove tracking for a player (e.g. on disconnect). */
    public void remove(UUID playerId) {
        snapshots.remove(playerId);
    }

    private static final class Snapshot {
        final double x, y, z;
        final float yaw, pitch;
        final boolean dirty;

        Snapshot(double x, double y, double z, float yaw, float pitch, boolean dirty) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.dirty = dirty;
        }
    }
}

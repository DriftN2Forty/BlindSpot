package dev.driftn2forty.blindspot.proximity;

import org.bukkit.Bukkit;
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
     * stored snapshot at most once per server tick; subsequent calls in the
     * same tick return the cached result so multiple services sharing this
     * tracker all see the same answer.
     */
    public boolean hasMoved(Player player) {
        UUID id = player.getUniqueId();
        int currentTick = Bukkit.getCurrentTick();
        Snapshot prev = snapshots.get(id);

        // Already evaluated this tick — return the cached result.
        if (prev != null && prev.checkedTick == currentTick) {
            return prev.moved;
        }

        Location loc = player.getLocation();

        boolean moved;
        if (prev == null || prev.dirty) {
            moved = true;
        } else {
            double dx = loc.getX() - prev.x;
            double dy = loc.getY() - prev.y;
            double dz = loc.getZ() - prev.z;
            moved = dx * dx + dy * dy + dz * dz > POS_THRESHOLD_SQ;

            if (!moved) {
                float dYaw = Math.abs(loc.getYaw() - prev.yaw);
                if (dYaw > 180f) dYaw = 360f - dYaw;
                moved = dYaw > ROT_THRESHOLD;
            }
            if (!moved) {
                moved = Math.abs(loc.getPitch() - prev.pitch) > ROT_THRESHOLD;
            }
        }

        if (moved) {
            snapshots.put(id, new Snapshot(loc.getX(), loc.getY(), loc.getZ(),
                    loc.getYaw(), loc.getPitch(), false, currentTick, true));
        } else {
            snapshots.put(id, new Snapshot(prev.x, prev.y, prev.z,
                    prev.yaw, prev.pitch, false, currentTick, false));
        }

        return moved;
    }

    /**
     * Flags a player for re-check on the next tick regardless of movement
     * (e.g. when a nearby block or hanging entity changes).
     */
    public void markDirty(UUID playerId) {
        Snapshot s = snapshots.get(playerId);
        if (s != null) {
            snapshots.put(playerId, new Snapshot(s.x, s.y, s.z, s.yaw, s.pitch,
                    true, s.checkedTick, s.moved));
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
        final int checkedTick;
        final boolean moved;

        Snapshot(double x, double y, double z, float yaw, float pitch, boolean dirty,
                 int checkedTick, boolean moved) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.dirty = dirty;
            this.checkedTick = checkedTick;
            this.moved = moved;
        }
    }
}

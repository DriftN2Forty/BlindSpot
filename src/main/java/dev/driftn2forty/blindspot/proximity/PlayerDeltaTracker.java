package dev.driftn2forty.blindspot.proximity;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-player position and look-direction snapshots to detect when a
 * player is stationary. Each consumer service should hold its own instance
 * so that snapshot updates are scoped to that service's tick interval.
 * <p>
 * Sensitivity level controls the dead-zone thresholds:
 * <ul>
 *   <li><b>0</b> — disabled (always reports moved)</li>
 *   <li><b>1</b> — high sensitivity (pos &gt; 0.01 blocks, rot &gt; 0.5°)</li>
 *   <li><b>2</b> — normal (pos &gt; 0.1 blocks, rot &gt; 2°)</li>
 *   <li><b>3</b> — low sensitivity (pos &gt; 0.5 blocks, rot &gt; 5°)</li>
 * </ul>
 * External events (block changes, hanging entity changes) can flag a player
 * as dirty via {@link #markDirty(UUID)} to force a re-check even when the
 * player hasn't moved.
 */
public final class PlayerDeltaTracker {

    private int sensitivity;
    private double posThresholdSq;
    private float rotThreshold;
    private final Map<UUID, Snapshot> snapshots = new ConcurrentHashMap<>();

    public PlayerDeltaTracker(int sensitivity) {
        setSensitivity(sensitivity);
    }

    /** Update thresholds from a new sensitivity level (0–3). */
    public void setSensitivity(int sensitivity) {
        this.sensitivity = Math.max(0, Math.min(3, sensitivity));
        switch (this.sensitivity) {
            case 1  -> { posThresholdSq = 0.01 * 0.01; rotThreshold = 0.5f; }
            case 3  -> { posThresholdSq = 0.5 * 0.5;   rotThreshold = 5.0f; }
            default -> { posThresholdSq = 0.1 * 0.1;    rotThreshold = 2.0f; }
        }
        snapshots.clear();
    }

    /**
     * Returns {@code true} if the player has moved or rotated beyond
     * threshold, or has been flagged dirty since the last call. Only updates
     * the stored snapshot when returning {@code true}, so that small
     * incremental movements accumulate against the last "work" position.
     */
    public boolean hasMoved(Player player) {
        if (sensitivity == 0) return true;
        UUID id = player.getUniqueId();
        Location loc = player.getLocation();
        Snapshot prev = snapshots.get(id);

        Snapshot current = new Snapshot(loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch(), false);

        if (prev == null || prev.dirty) {
            snapshots.put(id, current);
            return true;
        }

        double dx = current.x - prev.x;
        double dy = current.y - prev.y;
        double dz = current.z - prev.z;
        boolean moved = dx * dx + dy * dy + dz * dz > posThresholdSq;

        if (!moved) {
            float dYaw = Math.abs(current.yaw - prev.yaw);
            if (dYaw > 180f) dYaw = 360f - dYaw;
            moved = dYaw > rotThreshold || Math.abs(current.pitch - prev.pitch) > rotThreshold;
        }

        if (moved) {
            snapshots.put(id, current);
        }
        return moved;
    }

    /**
     * Flags a player for re-check on the next call regardless of movement
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

    /** Current sensitivity level (0–3). */
    public int getSensitivity() {
        return sensitivity;
    }

    /** Position threshold in blocks (linear, not squared). */
    public double getPosThreshold() {
        return Math.sqrt(posThresholdSq);
    }

    /** Rotation threshold in degrees. */
    public float getRotThreshold() {
        return rotThreshold;
    }

    /**
     * Returns the player's current position delta (blocks) and rotation
     * delta (degrees) relative to the last snapshot. Returns {@code null}
     * if no snapshot exists for the player. Index 0 = position delta,
     * index 1 = max rotation delta (yaw or pitch).
     */
    public double[] getDelta(Player player) {
        if (sensitivity == 0) return null;
        Snapshot prev = snapshots.get(player.getUniqueId());
        if (prev == null) return null;
        Location loc = player.getLocation();
        double dx = loc.getX() - prev.x;
        double dy = loc.getY() - prev.y;
        double dz = loc.getZ() - prev.z;
        double posDelta = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float dYaw = Math.abs(loc.getYaw() - prev.yaw);
        if (dYaw > 180f) dYaw = 360f - dYaw;
        float dPitch = Math.abs(loc.getPitch() - prev.pitch);
        double rotDelta = Math.max(dYaw, dPitch);
        return new double[]{posDelta, rotDelta};
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

package dev.driftn2forty.blindspot.util;

import java.util.Arrays;

/**
 * Rolling-window timing recorder for a single scheduled task.
 * <p>
 * Uses 20 one-second buckets in a ring buffer. Old buckets rotate out
 * continuously, so {@link #amortizedAverageMs()} and {@link #maxMs()}
 * always reflect the most recent 20 seconds of data without stale
 * snapshots. This gives a smooth, truly rolling average suitable for
 * live displays (boss bars) and one-shot commands alike.
 * <p>
 * <strong>Amortized average</strong> = total execution time across the
 * window divided by the number of server ticks in that window. This
 * gives the true per-tick cost even when tasks run at intervals greater
 * than 1 tick or are skipped by guards / delta-tracking.
 */
public final class TickTimings {

    private static final int BUCKET_COUNT = 20;                       // 20 seconds of history
    private static final long BUCKET_NANOS = 1_000_000_000L;         // 1 second per bucket
    private static final long NANOS_PER_TICK = 50_000_000L;          // 50 ms at 20 TPS

    private final long[] bucketExec = new long[BUCKET_COUNT];
    private final long[] bucketMax = new long[BUCKET_COUNT];
    private long bucketStart;   // nanoTime when the current head bucket began
    private int head;           // index of the current (newest) bucket
    private int filled;         // number of buckets that have received data (1..BUCKET_COUNT)
    private boolean hasData;

    /** Record one execution duration in nanoseconds. */
    public void record(long nanos) {
        long now = System.nanoTime();
        if (!hasData) {
            hasData = true;
            bucketStart = now;
            head = 0;
            filled = 1;
            clearAll();
        } else {
            advance(now);
        }
        bucketExec[head] += nanos;
        if (nanos > bucketMax[head]) bucketMax[head] = nanos;
    }

    /** Whether any data has been recorded. */
    public boolean hasData() {
        return hasData;
    }

    /**
     * Amortized average execution time per server tick in milliseconds.
     * Computed over all active buckets in the rolling window.
     */
    public double amortizedAverageMs() {
        if (!hasData) return 0;
        advance(System.nanoTime());
        long totalExec = 0;
        for (long e : bucketExec) totalExec += e;
        long windowNanos = (long) filled * BUCKET_NANOS;
        long ticks = Math.max(1, windowNanos / NANOS_PER_TICK);
        return (totalExec / (double) ticks) / 1_000_000.0;
    }

    /** Maximum single-execution time in milliseconds across the window. */
    public double maxMs() {
        if (!hasData) return 0;
        advance(System.nanoTime());
        long max = 0;
        for (long m : bucketMax) if (m > max) max = m;
        return max / 1_000_000.0;
    }

    /** Reset all accumulated data. */
    public void reset() {
        clearAll();
        head = 0;
        filled = 0;
        hasData = false;
        bucketStart = 0;
    }

    // ── internals ──

    /** Advance the ring buffer, clearing buckets for any elapsed seconds. */
    private void advance(long now) {
        long elapsed = now - bucketStart;
        int steps = (int) (elapsed / BUCKET_NANOS);
        if (steps <= 0) return;
        steps = Math.min(steps, BUCKET_COUNT);
        for (int i = 0; i < steps; i++) {
            head = (head + 1) % BUCKET_COUNT;
            bucketExec[head] = 0;
            bucketMax[head] = 0;
        }
        bucketStart += steps * BUCKET_NANOS;
        filled = Math.min(BUCKET_COUNT, filled + steps);
    }

    private void clearAll() {
        Arrays.fill(bucketExec, 0);
        Arrays.fill(bucketMax, 0);
    }
}

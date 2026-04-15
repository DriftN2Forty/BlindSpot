package dev.driftn2forty.blindspot.util;

/**
 * Time-windowed timing recorder for a single scheduled task.
 * <p>
 * Accumulates execution durations over a 20-second window and exposes
 * <strong>amortized</strong> average (total exec time / total server ticks)
 * and max single-execution time. This gives the true per-tick cost even
 * when tasks run at intervals greater than 1 tick or are skipped by
 * guards / delta-tracking.
 */
public final class TickTimings {

    private static final long WINDOW_NANOS = 20_000_000_000L; // 20 seconds
    private static final long NANOS_PER_TICK = 50_000_000L;   // 50 ms at 20 TPS

    // ── current accumulation window ──
    private long windowStartNanos;
    private long totalExecNanos;
    private long maxExecNanos;
    private boolean hasCurrentData;

    // ── completed-window snapshot (stable for reporting) ──
    private double reportAmortizedAvgMs;
    private double reportMaxMs;
    private boolean hasReport;

    /** Record one execution duration in nanoseconds. */
    public void record(long nanos) {
        long now = System.nanoTime();

        if (!hasCurrentData) {
            windowStartNanos = now;
            hasCurrentData = true;
        }

        totalExecNanos += nanos;
        if (nanos > maxExecNanos) maxExecNanos = nanos;

        long elapsed = now - windowStartNanos;
        if (elapsed >= WINDOW_NANOS) {
            long ticks = Math.max(1, elapsed / NANOS_PER_TICK);
            reportAmortizedAvgMs = (totalExecNanos / (double) ticks) / 1_000_000.0;
            reportMaxMs = maxExecNanos / 1_000_000.0;
            hasReport = true;

            // reset accumulators for next window
            windowStartNanos = now;
            totalExecNanos = 0;
            maxExecNanos = 0;
        }
    }

    /** Whether any completed or in-progress data exists. */
    public boolean hasData() {
        return hasReport || hasCurrentData;
    }

    /**
     * Amortized average execution time per server tick in milliseconds.
     * Uses the last completed 20-second window; falls back to the
     * in-progress window if no completed window exists yet.
     */
    public double amortizedAverageMs() {
        if (hasReport) return reportAmortizedAvgMs;
        if (!hasCurrentData) return 0;
        long elapsed = System.nanoTime() - windowStartNanos;
        long ticks = Math.max(1, elapsed / NANOS_PER_TICK);
        return (totalExecNanos / (double) ticks) / 1_000_000.0;
    }

    /** Maximum single-execution time in milliseconds. */
    public double maxMs() {
        if (hasReport) return reportMaxMs;
        return maxExecNanos / 1_000_000.0;
    }

    /** Reset all accumulated and snapshot data. */
    public void reset() {
        windowStartNanos = 0;
        totalExecNanos = 0;
        maxExecNanos = 0;
        hasCurrentData = false;
        reportAmortizedAvgMs = 0;
        reportMaxMs = 0;
        hasReport = false;
    }
}

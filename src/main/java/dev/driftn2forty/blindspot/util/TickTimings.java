package dev.driftn2forty.blindspot.util;

/**
 * Rolling-window timing recorder for a single scheduled task.
 * Records the duration of each tick and exposes min, max,
 * and average over the last 20 samples.
 */
public final class TickTimings {

    private final long[] samples = new long[20];
    private int head;
    private int count;

    /** Record one tick duration in nanoseconds. */
    public void record(long nanos) {
        samples[head] = nanos;
        head = (head + 1) % samples.length;
        if (count < samples.length) count++;
    }

    /** Number of samples currently stored. */
    public int count() {
        return count;
    }

    /** Average tick time in milliseconds. */
    public double averageMs() {
        if (count == 0) return 0;
        long sum = 0;
        for (int i = 0; i < count; i++) sum += samples[i];
        return (sum / (double) count) / 1_000_000.0;
    }

    /** Minimum tick time in milliseconds over the window. */
    public double minMs() {
        if (count == 0) return 0;
        long min = Long.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            if (samples[i] < min) min = samples[i];
        }
        return min / 1_000_000.0;
    }

    /** Maximum tick time in milliseconds over the window. */
    public double maxMs() {
        long max = 0;
        for (int i = 0; i < count; i++) {
            if (samples[i] > max) max = samples[i];
        }
        return max / 1_000_000.0;
    }

    /** Reset all samples. */
    public void reset() {
        head = 0;
        count = 0;
    }
}

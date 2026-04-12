package dev.driftn2forty.blindspot.guard;

import dev.driftn2forty.blindspot.config.PluginConfig;
import org.bukkit.Bukkit;

public final class TpsGuard implements TpsThrottle {

    private final PluginConfig config;

    public TpsGuard(PluginConfig config) {
        this.config = config;
    }

    public boolean allowHeavyWork() {
        if (!config.tpsGuardEnabled) return true;
        try {
            double[] tps = Bukkit.getServer().getTPS();
            double current = (tps != null && tps.length > 0) ? tps[0] : 20.0;
            return current >= config.tpsGuardMin;
        } catch (Throwable t) {
            return true;
        }
    }
}

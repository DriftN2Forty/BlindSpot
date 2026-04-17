package dev.driftn2forty.blindspot.command;

import dev.driftn2forty.blindspot.BlindSpotPlugin;
import dev.driftn2forty.blindspot.proximity.PlayerDeltaTracker;
import dev.driftn2forty.blindspot.util.TickTimings;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PerfBossBarManager {

    private static final long UPDATE_INTERVAL = 20L; // 1 second
    private static final double TICK_BUDGET_MS = 50.0;
    private static final int BAR_COUNT = 4; // total, breakdown, movement, rotation
    private static final int IDX_TOTAL = 0;
    private static final int IDX_BREAKDOWN = 1;
    private static final int IDX_MOVEMENT = 2;
    private static final int IDX_ROTATION = 3;

    private final BlindSpotPlugin plugin;
    private final Map<UUID, BossBar[]> viewers = new ConcurrentHashMap<>();
    private BukkitTask task;

    public PerfBossBarManager(BlindSpotPlugin plugin) {
        this.plugin = plugin;
    }

    /** Toggles the performance monitor for the given player. Returns true if now showing. */
    public boolean toggle(Player player) {
        UUID uuid = player.getUniqueId();
        if (viewers.containsKey(uuid)) {
            removeBars(player);
            return false;
        }
        addBars(player);
        return true;
    }

    private void addBars(Player player) {
        BossBar totalBar = BossBar.bossBar(
                Component.text("BlindSpot: loading...", NamedTextColor.WHITE),
                0f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
        BossBar breakdownBar = BossBar.bossBar(
                Component.text("loading...", NamedTextColor.WHITE),
                0f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        BossBar movementBar = BossBar.bossBar(
                Component.text("Movement: loading...", NamedTextColor.WHITE),
                0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
        BossBar rotationBar = BossBar.bossBar(
                Component.text("Rotation: loading...", NamedTextColor.WHITE),
                0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);

        player.showBossBar(totalBar);
        player.showBossBar(breakdownBar);
        player.showBossBar(movementBar);
        player.showBossBar(rotationBar);
        viewers.put(player.getUniqueId(), new BossBar[]{totalBar, breakdownBar, movementBar, rotationBar});
        ensureTaskRunning();
    }

    private void removeBars(Player player) {
        BossBar[] bars = viewers.remove(player.getUniqueId());
        if (bars != null) {
            for (BossBar bar : bars) player.hideBossBar(bar);
        }
        if (viewers.isEmpty()) stopTask();
    }

    public void removeAll() {
        for (var entry : viewers.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                for (BossBar bar : entry.getValue()) player.hideBossBar(bar);
            }
        }
        viewers.clear();
        stopTask();
    }

    private void ensureTaskRunning() {
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::update, UPDATE_INTERVAL, UPDATE_INTERVAL);
        }
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void update() {
        if (viewers.isEmpty()) {
            stopTask();
            return;
        }

        // ── Collect timings ──
        double entityAvg = 0, entityMax = 0;
        for (TickTimings t : plugin.getEntityTimings()) {
            if (t != null && t.hasData()) {
                entityAvg += t.amortizedAverageMs();
                entityMax += t.maxMs();
            }
        }

        TickTimings blockEntity = plugin.getBlockEntityTimings();
        double blockAvg = 0, blockMax = 0;
        if (blockEntity != null && blockEntity.hasData()) {
            blockAvg = blockEntity.amortizedAverageMs();
            blockMax = blockEntity.maxMs();
        }

        TickTimings scanBlock = plugin.getScanBlockTimings();
        double scanAvg = 0, scanMax = 0;
        if (scanBlock != null && scanBlock.hasData()) {
            scanAvg = scanBlock.amortizedAverageMs();
            scanMax = scanBlock.maxMs();
        }

        double totalAvg = entityAvg + blockAvg + scanAvg;
        double peakMax = Math.max(entityMax, Math.max(blockMax, scanMax));

        // ── Server MSPT ──
        double mspt = Bukkit.getServer().getAverageTickTime();
        double effectiveTps = Math.min(20.0, 1000.0 / Math.max(1.0, mspt));

        // ── Bar 1: Total load ──
        double budgetPct = (totalAvg / TICK_BUDGET_MS) * 100.0;
        float bar1Progress = (float) Math.min(1.0, totalAvg / TICK_BUDGET_MS);

        BossBar.Color bar1Color;
        NamedTextColor bar1TextColor;
        if (budgetPct < 5.0) {
            bar1Color = BossBar.Color.GREEN;
            bar1TextColor = NamedTextColor.GREEN;
        } else if (budgetPct < 10.0) {
            bar1Color = BossBar.Color.YELLOW;
            bar1TextColor = NamedTextColor.YELLOW;
        } else {
            bar1Color = BossBar.Color.RED;
            bar1TextColor = NamedTextColor.RED;
        }

        Component bar1Title = Component.text(
                String.format("BlindSpot: %.2fms/tick (%.1f%%) | MSPT %.1f (TPS ~%.1f)",
                        totalAvg, budgetPct, mspt, effectiveTps),
                bar1TextColor);

        // ── Bar 2: Service breakdown ──
        float bar2Progress = (float) Math.min(1.0, peakMax / TICK_BUDGET_MS);

        BossBar.Color bar2Color;
        if (entityAvg >= blockAvg && entityAvg >= scanAvg) bar2Color = BossBar.Color.BLUE;
        else if (blockAvg >= scanAvg) bar2Color = BossBar.Color.PURPLE;
        else bar2Color = BossBar.Color.GREEN;

        Component bar2Title = Component.text(
                String.format("Ent %.2f | Blk %.2f | Scan %.2f | Peak %.2fms",
                        entityAvg, blockAvg, scanAvg, peakMax),
                NamedTextColor.WHITE);

        // ── Update all viewers, prune disconnected ──
        // Delta tracker info (shared thresholds — same sensitivity for both trackers)
        PlayerDeltaTracker beDelta = plugin.getBeDeltaTracker();
        int sens = beDelta.getSensitivity();
        double posThreshold = beDelta.getPosThreshold();
        float rotThreshold = beDelta.getRotThreshold();
        String sensLabel = switch (sens) {
            case 0 -> "OFF";
            case 1 -> "High";
            case 3 -> "Low";
            default -> "Normal";
        };

        var it = viewers.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                it.remove();
                continue;
            }
            BossBar[] bars = entry.getValue();
            bars[IDX_TOTAL].name(bar1Title);
            bars[IDX_TOTAL].progress(bar1Progress);
            bars[IDX_TOTAL].color(bar1Color);
            bars[IDX_BREAKDOWN].name(bar2Title);
            bars[IDX_BREAKDOWN].progress(bar2Progress);
            bars[IDX_BREAKDOWN].color(bar2Color);

            // ── Per-player movement & rotation bars ──
            if (sens == 0) {
                bars[IDX_MOVEMENT].name(Component.text(
                        "Movement: deltaTracker disabled (sensitivity 0)", NamedTextColor.GRAY));
                bars[IDX_MOVEMENT].progress(0f);
                bars[IDX_MOVEMENT].color(BossBar.Color.WHITE);
                bars[IDX_ROTATION].name(Component.text(
                        "Rotation: deltaTracker disabled (sensitivity 0)", NamedTextColor.GRAY));
                bars[IDX_ROTATION].progress(0f);
                bars[IDX_ROTATION].color(BossBar.Color.WHITE);
            } else {
                double[] delta = beDelta.getDelta(player);
                double posDelta = (delta != null) ? delta[0] : 0;
                double rotDelta = (delta != null) ? delta[1] : 0;

                // Movement bar
                float movePct = (float) Math.min(1.0, posDelta / posThreshold);
                BossBar.Color moveColor = movePct >= 1.0f ? BossBar.Color.GREEN : BossBar.Color.WHITE;
                String moveStatus = movePct >= 1.0f ? "RECALC" : "skip";
                bars[IDX_MOVEMENT].name(Component.text(String.format(
                        "Move: %.3f / %.3f blk [%s] (%s sens)",
                        posDelta, posThreshold, moveStatus, sensLabel), 
                        movePct >= 1.0f ? NamedTextColor.GREEN : NamedTextColor.GRAY));
                bars[IDX_MOVEMENT].progress(movePct);
                bars[IDX_MOVEMENT].color(moveColor);

                // Rotation bar
                float rotPct = (float) Math.min(1.0, rotDelta / rotThreshold);
                BossBar.Color rotColor = rotPct >= 1.0f ? BossBar.Color.GREEN : BossBar.Color.WHITE;
                String rotStatus = rotPct >= 1.0f ? "RECALC" : "skip";
                bars[IDX_ROTATION].name(Component.text(String.format(
                        "Look: %.1f / %.1f° [%s] (%s sens)",
                        rotDelta, rotThreshold, rotStatus, sensLabel),
                        rotPct >= 1.0f ? NamedTextColor.GREEN : NamedTextColor.GRAY));
                bars[IDX_ROTATION].progress(rotPct);
                bars[IDX_ROTATION].color(rotColor);
            }
        }

        if (viewers.isEmpty()) stopTask();
    }
}

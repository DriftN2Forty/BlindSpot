package dev.driftn2forty.blindspot.command;

import dev.driftn2forty.blindspot.BlindSpotPlugin;
import dev.driftn2forty.blindspot.util.TickTimings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ReloadCommand implements CommandExecutor, TabCompleter {

    private final BlindSpotPlugin plugin;

    public ReloadCommand(BlindSpotPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            if (sender.hasPermission("blindspot.reload") && "reload".startsWith(args[0].toLowerCase())) {
                completions.add("reload");
            }
            if (sender.hasPermission("blindspot.timings") && "timings".startsWith(args[0].toLowerCase())) {
                completions.add("timings");
            }
            return completions;
        }
        return Collections.emptyList();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /blindspot <reload|timings>", NamedTextColor.YELLOW));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "timings" -> handleTimings(sender);
            default -> sender.sendMessage(Component.text("Usage: /blindspot <reload|timings>", NamedTextColor.YELLOW));
        }
        return true;
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("blindspot.reload")) {
            sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return;
        }
        try {
            plugin.reloadBlindSpot();
            sender.sendMessage(Component.text("BlindSpot reloaded.", NamedTextColor.GREEN));
        } catch (Exception e) {
            sender.sendMessage(Component.text("Reload failed: " + e.getMessage(), NamedTextColor.RED));
            plugin.getLogger().severe("Config reload error: " + e.getMessage());
        }
    }

    private void handleTimings(CommandSender sender) {
        if (!sender.hasPermission("blindspot.timings")) {
            sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return;
        }

        sender.sendMessage(Component.text("─── BlindSpot Timings ───", NamedTextColor.GOLD));

        double totalMax = 0, totalAvg = 0;

        double[] entityStats = printCombinedTimings(sender, "Entities", plugin.getEntityTimings());
        totalMax += entityStats[0]; totalAvg += entityStats[1];

        TickTimings blocks = plugin.getBlockEntityTimings();
        if (blocks != null && blocks.hasData()) {
            totalMax += blocks.maxMs(); totalAvg += blocks.amortizedAverageMs();
        }
        printTimings(sender, "Blocks", blocks);

        sender.sendMessage(Component.text("Total: ", NamedTextColor.WHITE)
                .append(Component.text(String.format("avg %.3fms/tick", totalAvg), NamedTextColor.GREEN))
                .append(Component.text(" | ", NamedTextColor.GRAY))
                .append(Component.text(String.format("max %.3fms", totalMax), NamedTextColor.YELLOW)));
    }

    /** Returns [max, avg] sums for the combined timings. */
    private double[] printCombinedTimings(CommandSender sender, String name, TickTimings[] timingsArray) {
        double maxSum = 0, avgSum = 0;
        boolean hasData = false;
        for (TickTimings t : timingsArray) {
            if (t != null && t.hasData()) {
                maxSum += t.maxMs();
                avgSum += t.amortizedAverageMs();
                hasData = true;
            }
        }
        if (!hasData) {
            sender.sendMessage(Component.text(name + ": ", NamedTextColor.WHITE)
                    .append(Component.text("no data", NamedTextColor.GRAY)));
            return new double[]{0, 0};
        }
        sender.sendMessage(Component.text(name + ": ", NamedTextColor.WHITE)
                .append(Component.text(String.format("avg %.3fms/tick", avgSum), NamedTextColor.GREEN))
                .append(Component.text(" | ", NamedTextColor.GRAY))
                .append(Component.text(String.format("max %.3fms", maxSum), NamedTextColor.YELLOW)));
        return new double[]{maxSum, avgSum};
    }

    private void printTimings(CommandSender sender, String name, TickTimings timings) {
        if (timings == null || !timings.hasData()) {
            sender.sendMessage(Component.text(name + ": ", NamedTextColor.WHITE)
                    .append(Component.text("no data", NamedTextColor.GRAY)));
            return;
        }
        sender.sendMessage(Component.text(name + ": ", NamedTextColor.WHITE)
                .append(Component.text(String.format("avg %.3fms/tick", timings.amortizedAverageMs()), NamedTextColor.GREEN))
                .append(Component.text(" | ", NamedTextColor.GRAY))
                .append(Component.text(String.format("max %.3fms", timings.maxMs()), NamedTextColor.YELLOW)));
    }
}

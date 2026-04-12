package dev.driftn2forty.blindspot.command;

import dev.driftn2forty.blindspot.BlindSpotPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Collections;
import java.util.List;

public final class ReloadCommand implements CommandExecutor, TabCompleter {

    private final BlindSpotPlugin plugin;

    public ReloadCommand(BlindSpotPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("blindspot.reload")) {
            if ("reload".startsWith(args[0].toLowerCase())) {
                return List.of("reload");
            }
        }
        return Collections.emptyList();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("blindspot.reload")) {
            sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return true;
        }
        try {
            plugin.reloadBlindSpot();
            sender.sendMessage(Component.text("BlindSpot reloaded.", NamedTextColor.GREEN));
        } catch (Exception e) {
            sender.sendMessage(Component.text("Reload failed: " + e.getMessage(), NamedTextColor.RED));
            plugin.getLogger().severe("Config reload error: " + e.getMessage());
        }
        return true;
    }
}

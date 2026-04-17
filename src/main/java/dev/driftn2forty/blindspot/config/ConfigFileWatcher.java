package dev.driftn2forty.blindspot.config;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.file.*;
import java.util.logging.Level;

/**
 * Watches the plugin's {@code config.yml} for modifications and triggers
 * a reload after a 1-second delay. The delay lets editors finish flushing
 * the file before BlindSpot reads it.
 * <p>
 * The WatchService runs on a dedicated daemon thread; the actual reload
 * is scheduled back onto the main server thread via
 * {@link Bukkit#getScheduler()}.
 */
public final class ConfigFileWatcher {

    private static final long RELOAD_DELAY_TICKS = 20L; // 1 second

    private final Plugin plugin;
    private final Runnable reloadAction;
    private WatchService watchService;
    private Thread watchThread;
    private BukkitTask pendingReload;
    private volatile boolean running;

    public ConfigFileWatcher(Plugin plugin, Runnable reloadAction) {
        this.plugin = plugin;
        this.reloadAction = reloadAction;
    }

    public void start() {
        Path configFile = plugin.getDataFolder().toPath().resolve("config.yml");
        Path dir = configFile.getParent();
        if (dir == null || !Files.isDirectory(dir)) return;

        try {
            this.watchService = FileSystems.getDefault().newWatchService();
            dir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not start config file watcher", e);
            return;
        }

        this.running = true;
        this.watchThread = new Thread(this::pollLoop, "BlindSpot-ConfigWatcher");
        this.watchThread.setDaemon(true);
        this.watchThread.start();
        plugin.getLogger().info("Config auto-reload enabled — watching config.yml for changes.");
    }

    public void stop() {
        this.running = false;
        if (watchThread != null) {
            watchThread.interrupt();
            watchThread = null;
        }
        if (watchService != null) {
            try { watchService.close(); } catch (IOException ignored) {}
            watchService = null;
        }
        if (pendingReload != null) {
            pendingReload.cancel();
            pendingReload = null;
        }
    }

    private void pollLoop() {
        while (running) {
            WatchKey key;
            try {
                key = watchService.take(); // blocks until event
            } catch (InterruptedException | ClosedWatchServiceException e) {
                return;
            }

            boolean configChanged = false;
            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;
                Path changed = (Path) event.context();
                if (changed != null && "config.yml".equals(changed.getFileName().toString())) {
                    configChanged = true;
                }
            }
            key.reset();

            if (configChanged) {
                scheduleReload();
            }
        }
    }

    private synchronized void scheduleReload() {
        // Cancel any previously pending reload so we only fire once per burst
        // of file-system events (some editors write multiple times).
        if (pendingReload != null) {
            pendingReload.cancel();
        }
        try {
            pendingReload = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                plugin.getLogger().info("Config change detected — auto-reloading.");
                try {
                    reloadAction.run();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Auto-reload failed", e);
                }
            }, RELOAD_DELAY_TICKS);
        } catch (IllegalStateException ignored) {
            // Plugin disabled between detection and scheduling — safe to ignore.
        }
    }
}

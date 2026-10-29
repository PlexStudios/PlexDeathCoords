package plex.deathcoords;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class PlexDeathCoords extends JavaPlugin implements Listener {
    private DeathStore store;
    private Settings settings;
    private CompletableFuture<Boolean> pendingReload;

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            settings = readSettings();
            store = new DeathStore(getDataFolder().toPath().resolve("deaths.yml"), getLogger());
            DeathCommands handler = new DeathCommands(store, () -> settings, this::reloadSettings);
            for (String name : List.of("lastdeath", "plexdeathcoords")) {
                PluginCommand command = getCommand(name);
                if (command == null) {
                    throw new IllegalStateException("Missing command registration: " + name);
                }
                command.setExecutor(handler);
                command.setTabCompleter(handler);
            }
            getServer().getPluginManager().registerEvents(this, this);
        } catch (IOException | RuntimeException exception) {
            getLogger().log(Level.SEVERE, "Could not enable PlexDeathCoords; existing death data is untouched.", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private Settings readSettings() throws IOException {
        try (InputStream resource = getResource("config.yml")) {
            if (resource == null) {
                throw new IOException("Bundled config.yml is missing");
            }
            return Settings.load(getDataFolder().toPath().resolve("config.yml"),
                    new InputStreamReader(resource, StandardCharsets.UTF_8));
        }
    }

    private CompletableFuture<Boolean> reloadSettings() {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        pendingReload = result;
        BukkitScheduler scheduler = getServer().getScheduler();
        scheduler.runTaskAsynchronously(this, () -> {
            Settings loaded = null;
            String failure = null;
            try {
                loaded = readSettings();
            } catch (IOException | RuntimeException exception) {
                failure = exception.getMessage();
            }
            Settings candidate = loaded;
            String error = failure;
            if (result.isDone()) {
                return;
            }
            try {
                scheduler.runTask(this, () -> {
                    if (result.isDone()) {
                        return;
                    }
                    if (candidate != null) {
                        settings = candidate;
                    } else {
                        getLogger().severe("Could not load config.yml; previous settings remain active: " + error);
                    }
                    pendingReload = null;
                    result.complete(candidate != null);
                });
            } catch (IllegalPluginAccessException exception) {
                result.cancel(false);
            }
        });
        return result;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Location location = player.getLocation();
        World world = player.getWorld();
        store.put(player.getUniqueId(), new DeathLocation(world.getUID(), world.getName(),
                location.getX(), location.getY(), location.getZ(), System.currentTimeMillis()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!settings.respawnReminder()) return;
        Player player = event.getPlayer();
        DeathLocation death = store.get(player.getUniqueId());
        if (death == null) return;
        getServer().getScheduler().runTask(this, () -> {
            if (player.isOnline() && settings.respawnReminder()) {
                player.sendMessage(settings.respawnMessage(death, System.currentTimeMillis()));
            }
        });
    }

    @Override
    public void onDisable() {
        if (pendingReload != null) {
            pendingReload.cancel(false);
            pendingReload = null;
        }
        if (store != null) {
            try {
                store.close();
            } catch (IOException | RuntimeException exception) {
                getLogger().log(Level.SEVERE, "Could not flush death storage during shutdown; latest changes may be unsaved.",
                        exception);
            }
            store = null;
        }
    }
}

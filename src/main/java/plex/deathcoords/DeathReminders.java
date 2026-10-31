package plex.deathcoords;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

final class DeathReminders implements Listener {
    private final DeathStore store;
    private final Supplier<Settings> settings;
    private final Set<UUID> pending = new HashSet<>();

    DeathReminders(DeathStore store, Supplier<Settings> settings) {
        this.store = store;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        UUID player = event.getPlayer().getUniqueId();
        if (event.getRespawnReason() == PlayerRespawnEvent.RespawnReason.DEATH) {
            pending.add(player);
        } else {
            pending.remove(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPostRespawn(PlayerPostRespawnEvent event) {
        Player player = event.getPlayer();
        if (!pending.remove(player.getUniqueId())) {
            return;
        }
        Settings current = settings.get();
        if (!current.respawnReminder() || !player.isOnline() || player.isDead()
                || !player.hasPermission("plexdeathcoords.use")) {
            return;
        }
        DeathLocation death = store.get(player.getUniqueId());
        if (death != null) {
            player.sendMessage(current.coordinates("respawn-reminder", death, System.currentTimeMillis()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}

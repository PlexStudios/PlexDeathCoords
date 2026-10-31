package plex.deathcoords;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class DeathRemindersTest {
    @TempDir
    Path directory;
    private final UUID id = UUID.randomUUID();
    private final List<Component> messages = new ArrayList<>();
    private final Location spawn = new Location(null, 0, 64, 0);
    private boolean online = true;
    private boolean permitted = true;
    private boolean dead;

    @Test
    void sendsOnceAfterDeathRespawnAndUsesCurrentSettings() throws Exception {
        AtomicReference<Settings> settings = new AtomicReference<>(settings("respawn-reminder: true\n"));
        try (DeathStore store = store()) {
            store.put(id, new DeathLocation(UUID.randomUUID(), "world_nether", 10, 30, -5, 0));
            DeathReminders reminders = new DeathReminders(store, settings::get);
            Player player = player();
            reminders.onRespawn(respawn(player, PlayerRespawnEvent.RespawnReason.DEATH));
            assertTrue(messages.isEmpty());
            settings.set(settings("respawn-reminder: true\nshow-time: false\ncoordinate-precision: 1\n"));
            reminders.onPostRespawn(new PlayerPostRespawnEvent(player, spawn, false));
            assertEquals("Your last death: 10.0, 30.0, -5.0 in world_nether", plain(messages.getFirst()));
            reminders.onPostRespawn(new PlayerPostRespawnEvent(player, spawn, false));
            assertEquals(1, messages.size());
        }
    }

    @Test
    void ignoresEndPortalMissingDataDisabledAndDisconnectedPlayers() throws Exception {
        AtomicReference<Settings> settings = new AtomicReference<>(settings("respawn-reminder: true\n"));
        try (DeathStore store = store()) {
            DeathReminders reminders = new DeathReminders(store, settings::get);
            Player player = player();
            reminders.onRespawn(respawn(player, PlayerRespawnEvent.RespawnReason.DEATH));
            reminders.onPostRespawn(new PlayerPostRespawnEvent(player, spawn, false));
            assertTrue(messages.isEmpty());
            store.put(id, new DeathLocation(UUID.randomUUID(), "world", 1, 2, 3, 0));
            reminders.onRespawn(respawn(player, PlayerRespawnEvent.RespawnReason.END_PORTAL));
            reminders.onPostRespawn(new PlayerPostRespawnEvent(player, spawn, false));
            assertTrue(messages.isEmpty());
            reminders.onRespawn(respawn(player, PlayerRespawnEvent.RespawnReason.DEATH));
            settings.set(settings(""));
            reminders.onPostRespawn(new PlayerPostRespawnEvent(player, spawn, false));
            assertTrue(messages.isEmpty());
            settings.set(settings("respawn-reminder: true\n"));
            reminders.onPostRespawn(new PlayerPostRespawnEvent(player, spawn, false));
            assertTrue(messages.isEmpty());
            reminders.onRespawn(respawn(player, PlayerRespawnEvent.RespawnReason.DEATH));
            reminders.onQuit(new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
            reminders.onPostRespawn(new PlayerPostRespawnEvent(player, spawn, false));
            assertTrue(messages.isEmpty());
            reminders.onRespawn(respawn(player, PlayerRespawnEvent.RespawnReason.DEATH));
            store.clear(id);
            reminders.onPostRespawn(new PlayerPostRespawnEvent(player, spawn, false));
            assertTrue(messages.isEmpty());
        }
    }

    @Test
    void suppressesReminderForOfflineDeadOrUnauthorizedPlayers() throws Exception {
        Settings settings = settings("respawn-reminder: true\n");
        try (DeathStore store = store()) {
            store.put(id, new DeathLocation(UUID.randomUUID(), "world", 1, 2, 3, 0));
            DeathReminders reminders = new DeathReminders(store, () -> settings);
            Player player = player();
            for (int state = 0; state < 3; state++) {
                online = state != 0;
                dead = state == 1;
                permitted = state != 2;
                reminders.onRespawn(respawn(player, PlayerRespawnEvent.RespawnReason.DEATH));
                reminders.onPostRespawn(new PlayerPostRespawnEvent(player, spawn, false));
                assertTrue(messages.isEmpty());
            }
            online = true;
            dead = false;
            permitted = true;
            reminders.onPostRespawn(new PlayerPostRespawnEvent(player, spawn, false));
            assertTrue(messages.isEmpty());
        }
    }

    private PlayerRespawnEvent respawn(Player player, PlayerRespawnEvent.RespawnReason reason) {
        return new PlayerRespawnEvent(player, spawn, false, false, reason,
                com.google.common.collect.ImmutableSet.builder());
    }

    private Player player() {
        return (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "isOnline" -> online;
                    case "hasPermission" -> permitted;
                    case "isDead" -> dead;
                    case "sendMessage" -> { messages.add((Component) args[0]); yield null; }
                    default -> throw new AssertionError(method.getName());
                });
    }

    private DeathStore store() throws Exception {
        return new DeathStore(directory.resolve("deaths.yml"), Logger.getLogger(getClass().getName()));
    }

    private Settings settings(String yaml) throws Exception {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, yaml);
        try (var reader = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/config.yml")),
                StandardCharsets.UTF_8)) {
            return Settings.load(file, reader);
        }
    }

    private String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}

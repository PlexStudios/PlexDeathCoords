package plex.deathcoords;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class DeathCommandsTest {
    @TempDir
    Path directory;
    private final List<String> messages = new ArrayList<>();
    private final UUID playerId = UUID.randomUUID();
    private final Command lastDeath = command("lastdeath");
    private final Command admin = command("plexdeathcoords");
    private Settings defaults;

    @BeforeEach
    void loadDefaults() throws Exception {
        defaults = settings("");
    }

    @Test
    void showsRealSavedCoordinatesUsingTheAlias() throws Exception {
        try (DeathStore store = store()) {
            store.put(playerId, death());
            DeathCommands handler = new DeathCommands(store, () -> defaults, () -> CompletableFuture.completedFuture(true));
            assertTrue(handler.onCommand(sender(true, Set.of("plexdeathcoords.use")),
                    lastDeath, "deathcoords", new String[0]));
            assertTrue(messages.getLast().startsWith("Last death: -10, 64, 20 in world_the_end"));
        }
    }

    @Test
    void showsNoDeathMessageAndClearsOnlyTheInvokingPlayer() throws Exception {
        try (DeathStore store = store()) {
            DeathCommands handler = new DeathCommands(store, () -> defaults, () -> CompletableFuture.completedFuture(true));
            CommandSender sender = sender(true, Set.of("plexdeathcoords.use", "plexdeathcoords.clear"));
            handler.onCommand(sender, lastDeath, "lastdeath", new String[0]);
            assertEquals("You have no saved death location.", messages.getLast());
            UUID other = UUID.randomUUID();
            store.put(other, death());
            store.put(playerId, death());
            handler.onCommand(sender, lastDeath, "lastdeath", new String[] {"CLEAR"});
            assertEquals("Your saved death location has been cleared.", messages.getLast());
            assertNull(store.get(playerId));
            assertNotNull(store.get(other));
            handler.onCommand(sender, lastDeath, "lastdeath", new String[] {"clear"});
            assertEquals("You have no saved death location.", messages.getLast());
        }
    }

    @Test
    void enforcesBothUseAndClearPermissions() throws Exception {
        try (DeathStore store = store()) {
            store.put(playerId, death());
            DeathCommands handler = new DeathCommands(store, () -> defaults, () -> CompletableFuture.completedFuture(true));
            handler.onCommand(sender(true, Set.of()), lastDeath, "lastdeath", new String[0]);
            assertEquals("You do not have permission to use this command.", messages.getLast());
            handler.onCommand(sender(true, Set.of("plexdeathcoords.use")),
                    lastDeath, "lastdeath", new String[] {"clear"});
            assertEquals("You do not have permission to use this command.", messages.getLast());
            handler.onCommand(sender(true, Set.of("plexdeathcoords.clear")),
                    lastDeath, "lastdeath", new String[] {"clear"});
            assertEquals("You do not have permission to use this command.", messages.getLast());
            assertNotNull(store.get(playerId));
        }
    }

    @Test
    void disablingClearKeepsTheRecordAndRemovesTheSuggestion() throws Exception {
        Settings disabled = settings("allow-clear: false\n");
        try (DeathStore store = store()) {
            store.put(playerId, death());
            DeathCommands handler = new DeathCommands(store, () -> disabled, () -> CompletableFuture.completedFuture(true));
            CommandSender sender = sender(true, Set.of("plexdeathcoords.use", "plexdeathcoords.clear"));
            handler.onCommand(sender, lastDeath, "lastdeath", new String[] {"clear"});
            assertEquals("Clearing death locations is disabled.", messages.getLast());
            assertNotNull(store.get(playerId));
            assertEquals(List.of(), handler.onTabComplete(sender, lastDeath, "lastdeath", new String[] {""}));
        }
    }

    @Test
    void handlesConsoleUsagePermissionsAndReloadFailure() throws Exception {
        AtomicInteger reloads = new AtomicInteger();
        try (DeathStore store = store()) {
            DeathCommands handler = new DeathCommands(store, () -> defaults, () -> {
                reloads.incrementAndGet();
                return CompletableFuture.completedFuture(false);
            });
            CommandSender console = sender(false, Set.of("plexdeathcoords.admin"));
            handler.onCommand(console, lastDeath, "lastdeath", new String[0]);
            assertEquals("Only players can use this command.", messages.getLast());
            handler.onCommand(console, admin, "plexdeathcoords", new String[0]);
            assertEquals("Usage: /plexdeathcoords reload", messages.getLast());
            handler.onCommand(sender(false, Set.of()), admin, "plexdeathcoords", new String[] {"reload"});
            assertEquals("You do not have permission to use this command.", messages.getLast());
            assertEquals(0, reloads.get());
            handler.onCommand(console, admin, "plexdeathcoords", new String[] {"reload"});
            assertEquals("Configuration could not be reloaded. Check the server log.", messages.getLast());
            assertEquals(1, reloads.get());
        }
    }

    @Test
    void reloadSuccessUsesTheNewMessages() throws Exception {
        Settings changed = settings("messages:\n  reloaded: '<green>Updated</green>'\n");
        AtomicReference<Settings> current = new AtomicReference<>(defaults);
        try (DeathStore store = store()) {
            DeathCommands handler = new DeathCommands(store, current::get, () -> {
                current.set(changed);
                return CompletableFuture.completedFuture(true);
            });
            handler.onCommand(sender(false, Set.of("plexdeathcoords.admin")),
                    admin, "plexdeathcoords", new String[] {"reload"});
            assertEquals("Updated", messages.getLast());
        }
    }

    @Test
    void returnsHelpfulUsageForInvalidAndExcessArguments() throws Exception {
        try (DeathStore store = store()) {
            DeathCommands handler = new DeathCommands(store, () -> defaults, () -> CompletableFuture.completedFuture(true));
            CommandSender sender = sender(true, Set.of("plexdeathcoords.use"));
            handler.onCommand(sender, lastDeath, "deathcoords", new String[] {"unknown"});
            assertEquals("Usage: /deathcoords [clear|compass]", messages.getLast());
            handler.onCommand(sender, lastDeath, "lastdeath", new String[] {"clear", "extra"});
            assertEquals("Usage: /lastdeath [clear|compass]", messages.getLast());
        }
    }

    @Test
    void filtersSuggestionsByPrefixAndPermissions() throws Exception {
        try (DeathStore store = store()) {
            DeathCommands handler = new DeathCommands(store, () -> defaults, () -> CompletableFuture.completedFuture(true));
            CommandSender player = sender(true, Set.of("plexdeathcoords.use", "plexdeathcoords.clear"));
            assertEquals(List.of("clear"), handler.onTabComplete(player, lastDeath, "lastdeath", new String[] {"C"}));
            assertEquals(List.of(), handler.onTabComplete(player, lastDeath, "lastdeath", new String[] {"z"}));
            assertEquals(List.of(), handler.onTabComplete(player, lastDeath, "lastdeath", new String[] {"clear", ""}));
            assertEquals(List.of(), handler.onTabComplete(sender(true, Set.of("plexdeathcoords.clear")),
                    lastDeath, "lastdeath", new String[] {""}));
            assertEquals(List.of(), handler.onTabComplete(sender(false, Set.of()), admin, "plexdeathcoords", new String[] {""}));
            assertEquals(List.of("reload"), handler.onTabComplete(sender(false, Set.of("plexdeathcoords.admin")),
                    admin, "plexdeathcoords", new String[] {"re"}));
        }
    }

    @Test
    void waitsForReloadCompletionAndRejectsDuplicateRequests() throws Exception {
        CompletableFuture<Boolean> completion = new CompletableFuture<>();
        AtomicInteger reloads = new AtomicInteger();
        AtomicReference<Settings> current = new AtomicReference<>(defaults);
        Settings changed = settings("messages:\n  reloaded: '<green>Updated</green>'\n");
        try (DeathStore store = store()) {
            DeathCommands handler = new DeathCommands(store, current::get, () -> {
                reloads.incrementAndGet();
                return completion;
            });
            CommandSender console = sender(false, Set.of("plexdeathcoords.admin"));
            handler.onCommand(console, admin, "plexdeathcoords", new String[] {"reload"});
            assertTrue(messages.isEmpty());
            handler.onCommand(console, admin, "plexdeathcoords", new String[] {"reload"});
            assertEquals(1, reloads.get());
            assertEquals("Configuration reload is already in progress.", messages.getLast());
            current.set(changed);
            completion.complete(true);
            assertEquals("Updated", messages.getLast());
            handler.onCommand(console, admin, "plexdeathcoords", new String[] {"reload"});
            assertEquals(2, reloads.get());
        }
    }

    @Test
    void failedReloadKeepsWorkingSettingsAndSavedData() throws Exception {
        AtomicReference<Settings> current = new AtomicReference<>(defaults);
        try (DeathStore store = store()) {
            DeathLocation saved = death();
            store.put(playerId, saved);
            DeathCommands handler = new DeathCommands(store, current::get, () -> {
                try {
                    Settings loaded = settings("coordinate-precision: wrong\n");
                    current.set(loaded);
                    return CompletableFuture.completedFuture(true);
                } catch (Exception exception) {
                    return CompletableFuture.completedFuture(false);
                }
            });
            handler.onCommand(sender(false, Set.of("plexdeathcoords.admin")),
                    admin, "plexdeathcoords", new String[] {"reload"});
            assertSame(defaults, current.get());
            assertEquals(saved, store.get(playerId));
            assertEquals("Configuration could not be reloaded. Check the server log.", messages.getLast());
            handler.onCommand(sender(true, Set.of("plexdeathcoords.use")), lastDeath, "lastdeath", new String[0]);
            assertTrue(messages.getLast().startsWith("Last death: -10, 64, 20 in world_the_end"));
        }
    }

    @Test
    void configurationReloadAppliesDisplayAndClearSettings() throws Exception {
        AtomicReference<Settings> current = new AtomicReference<>(defaults);
        try (DeathStore store = store()) {
            store.put(playerId, death());
            DeathCommands handler = new DeathCommands(store, current::get, () -> {
                try {
                    current.set(settings("coordinate-precision: 2\nshow-world: false\nshow-time: false\nallow-clear: false\n"
                            + "clickable-coordinates: false\nrespawn-reminder: true\nallow-compass: false\n"));
                    return CompletableFuture.completedFuture(true);
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            });
            handler.onCommand(sender(false, Set.of("plexdeathcoords.admin")),
                    admin, "plexdeathcoords", new String[] {"reload"});
            CommandSender player = sender(true, Set.of("plexdeathcoords.use", "plexdeathcoords.clear"));
            handler.onCommand(player, lastDeath, "lastdeath", new String[0]);
            assertEquals("Last death: -10.00, 64.00, 20.00", messages.getLast());
            assertTrue(current.get().respawnReminder());
            assertFalse(current.get().allowCompass());
            handler.onCommand(player, lastDeath, "lastdeath", new String[] {"clear"});
            assertEquals("Clearing death locations is disabled.", messages.getLast());
            assertNotNull(store.get(playerId));
            assertEquals(List.of(), handler.onTabComplete(player, lastDeath, "lastdeath", new String[] {""}));
        }
    }

    @Test
    void newSenderWithSameUuidRetrievesThePersistedDeath() throws Exception {
        DeathLocation saved = death();
        try (DeathStore store = store()) {
            store.put(playerId, saved);
        }
        try (DeathStore store = store()) {
            DeathCommands handler = new DeathCommands(store, () -> defaults, () -> CompletableFuture.completedFuture(true));
            handler.onCommand(sender(true, Set.of("plexdeathcoords.use")), lastDeath, "lastdeath", new String[0]);
            String firstResponse = messages.getLast();
            handler.onCommand(sender(true, Set.of("plexdeathcoords.use")), lastDeath, "deathcoords", new String[0]);
            assertEquals(firstResponse, messages.getLast());
            assertEquals(saved, store.get(playerId));
        }
    }

    @Test
    void pluginMetadataRegistersTheImplementedCommandsAndPermissions() throws Exception {
        PluginDescriptionFile description;
        try (var stream = Objects.requireNonNull(getClass().getResourceAsStream("/plugin.yml"))) {
            description = new PluginDescriptionFile(stream);
        }
        assertTrue(JavaPlugin.class.isAssignableFrom(Class.forName(description.getMain(), false, getClass().getClassLoader())));
        assertEquals(Set.of("lastdeath", "plexdeathcoords"), description.getCommands().keySet());
        assertEquals(List.of("deathcoords"), description.getCommands().get("lastdeath").get("aliases"));
        assertEquals("1.1.0", description.getVersion());
        Map<String, PermissionDefault> permissions = description.getPermissions().stream()
                .collect(Collectors.toMap(Permission::getName, Permission::getDefault));
        assertEquals(Map.of("plexdeathcoords.use", PermissionDefault.TRUE,
                "plexdeathcoords.clear", PermissionDefault.TRUE,
                "plexdeathcoords.compass", PermissionDefault.TRUE,
                "plexdeathcoords.admin", PermissionDefault.OP), permissions);
    }

    private CommandSender sender(boolean player, Set<String> permissions) {
        Class<?> type = player ? Player.class : CommandSender.class;
        return (CommandSender) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> playerId;
                    case "hasPermission" -> permissions.contains(args[0]);
                    case "sendMessage" -> {
                        if (args[0] instanceof Component component) {
                            messages.add(PlainTextComponentSerializer.plainText().serialize(component));
                        } else {
                            throw new AssertionError("Expected an Adventure component");
                        }
                        yield null;
                    }
                    case "toString" -> type.getSimpleName();
                    default -> throw new AssertionError("Unexpected server interaction: " + method.getName());
                });
    }

    private Command command(String name) {
        return new Command(name) {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                throw new AssertionError("Use the command handler under test");
            }
        };
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

    private DeathLocation death() {
        return new DeathLocation(UUID.randomUUID(), "world_the_end", -10, 64, 20, System.currentTimeMillis());
    }
}

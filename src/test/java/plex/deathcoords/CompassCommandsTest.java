package plex.deathcoords;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class CompassCommandsTest {
    @TempDir
    Path directory;
    private final UUID playerId = UUID.randomUUID();
    private final UUID worldId = UUID.randomUUID();
    private final List<String> messages = new ArrayList<>();
    private final Set<String> permissions = new java.util.HashSet<>(Set.of("plexdeathcoords.use", "plexdeathcoords.compass"));
    private final AtomicReference<Location> target = new AtomicReference<>();
    private boolean tracked = true;
    private boolean loaded = true;
    private boolean sameWorld = true;
    private boolean acceptsMeta = true;
    private Material held = Material.COMPASS;
    private int updates;
    private final Command command = new Command("lastdeath") {
        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            throw new AssertionError("Use handler");
        }
    };

    @Test
    void targetsHeldCompassWithoutLodestoneValidationInEveryDimension() throws Exception {
        AtomicReference<Settings> current = new AtomicReference<>(settings(""));
        try (DeathStore store = store()) {
            DeathCommands handler = new DeathCommands(store, current::get, () -> CompletableFuture.completedFuture(true));
            Player player = player();
            for (World.Environment environment : List.of(World.Environment.NORMAL, World.Environment.NETHER, World.Environment.THE_END)) {
                store.put(playerId, new DeathLocation(worldId, environment.name(), -10.6, 64.25, 20.9, 0));
                handler.onCommand(player, command, "deathcoords", new String[] {"compass"});
                assertEquals(-10.6, target.get().getX());
                assertEquals(worldId, target.get().getWorld().getUID());
                assertFalse(tracked);
                assertTrue(messages.getLast().startsWith("Your held compass now points to -11, 64, 21 in " + environment.name()));
            }
            assertEquals(3, updates);
            assertEquals(List.of("compass"), handler.onTabComplete(player, command, "lastdeath", new String[] {"co"}));
            current.set(settings("allow-compass: false\n"));
            handler.onCommand(player, command, "lastdeath", new String[] {"compass"});
            assertEquals("Death compass tracking is disabled.", messages.getLast());
            assertEquals(List.of(), handler.onTabComplete(player, command, "lastdeath", new String[] {"co"}));
            assertEquals(3, updates);
        }
    }

    @Test
    void rejectsMissingDeathsUnloadedAndDifferentWorldsAndUnsuitableItems() throws Exception {
        Settings settings = settings("");
        try (DeathStore store = store()) {
            DeathCommands handler = new DeathCommands(store, () -> settings, () -> CompletableFuture.completedFuture(true));
            Player player = player();
            handler.onCommand(player, command, "lastdeath", new String[] {"compass"});
            assertEquals("You have no saved death location.", messages.getLast());
            store.put(playerId, new DeathLocation(worldId, "world", 1, 2, 3, 0));
            loaded = false;
            handler.onCommand(player, command, "lastdeath", new String[] {"compass"});
            assertEquals("The world of your last death is not loaded.", messages.getLast());
            loaded = true;
            sameWorld = false;
            handler.onCommand(player, command, "lastdeath", new String[] {"compass"});
            assertEquals("Travel to the world of your last death before tracking it.", messages.getLast());
            sameWorld = true;
            for (Material material : List.of(Material.AIR, Material.STONE, Material.RECOVERY_COMPASS)) {
                held = material;
                handler.onCommand(player, command, "lastdeath", new String[] {"compass"});
                assertEquals("Hold a regular compass in your main hand first.", messages.getLast());
            }
            assertEquals(0, updates);
            assertNull(target.get());
            held = Material.COMPASS;
            acceptsMeta = false;
            handler.onCommand(player, command, "lastdeath", new String[] {"compass"});
            assertEquals("Your held compass could not be updated.", messages.getLast());
        }
    }

    @Test
    void deniesCompassWithoutBothPermissionsAndDoesNotSuggestIt() throws Exception {
        Settings settings = settings("");
        try (DeathStore store = store()) {
            DeathCommands handler = new DeathCommands(store, () -> settings, () -> CompletableFuture.completedFuture(true));
            Player player = player();
            for (String permission : List.of("plexdeathcoords.use", "plexdeathcoords.compass")) {
                permissions.remove(permission);
                handler.onCommand(player, command, "lastdeath", new String[] {"compass"});
                assertEquals("You do not have permission to use this command.", messages.getLast());
                assertEquals(List.of(), handler.onTabComplete(player, command, "lastdeath", new String[] {"co"}));
                permissions.add(permission);
            }
            assertEquals(0, updates);
        }
    }

    private Player player() {
        World world = proxy(World.class, (method, args) -> switch (method) {
            case "getUID" -> worldId;
            default -> throw new AssertionError(method);
        });
        Server server = proxy(Server.class, (method, args) -> switch (method) {
            case "getWorld" -> { assertEquals(worldId, args[0]); yield loaded ? world : null; }
            default -> throw new AssertionError(method);
        });
        CompassMeta meta = proxy(CompassMeta.class, (method, args) -> switch (method) {
            case "setLodestone" -> { target.set((Location) args[0]); yield null; }
            case "setLodestoneTracked" -> { tracked = (boolean) args[0]; yield null; }
            default -> throw new AssertionError(method);
        });
        ItemStack item = new ItemStack() {
            @Override
            public Material getType() { return held; }
            @Override
            public ItemMeta getItemMeta() { return meta; }
            @Override
            public boolean setItemMeta(ItemMeta value) {
                assertSame(meta, value);
                updates++;
                return acceptsMeta;
            }
        };
        PlayerInventory inventory = proxy(PlayerInventory.class, (method, args) -> switch (method) {
            case "getItemInMainHand" -> item;
            default -> throw new AssertionError(method);
        });
        return proxy(Player.class, (method, args) -> switch (method) {
            case "getUniqueId" -> playerId;
            case "hasPermission" -> permissions.contains(args[0]);
            case "getServer" -> server;
            case "getWorld" -> sameWorld ? world : proxy(World.class, (name, values) -> UUID.randomUUID());
            case "getInventory" -> inventory;
            case "sendMessage" -> {
                messages.add(PlainTextComponentSerializer.plainText().serialize((Component) args[0]));
                yield null;
            }
            default -> throw new AssertionError(method);
        });
    }

    private <T> T proxy(Class<T> type, java.util.function.BiFunction<String, Object[], Object> handler) {
        return type.cast(Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {type},
                (instance, method, args) -> handler.apply(method.getName(), args)));
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
}

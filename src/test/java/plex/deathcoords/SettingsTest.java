package plex.deathcoords;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SettingsTest {
    @TempDir
    Path directory;

    @Test
    void formatsCoordinatesAndTimeFromDefaultsWithoutLookingUpWorld() throws Exception {
        Settings settings = load("");
        DeathLocation death = new DeathLocation(UUID.randomUUID(), "world_nether", -10.6, 64.25, 5.2, 1000);
        assertEquals("Last death: -11, 64, 5 in world_nether (1m ago)", plain(settings.coordinates(death, 61_000)));
    }

    @Test
    void respectsPrecisionAndHidesOptionalSections() throws Exception {
        Settings settings = load("coordinate-precision: 2\nshow-world: false\nshow-time: false\n");
        DeathLocation death = new DeathLocation(UUID.randomUUID(), "world", -10.125, 64, 0.001, 1);
        assertEquals("Last death: -10.13, 64.00, 0.00", plain(settings.coordinates(death, 2)));
    }

    @Test
    void treatsWorldNamesAsPlainText() throws Exception {
        Settings settings = load("");
        DeathLocation death = new DeathLocation(UUID.randomUUID(), "<red>world</red>", 0, 64, 0, 1);
        assertTrue(plain(settings.coordinates(death, 1)).contains("<red>world</red>"));
    }

    @Test
    void formatsAgeBoundariesAndClockMovingBackwards() throws Exception {
        Settings settings = load("");
        DeathLocation death = new DeathLocation(UUID.randomUUID(), "world", 0, 64, 0, 1000);
        assertTrue(plain(settings.coordinates(death, 0)).endsWith("(0s ago)"));
        assertTrue(plain(settings.coordinates(death, 60_000)).endsWith("(59s ago)"));
        assertTrue(plain(settings.coordinates(death, 3_601_000)).endsWith("(1h ago)"));
        assertTrue(plain(settings.coordinates(death, 86_401_000)).endsWith("(1d ago)"));
    }

    @Test
    void rejectsInvalidSettingsInsteadOfCoercingThem() throws Exception {
        for (String invalid : new String[] {
                "coordinate-precision: -1\n", "coordinate-precision: 7\n",
                "coordinate-precision: 1.5\n", "show-world: maybe\n", "allow-clear: []\n",
                "messages: wrong\n", "messages:\n  no-death: 1\n",
                "messages:\n  no-death: '<red>Missing close'\n", "show-time: 'null'\n"
        }) {
            assertThrows(IllegalArgumentException.class, () -> load(invalid), invalid);
        }
    }

    @Test
    void rejectsMalformedYaml() {
        assertThrows(IOException.class, () -> load("messages: [unfinished\n"));
    }

    @Test
    void readsUpdatedMessagesAndFlags() throws Exception {
        Settings settings = load("allow-clear: false\nmessages:\n  no-death: '<gold>No record</gold>'\n  days: '<value> days'\n");
        assertFalse(settings.allowClear());
        assertEquals("No record", plain(settings.message("no-death")));
        DeathLocation death = new DeathLocation(UUID.randomUUID(), "world", 0, 64, 0, 0);
        assertTrue(plain(settings.coordinates(death, 172_800_000)).contains("2 days ago"));
    }

    @Test
    void rejectsDuplicateKeysAndExplicitNullSettings() throws Exception {
        assertThrows(IOException.class, () -> load("allow-clear: false\nallow-clear: true\n"));
        for (String invalid : new String[] {
                "show-world: null\n", "messages:\n  no-death: null\n"
        }) {
            assertThrows(IllegalArgumentException.class, () -> load(invalid), invalid);
        }
    }

    @Test
    void ignoresUnknownYamlDataWithoutDeserializingBukkitObjects() throws Exception {
        Settings settings = load("unknown:\n  ==: org.bukkit.inventory.ItemStack\n  type: STONE\n");
        assertTrue(settings.allowClear());
        assertEquals("You have no saved death location.", plain(settings.message("no-death")));
    }

    @Test
    void worldAndTimeTogglesWorkIndependently() throws Exception {
        DeathLocation death = new DeathLocation(UUID.randomUUID(), "world_the_end", 0, 64, 0, 1000);
        assertEquals("Last death: 0, 64, 0 (1m ago)",
                plain(load("show-world: false\n").coordinates(death, 61_000)));
        assertEquals("Last death: 0, 64, 0 in world_the_end",
                plain(load("show-time: false\n").coordinates(death, 61_000)));
    }

    private Settings load(String yaml) throws Exception {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, yaml);
        try (Reader defaults = new InputStreamReader(Objects.requireNonNull(
                getClass().getResourceAsStream("/config.yml")), StandardCharsets.UTF_8)) {
            return Settings.load(file, defaults);
        }
    }

    private String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}

package plex.deathcoords;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
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
    void coordinatesCopyFormattedTripleAndKeepWorldAndTimeOutsideTheClick() throws Exception {
        Settings settings = load("coordinate-precision: 2\nmessages:\n  copy-hover: '<gold>Copy <world></gold>'\n");
        DeathLocation death = new DeathLocation(UUID.randomUUID(), "<red>nether</red>", -10.125, 64, 0.001, 1000);
        Component result = settings.coordinates(death, 61_000);
        java.util.List<Component> clickable = new java.util.ArrayList<>();
        collectClickable(result, clickable);
        assertFalse(clickable.isEmpty());
        for (Component part : clickable) {
            assertEquals(ClickEvent.copyToClipboard("-10.13, 64.00, 0.00"), part.clickEvent());
            assertFalse(plain(part).contains("nether"));
            assertFalse(plain(part).contains("ago"));
            assertNotNull(part.hoverEvent());
            assertEquals("Copy <red>nether</red>", plain((Component) part.hoverEvent().value()));
        }
        Component disabled = load("clickable-coordinates: false\n").coordinates(death, 61_000);
        clickable.clear();
        collectClickable(disabled, clickable);
        assertTrue(clickable.isEmpty());
    }

    @Test
    void legacyCoordinateTemplatesRemainClickableWithoutChangingTheirText() throws Exception {
        Settings settings = load("messages:\n  last-death: '<gray>At <x> / <y> / <z><world_part></gray>'\n");
        Component result = settings.coordinates(new DeathLocation(UUID.randomUUID(), "world", 1, 2, 3, 0), 0);
        assertEquals("At 1 / 2 / 3 in world", plain(result));
        java.util.List<Component> clickable = new java.util.ArrayList<>();
        collectClickable(result, clickable);
        assertEquals(3, clickable.size());
        clickable.forEach(part -> assertEquals(ClickEvent.copyToClipboard("1, 2, 3"), part.clickEvent()));
    }

    private void collectClickable(Component component, java.util.List<Component> result) {
        if (component.clickEvent() != null) {
            result.add(component);
        }
        component.children().forEach(child -> collectClickable(child, result));
    }

    @Test
    void oldConfigurationGetsNewDefaultsWithoutBeingRewritten() throws Exception {
        String legacy = "coordinate-precision: 1\nshow-world: true\nshow-time: false\nallow-clear: false\n"
                + "messages:\n  last-death: 'Saved: <x>, <y>, <z><world_part>'\n";
        Settings settings = load(legacy);
        assertTrue(settings.allowCompass());
        assertFalse(settings.respawnReminder());
        assertFalse(settings.allowClear());
        assertEquals(legacy, Files.readString(directory.resolve("config.yml")));
        DeathLocation death = new DeathLocation(UUID.randomUUID(), "world", 1, 2, 3, 0);
        assertEquals("Saved: 1.0, 2.0, 3.0 in world", plain(settings.coordinates(death, 1000)));
        assertEquals("Your last death: 1.0, 2.0, 3.0 in world",
                plain(settings.coordinates("respawn-reminder", death, 1000)));
    }

    @Test
    void validatesNewFlagsAndMessagesAndLoadsReminderCustomization() throws Exception {
        for (String invalid : new String[] {"clickable-coordinates: null\n", "respawn-reminder: yesplease\n",
                "allow-compass: []\n", "messages:\n  copy-hover: 42\n",
                "messages:\n  respawn-reminder: '<red>Unclosed'\n"}) {
            assertThrows(IllegalArgumentException.class, () -> load(invalid), invalid);
        }
        Settings settings = load("respawn-reminder: true\nallow-compass: false\nmessages:\n"
                + "  respawn-reminder: '<gold>Return to <coordinates> in <world> (<ago>)</gold>'\n");
        assertTrue(settings.respawnReminder());
        assertFalse(settings.allowCompass());
        DeathLocation death = new DeathLocation(UUID.randomUUID(), "<red>world</red>", 1, 2, 3, 0);
        Component reminder = settings.coordinates("respawn-reminder", death, 60_000);
        assertEquals("Return to 1, 2, 3 in <red>world</red> (1m)", plain(reminder));
        java.util.List<Component> clickable = new java.util.ArrayList<>();
        collectClickable(reminder, clickable);
        assertEquals(1, clickable.size());
    }

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

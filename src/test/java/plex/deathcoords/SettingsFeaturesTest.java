package plex.deathcoords;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class SettingsFeaturesTest {
    @TempDir
    Path temp;

    private Settings settings(String config) throws IOException {
        Path file = temp.resolve("config.yml");
        Files.writeString(file, config, StandardCharsets.UTF_8);
        try (InputStream resource = Objects.requireNonNull(getClass().getResourceAsStream("/config.yml"));
             InputStreamReader defaults = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            return Settings.load(file, defaults);
        }
    }

    private DeathLocation death() {
        return new DeathLocation(UUID.randomUUID(), "world", 10.4, -2.0, 3.0, 1_000L);
    }

    @Test
    void clickableCoordinatesCopyRoundedCoordinates() throws IOException {
        Settings settings = settings("clickable-coordinates: true\ncoordinate-precision: 0\n");
        Component result = settings.coordinates(death(), 2_000L);
        assertNotNull(result.clickEvent());
        assertEquals("10 -2 3", result.clickEvent().value());
        assertNotNull(result.hoverEvent());
    }

    @Test
    void disabledClickableCoordinatesHaveNoClickAction() throws IOException {
        Settings settings = settings("clickable-coordinates: false\n");
        assertNull(settings.coordinates(death(), 2_000L).clickEvent());
    }

    @Test
    void respawnReminderCanBeEnabledAndRendered() throws IOException {
        Settings settings = settings("respawn-reminder: true\n");
        assertTrue(settings.respawnReminder());
        assertNotNull(settings.respawnMessage(death(), 2_000L));
    }

    @Test
    void compassOptionIsConfigurable() throws IOException {
        assertTrue(settings("allow-compass: true\n").allowCompass());
        assertFalse(settings("allow-compass: false\n").allowCompass());
    }

    @Test
    void invalidOptionTypeFailsWithoutBeingSilentlyAccepted() {
        assertThrows(IllegalArgumentException.class,
                () -> settings("clickable-coordinates: 'yes'\n"));
    }
}
